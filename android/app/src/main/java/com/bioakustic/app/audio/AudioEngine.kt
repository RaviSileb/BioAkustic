package com.bioakustic.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Captures mono audio from the internal microphone.
 *
 * Strategy (Galaxy S23 Ultra): UNPROCESSED source at 48 kHz float, with
 * graceful fallbacks (VOICE_RECOGNITION, DEFAULT, 16-bit encoding).
 * Keeps a rolling buffer so the detector can snapshot seconds of audio
 * including the past (pre-roll).
 */
class AudioEngine(private val context: Context) {

    data class Frame(
        val samples: FloatArray,
        val sampleRate: Int,
        /** Signal level in dBFS (roughly -90 .. 0). */
        val levelDb: Double,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var record: AudioRecord? = null
    private var job: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _status = MutableStateFlow("Zastaveno")
    val status: StateFlow<String> = _status

    var actualSampleRate: Int = SAMPLE_RATE
        private set
    var actualSourceName: String = "-"
        private set

    private val lock = Any()
    private var ring = FloatArray(SAMPLE_RATE * RING_SECONDS)
    private var ringPos = 0
    private var ringCount = 0

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Starts capture. [onFrame] is invoked on a background thread for every
     * chunk read from the microphone.
     */
    fun start(onFrame: (Frame) -> Unit): Boolean {
        if (_running.value) return true
        if (!hasPermission()) {
            _status.value = "Chybí oprávnění k mikrofonu"
            return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessedSupported =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = listOfNotNull(
            if (unprocessedSupported) MediaRecorder.AudioSource.UNPROCESSED else null,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT,
        )
        val encodings = listOf(
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        var lastError = "unknown error"
        for (source in sources) {
            for (encoding in encodings) {
                val rec = tryOpen(source, encoding)
                if (rec == null) {
                    lastError = "init failed (source=$source, encoding=$encoding)"
                    continue
                }
                try {
                    rec.startRecording()
                } catch (e: Exception) {
                    lastError = e.message ?: "start failed"
                    rec.release()
                    continue
                }
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    lastError = "not recording (source=$source)"
                    runCatching { rec.stop() }
                    rec.release()
                    continue
                }
                record = rec
                actualSampleRate = rec.sampleRate
                actualSourceName = sourceName(source) +
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) " / float" else " / 16-bit"
                synchronized(lock) {
                    ringPos = 0
                    ringCount = 0
                }
                startLoop(rec, encoding, onFrame)
                _running.value = true
                _status.value = "Nahrávání: $actualSourceName @ $actualSampleRate Hz"
                return true
            }
        }
        _status.value = "Mikrofon nelze otevřít: $lastError"
        return false
    }

    fun stop() {
        job?.cancel()
        job = null
        val rec = record
        record = null
        if (rec != null) {
            runCatching {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
            }
            rec.release()
        }
        _running.value = false
        _status.value = "Zastaveno"
    }

    fun release() {
        stop()
        scope.cancel()
    }

    /** Returns up to [seconds] of the most recent audio (oldest first). */
    fun snapshot(seconds: Float): FloatArray {
        val want = (actualSampleRate * seconds).toInt().coerceAtLeast(1)
        synchronized(lock) {
            val available = minOf(want, ringCount, ring.size)
            val out = FloatArray(available)
            var readPos = ringPos - available
            if (readPos < 0) readPos += ring.size
            for (i in 0 until available) {
                out[i] = ring[(readPos + i) % ring.size]
            }
            return out
        }
    }

    private fun tryOpen(source: Int, encoding: Int): AudioRecord? {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, encoding
            )
            if (minBuf <= 0) return null
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(encoding)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val rec = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 4)
                .build()
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                null
            } else {
                rec
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun startLoop(rec: AudioRecord, encoding: Int, onFrame: (Frame) -> Unit) {
        job = scope.launch {
            val chunkFloat = FloatArray(READ_CHUNK)
            val chunkShort = ShortArray(READ_CHUNK)
            val mono = FloatArray(READ_CHUNK)
            while (isActive) {
                val n = try {
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        rec.read(chunkFloat, 0, READ_CHUNK, AudioRecord.READ_BLOCKING)
                    } else {
                        val count = rec.read(chunkShort, 0, READ_CHUNK, AudioRecord.READ_BLOCKING)
                        if (count > 0) {
                            for (i in 0 until count) {
                                chunkFloat[i] = chunkShort[i] / 32768f
                            }
                        }
                        count
                    }
                } catch (_: Exception) {
                    break
                }
                if (n == null || n <= 0) continue
                System.arraycopy(chunkFloat, 0, mono, 0, n)
                appendRing(mono, n)
                val levelDb = levelDb(mono, n)
                val copy = mono.copyOf(n)
                try {
                    onFrame(Frame(copy, actualSampleRate, levelDb))
                } catch (_: Exception) {
                    // A failing observer must not kill the loop.
                }
            }
        }
    }

    private fun appendRing(samples: FloatArray, n: Int) {
        synchronized(lock) {
            for (i in 0 until n) {
                ring[ringPos] = samples[i]
                ringPos++
                if (ringPos >= ring.size) ringPos = 0
            }
            ringCount = minOf(ringCount + n, ring.size)
        }
    }

    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        else -> "DEFAULT"
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        private const val RING_SECONDS = 12
        private const val READ_CHUNK = 4096

        fun levelDb(samples: FloatArray, n: Int = samples.size): Double {
            if (n <= 0) return -90.0
            var sum = 0.0
            for (i in 0 until n) {
                val s = samples[i].toDouble()
                sum += s * s
            }
            val rms = sqrt(sum / n)
            return if (rms <= 1e-9) -90.0 else (20.0 * log10(rms)).coerceIn(-90.0, 0.0)
        }
    }
}
