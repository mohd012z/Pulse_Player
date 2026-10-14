package com.pulseplayer.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import org.json.JSONArray
import org.json.JSONObject

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

    fun discovered(): List<Station> = runCatching {
        val arr = JSONArray(prefs.getString("discovered_my", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("name").trim()
            val url = obj.optString("url").trim()
            if (!name.startsWith("MY ·") || !isValidUrl(url)) null else Station(name.take(90), url)
        }
    }.getOrDefault(emptyList())

    fun replaceDiscovery(found: List<Station>): Boolean {
        val arr = JSONArray()
        found.asSequence().filter { it.name.startsWith("MY ·") && isValidUrl(it.url) }
            .distinctBy { it.url }.take(70).forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        return prefs.edit().putString("discovered_my", arr.toString()).commit()
    }

    fun stations(): List<Station> {
        val saved = runCatching {
            val array = JSONArray(prefs.getString("stations", "[]"))
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val url = obj.optString("url").trim()
                if (name.isEmpty() || !isValidUrl(url)) null else Station(name, url)
            }
        }.getOrDefault(emptyList())
        return (saved + discovered() + examples).distinctBy { it.url }
    }

    fun favorites(): List<Station> = runCatching {
        val arr = JSONArray(prefs.getString("stations", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("name").trim()
            val url = obj.optString("url").trim()
            if (name.isEmpty() || !isValidUrl(url)) null else Station(name, url)
        }
    }.getOrDefault(emptyList())

    fun add(name: String, url: String): Boolean {
        val cleanName = name.trim().take(90)
        val cleanUrl = url.trim()
        if (cleanName.isEmpty() || !isValidUrl(cleanUrl) || favorites().any { it.url == cleanUrl }) return false
        val arr = JSONArray(prefs.getString("stations", "[]"))
        arr.put(JSONObject().put("name", cleanName).put("url", cleanUrl))
        return prefs.edit().putString("stations", arr.toString()).commit()
    }

    fun remove(url: String): Boolean {
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