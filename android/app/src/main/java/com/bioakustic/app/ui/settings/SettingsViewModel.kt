package com.bioakustic.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bioakustic.app.AppContainer
import com.bioakustic.app.classify.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val modelState: StateFlow<ModelManager.ModelState> = container.modelManager.state

    private val _modelUrl = MutableStateFlow(container.modelUrl)
    val modelUrl: StateFlow<String> = _modelUrl

    private val _labelsUrl = MutableStateFlow(container.labelsUrl)
    val labelsUrl: StateFlow<String> = _labelsUrl

    private val _useNnapi = MutableStateFlow(container.useNnapi)
    val useNnapi: StateFlow<Boolean> = _useNnapi

    private val _autoDetect = MutableStateFlow(container.autoDetect)
    val autoDetect: StateFlow<Boolean> = _autoDetect

    private val _thresholdDb = MutableStateFlow(container.thresholdDb)
    val thresholdDb: StateFlow<Float> = _thresholdDb

    init {
        container.modelManager.refresh(container.useNnapi)
    }

    fun setModelUrl(value: String) {
        container.modelUrl = value
        _modelUrl.value = value
    }

    fun setLabelsUrl(value: String) {
        container.labelsUrl = value
        _labelsUrl.value = value
    }

    fun setUseNnapi(value: Boolean) {
        container.useNnapi = value
        _useNnapi.value = value
        container.modelManager.refresh(value)
    }

    fun setAutoDetect(value: Boolean) {
        container.autoDetect = value
        _autoDetect.value = value
    }

    fun setThresholdDb(value: Float) {
        container.thresholdDb = value
        _thresholdDb.value = value
    }

    fun downloadModel() {
        container.modelManager.download(
            _modelUrl.value.trim(),
            _labelsUrl.value.trim(),
            _useNnapi.value,
        )
    }

    fun cancelDownload() = container.modelManager.cancelDownload()

    fun deleteModel() = container.modelManager.deleteModel()

    fun modelFilesInfo(): String {
        val model = container.modelManager.modelFile()
        val labels = container.modelManager.labelsFile()
        fun size(f: java.io.File): String =
            if (f.exists()) "%.1f MB".format(f.length() / 1048576.0) else "chybí"
        return "Model: ${size(model)}\nPopisky: ${size(labels)}"
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(container) as T
        }
    }
}
