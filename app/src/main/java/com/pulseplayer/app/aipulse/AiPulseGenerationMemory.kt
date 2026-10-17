package com.pulseplayer.app.aipulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, chronological, bounded generation history. Does not train model weights.
 * Store no source audio, access tokens, or raw voice recordings here.
 */
class AiPulseGenerationMemory(context: Context) {
    private val prefs = context.getSharedPreferences("ai_pulse_generation_memory_v1", Context.MODE_PRIVATE)

    data class Event(
        val id: String,
        val timestampMs: Long,
        val backendId: String,
        val task: String,
        val promptSummary: String,
        val outcome: String,
        val durationMs: Long,
        val userRating: Int? = null
    )

    @Synchronized fun record(event: Event) {
        require(event.id.isNotBlank() && event.backendId.isNotBlank())
        require(event.userRating == null || event.userRating in 1..5)
        val entries = readArray()
        val item = JSONObject().apply {
            put("id", event.id.take(120))
            put("time", event.timestampMs)
            put("backend", event.backendId.take(120))
            put("task", event.task.take(80))
            put("prompt", event.promptSummary.take(240))
            put("outcome", event.outcome.take(120))
            put("duration", event.durationMs.coerceAtLeast(0))
            if (event.userRating != null) put("rating", event.userRating)
        }
        val items = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            .filterNot { it.optString("id") == event.id } + item
        val trimmed = items.sortedWith(compareBy<JSONObject> { it.optLong("time") }.thenBy { it.optString("id") })
            .takeLast(200)
        val output = JSONArray()
        trimmed.forEach(output::put)
        check(prefs.edit().putString("events", output.toString()).commit()) { "Unable to persist generation history" }
    }

    @Synchronized fun recent(limit: Int = 20): List<Event> {
        val array = readArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
            Event(
                id = it.optString("id"),
                timestampMs = it.optLong("time"),
                backendId = it.optString("backend"),
                task = it.optString("task"),
                promptSummary = it.optString("prompt"),
                outcome = it.optString("outcome"),
                durationMs = it.optLong("duration"),
                userRating = if (it.has("rating")) it.optInt("rating") else null
            )
        }.sortedWith(compareByDescending<Event> { it.timestampMs }.thenByDescending { it.id })
            .take(limit.coerceIn(0, 200))
    }

    /** Deterministic feedback summary, not a language-model inference or weight update. */
    fun summary(): String {
        val events = recent(200)
        val success = events.count { it.outcome.equals("success", ignoreCase = true) }
        val failed = events.count { it.outcome.equals("failed", ignoreCase = true) }
        val rated = events.mapNotNull { it.userRating }
        val average = if (rated.isEmpty()) "unrated" else "%.2f/5".format(java.util.Locale.US, rated.average())
        val backendCounts = events.groupingBy { it.backendId }.eachCount().toSortedMap()
            .entries.joinToString(", ") { "${it.key}: ${it.value}" }
        return "Attempts: ${events.size}; successful: $success; failed: $failed; average feedback: $average; backends: $backendCounts"
    }

    @Synchronized fun clear() {
        check(prefs.edit().remove("events").commit()) { "Unable to clear generation history" }
    }

    private fun readArray(): JSONArray = try {
        JSONArray(prefs.getString("events", "[]") ?: "[]")
    } catch (_: org.json.JSONException) {
        JSONArray()
    }
}
