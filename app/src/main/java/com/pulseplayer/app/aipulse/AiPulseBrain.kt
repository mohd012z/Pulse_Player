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
        // Preserve the chronological block sequence. Earlier implementations counted
        // sections first and accidentally reordered repeated verses and choruses.
        val blocks = input.bars / 4
        val energies = mapOf(
            Section.INTRO to 0.40,
            Section.VERSE to 0.65,
            Section.CHORUS to 0.95,
            Section.BRIDGE to 0.55,
            Section.OUTRO to 0.35
        )
        val order = (0 until blocks).map { i ->
            when {
                i == 0 -> Section.INTRO
                i == blocks - 1 -> Section.OUTRO
                i % 4 == 3 -> Section.BRIDGE
                i % 2 == 0 -> Section.CHORUS
                else -> Section.VERSE
            }
        }
        var cursor = 0
        val segments = mutableListOf<Segment>()
        for (section in order) {
            val previous = segments.lastOrNull()
            if (previous?.section == section) {
                segments[segments.lastIndex] = previous.copy(bars = previous.bars + 4)
            } else {
                segments.add(Segment(
                    section = section,
                    startBar = cursor,
                    bars = 4,
                    chordRoots = progression,
                    energy = requireNotNull(energies[section]),
                    instruments = instrumentation
                ))
            }
            cursor += 4
        }
        check(cursor == input.bars)
        return Plan(input.bpm, input.genre, segments)
    }
}
