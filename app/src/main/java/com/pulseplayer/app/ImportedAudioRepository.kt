package com.pulseplayer.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

/** Persists only user-picked content URIs; never copies or exports copyrighted music. */
class ImportedAudioRepository(private val context: Context) {
    data class Track(val name: String, val uri: Uri) {
        fun toMediaItem(): MediaItem = MediaItem.Builder()
            .setUri(uri).setMediaId(uri.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build()).build()
    }

    private val prefs = context.getSharedPreferences("imported_audio", Context.MODE_PRIVATE)
    private val maxFiles = 500

    fun all(): List<Track> {
        val stored = runCatching { JSONArray(prefs.getString("tracks", "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until minOf(stored.length(), maxFiles)) {
                val item = stored.optJSONObject(i) ?: continue
                val raw = item.optString("uri")
                val title = item.optString("name")
                val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
                if (uri.scheme == "content" && title.isNotBlank()) add(Track(title, uri))
            }
        }
    }

    fun add(uris: List<Uri>): Int {
        val saved = all().associateByTo(linkedMapOf()) { it.uri.toString() }
        for (uri in uris.take(maxFiles)) {
            if (saved.size >= maxFiles) break
            if (uri.scheme != "content") continue
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment ?: "Imported audio"
            if (!PlaybackInputs.isAudioFile(name, runCatching { context.contentResolver.getType(uri) }.getOrNull())) continue
            saved[uri.toString()] = Track(name.take(200), uri)
        }
        val arr = JSONArray()
        saved.values.forEach { arr.put(JSONObject().put("uri", it.uri.toString()).put("name", it.name)) }
        if (!prefs.edit().putString("tracks", arr.toString()).commit()) return 0
        return saved.size
    }

    fun removeAll(): Boolean = prefs.edit().remove("tracks").commit()
}