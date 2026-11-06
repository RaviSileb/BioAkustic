package com.bioakustic.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recognition history entry. Created automatically by the detection
 * pipeline (or manually) and fully editable by the user afterwards.
 *
 * Media paths are stored relative to [com.bioakustic.app.data.repo.DetectionRepository.rootDir].
 * Photo/video lists are JSON arrays of relative paths ("[]" when empty).
 */
@Entity(tableName = "detections")
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** When the sound was recorded (epoch millis). User-editable. */
    val recordedAt: Long,
    val durationMs: Long,
    /** Relative path of the WAV clip, null when no audio was kept. */
    val audioPath: String?,
    val sampleRate: Int,
    val speciesCommon: String?,
    val speciesScientific: String?,
    /** 0..1, null when unknown. */
    val confidence: Float?,
    /** One of [DetectionSource]. */
    val source: String,
    val behavior: String?,
    val behaviorConfidence: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
    val note: String?,
    val photoPaths: String = "[]",
    val videoPaths: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
)

/** How the species label was produced. */
object DetectionSource {
    const val HEURISTIC = "HEURISTIC"
    const val TFLITE = "TFLITE"
    const val MANUAL = "MANUAL"
}
