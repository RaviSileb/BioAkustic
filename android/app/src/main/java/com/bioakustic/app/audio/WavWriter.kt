package com.bioakustic.app.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Writes mono float samples (-1..1) as 16-bit PCM WAV.
 */
object WavWriter {
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        require(sampleRate > 0) { "Invalid sample rate: $sampleRate" }
        val dataBytes = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes)
        FileOutputStream(file).use { out ->
            out.write(header.array())
            val buf = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            var i = 0
            while (i < samples.size) {
                buf.clear()
                val end = minOf(i + 4096, samples.size)
                for (j in i until end) {
                    val v = (samples[j].coerceIn(-1f, 1f) * 32767f).roundToInt()
                        .coerceIn(-32768, 32767)
                    buf.putShort(v.toShort())
                }
                out.write(buf.array(), 0, (end - i) * 2)
                i = end
            }
        }
    }
}
