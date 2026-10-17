package com.pulseplayer.app.aipulse

import java.security.MessageDigest

/**
 * Original local composition-variation engine.
 * Deterministic prompt-conditioned seeds; does not claim neural audio or singing.
 */
internal object AiPulseVariationEngine {
    fun seed(prompt: String, lyrics: String, genre: String, mood: String, variation: Int): Int {
        require(variation in 0..999999) { "Variation out of range" }
        val input = listOf(prompt.trim(), lyrics.trim(), genre, mood, variation.toString())
            .joinToString("\u001f").toByteArray(Charsets.UTF_8)
        val bytes = MessageDigest.getInstance("SHA-256").digest(input)
        return ((bytes[0].toInt() and 255) shl 24) or
            ((bytes[1].toInt() and 255) shl 16) or
            ((bytes[2].toInt() and 255) shl 8) or (bytes[3].toInt() and 255)
    }
}
