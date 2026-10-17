package com.pulseplayer.app.aipulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, expiring cache for public recording metadata; never stores song audio. */
object AiPulsePersistentCache {
    private const val PREFS = "ai_pulse_public_metadata_v1"
    private const val TTL_MS = 24L * 60 * 60 * 1000
    private const val MAX_ENTRIES = 40
    private const val MAX_BYTES = 250_000

    fun get(context: Context, query: String): List<AiPulseHybridDiscovery.Recording>? {
        val key = query.trim().lowercase()
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return null
        return runCatching {
            val entry = JSONObject(raw)
            val age = System.currentTimeMillis() - entry.getLong("savedAt")
            if (age < 0 || age > TTL_MS) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
                return null
            }
            val array = entry.getJSONArray("records")
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                AiPulseHybridDiscovery.Recording(
                    item.getString("title"), item.getString("artist"), item.getString("id")
                )
            }
        }.getOrNull()
    }

    fun put(context: Context, query: String, records: List<AiPulseHybridDiscovery.Recording>) {
        val key = query.trim().lowercase().take(80)
        if (key.length < 2) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recordsJson = JSONArray()
        records.take(8).forEach { record ->
            recordsJson.put(JSONObject().put("title", record.title.take(150))
                .put("artist", record.artist.take(100)).put("id", record.id.take(64)))
        }
        val entry = JSONObject().put("savedAt", System.currentTimeMillis())
            .put("records", recordsJson).toString()
        if (entry.length > MAX_BYTES) return
        val keys = prefs.all.keys.filter { it != key }
        val editor = prefs.edit()
        if (keys.size >= MAX_ENTRIES) keys.take(keys.size - MAX_ENTRIES + 1).forEach { editor.remove(it) }
        editor.putString(key, entry).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
