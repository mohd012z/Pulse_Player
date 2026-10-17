package com.pulseplayer.app.aipulse

import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Converts a licensed voicebank's explicit phoneme dictionary and note timeline
 * into the frame-aligned inputs accepted by AiPulseDiffSinger.
 *
 * No guessed G2P: unknown syllables are rejected, not silently mispronounced.
 */
internal object AiPulsePhraseBuilder {
    data class Note(val syllable: String, val midi: Int, val seconds: Double)
    fun build(notes: List<Note>, dictionaryJson: String, frameRate: Int): AiPulseDiffSinger.Phrase {
        require(frameRate in 20..1000) { "Invalid acoustic frame rate" }
        require(notes.isNotEmpty() && notes.size <= 256) { "Invalid note count" }
        val dictionary = JSONObject(dictionaryJson)
        val ids = ArrayList<Long>()
        val durations = ArrayList<Long>()
        val midiCurve = ArrayList<Float>()
        var totalFrames = 0
        for (note in notes) {
            require(note.midi in 0..127 && note.seconds.isFinite() &&
                note.seconds > 0 && note.seconds <= 30) { "Invalid note" }
            val syllable = note.syllable.trim().lowercase()
            require(dictionary.has(syllable)) { "Unknown phoneme mapping: $syllable" }
            val tokens = dictionary.getJSONArray(syllable)
            require(tokens.length() in 1..16) { "Invalid phoneme count" }
            val frames = (note.seconds * frameRate).roundToInt()
            require(frames >= tokens.length()) { "Note too short for phonemes" }
            val base = frames / tokens.length()
            val remainder = frames % tokens.length()
            for (i in 0 until tokens.length()) {
                val id = tokens.getLong(i)
                require(id >= 0) { "Invalid phoneme token" }
                ids.add(id)
                durations.add((base + if (i < remainder) 1 else 0).toLong())
            }
            repeat(frames) { midiCurve.add(note.midi.toFloat()) }
            totalFrames += frames
            require(totalFrames <= 100000) { "Song phrase too long" }
        }
        return AiPulseDiffSinger.Phrase(ids.toLongArray(), durations.toLongArray(),
            midiCurve.toFloatArray())
    }
}
