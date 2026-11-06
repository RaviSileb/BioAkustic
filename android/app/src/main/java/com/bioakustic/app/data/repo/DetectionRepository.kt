package com.bioakustic.app.data.repo

import android.content.Context
import com.bioakustic.app.data.db.DetectionDao
import com.bioakustic.app.data.db.DetectionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * Repository for the recognition history: database rows plus the media files
 * (audio clips, photos, videos) stored under the app-private files dir.
 */
class DetectionRepository(
    private val context: Context,
    private val dao: DetectionDao,
) {
    val rootDir: File get() = context.filesDir

    fun audioDir(): File = File(context.filesDir, "audio").apply { mkdirs() }
    fun photoDir(): File = File(context.filesDir, "photos").apply { mkdirs() }
    fun videoDir(): File = File(context.filesDir, "videos").apply { mkdirs() }

    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    fun relative(file: File): String =
        file.absolutePath.removePrefix(context.filesDir.absolutePath).trimStart('/')

    fun newAudioFile(recordedAt: Long): File =
        File(audioDir(), "rec_${FILE_STAMP.format(Date(recordedAt))}.wav")

    fun newPhotoFile(): File =
        File(photoDir(), "img_${FILE_STAMP.format(Date())}_${System.nanoTime() % 100000}.jpg")

    fun newVideoFile(): File =
        File(videoDir(), "vid_${FILE_STAMP.format(Date())}_${System.nanoTime() % 100000}.mp4")

    fun all(): Flow<List<DetectionEntity>> = dao.getAll()

    fun search(query: String): Flow<List<DetectionEntity>> =
        if (query.isBlank()) dao.getAll() else dao.search(query.trim())

    suspend fun get(id: Long): DetectionEntity? = dao.getById(id)

    suspend fun insert(entity: DetectionEntity): Long = dao.insert(entity)

    suspend fun update(entity: DetectionEntity) =
        dao.update(entity.copy(updatedAt = System.currentTimeMillis()))

    /** Deletes the row and all media files referenced by it. */
    suspend fun delete(entity: DetectionEntity) {
        entity.audioPath?.let { deleteQuietly(resolve(it)) }
        decodePaths(entity.photoPaths).forEach { deleteQuietly(resolve(it)) }
        decodePaths(entity.videoPaths).forEach { deleteQuietly(resolve(it)) }
        dao.delete(entity)
    }

    suspend fun addPhoto(id: Long, file: File) = modifyPaths(id, file, updatePhotos = true)
    suspend fun addVideo(id: Long, file: File) = modifyPaths(id, file, updatePhotos = false)

    suspend fun removePhoto(id: Long, relativePath: String) {
        dao.getById(id) ?: return
        deleteQuietly(resolve(relativePath))
        updatePaths(id, relativePath, updatePhotos = true, remove = true)
    }

    suspend fun removeVideo(id: Long, relativePath: String) {
        dao.getById(id) ?: return
        deleteQuietly(resolve(relativePath))
        updatePaths(id, relativePath, updatePhotos = false, remove = true)
    }

    private suspend fun modifyPaths(id: Long, file: File, updatePhotos: Boolean) {
        val entity = dao.getById(id) ?: return
        val current = decodePaths(if (updatePhotos) entity.photoPaths else entity.videoPaths)
        val updated = (current + relative(file)).distinct()
        val copy = if (updatePhotos) {
            entity.copy(photoPaths = encodePaths(updated), updatedAt = System.currentTimeMillis())
        } else {
            entity.copy(videoPaths = encodePaths(updated), updatedAt = System.currentTimeMillis())
        }
        dao.update(copy)
    }

    private suspend fun updatePaths(id: Long, relativePath: String, updatePhotos: Boolean, remove: Boolean) {
        val entity = dao.getById(id) ?: return
        val current = decodePaths(if (updatePhotos) entity.photoPaths else entity.videoPaths)
        val updated = if (remove) current - relativePath else (current + relativePath).distinct()
        val copy = if (updatePhotos) {
            entity.copy(photoPaths = encodePaths(updated), updatedAt = System.currentTimeMillis())
        } else {
            entity.copy(videoPaths = encodePaths(updated), updatedAt = System.currentTimeMillis())
        }
        dao.update(copy)
    }

    private fun deleteQuietly(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
            // Best effort: a stale file is harmless.
        }
    }

    companion object {
        private val FILE_STAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        fun decodePaths(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                List(array.length()) { i -> array.optString(i) }.filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun encodePaths(paths: List<String>): String = JSONArray(paths).toString()
    }
}
