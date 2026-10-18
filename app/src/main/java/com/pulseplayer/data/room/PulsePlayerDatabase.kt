package com.pulseplayer.data.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class DownloadState { PENDING, DOWNLOADING, COMPLETED, FAILED }

@Entity(tableName = "downloaded_media", indices = [Index(value = ["sourceKey"], unique = true)])
data class DownloadedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Non-secret stable identifier; do not persist signed CDN tokens as an indexed key. */
    val sourceKey: String,
    val title: String,
    val localMediaUri: String? = null,
    val mimeType: String? = null,
    val fileSizeBytes: Long = 0,
    val state: String = DownloadState.PENDING.name,
    val progress: Float = 0f,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE state = 'COMPLETED' ORDER BY createdAtEpochMs DESC")
    fun observeCompleted(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadedMediaEntity?

    @Query("SELECT * FROM downloaded_media WHERE sourceKey = :key LIMIT 1")
    suspend fun getBySourceKey(key: String): DownloadedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: DownloadedMediaEntity): Long

    @Query("UPDATE downloaded_media SET state = :state, progress = :progress WHERE id = :id")
    suspend fun updateState(id: Long, state: String, progress: Float): Int

    @Query("UPDATE downloaded_media SET state = 'COMPLETED', progress = 1.0, localMediaUri = :uri, fileSizeBytes = :size WHERE id = :id")
    suspend fun markCompleted(id: Long, uri: String, size: Long): Int

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Database(entities = [DownloadedMediaEntity::class], version = 1, exportSchema = false)
abstract class PulsePlayerDatabase : RoomDatabase() {
    abstract fun downloadedMediaDao(): DownloadedMediaDao

    companion object {
        @Volatile private var instance: PulsePlayerDatabase? = null
        fun getInstance(context: Context): PulsePlayerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, PulsePlayerDatabase::class.java, "pulse_player_media.db"
                ).build().also { instance = it }
            }
    }
}
