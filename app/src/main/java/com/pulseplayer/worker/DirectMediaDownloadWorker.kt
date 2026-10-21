package com.pulseplayer.worker

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pulseplayer.data.room.DownloadQueuePolicy
import com.pulseplayer.data.room.DownloadState
import com.pulseplayer.data.room.PulsePlayerDatabase
import com.pulseplayer.network.PublicEndpointPolicy
import com.pulseplayer.inspector.SourceType
import com.pulseplayer.inspector.UrlInspector
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Direct HTTPS file URLs only. Does not extract YouTube media.
 * Redirects are rejected pending a transport-level SSRF-resistant resolver.
 */
class DirectMediaDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_URL = "media_url"
        const val KEY_TITLE = "media_title"
        private const val MAX_FILE_BYTES = 1024L * 1024L * 1024L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getLong(KEY_MEDIA_ID, -1)
        val raw = inputData.getString(KEY_URL)
        val dao = PulsePlayerDatabase.getInstance(applicationContext).downloadedMediaDao()
        if (id <= 0 || raw.isNullOrBlank()) return@withContext Result.failure()
        val item = dao.getById(id) ?: return@withContext Result.failure()
        if (item.state != DownloadState.PENDING.name) return@withContext Result.failure()
        val inspected = UrlInspector.inspect(raw)
        val type = inspected.sourceType as? SourceType.DirectMedia ?: return@withContext Result.failure()
        if (type.extension !in setOf("mp4", "webm", "mp3", "m4a", "aac", "opus")) return@withContext Result.failure()
        val uri = try { PublicEndpointPolicy.validateUrl(inspected.normalizedUrl) } catch (_: Exception) { return@withContext Result.failure() }
        val host = uri.host ?: return@withContext Result.failure()
        val addresses = try { InetAddress.getAllByName(host) } catch (_: Exception) { return@withContext Result.failure() }
        if (addresses.isEmpty() || addresses.any { !PublicEndpointPolicy.isAllowed(it) }) {
            return@withContext Result.failure()
        }
        // DNS validation and connection are not pinned to the same resolved IP.
        // Until transport-level pinning is implemented, this worker is deliberately disabled.
        return@withContext Result.failure(workDataOf("error" to "Network transport security verification pending"))
    }
}
