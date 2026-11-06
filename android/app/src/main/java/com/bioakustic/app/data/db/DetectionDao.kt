package com.bioakustic.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {
    @Insert
    suspend fun insert(entity: DetectionEntity): Long

    @Update
    suspend fun update(entity: DetectionEntity)

    @Delete
    suspend fun delete(entity: DetectionEntity)

    @Query("SELECT * FROM detections WHERE id = :id")
    suspend fun getById(id: Long): DetectionEntity?

    @Query("SELECT * FROM detections ORDER BY recordedAt DESC")
    fun getAll(): Flow<List<DetectionEntity>>

    @Query(
        "SELECT * FROM detections WHERE " +
            "speciesCommon LIKE '%' || :query || '%' OR " +
            "speciesScientific LIKE '%' || :query || '%' OR " +
            "placeName LIKE '%' || :query || '%' OR " +
            "note LIKE '%' || :query || '%' " +
            "ORDER BY recordedAt DESC"
    )
    fun search(query: String): Flow<List<DetectionEntity>>

    @Query("SELECT COUNT(*) FROM detections")
    suspend fun count(): Int
}
