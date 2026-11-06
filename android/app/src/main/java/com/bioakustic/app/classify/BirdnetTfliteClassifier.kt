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
 * "Common name_Scientific name".
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
        labels = labelsFile.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val sep = line.lastIndexOf('_')
                if (sep > 0) {
                    line.substring(0, sep) to line.substring(sep + 1)
                } else {
                    line to ""
                }
            }
        require(labels.isNotEmpty()) { "Labels file is empty: $labelsFile" }
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            try {
                GpuDelegate().let {
                    gpuDelegate = it
                    addDelegate(it)
                    delegateUsed = "GPU"
                }
            } catch (_: Exception) {
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
        } catch (e: Exception) {
            gpuDelegate?.close()
            gpuDelegate = null
            throw e
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
        val ranked = (0 until outputSize)
            .sortedByDescending { scores[it] }
            .take(TOP_K)
        val best = ranked.first()
        val (common, scientific) = labels.getOrElse(best) { "Neznámý" to "" }
        val topLabels = ranked.map { i ->
            val (c, s) = labels.getOrElse(i) { "Neznámý" to "" }
            Classifier.ScoredLabel(c, s.ifEmpty { null }, scores[i].coerceIn(0f, 1f))
        }
        return Classifier.Result(
            commonName = common,
            scientificName = scientific.ifEmpty { null },
            confidence = scores[best].coerceIn(0f, 1f),
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
    }
}
