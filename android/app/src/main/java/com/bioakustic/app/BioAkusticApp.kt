package com.bioakustic.app

import android.app.Application
import com.bioakustic.app.audio.AudioEngine
import com.bioakustic.app.classify.ModelManager
import com.bioakustic.app.data.db.AppDatabase
import com.bioakustic.app.data.repo.DetectionRepository
import com.bioakustic.app.location.LocationProvider
import com.bioakustic.app.playback.AudioPlayer

class BioAkusticApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container (no DI framework in the MVP). */
class AppContainer(val app: Application) {
    val prefs = app.getSharedPreferences(PREFS, 0)

    val database: AppDatabase by lazy { AppDatabase.create(app) }
    val repository: DetectionRepository by lazy {
        DetectionRepository(app, database.detectionDao())
    }
    val audioEngine: AudioEngine by lazy { AudioEngine(app) }
    val modelManager: ModelManager by lazy { ModelManager(app) }
    val locationProvider: LocationProvider by lazy { LocationProvider(app) }
    val audioPlayer: AudioPlayer by lazy { AudioPlayer() }

    var autoDetect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()

    var thresholdDb: Float
        get() = prefs.getFloat(KEY_THRESHOLD_DB, -38f)
        set(value) = prefs.edit().putFloat(KEY_THRESHOLD_DB, value).apply()

    var useNnapi: Boolean
        get() = prefs.getBoolean(KEY_NNAPI, false)
        set(value) = prefs.edit().putBoolean(KEY_NNAPI, value).apply()

    var modelUrl: String
        get() = prefs.getString(KEY_MODEL_URL, DEFAULT_MODEL_URL) ?: DEFAULT_MODEL_URL
        set(value) = prefs.edit().putString(KEY_MODEL_URL, value).apply()

    var labelsUrl: String
        get() = prefs.getString(KEY_LABELS_URL, DEFAULT_LABELS_URL) ?: DEFAULT_LABELS_URL
        set(value) = prefs.edit().putString(KEY_LABELS_URL, value).apply()

    companion object {
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/sammlapp/BirdNET_v2.4/resolve/main/" +
                "V2.4/BirdNET_GLOBAL_6K_V2.4_Model_FP16.tflite"
        const val DEFAULT_LABELS_URL =
            "https://huggingface.co/sammlapp/BirdNET_v2.4/resolve/main/" +
                "V2.4/BirdNET_GLOBAL_6K_V2.4_Labels.txt"
        const val PREFS = "bioakustic_prefs"
        const val KEY_AUTO_DETECT = "auto_detect"
        const val KEY_THRESHOLD_DB = "threshold_db"
        const val KEY_NNAPI = "use_nnapi"
        const val KEY_MODEL_URL = "model_url"
        const val KEY_LABELS_URL = "labels_url"
    }
}
