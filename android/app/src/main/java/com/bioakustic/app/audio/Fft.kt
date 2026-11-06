package com.bioakustic.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radix-2 real-input FFT (in-place, float). Only the forward transform is
 * needed for spectrum/spectrogram analysis.
 */
class Fft(val size: Int) {
    init {
        require(size >= 16 && size and (size - 1) == 0) { "FFT size must be a power of two >= 16" }
    }

    private val rev = IntArray(size)
    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)

    init {
        val bits = 31 - Integer.numberOfLeadingZeros(size)
        for (i in 0 until size) {
            rev[i] = Integer.reverse(i).ushr(32 - bits)
        }
        for (i in 0 until size / 2) {
            val angle = 2.0 * PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
    }

    /** Forward FFT of [re]/[im] (both of length [size]), in place. */
    fun forward(re: FloatArray, im: FloatArray) {
        require(re.size == size && im.size == size)
        for (i in 0 until size) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var k = 0
            while (k < size) {
                for (j in 0 until half) {
                    val wr = cosTable[j * step]
                    val wi = -sinTable[j * step]
                    val xr = re[k + j + half] * wr - im[k + j + half] * wi
                    val xi = re[k + j + half] * wi + im[k + j + half] * wr
                    re[k + j + half] = re[k + j] - xr
                    im[k + j + half] = im[k + j] - xi
                    re[k + j] += xr
                    im[k + j] += xi
                }
                k += len
            }
            len *= 2
        }
    }

    /** Power spectrum (size/2 + 1 bins) of a windowed real frame. */
    fun powerSpectrum(frame: FloatArray, window: FloatArray): FloatArray {
        require(frame.size == size && window.size == size)
        val re = FloatArray(size) { frame[it] * window[it] }
        val im = FloatArray(size)
        forward(re, im)
        val out = FloatArray(size / 2 + 1)
        for (i in out.indices) {
            out[i] = re[i] * re[i] + im[i] * im[i]
        }
        return out
    }

    companion object {
        fun hannWindow(size: Int): FloatArray {
            val w = FloatArray(size)
            for (i in 0 until size) {
                w[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))).toFloat()
            }
            return w
        }
    }
}
