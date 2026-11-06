package com.bioakustic.app.classify

import com.bioakustic.app.audio.Resample
import com.bioakustic.app.data.db.DetectionSource
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

/**
 * BirdNET-compatible TFLite classifier.
 *
 * Expected model: raw-waveform input, 3 s mono @48 kHz float32 ([1, 144000]),
 * single output vector of per-class scores. Labels file: one entry per line,
 * "Scientific name_Common name" (BirdNET GLOBAL_6K format).
 *
 * Delegate strategy on Snapdragon 8 Gen 2: GPU delegate first, NNAPI
 * (Hexagon) on request, plain CPU as the last resort.
 */
class BirdnetTfliteClassifier(
    modelFile: File,
    labelsFile: File,
    useNnapi: Boolean,
) : Classifier {
    override val name: String = "BirdNET (TFLite)"

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val labels: List<Pair<String, String>>
    private var outputSize: Int = 0

    @Volatile
    override var isReady: Boolean = false
        private set

    var delegateUsed: String = "none"
        private set

    init {
        labels = parseLabels(labelsFile.readLines(Charsets.UTF_8))
        require(labels.isNotEmpty()) { "Labels file is empty: $labelsFile" }
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            try {
                GpuDelegate().let {
                    gpuDelegate = it
                    addDelegate(it)
                    delegateUsed = "GPU"
                }
            } catch (_: Throwable) {
                // Covers Exception and linkage Errors (e.g. missing GPU API
                // classes on some builds) so inference still falls back.
                gpuDelegate = null
                if (useNnapi) {
                    runCatching {
                        setUseNNAPI(true)
                        delegateUsed = "NNAPI"
                    }.onFailure {
                        delegateUsed = "CPU"
                    }
                } else {
                    delegateUsed = "CPU"
                }
            }
        }
        val created = try {
            Interpreter(modelFile, options)
        } catch (e: Throwable) {
            // The delegate (usually GPU) rejected this model graph: retry
            // once with a plain interpreter (NNAPI when requested, else CPU).
            runCatching { gpuDelegate?.close() }
            gpuDelegate = null
            val fallback = Interpreter.Options().apply {
                setNumThreads(4)
                if (useNnapi) runCatching { setUseNNAPI(true) }
            }
            delegateUsed = if (useNnapi) "NNAPI" else "CPU"
            try {
                Interpreter(modelFile, fallback)
            } catch (e2: Throwable) {
                e2.addSuppressed(e)
                throw e2
            }
        }
        interpreter = created
        outputSize = created.getOutputTensor(0).shape().reduce { a, b -> a * b }
        isReady = true
    }

    override fun classify(samples: FloatArray, sampleRate: Int): Classifier.Result {
        val interp = interpreter ?: throw IllegalStateException("Classifier is closed")
        val audio48k = Resample.linear(samples, sampleRate, MODEL_RATE)
        val input = FloatArray(MODEL_LEN)
        // Center-crop or center-pad to exactly 3 s.
        if (audio48k.size >= MODEL_LEN) {
            val start = (audio48k.size - MODEL_LEN) / 2
            System.arraycopy(audio48k, start, input, 0, MODEL_LEN)
        } else {
            val start = (MODEL_LEN - audio48k.size) / 2
            System.arraycopy(audio48k, 0, input, start, audio48k.size)
        }
        val inputBuffer = ByteBuffer.allocateDirect(MODEL_LEN * 4)
            .order(ByteOrder.nativeOrder())
        for (v in input) inputBuffer.putFloat(v)
        inputBuffer.rewind()
        val scores = FloatArray(outputSize)
        val output = mapOutput(scores)
        synchronized(interp) {
            interp.run(inputBuffer, output)
        }
        // Raw outputs are logits; sigmoid maps them to 0..1 probabilities.
        // (Ranking by logits is identical since sigmoid is monotonic.)
        val ranked = (0 until outputSize)
            .sortedByDescending { scores[it] }
            .take(TOP_K)
        val best = ranked.first()
        val (common, scientific) = labels.getOrElse(best) { "Neznámý" to "" }
        val topLabels = ranked.map { i ->
            val (c, s) = labels.getOrElse(i) { "Neznámý" to "" }
            Classifier.ScoredLabel(c, s.ifEmpty { null }, sigmoid(scores[i]))
        }
        return Classifier.Result(
            commonName = common,
            scientificName = scientific.ifEmpty { null },
            confidence = sigmoid(scores[best]),
            behavior = null,
            behaviorConfidence = null,
            source = DetectionSource.TFLITE,
            topLabels = topLabels,
        )
    }

    /**
     * Wraps the flat scores array into the nested shape the model expects
     * (supports [N] and [1, N]).
     */
    private fun mapOutput(scores: FloatArray): Any {
        val shape = interpreter?.getOutputTensor(0)?.shape() ?: intArrayOf(scores.size)
        return if (shape.size == 2) arrayOf(scores) else scores
    }

    override fun close() {
        isReady = false
        runCatching { interpreter?.close() }
        interpreter = null
        runCatching { gpuDelegate?.close() }
        gpuDelegate = null
    }

    companion object {
        const val MODEL_RATE = 48_000
        const val MODEL_SECONDS = 3
        const val MODEL_LEN = MODEL_RATE * MODEL_SECONDS
        const val TOP_K = 5

        fun sigmoid(logit: Float): Float =
            (1.0 / (1.0 + kotlin.math.exp(-logit.toDouble()))).toFloat()

        /** Parses BirdNET "Scientific name_Common name" lines into (common, scientific). */
        fun parseLabels(lines: List<String>): List<Pair<String, String>> =
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    val sep = line.indexOf('_')
                    if (sep > 0) {
                        line.substring(sep + 1) to line.substring(0, sep)
                    } else {
                        line to ""
                    }
                }
    }
}
