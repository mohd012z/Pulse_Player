package com.pulseplayer.app.aipulse

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hybrid source discovery: offline composition never depends on connectivity.
 * Online mode retrieves public recording metadata only, not copyrighted audio.
 * MusicBrainz asks clients to identify themselves and stay below 1 request/second.
 */
object AiPulseHybridDiscovery {
    data class Recording(val title: String, val artist: String, val id: String)
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun searchRecording(query: String): List<Recording> {
        val cleaned = query.trim().take(80)
        require(cleaned.length >= 2) { "Enter at least two characters" }
        val encoded = java.net.URLEncoder.encode(cleaned, "UTF-8")
        val url = URL("https://musicbrainz.org/ws/2/recording/?query=$encoded&fmt=json&limit=8")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "PulsePlayer/4.0 (https://github.com/mohd012z/Pulse_Player)")
            connection.setRequestProperty("Accept", "application/json")
            require(connection.responseCode == 200) { "Music metadata service unavailable: ${connection.responseCode}" }
            val text = connection.inputStream.bufferedReader().use { it.readText().take(250_000) }
            val array = JSONObject(text).optJSONArray("recordings") ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val credit = item.optJSONArray("artist-credit")
                val artist = credit?.optJSONObject(0)?.optJSONObject("artist")?.optString("name").orEmpty()
                Recording(item.optString("title").take(150), artist.take(100), item.optString("id"))
            }
        } finally {
            connection.disconnect()
        }
    }
}
