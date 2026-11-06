package com.bioakustic.app.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Short-time spectrum analyzer: STFT magnitudes plus a log-Mel projection
 * used both for the on-screen spectrogram and for classifier features.
 */
class SpectrumAnalyzer(
    val sampleRate: Int,
    val nFft: Int = 2048,
    val nMels: Int = 64,
    val fMin: Float = 50f,
    val fMax: Float = 16_000f,
) {
    private val fft = Fft(nFft)
    private val window = Fft.hannWindow(nFft)
    private val melBank = buildMelBank()

    /**
     * Analyzes one frame ([nFft] samples) and returns log10 Mel energies
     * ([nMels] values, roughly -8 .. +2).
     */
    fun analyze(frame: FloatArray): FloatArray {
        require(frame.size == nFft) { "Expected $nFft samples, got ${frame.size}" }
        val power = fft.powerSpectrum(frame, window)
        val mels = FloatArray(nMels)
        for (m in 0 until nMels) {
            var energy = 0f
            val row = melBank[m]
            for (b in row.indices) {
                energy += power[b] * row[b]
            }
            mels[m] = log10(max(energy, 1e-10f))
        }
        return mels
    }

    /** Raw power spectrum of one frame, for heuristic features. */
    fun power(frame: FloatArray): FloatArray {
        require(frame.size == nFft)
        return fft.powerSpectrum(frame, window)
    }

    /** Spectral centroid in Hz for a power spectrum. */
    fun centroidHz(power: FloatArray): Float {
        var weighted = 0.0
        var total = 0.0
        for (b in power.indices) {
            val freq = b.toDouble() * sampleRate / nFft
            weighted += freq * power[b]
            total += power[b]
        }
        return if (total <= 0.0) 0f else (weighted / total).toFloat()
    }

    /**
     * Spectral flatness (0 = pure tone, 1 = white noise) for tonality cues.
     */
    fun flatness(power: FloatArray): Float {
        var logSum = 0.0
        var arith = 0.0
        var n = 0
        for (b in 1 until power.size) {
            val p = power[b].toDouble()
            if (p <= 0.0) continue
            logSum += kotlin.math.ln(p)
            arith += p
            n++
        }
        if (n == 0) return 1f
        arith /= n
        if (arith <= 0.0) return 1f
        return (kotlin.math.exp(logSum / n) / arith).toFloat().coerceIn(0f, 1f)
    }

    private fun buildMelBank(): Array<FloatArray> {
        val bins = nFft / 2 + 1
        fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)
        fun melToHz(mel: Float): Float = 700f * (10f.pow(mel / 2595f) - 1f)
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax.coerceAtMost(sampleRate / 2f))
        val points = FloatArray(nMels + 2) { i ->
            melToHz(melMin + (melMax - melMin) * i / (nMels + 1))
        }
        val bank = Array(nMels) { FloatArray(bins) }
        for (m in 0 until nMels) {
            val f0 = points[m]
            val f1 = points[m + 1]
            val f2 = points[m + 2]
            for (b in 0 until bins) {
                val freq = b.toFloat() * sampleRate / nFft
                bank[m][b] = when {
                    freq < f0 || freq > f2 -> 0f
                    freq <= f1 -> (freq - f0) / (f1 - f0)
                    else -> (f2 - freq) / (f2 - f1)
                }
            }
        }
        return bank
    }
}

/** Linear resampler for mono float audio. */
object Resample {
    fun linear(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || samples.isEmpty()) return samples
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLen = (samples.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceIn(0, samples.size - 1)
            val i1 = minOf(i0 + 1, samples.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = samples[i0] * (1f - frac) + samples[i1] * frac
        }
        return out
    }
}
