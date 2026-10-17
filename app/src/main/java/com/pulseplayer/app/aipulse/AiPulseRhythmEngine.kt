package com.pulseplayer.app.aipulse

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Original deterministic percussion voices; no third-party samples.
 * Generates kick, snare and hat with bounded synthetic noise.
 */
object AiPulseRhythmEngine {
    fun sample(genre: String, beat: Double, seconds: Double, seed: Int): Double {
        require(beat.isFinite() && beat >= 0.0 && seconds.isFinite() && seconds >= 0.0)
        val beatInBar = beat % 4.0
        val beatPhase = beat % 1.0
        val step = kotlin.math.floor(beat * 2.0).toLong()
        val halfPhase = (beat * 2.0) % 1.0
        val kickOn = when (genre) {
            "EDM" -> true
            "Ballad" -> beatInBar < 1.0 || beatInBar in 2.0..3.0
            else -> beatInBar < 1.0 || beatInBar in 2.0..3.0
        }
        val kick = if (kickOn) {
            sin(2 * PI * 62.0 * seconds) * exp(-18.0 * beatPhase) * 0.16
        } else 0.0
        val snareOn = beatInBar in 1.0..2.0 || beatInBar >= 3.0
        val snarePhase = beatInBar % 1.0
        val noise = hashedNoise(step, seed)
        val snare = if (snareOn) noise * exp(-20.0 * snarePhase) * 0.055 else 0.0
        val hatLevel = when (genre) {
            "Ballad" -> 0.012
            "Acoustic" -> 0.018
            "EDM" -> 0.038
            "R&B" -> 0.028
            else -> 0.024
        }
        val hat = hashedNoise(step * 17 + 9, seed) * exp(-14.0 * halfPhase) * hatLevel
        return (kick + snare + hat).coerceIn(-0.25, 0.25)
    }

    private fun hashedNoise(index: Long, seed: Int): Double {
        var value = index xor (seed.toLong() * -7046029254386353131L)
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        value = value xor (value ushr 31)
        return ((value ushr 40).toDouble() / 8388607.5) - 1.0
    }
}
