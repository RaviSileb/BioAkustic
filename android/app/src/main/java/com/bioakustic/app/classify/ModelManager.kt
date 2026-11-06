package com.bioakustic.app.classify

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the on-device model files (TFLite + labels) and the active classifier.
 * Falls back to [HeuristicClassifier] when no trained model is installed.
 */
class ModelManager(private val context: Context) {

    sealed interface ModelState {
        data object NotDownloaded : ModelState
        data class Downloading(val fraction: Float, val status: String) : ModelState
        data class Ready(val detail: String) : ModelState
        data class Error(val message: String) : ModelState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state

    @Volatile
    var classifier: Classifier = HeuristicClassifier()
        private set

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }
    fun modelFile(): File = File(modelsDir(), "birdnet.tflite")
    fun labelsFile(): File = File(modelsDir(), "birdnet_labels.txt")

    /** Loads the TFLite model if files exist, otherwise keeps the heuristic. */
    fun refresh(useNnapi: Boolean) {
        val model = modelFile()
        val labels = labelsFile()
        if (!model.exists() || !labels.exists()) {
            replaceClassifier(HeuristicClassifier())
            _state.value = ModelState.NotDownloaded
            return
        }
        try {
            val tflite = BirdnetTfliteClassifier(model, labels, useNnapi)
            replaceClassifier(tflite)
            _state.value = ModelState.Ready("BirdNET TFLite (${tflite.delegateUsed})")
        } catch (e: Throwable) {
            replaceClassifier(HeuristicClassifier())
            _state.value = ModelState.Error("Model nelze načíst: ${e.message}")
        }
    }

    fun download(modelUrl: String, labelsUrl: String, useNnapi: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = ModelState.Downloading(0f, "Stahuji model…")
            try {
                withContext(Dispatchers.IO) {
                    downloadFile(modelUrl, modelFile()) { f ->
                        _state.value = ModelState.Downloading(f * 0.9f, "Stahuji model…")
                    }
                    _state.value = ModelState.Downloading(0.92f, "Stahuji popisky…")
                    downloadFile(labelsUrl, labelsFile()) { f ->
                        _state.value =
                            ModelState.Downloading(0.9f + f * 0.1f, "Stahuji popisky…")
                    }
                }
                refresh(useNnapi)
            } catch (e: Exception) {
                _state.value = ModelState.Error("Stažení selhalo: ${e.message}")
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
        job = null
        if (_state.value is ModelState.Downloading) {
            _state.value = ModelState.NotDownloaded
        }
    }

    fun deleteModel() {
        cancelDownload()
        runCatching { modelFile().delete() }
        runCatching { labelsFile().delete() }
        replaceClassifier(HeuristicClassifier())
        _state.value = ModelState.NotDownloaded
    }

    private fun replaceClassifier(next: Classifier) {
        val old = classifier
        classifier = next
        if (old !== next) runCatching { old.close() }
    }

    @Throws(Exception::class)
    private fun downloadFile(url: String, target: File, onProgress: (Float) -> Unit) {
        require(url.startsWith("http")) { "Neplatná URL: $url" }
        val tmp = File(target.absolutePath + ".part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                connect()
            }
            val code = connection.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            if (tmp.length() == 0L) throw Exception("Prázdný soubor")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw Exception("Nelze uložit soubor")
        } finally {
            connection?.disconnect()
            if (tmp.exists() && !target.exists()) runCatching { tmp.delete() }
        }
    }
}
