package com.pulseplayer.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Isolated video database. Does not touch any existing audio or download database.
 * Future schema changes must supply explicit, tested migrations.
 */
@Database(entities = [VideoItem::class, WatchProgress::class], version = 1, exportSchema = true)
abstract class VideoDatabase : RoomDatabase() {
    abstract fun videos(): VideoDao

    companion object {
        @Volatile private var instance: VideoDatabase? = null

        fun get(context: Context): VideoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoDatabase::class.java,
                    "pulse_video.db"
                ).build().also { instance = it }
            }
    }
}
