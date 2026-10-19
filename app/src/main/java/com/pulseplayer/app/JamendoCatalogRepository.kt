package com.pulseplayer.app

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Publicly licensed indie-music search via Jamendo's documented developer API.
 *  Requires the app owner's own client ID and network access. No bypass of paid catalogs.
 *  Run search on a worker thread, never on Android's main thread.
 */
class JamendoCatalogRepository {
    data class Track(
        val id: String,
        val title: String,
        val artist: String,
        val audioUrl: String,
        val licenseUrl: String,
        val downloadAllowed: Boolean,
        val downloadUrl: String
    ) {
        fun toMediaItem(): MediaItem = MediaItem.Builder()
            .setMediaId("jamendo:$id")
            .setUri(audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
            ).build()
    }

    /** Re-resolve the authorized download for the selected format, checking the artist's
     *  current permission at download time. Never substitute the playback stream URL.
     */
    fun resolveMp3Download(clientId: String, trackId: String, quality: Mp3DownloadInputs.Quality): String {
        require(WorldMusicInputs.validClientId(clientId)) { "Valid Jamendo client ID required" }
        require(Mp3DownloadInputs.validTrackId(trackId)) { "Invalid Jamendo track identifier" }
        val url = Uri.parse("https://api.jamendo.com/v3.0/tracks/").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("id", trackId)
            .appendQueryParameter("limit", "1")
            .appendQueryParameter("audiodlformat", quality.apiValue)
            .build().toString()
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PulsePlayer/6.0")
        }
        return try {
            if (c.responseCode != HttpURLConnection.HTTP_OK)
                throw IllegalStateException("Download authorization HTTP ${c.responseCode}")
            val response = c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val chars = CharArray(4096)
                val b = StringBuilder()
                while (true) {
                    val n = reader.read(chars)
                    if (n < 0) break
                    b.append(chars, 0, n)
                    if (b.length > 200_000) throw IllegalStateException("Unexpected download response")
                }
                b.toString()
            }
            val root = JSONObject(response)
            if (root.optJSONObject("headers")?.optString("status") != "success")
                throw IllegalStateException("Catalog did not authorize this download")
            val track = root.optJSONArray("results")?.optJSONObject(0)
                ?: throw IllegalStateException("Track not available")
            if (track.optString("id") != trackId || !track.optBoolean("audiodownload_allowed", false))
                throw IllegalStateException("Artist has disabled downloads")
            val download = track.optString("audiodownload").trim()
            require(WorldMusicInputs.validAudioUrl(download)) { "Provider returned no valid HTTPS MP3 URL" }
            download
        } finally {
            c.disconnect()
        }
    }

    fun search(clientId: String, query: String, offset: Int = 0, limit: Int = 50): List<Track> {
        require(WorldMusicInputs.validClientId(clientId)) { "A valid Jamendo client ID is required" }
        require(query.isBlank() || WorldMusicInputs.validSearch(query)) { "Search with at least two characters" }
        require(offset in 0..100_000) { "Invalid catalog offset" }
        require(limit in 1..100) { "Invalid catalog page size" }
        val url = Uri.parse("https://api.jamendo.com/v3.0/tracks/").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("audioformat", "mp32")
            .appendQueryParameter("audiodlformat", "mp32")
            .appendQueryParameter("limit", limit.toString())
            .appendQueryParameter("offset", offset.toString())
            .apply {
                if (query.isBlank()) appendQueryParameter("order", "popularity_total")
                else appendQueryParameter("search", query.trim())
            }
            .build().toString()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PulsePlayer/5.0 (Android music discovery)")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Catalog HTTP ${connection.responseCode}")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val chars = CharArray(4096)
                val output = StringBuilder()
                while (true) {
                    val count = reader.read(chars)
                    if (count < 0) break
                    output.append(chars, 0, count)
                    if (output.length > 1_500_000) throw IllegalStateException("Catalog response too large")
                }
                output.toString()
            }
            val json = JSONObject(text)
            val header = json.optJSONObject("headers")
            if (header?.optString("status") != "success") {
                throw IllegalStateException("Catalog rejected request: ${header?.optString("error_message") ?: "Unknown error"}")
            }
            val results = json.optJSONArray("results") ?: return emptyList()
            buildList {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val audio = item.optString("audio").trim()
                    val title = item.optString("name").trim()
                    val artist = item.optString("artist_name").trim()
                    val license = item.optString("license_ccurl").trim()
                    val mayDownload = item.optBoolean("audiodownload_allowed", false)
                    val downloadUrl = item.optString("audiodownload").trim()
                    if (id.isNotEmpty() && title.isNotEmpty() && WorldMusicInputs.validAudioUrl(audio)) {
                        add(Track(id, title, artist.ifBlank { "Independent artist" }, audio,
                            license, mayDownload && WorldMusicInputs.validAudioUrl(downloadUrl),
                            if (mayDownload && WorldMusicInputs.validAudioUrl(downloadUrl)) downloadUrl else ""))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}