package com.bioakustic.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bioakustic.app.AppContainer
import com.bioakustic.app.data.db.DetectionEntity
import com.bioakustic.app.data.db.DetectionSource
import com.bioakustic.app.data.repo.DetectionRepository
import com.bioakustic.app.util.TimeUtils
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Editable detail of one history entry: species, time, place, note,
 * audio playback and photo/video attachments.
 */
class DetailViewModel(
    private val container: AppContainer,
    private val entryId: Long,
) : ViewModel() {

    private val _entry = MutableStateFlow<DetectionEntity?>(null)
    val entry: StateFlow<DetectionEntity?> = _entry

    // Editable fields.
    val speciesCommon = MutableStateFlow("")
    val speciesScientific = MutableStateFlow("")
    val recordedAtText = MutableStateFlow("")
    val placeName = MutableStateFlow("")
    val latitudeText = MutableStateFlow("")
    val longitudeText = MutableStateFlow("")
    val note = MutableStateFlow("")
    val behavior = MutableStateFlow("")

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating

    val player = container.audioPlayer

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val entity = container.repository.get(entryId)
            _entry.value = entity
            entity?.let {
                speciesCommon.value = it.speciesCommon ?: ""
                speciesScientific.value = it.speciesScientific ?: ""
                recordedAtText.value = TimeUtils.formatForEdit(it.recordedAt)
                placeName.value = it.placeName ?: ""
                latitudeText.value = it.latitude?.toString() ?: ""
                longitudeText.value = it.longitude?.toString() ?: ""
                note.value = it.note ?: ""
                behavior.value = it.behavior ?: ""
            }
        }
    }

    fun photoFiles(): List<File> =
        DetectionRepository.decodePaths(_entry.value?.photoPaths ?: "[]")
            .map { container.repository.resolve(it) }
            .filter { it.exists() }

    fun videoFiles(): List<File> =
        DetectionRepository.decodePaths(_entry.value?.videoPaths ?: "[]")
            .map { container.repository.resolve(it) }
            .filter { it.exists() }

    fun audioFile(): File? =
        _entry.value?.audioPath?.let { container.repository.resolve(it) }?.takeIf { it.exists() }

    fun save() {
        val current = _entry.value ?: return
        val recordedAt = TimeUtils.parseEdited(recordedAtText.value)
        if (recordedAt == null) {
            _message.value = "Neplatný čas. Formát: RRRR-MM-DD HH:MM"
            return
        }
        val lat = latitudeText.value.trim().ifEmpty { null }?.toDoubleOrNull()
        val lon = longitudeText.value.trim().ifEmpty { null }?.toDoubleOrNull()
        if ((latitudeText.value.isNotBlank() && lat == null) ||
            (longitudeText.value.isNotBlank() && lon == null)
        ) {
            _message.value = "Neplatné souřadnice."
            return
        }
        if (lat != null && (lat < -90 || lat > 90)) {
            _message.value = "Zeměpisná šířka musí být -90..90."
            return
        }
        if (lon != null && (lon < -180 || lon > 180)) {
            _message.value = "Zeměpisná délka musí být -180..180."
            return
        }
        viewModelScope.launch {
            val labelChanged = speciesCommon.value.trim() != (current.speciesCommon ?: "") ||
                speciesScientific.value.trim() != (current.speciesScientific ?: "")
            container.repository.update(
                current.copy(
                    speciesCommon = speciesCommon.value.trim().ifEmpty { null },
                    speciesScientific = speciesScientific.value.trim().ifEmpty { null },
                    // A user-corrected label becomes MANUAL with full confidence.
                    source = if (labelChanged) DetectionSource.MANUAL else current.source,
                    confidence = if (labelChanged) 1f else current.confidence,
                    recordedAt = recordedAt,
                    placeName = placeName.value.trim().ifEmpty { null },
                    latitude = lat,
                    longitude = lon,
                    note = note.value.trim().ifEmpty { null },
                    behavior = behavior.value.trim().ifEmpty { null },
                )
            )
            _message.value = "Uloženo"
            refresh()
        }
    }

    fun useCurrentLocation() {
        if (_locating.value) return
        _locating.value = true
        viewModelScope.launch {
            try {
                val fix = container.locationProvider.getFix()
                if (fix == null) {
                    _message.value = "Polohu se nepodařilo zjistit (oprávnění/GPS)."
                } else {
                    latitudeText.value = fix.latitude.toString()
                    longitudeText.value = fix.longitude.toString()
                    _message.value = "Poloha doplněna (±${fix.accuracyM?.toInt() ?: '?'} m)"
                }
            } finally {
                _locating.value = false
            }
        }
    }

    fun addPhoto(file: File) {
        viewModelScope.launch {
            container.repository.addPhoto(entryId, file)
            refresh()
        }
    }

    fun addVideo(file: File) {
        viewModelScope.launch {
            container.repository.addVideo(entryId, file)
            refresh()
        }
    }

    fun removePhoto(relativePath: String) {
        viewModelScope.launch {
            container.repository.removePhoto(
                entryId,
                container.repository.relative(container.repository.resolve(relativePath)),
            )
            refresh()
        }
    }

    fun removeVideo(relativePath: String) {
        viewModelScope.launch {
            container.repository.removeVideo(
                entryId,
                container.repository.relative(container.repository.resolve(relativePath)),
            )
            refresh()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            _entry.value?.let { container.repository.delete(it) }
            onDone()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        player.stop()
    }

    class Factory(
        private val container: AppContainer,
        private val entryId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailViewModel(container, entryId) as T
        }
    }
}
