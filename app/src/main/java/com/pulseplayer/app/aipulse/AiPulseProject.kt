package com.pulseplayer.app.aipulse

import org.json.JSONArray
import org.json.JSONObject

/** Offline song-project model; lyrics and arrangement are editable, not automatically sung. */
data class AiPulseProject(
    val title: String,
    val idea: String,
    val genre: String,
    val language: String,
    val mood: String,
    val instruments: List<String>,
    val lyrics: String,
    val bpm: Int,
    val mixer: AiPulseMixPreset = AiPulseMixPreset(),
    val arrangement: AiPulseBrain.Plan? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("version", 3)
        put("title", title)
        put("idea", idea)
        put("genre", genre)
        put("language", language)
        put("mood", mood)
        put("instruments", JSONArray(instruments))
        put("lyrics", lyrics)
        put("bpm", bpm)
        put("mixer", JSONObject(mixer.toJson()))
        if (arrangement != null) put("arrangement", AiPulseArrangementStorage.toJson(arrangement))
    }.toString(2)

    companion object {
        fun fromJson(json: String): AiPulseProject {
            val o = JSONObject(json)
            val version = o.getInt("version")
            require(version in 1..3) { "Unsupported project version" }
            val array = o.getJSONArray("instruments")
            require(array.length() <= 32) { "Too many instruments" }
            val project = AiPulseProject(
                o.getString("title"), o.getString("idea"), o.getString("genre"),
                o.getString("language"), o.getString("mood"),
                (0 until array.length()).map { array.getString(it) },
                o.getString("lyrics"), o.getInt("bpm"),
                if (version >= 2) AiPulseMixPreset.fromJson(o.getJSONObject("mixer").toString())
                else AiPulseMixPreset(),
                if (version >= 3 && !o.isNull("arrangement") && o.has("arrangement"))
                    AiPulseArrangementStorage.fromJson(o.getJSONObject("arrangement")) else null
            )
            require(project.title.isNotBlank() && project.title.length <= 100)
            require(project.lyrics.length <= 12000)
            require(project.bpm in 60..180)
            require(project.arrangement == null ||
                (project.arrangement.bpm == project.bpm && project.arrangement.genre == project.genre)) {
                "Arrangement must match project tempo and genre"
            }
            return project
        }
    }
}

/** Rule-based assistant for offline creative scaffolding, not an LLM. */
object AiPulseSongAssistant {
    data class Style(val bpm: Int, val instruments: List<String>, val chords: String, val guidance: String)
    fun style(genre: String): Style = when (genre.lowercase()) {
        "pop rock" -> Style(108, listOf("Electric guitar", "Bass", "Drums", "Piano"), "G – D – Em – C", "Build from a quiet verse to a strong chorus.")
        "ballad" -> Style(76, listOf("Piano", "Strings", "Acoustic guitar"), "C – G – Am – F", "Keep the verse sparse; introduce strings in the chorus.")
        "r&b" -> Style(92, listOf("Keys", "Bass", "Drums"), "Am7 – Dm7 – G7 – Cmaj7", "Use syncopated drums and warm harmonies.")
        "acoustic" -> Style(96, listOf("Acoustic guitar", "Piano", "Shaker"), "D – A – Bm – G", "Use intimate dynamics and simple percussion.")
        "edm" -> Style(124, listOf("Synth", "Bass", "Electronic drums"), "Am – F – C – G", "Use a gradual build and a rhythmic instrumental hook.")
        else -> Style(100, listOf("Piano", "Bass", "Drums"), "C – G – Am – F", "Start with a clear melodic motif.")
    }
    fun lyricOutline(idea: String, language: String): String {
        val topic = idea.trim().take(200).ifBlank { "An original story" }
        val sections = if (language == "Bahasa Melayu") listOf("Rangkap 1", "Pra-Korus", "Korus", "Rangkap 2", "Jambatan", "Korus Akhir")
            else listOf("Verse 1", "Pre-Chorus", "Chorus", "Verse 2", "Bridge", "Final Chorus")
        return sections.joinToString("\n\n") { "[$it]\n(Write original lyrics about: $topic)" }
    }
}
