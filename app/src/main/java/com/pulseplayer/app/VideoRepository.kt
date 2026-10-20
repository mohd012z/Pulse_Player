package com.pulseplayer.app

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first local metadata and progress repository.
 * Remote synchronization is intentionally absent until a verified authorized API exists.
 */
class VideoRepository(context: Context) {
    private val dao = VideoDatabase.get(context).videos()

    fun category(category: String): Flow<List<VideoItem>> = dao.byCategory(category)
    fun continueWatching(): Flow<List<VideoItem>> = dao.continueWatching()
    suspend fun search(query: String): List<VideoItem> = dao.search(query.trim())
    suspend fun get(id: String): VideoItem? = dao.byId(id)
    suspend fun cache(items: List<VideoItem>) = dao.upsert(items.filter { it.id.isNotBlank() && it.title.isNotBlank() })
    suspend fun progress(id: String): WatchProgress? = dao.progress(id)

    suspend fun saveProgress(id: String, positionMs: Long, durationMs: Long) {
        if (id.isBlank()) return
        val duration = durationMs.coerceAtLeast(0)
        val position = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        dao.upsertProgress(WatchProgress(
            mediaId = id,
            playbackPositionMs = position,
            totalDurationMs = duration,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            isCompleted = duration > 0 && position.toDouble() / duration >= 0.95
        ))
    }
}
