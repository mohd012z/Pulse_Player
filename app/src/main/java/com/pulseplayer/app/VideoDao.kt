package com.pulseplayer.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_catalog WHERE category = :category ORDER BY title")
    fun byCategory(category: String): Flow<List<VideoItem>>

    @Query("SELECT * FROM video_catalog WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): VideoItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<VideoItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: WatchProgress)

    @Query("SELECT * FROM watch_progress WHERE mediaId = :id LIMIT 1")
    suspend fun progress(id: String): WatchProgress?

    @Query("SELECT v.* FROM video_catalog v INNER JOIN watch_progress p ON p.mediaId = v.id WHERE p.isCompleted = 0 ORDER BY p.lastUpdatedTimestamp DESC")
    fun continueWatching(): Flow<List<VideoItem>>

    @Query("SELECT * FROM video_catalog WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY title")
    suspend fun search(query: String): List<VideoItem>
}
