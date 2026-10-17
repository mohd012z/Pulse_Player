package com.pulseplayer.app.aipulse

import org.json.JSONArray
import org.json.JSONObject

/** Serialization of editable, offline arrangement timelines. */
object AiPulseArrangementStorage {
    fun toJson(plan: AiPulseBrain.Plan): JSONObject {
        val validated = AiPulseArrangementEditor.rebuild(plan)
        return JSONObject().apply {
            put("bpm", validated.bpm)
            put("genre", validated.genre)
            put("segments", JSONArray().apply {
                validated.segments.forEach { segment ->
                    put(JSONObject().apply {
                        put("section", segment.section.name)
                        put("startBar", segment.startBar)
                        put("bars", segment.bars)
                        put("chordRoots", JSONArray(segment.chordRoots))
                        put("energy", segment.energy)
                        put("instruments", JSONArray(segment.instruments))
                    })
                }
            })
        }
    }

    fun fromJson(data: JSONObject): AiPulseBrain.Plan {
        val bpm = data.getInt("bpm")
        require(bpm in 60..180)
        val genre = data.getString("genre")
        val items = data.getJSONArray("segments")
        require(items.length() in 1..32) { "Invalid arrangement section count" }
        val segments = (0 until items.length()).map { index ->
            val row = items.getJSONObject(index)
            val roots = row.getJSONArray("chordRoots")
            val instruments = row.getJSONArray("instruments")
            require(roots.length() in 1..16 && instruments.length() <= 16)
            val energy = row.getDouble("energy")
            require(energy.isFinite() && energy in 0.0..1.0)
            val segment = AiPulseBrain.Segment(
                section = AiPulseBrain.Section.valueOf(row.getString("section")),
                startBar = row.getInt("startBar"),
                bars = row.getInt("bars"),
                chordRoots = (0 until roots.length()).map { roots.getInt(it) },
                energy = energy,
                instruments = (0 until instruments.length()).map { instruments.getString(it).take(60) }
            )
            require(segment.startBar >= 0 && segment.chordRoots.all { it in 0..11 })
            segment
        }
        var cursor = 0
        segments.forEach { segment ->
            require(segment.startBar == cursor) { "Arrangement contains an invalid bar offset" }
            cursor += segment.bars
        }
        return AiPulseArrangementEditor.rebuild(AiPulseBrain.Plan(bpm, genre, segments))
    }
}
