package com.pulseplayer.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Discover public directory entries, never scan Bluetooth/Wi-Fi networks or radio frequencies. */
class MalaysiaRadioDirectory {
    fun scan(): List<RadioRepository.Station> {
        val hosts = listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info")
        var lastError: Exception? = null
        for (host in hosts) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://$host/json/stations/bycountrycodeexact/MY?hidebroken=true&limit=80&order=votes&reverse=true")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6500
                    readTimeout = 6500
                    setRequestProperty("User-Agent", "PulsePlayer/4.0 (Android; RadioBrowser Directory)")
                    setRequestProperty("Accept", "application/json")
                }
                check(connection.responseCode == 200) { "Directory HTTP ${connection.responseCode}" }
                val bytes = connection.inputStream.use { stream ->
                    val chunk = ByteArray(4096)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val count = stream.read(chunk)
                        if (count < 0) break
                        check(output.size() + count <= 2_000_000) { "Directory payload too large" }
                        output.write(chunk, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
                val response = JSONArray(bytes)
                val stations = ArrayList<Pair<RadioRepository.Station, Int>>()
                for (i in 0 until minOf(response.length(), 80)) {
                    val item = response.optJSONObject(i) ?: continue
                    if (!item.optString("countrycode").equals("MY", ignoreCase = true)) continue
                    if (item.optInt("lastcheckok", 0) != 1) continue
                    val name = item.optString("name").trim().take(85)
                    val stream = item.optString("url_resolved").ifBlank { item.optString("url") }.trim()
                    if (name.isEmpty() || !RadioRepository.isValidUrl(stream)) continue
                    val bitrate = item.optInt("bitrate", 0)
                    val codec = item.optString("codec").uppercase()
                    // Unknown bitrate is retained but ranked below documented quality streams.
                    val codecScore = when { codec.contains("AAC") -> 25; codec.contains("MP3") -> 15; else -> 0 }
                    val qualityScore = bitrate.coerceIn(0, 320) + codecScore
                    stations.add(RadioRepository.Station("MY · $name", stream) to qualityScore)
                }
                return stations.sortedByDescending { it.second }.map { it.first }.distinctBy { it.url }.take(70)
            } catch (e: Exception) {
                lastError = e
                Log.w("PulseRadio", "Radio directory host unavailable: $host", e)
            } finally {
                connection?.disconnect()
            }
        }
        throw IllegalStateException("Radio directory not reachable", lastError)
    }
}