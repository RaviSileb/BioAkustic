package com.bioakustic.app.classify

import com.bioakustic.app.audio.AudioEngine
import com.bioakustic.app.audio.Resample
import com.bioakustic.app.audio.SpectrumAnalyzer
import com.bioakustic.app.data.db.DetectionSource
import kotlin.math.sqrt

/**
 * Fallback detector used when no trained model is installed.
 *
 * It cannot identify species. It reports an "unknown sound" detection with a
 * confidence derived from SNR, plus a rough Morton-style pattern hint
 * (low/harsh vs. high/tonal) that is explicitly marked as heuristic.
 */
class HeuristicClassifier : Classifier {
    override val name: String = "Heuristický detektor"
    override val isReady: Boolean = true

    private val analyzer48k = SpectrumAnalyzer(sampleRate = 48_000)

    override fun classify(samples: FloatArray, sampleRate: Int): Classifier.Result {
        val audio = Resample.linear(samples, sampleRate, 48_000)
        if (audio.size < analyzer48k.nFft) {
            return Classifier.Result(
                commonName = "Neznámý zvuk",
                scientificName = null,
                confidence = 0.1f,
                behavior = null,
                behaviorConfidence = null,
                source = DetectionSource.HEURISTIC,
                topLabels = listOf(Classifier.ScoredLabel("Neznámý zvuk", null, 0.1f)),
            )
        }
        // Analyze the loudest frame to characterize the event.
        val nFft = analyzer48k.nFft
        var bestRms = 0f
        var bestOffset = 0
        var offset = 0
        while (offset + nFft <= audio.size) {
            var sum = 0.0
            for (i in 0 until nFft) {
                val s = audio[offset + i].toDouble()
                sum += s * s
            }
            val rms = sqrt(sum / nFft).toFloat()
            if (rms > bestRms) {
                bestRms = rms
                bestOffset = offset
            }
            offset += nFft / 2
        }
        val frame = audio.copyOfRange(bestOffset, bestOffset + nFft)
        val power = analyzer48k.power(frame)
        val centroid = analyzer48k.centroidHz(power)
        val flatness = analyzer48k.flatness(power)

        // Confidence from SNR-ish estimate: loudest frame vs. whole-clip floor.
        var total = 0.0
        for (s in audio) total += s * s
        val floor = sqrt(total / audio.size).toFloat()
        val snr = if (floor <= 1e-6f) 30f else 20f * kotlin.math.log10(bestRms / floor + 1e-6f)
        val confidence = ((snr - 3f) / 20f).coerceIn(0.15f, 0.6f)

        // Morton-style pattern hint (explicitly heuristic, never a species claim).
        val behavior = when {
            centroid < 900f && flatness > 0.35f ->
                "Nízký/drsný vzorec (může odpovídat varování)"
            centroid > 3000f && flatness < 0.25f ->
                "Vysoký/tónový vzorec (může odpovídat kontaktu)"
            else -> null
        }
        return Classifier.Result(
            commonName = "Neznámý zvuk (automatická detekce)",
            scientificName = null,
            confidence = confidence,
            behavior = behavior,
            behaviorConfidence = if (behavior == null) null else 0.3f,
            source = DetectionSource.HEURISTIC,
            topLabels = listOf(
                Classifier.ScoredLabel(
                    "Neznámý zvuk (automatická detekce)", null, confidence
                )
            ),
        )
    }

    override fun close() = Unit

    companion object {
        /** Minimum clip length the detector accepts (3 s @48 kHz). */
        const val MIN_SAMPLES = 3 * AudioEngine.SAMPLE_RATE
    }
}
