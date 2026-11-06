package com.bioakustic.app.playback

import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simple WAV clip player for history entries.
 */
class AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var pollJob: Job? = null

    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath

    private val _positionMs = MutableStateFlow(0)
    val positionMs: StateFlow<Int> = _positionMs

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs

    fun toggle(file: File) {
        if (_playingPath.value == file.absolutePath) {
            stop()
        } else {
            play(file)
        }
    }

    fun play(file: File) {
        stop()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            player = mp
            _playingPath.value = file.absolutePath
            _durationMs.value = runCatching { mp.duration }.getOrDefault(0)
            mp.setOnCompletionListener { stop() }
            pollJob = scope.launch {
                while (isActive) {
                    _positionMs.value = runCatching { mp.currentPosition }.getOrDefault(0)
                    delay(200)
                }
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        _playingPath.value = null
        _positionMs.value = 0
    }

    fun release() = stop()
}
