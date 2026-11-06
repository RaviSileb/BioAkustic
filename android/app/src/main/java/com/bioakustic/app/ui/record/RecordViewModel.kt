package com.bioakustic.app.ui.record

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bioakustic.app.AppContainer
import com.bioakustic.app.audio.AudioEngine
import com.bioakustic.app.audio.SpectrumAnalyzer
import com.bioakustic.app.audio.WavWriter
import com.bioakustic.app.data.db.DetectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live monitoring: audio capture, spectrogram columns, level meter and the
 * automatic detection pipeline (trigger -> classify -> WAV + history entry).
 */
class RecordViewModel(private val container: AppContainer) : ViewModel() {

    val running: StateFlow<Boolean> = container.audioEngine.running
    val engineStatus: StateFlow<String> = container.audioEngine.status

    private val _levelDb = MutableStateFlow(-90.0)
    val levelDb: StateFlow<Double> = _levelDb

    /** Newest column last, values are normalized 0..1. */
    val columns = mutableStateListOf<FloatArray>()

    private val _info = MutableStateFlow("")
    val info: StateFlow<String> = _info

    private val _lastDetection = MutableStateFlow<DetectionEntity?>(null)
    val lastDetection: StateFlow<DetectionEntity?> = _lastDetection

    private val _lastResults =
        MutableStateFlow<List<com.bioakustic.app.classify.Classifier.ScoredLabel>>(emptyList())
    /** Ranked recognition candidates of the last detection, best first. */
    val lastResults: StateFlow<List<com.bioakustic.app.classify.Classifier.ScoredLabel>> = _lastResults

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _autoDetect = MutableStateFlow(container.autoDetect)
    val autoDetect: StateFlow<Boolean> = _autoDetect

    private val _thresholdDb = MutableStateFlow(container.thresholdDb)
    val thresholdDb: StateFlow<Float> = _thresholdDb

    private var analyzer: SpectrumAnalyzer? = null
    private var spectrumTail = FloatArray(0)
    private var aboveSinceMs: Long = 0
    private var lastTriggerMs: Long = 0
    private var saveJob: Job? = null

    val engine: AudioEngine get() = container.audioEngine
    val classifierName: String get() = container.modelManager.classifier.name

    init {
        container.modelManager.refresh(container.useNnapi)
        updateInfo()
    }

    fun setAutoDetect(enabled: Boolean) {
        container.autoDetect = enabled
        _autoDetect.value = enabled
    }

    fun setThresholdDb(value: Float) {
        container.thresholdDb = value
        _thresholdDb.value = value
    }

    fun toggle() {
        if (container.audioEngine.running.value) {
            container.audioEngine.stop()
            updateInfo()
        } else {
            columns.clear()
            spectrumTail = FloatArray(0)
            aboveSinceMs = 0
            val ok = container.audioEngine.start { frame -> onFrame(frame) }
            if (ok) {
                analyzer = SpectrumAnalyzer(sampleRate = container.audioEngine.actualSampleRate)
            }
            updateInfo()
        }
    }

    /** Manually captures the last few seconds as a history entry. */
    fun captureNow() {
        if (!container.audioEngine.running.value || _saving.value) return
        saveDetection(manual = true)
    }

    private fun onFrame(frame: AudioEngine.Frame) {
        _levelDb.value = frame.levelDb
        // Spectrogram column from fixed-size windows.
        val spectrumAnalyzer = analyzer ?: return
        val nFft = spectrumAnalyzer.nFft
        val joined = spectrumTail + frame.samples
        var offset = 0
        var latest: FloatArray? = null
        while (offset + nFft <= joined.size) {
            val window = joined.copyOfRange(offset, offset + nFft)
            val mels = spectrumAnalyzer.analyze(window)
            latest = normalizeColumn(mels)
            offset += nFft / 2
        }
        spectrumTail = joined.copyOfRange(offset, joined.size)
        latest?.let { viewModelScope.launch { pushColumn(it) } }

        // Auto-detection trigger with hold time and cooldown.
        if (!_autoDetect.value || _saving.value) return
        val now = System.currentTimeMillis()
        if (frame.levelDb > _thresholdDb.value) {
            if (aboveSinceMs == 0L) aboveSinceMs = now
            if (now - aboveSinceMs > HOLD_MS && now - lastTriggerMs > COOLDOWN_MS) {
                lastTriggerMs = now
                aboveSinceMs = 0
                saveDetection(manual = false)
            }
        } else {
            aboveSinceMs = 0
        }
    }

