package com.bioakustic.app

import android.Manifest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.bioakustic.app.audio.AudioEngine
import com.bioakustic.app.audio.WavWriter
import com.bioakustic.app.classify.HeuristicClassifier
import com.bioakustic.app.data.db.AppDatabase
import com.bioakustic.app.data.db.DetectionEntity
import com.bioakustic.app.data.db.DetectionSource
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke tests, executed on the Galaxy S23 Ultra via
 * connectedAndroidTest. They verify the database, the microphone path and
 * the WAV/heuristic pipeline on real hardware.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun database_insertQueryDelete() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val now = System.currentTimeMillis()
            val id = db.detectionDao().insert(
                DetectionEntity(
                    recordedAt = now,
                    durationMs = 4000,
                    audioPath = "audio/test.wav",
                    sampleRate = 48_000,
                    speciesCommon = "Test Bird",
                    speciesScientific = "Testus birdus",
                    confidence = 0.9f,
                    source = DetectionSource.MANUAL,
                    behavior = null,
                    behaviorConfidence = null,
                    latitude = 50.0,
                    longitude = 14.0,
                    placeName = "Praha",
                    note = "smoke",
                    createdAt = now,
                    updatedAt = now,
                )
            )
            assertTrue(id > 0)
            val loaded = db.detectionDao().getById(id)
            assertNotNull(loaded)
            assertEquals("Praha", loaded!!.placeName)
            assertEquals(1, db.detectionDao().count())
            val found = db.detectionDao().search("Test").first()
            assertEquals(1, found.size)
            db.detectionDao().delete(loaded)
            assertEquals(0, db.detectionDao().count())
        } finally {
            db.close()
        }
    }

    @Test
    fun audioEngine_opensMicrophone() {
        val engine = AudioEngine(context())
        try {
            var frames = 0
            val ok = engine.start { frames++ }
            assertTrue("AudioRecord failed: ${engine.status.value}", ok)
            Thread.sleep(1200)
            assertTrue("no audio frames received", frames > 0)
            assertEquals(48_000, engine.actualSampleRate)
            val snap = engine.snapshot(1f)
            assertTrue("snapshot empty", snap.isNotEmpty())
        } finally {
            engine.release()
        }
    }

    @Test
    fun wavWriter_writesValidHeader() {
        val file = File(context().cacheDir, "smoke.wav")
        val samples = FloatArray(4800) { i -> (i % 100) / 100f - 0.5f }
        WavWriter.write(file, samples, 48_000)
        assertTrue(file.exists())
        assertEquals(44L + samples.size * 2L, file.length())
        val header = file.inputStream().use { it.readBytes().copyOf(12) }
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        file.delete()
    }

    @Test
    fun heuristicClassifier_returnsResult() {
        val classifier = HeuristicClassifier()
        try {
            assertTrue(classifier.isReady)
            // 3 s of a loud 2 kHz tone.
            val samples = FloatArray(3 * 48_000) { i ->
                (0.5 * kotlin.math.sin(2.0 * Math.PI * 2000 * i / 48_000)).toFloat()
            }
            val result = classifier.classify(samples, 48_000)
            assertEquals(DetectionSource.HEURISTIC, result.source)
            assertNotNull(result.commonName)
            assertTrue(result.confidence in 0f..1f)
        } finally {
            classifier.close()
        }
    }
}
