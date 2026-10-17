package com.pulseplayer.app.aipulse

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Original band-limited additive oscillator bank with distinct genre timbres.
 * No samples, SoundFonts, protected recordings or model weights are embedded.
 * Harmonics above Nyquist are excluded to avoid elementary oscillator aliasing.
 */
object AiPulseTimbreEngine {
    enum class Voice { LEAD, BASS, PAD }

    fun sample(voice: Voice, genre: String, frequency: Double, seconds: Double,
               beatPhase: Double, sampleRate: Int = 44100): Double {
        require(frequency.isFinite() && frequency in 1.0..12000.0)
        require(seconds.isFinite() && seconds >= 0.0)
        require(beatPhase.isFinite() && beatPhase >= 0.0)
        require(sampleRate in 16000..192000)
        val ratios = when (voice) {
            Voice.LEAD -> when (genre) {
                "EDM" -> doubleArrayOf(1.0, 0.48, 0.27, 0.15, 0.08)
                "Ballad" -> doubleArrayOf(1.0, 0.15, 0.06)
                "Acoustic" -> doubleArrayOf(1.0, 0.33, 0.17, 0.07)
                else -> doubleArrayOf(1.0, 0.34, 0.18, 0.08)
            }
            Voice.BASS -> doubleArrayOf(1.0, 0.32, 0.11)
            Voice.PAD -> doubleArrayOf(1.0, 0.19, 0.11, 0.06)
        }
        val fundamental = when (voice) {
            Voice.LEAD -> frequency * 2.0
            Voice.BASS -> frequency * 0.5
            Voice.PAD -> frequency
        }
        val nyquist = sampleRate * 0.5
        var sum = 0.0
        var weight = 0.0
        for (index in ratios.indices) {
            val hz = fundamental * (index + 1)
            if (hz >= nyquist) break
            sum += ratios[index] * sin(2.0 * PI * hz * seconds)
            weight += ratios[index]
        }
        val envelope = when (voice) {
            Voice.LEAD -> exp(-2.0 * (beatPhase % 1.0))
            Voice.BASS -> 0.85 + 0.15 * exp(-5.0 * (beatPhase % 1.0))
            Voice.PAD -> 0.85
        }
        val amplitude = when (voice) {
            Voice.LEAD -> 0.17
            Voice.BASS -> 0.17
            Voice.PAD -> 0.13
        }
        return if (weight > 0.0) (sum / weight * envelope * amplitude).coerceIn(-0.20, 0.20) else 0.0
    }
}
