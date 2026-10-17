package com.pulseplayer.app.aipulse

/**
 * AI Pulse Brain v1: deterministic, offline, explainable arrangement planner.
 * Does not scrape songs, copy melodies, or claim neural inference.
 * All patterns are generated from user parameters and original rule sets.
 */
object AiPulseBrain {
    enum class Section { INTRO, VERSE, CHORUS, BRIDGE, OUTRO }
    data class Input(
        val genre: String,
        val mood: String,
        val bpm: Int,
        val bars: Int = 16,
        val seed: Int = 42
    )
    data class Segment(
        val section: Section,
        val startBar: Int,
        val bars: Int,
        val chordRoots: List<Int>,
        val energy: Double,
        val instruments: List<String>
    )
    data class Plan(val bpm: Int, val genre: String, val segments: List<Segment>) {
        val totalBars: Int get() = segments.sumOf { it.bars }
    }

    fun plan(input: Input): Plan {
        require(input.bpm in 60..180) { "Tempo outside supported range" }
        require(input.bars in 8..128 && input.bars % 4 == 0) { "Bars must be 8–128, divisible by four" }
        val minor = input.mood.lowercase() in setOf("sad", "dark", "minor", "melancholy")
        val progression = when (input.genre) {
            "Ballad" -> listOf(0, 7, 9, 5)
            "R&B" -> listOf(0, 5, 9, 7)
            "EDM" -> listOf(0, 9, 5, 7)
            "Acoustic" -> listOf(0, 5, 7, 0)
            else -> listOf(0, 7, 5, 9)
        }.map { (it + if (minor) 3 else 0) % 12 }
        val instrumentation = when (input.genre) {
            "Ballad" -> listOf("piano", "bass", "soft drums")
            "R&B" -> listOf("keys", "bass", "syncopated drums")
            "EDM" -> listOf("synth", "sub bass", "electronic drums")
            "Acoustic" -> listOf("acoustic guitar", "bass", "percussion")
            else -> listOf("guitar", "bass", "drums")
        }
        val sections = listOf(Section.INTRO, Section.VERSE, Section.CHORUS, Section.BRIDGE, Section.OUTRO)
        val blocks = input.bars / 4
        val counts = IntArray(5)
        for (i in 0 until blocks) {
            val target = when {
                i == 0 -> 0
                i == blocks - 1 -> 4
                i % 4 == 3 -> 3
                i % 2 == 0 -> 2
                else -> 1
            }
            counts[target]++
        }
        var cursor = 0
        val segments = sections.indices.mapNotNull { i ->
            val count = counts[i] * 4
            if (count == 0) null else Segment(
                section = sections[i],
                startBar = cursor,
                bars = count,
                chordRoots = progression,
                energy = listOf(0.40, 0.65, 0.95, 0.55, 0.35)[i],
                instruments = instrumentation
            ).also { cursor += count }
        }
        check(cursor == input.bars)
        return Plan(input.bpm, input.genre, segments)
    }
}
