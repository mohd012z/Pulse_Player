package com.pulseplayer.app.aipulse

import org.json.JSONObject

/** Versioned, local-only mixer settings. Kept separate from legacy song project JSON. */
data class AiPulseMixPreset(
    val lead: Int = 100,
    val bass: Int = 100,
    val pad: Int = 100,
    val drums: Int = 100,
    val muteLead: Boolean = false,
    val muteBass: Boolean = false,
    val mutePad: Boolean = false,
    val muteDrums: Boolean = false,
    val solo: AiPulseTrackMixer.Track = AiPulseTrackMixer.Track.NONE,
    val musicGain: Int = 65,
    val vocalGain: Int = 75,
    val vocalDelayMs: Int = 0,
    val exportStems: Boolean = false
) {
    fun validate() {
        require(listOf(lead, bass, pad, drums, musicGain, vocalGain).all { it in 0..100 })
        require(vocalDelayMs in 0..30000)
    }
    fun toJson(): String {
        validate()
        return JSONObject().apply {
            put("version", 1)
            put("lead", lead); put("bass", bass); put("pad", pad); put("drums", drums)
            put("muteLead", muteLead); put("muteBass", muteBass)
            put("mutePad", mutePad); put("muteDrums", muteDrums)
            put("solo", solo.name)
            put("musicGain", musicGain); put("vocalGain", vocalGain)
            put("vocalDelayMs", vocalDelayMs); put("exportStems", exportStems)
        }.toString(2)
    }
    companion object {
        fun fromJson(json: String): AiPulseMixPreset {
            val data = JSONObject(json)
            require(data.getInt("version") == 1) { "Unsupported mixer settings version" }
            return AiPulseMixPreset(
                lead = data.getInt("lead"), bass = data.getInt("bass"),
                pad = data.getInt("pad"), drums = data.getInt("drums"),
                muteLead = data.getBoolean("muteLead"), muteBass = data.getBoolean("muteBass"),
                mutePad = data.getBoolean("mutePad"), muteDrums = data.getBoolean("muteDrums"),
                solo = AiPulseTrackMixer.Track.valueOf(data.getString("solo")),
                musicGain = data.getInt("musicGain"), vocalGain = data.getInt("vocalGain"),
                vocalDelayMs = data.getInt("vocalDelayMs"), exportStems = data.getBoolean("exportStems")
            ).also { it.validate() }
        }
    }
}
