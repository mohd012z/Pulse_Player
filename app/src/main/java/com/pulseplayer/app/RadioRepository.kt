package com.pulseplayer.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Curated example stations and personal HTTPS stream bookmarks; does not record or redistribute radio. */
class RadioRepository(context: Context) {
    data class Station(val name: String, val url: String) {
        fun toMediaItem(): MediaItem {
            val builder = MediaItem.Builder()
                .setUri(url)
                .setMediaId(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name).setArtist("Internet radio · Live").build())
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
            if (Uri.parse(url).lastPathSegment?.endsWith(".m3u8", ignoreCase = true) == true) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            return builder.build()
        }
    }

    private val prefs = context.getSharedPreferences("radio_bookmarks", Context.MODE_PRIVATE)

    private val examples = listOf(
        Station("Groove Salad · SomaFM", "https://ice5.somafm.com/groovesalad-128-mp3"),
        Station("Deep Space One · SomaFM", "https://ice5.somafm.com/deepspaceone-128-mp3"),
        // Third-party published endpoint; user should verify availability and station terms.
        Station("MY · BFM 89.9", "https://playerservices.streamtheworld.com/api/livestream-redirect/BFMAAC_SC")
    )

    fun discovered(): List<Station> = readArray("discovered")

    private fun readArray(key: String): List<Station> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val name = obj.optString("name").trim()
            val url = obj.optString("url").trim()
            if (name.isEmpty() || !isValidUrl(url)) null else Station(name, url)
        }
    }.getOrDefault(emptyList())

    /** Public Radio Browser directory; cache is persistent and no station audio is downloaded. */
    fun scanMalaysia(): Result<Int> = runCatching {
        val discoveredHosts = runCatching { InetAddress.getAllByName("all.api.radio-browser.info")
            .mapNotNull { addr -> addr.canonicalHostName.takeIf { it.endsWith(".api.radio-browser.info") } }
            .distinct() }.getOrDefault(emptyList())
        val hosts = (discoveredHosts + listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info")).distinct()
        var error: Exception? = null
        for (host in hosts.take(5)) {
            try {
                val url = URL("https://$host/json/stations/bycountrycodeexact/MY?hidebroken=true&limit=120&order=votes&reverse=true")
                val c = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 9000
                    setRequestProperty("User-Agent", "PulsePlayer/4.0 (Android; Malaysian radio directory)")
                    setRequestProperty("Accept", "application/json")
                    instanceFollowRedirects = false
                }
                val payload = try {
                    check(c.responseCode == 200) { "Directory returned HTTP ${c.responseCode}" }
                    c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(8192)
                        val text = StringBuilder()
                        while (text.length < 512_000) {
                            val read = reader.read(buffer, 0, minOf(buffer.size, 512_000 - text.length))
                            if (read <= 0) break
                            text.append(buffer, 0, read)
                        }
                        text.toString()
                    }
                } finally { c.disconnect() }
                val json = JSONArray(payload)
                val candidates = (0 until json.length()).mapNotNull { index ->
                    val obj = json.optJSONObject(index) ?: return@mapNotNull null
                    if (!obj.optString("countrycode").equals("MY", true)) return@mapNotNull null
                    val name = obj.optString("name").trim().take(85)
                    val link = obj.optString("url_resolved").ifBlank { obj.optString("url") }.trim()
                    if (name.isEmpty() || !isValidUrl(link)) null else Station("MY · $name", link)
                }.distinctBy { it.url }.take(90)
                val cache = JSONArray()
                candidates.forEach { cache.put(JSONObject().put("name", it.name).put("url", it.url)) }
                check(prefs.edit().putString("discovered", cache.toString()).commit()) { "Unable to save radio cache" }
                return@runCatching candidates.size
            } catch (e: Exception) { error = e }
        }
        throw error ?: IllegalStateException("No Radio Browser server available")
    }

    fun stations(): List<Station> {
        val saved = readArray("stations")
        return (examples + saved).distinctBy { it.url }
    }

    fun add(name: String, url: String): Boolean {
        val cleanName = name.trim().take(90)
        val cleanUrl = url.trim()
        if (cleanName.isEmpty() || !isValidUrl(cleanUrl) || stations().any { it.url == cleanUrl }) return false
        val arr = JSONArray(prefs.getString("stations", "[]"))
        arr.put(JSONObject().put("name", cleanName).put("url", cleanUrl))
        return prefs.edit().putString("stations", arr.toString()).commit()
    }

    fun remove(url: String): Boolean {
        if (examples.any { it.url == url }) return false
        val saved = runCatching { JSONArray(prefs.getString("stations", "[]")) }.getOrNull() ?: return false
        val keep = JSONArray()
        var deleted = false
        for (i in 0 until saved.length()) {
            val item = saved.optJSONObject(i) ?: continue
            if (item.optString("url") == url) deleted = true else keep.put(item)
        }
        return deleted && prefs.edit().putString("stations", keep.toString()).commit()
    }

    companion object {
        fun isValidUrl(value: String): Boolean = PlaybackInputs.isValidRadioStream(value)
    }
}