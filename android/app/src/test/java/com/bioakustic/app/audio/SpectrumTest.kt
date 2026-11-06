package com.bioakustic.app.audio

import kotlin.math.PI
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumTest {

    @Test
    fun fft_detectsSinePeak() {
        val sampleRate = 48_000
        val size = 2048
        val freq = 440f
        val frame = FloatArray(size) { i ->
            (0.5 * cos(2.0 * PI * freq * i / sampleRate)).toFloat()
        }
        val fft = Fft(size)
        val power = fft.powerSpectrum(frame, FloatArray(size) { 1f })
        var best = 0
        for (i in 1 until power.size) {
            if (power[i] > power[best]) best = i
        }
        // Expected bin: 440 * 2048 / 48000 = 18.77
        assertTrue("peak bin $best near 19", best in 17..20)
    }

    @Test
    fun melBank_dimensionsAndCentroid() {
        val analyzer = SpectrumAnalyzer(sampleRate = 48_000, nFft = 2048, nMels = 64)
        val frame = FloatArray(2048) { i ->
            (0.3 * cos(2.0 * PI * 3000.0 * i / 48_000)).toFloat()
        }
        val mels = analyzer.analyze(frame)
        assertEquals(64, mels.size)
        val centroid = analyzer.centroidHz(analyzer.power(frame))
        assertTrue("centroid $centroid near 3000 Hz", centroid in 2500f..3500f)
    }

    @Test
    fun resample_changesLengthProportionally() {
        val input = FloatArray(1000) { it.toFloat() }
        val out = Resample.linear(input, 48_000, 24_000)
        assertEquals(500, out.size)
        assertEquals(0f, out[0], 1e-3f)
    }

    @Test
    fun levelDb_silenceAndFullScale() {
        assertEquals(-90.0, AudioEngine.levelDb(FloatArray(1024)), 1e-9)
        val full = FloatArray(1024) { 1f }
        assertEquals(0.0, AudioEngine.levelDb(full), 1e-6)
    }
}
