package com.pulseplayer.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Catalog metadata for explicitly authorized sources; never treats metadata as playback permission. */
@Entity(tableName = "video_catalog")
data class VideoItem(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val posterUrl: String = "",
    val streamUrl: String = "",
    val durationMs: Long = 0,
    val summary: String = "",
    val releaseYear: Int = 0,
    val authorized: Boolean = false
)

@Entity(tableName = "watch_progress")
data class WatchProgress(
    @PrimaryKey val mediaId: String,
    val playbackPositionMs: Long,
    val totalDurationMs: Long,
    val lastUpdatedTimestamp: Long,
    val isCompleted: Boolean
)