    private fun pushColumn(column: FloatArray) {
        columns.add(column)
        while (columns.size > MAX_COLUMNS) columns.removeAt(0)
    }

    private fun saveDetection(manual: Boolean) {
        if (saveJob?.isActive == true) return
        _saving.value = true
        saveJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val engine = container.audioEngine
                val recordedAt = System.currentTimeMillis()
                // CLIP_SECONDS includes pre-roll from the ring buffer.
                val samples = engine.snapshot(CLIP_SECONDS)
                val sampleRate = engine.actualSampleRate
                val result = container.modelManager.classifier.classify(samples, sampleRate)
                // Confidence gate: the behavior estimate is only kept when the
                // top species candidate reaches CONFIDENCE_GATE.
                val gated = result.confidence >= CONFIDENCE_GATE
                _lastResults.value = result.topLabels
                val audioFile = container.repository.newAudioFile(recordedAt)
                withContext(Dispatchers.IO) {
                    WavWriter.write(audioFile, samples, sampleRate)
                }
                val fix = container.locationProvider.getFix()
                val durationMs = (samples.size.toLong() * 1000) / sampleRate
                val entity = DetectionEntity(
                    recordedAt = recordedAt,
                    durationMs = durationMs,
                    audioPath = container.repository.relative(audioFile),
                    sampleRate = sampleRate,
                    speciesCommon = result.commonName,
                    speciesScientific = result.scientificName,
                    confidence = result.confidence,
                    source = if (manual && result.source == "HEURISTIC") {
                        // Manual captures keep the classifier source; the label
                        // stays editable and can be corrected by the user.
                        result.source
                    } else {
                        result.source
                    },
                    behavior = if (gated) result.behavior else null,
                    behaviorConfidence = if (gated) result.behaviorConfidence else null,
                    latitude = fix?.latitude,
                    longitude = fix?.longitude,
                    placeName = null,
                    note = when {
                        manual -> "Ručně uloženo"
                        gated -> "Automatická detekce"
                        else -> "Automatická detekce (nízká spolehlivost, chování neurčeno)"
                    },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                val id = container.repository.insert(entity)
                _lastDetection.value = container.repository.get(id)
                updateInfo()
            } catch (e: Exception) {
                _info.value = "Uložení selhalo: ${e.message}"
            } finally {
                _saving.value = false
            }
        }
    }

    private fun updateInfo() {
        val engine = container.audioEngine
        _info.value = if (engine.running.value) {
            "${engine.actualSourceName} @ ${engine.actualSampleRate} Hz • ${classifierName}"
        } else if (engineStatus.value != "Zastaveno") {
            engineStatus.value
        } else {
            "Klasifikátor: $classifierName"
        }
    }

    private fun normalizeColumn(mels: FloatArray): FloatArray {
        // Log-Mel energies roughly span -8..+2; map to 0..1 for display.
        return FloatArray(mels.size) { i ->
            ((mels[i] - MEL_MIN) / (MEL_MAX - MEL_MIN)).coerceIn(0f, 1f)
        }
    }

    override fun onCleared() {
        container.audioEngine.stop()
    }

    companion object {
        const val MAX_COLUMNS = 140
        const val CLIP_SECONDS = 4f
        /**
         * Minimum top-1 confidence required to keep the behavior estimate.
         * Species candidates below this are still shown in the results table
         * but flagged as low-confidence.
         */
        const val CONFIDENCE_GATE = 0.4f
        private const val HOLD_MS = 500L
        private const val COOLDOWN_MS = 5000L
        private const val MEL_MIN = -7f
        private const val MEL_MAX = 1f
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordViewModel(container) as T
        }
    }
}
