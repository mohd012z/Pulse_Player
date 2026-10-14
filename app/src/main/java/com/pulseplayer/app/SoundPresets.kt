package com.pulseplayer.app

/** EQ curves in dB. Hardware equalizer implementation determines actual band count. */
object SoundPresets {
    val names = listOf("Flat", "Warm", "Vocal", "Bright", "Studio Punch", "Cinema", "Night", "Airy", "Immersive", "NovaStream TV", "R&B", "Rock", "Classical", "Subwoofer", "Loudness EQ", "Stereo Clarity", "3D Bass EQ", "Cinema Spatial EQ", "Studio Beats EQ", "Balanced Speaker", "Live Concert", "Noise Softening", "Custom")

    fun gainDb(name: String, frequencyHz: Double, customBass: Int = 0, customMid: Int = 0, customTreble: Int = 0): Double {
        require(frequencyHz >= 0.0 && frequencyHz.isFinite()) {
            "Frequency must be finite and non-negative"
        }
        return when (name) {
            "Flat" -> 0.0
            "Warm" -> when {
                frequencyHz < 180.0 -> 3.0
                frequencyHz < 700.0 -> 1.5
                frequencyHz > 5000.0 -> -1.5
                else -> 0.0
            }
            "Vocal" -> when {
                frequencyHz < 180.0 -> -2.0
                frequencyHz < 900.0 -> 0.0
                frequencyHz < 4500.0 -> 3.0
                else -> 0.5
            }
            "Bright" -> when {
                frequencyHz < 250.0 -> -1.0
                frequencyHz < 3000.0 -> 0.0
                frequencyHz < 7500.0 -> 2.0
                else -> 3.0
            }
            "Studio Punch" -> when {
                frequencyHz < 120.0 -> 4.0
                frequencyHz < 500.0 -> 1.5
                frequencyHz in 2000.0..6000.0 -> 2.0
                else -> 0.0
            }
            "Cinema" -> when {
                frequencyHz < 150.0 -> 3.0
                frequencyHz in 300.0..1400.0 -> -1.0
                frequencyHz > 4500.0 -> 2.0
                else -> 0.0
            }
            "Night" -> when {
                frequencyHz < 180.0 -> -2.0
                frequencyHz > 5000.0 -> -2.5
                else -> 0.0
            }
            "Airy" -> when {
                frequencyHz < 180.0 -> -1.5
                frequencyHz > 6000.0 -> 3.0
                else -> 0.0
            }
            // An EQ-only tuning; not licensed Atmos rendering or a surround decoder.
            "Immersive" -> when {
                frequencyHz < 150.0 -> 2.0
                frequencyHz < 650.0 -> -1.0
                frequencyHz in 1800.0..3800.0 -> 1.5
                frequencyHz > 6500.0 -> 2.0
                else -> 0.0
            }
            // "NovaStream TV" is an original cinematic profile, not vendor technology.
            "NovaStream TV" -> when {
                frequencyHz < 120.0 -> 2.5
                frequencyHz < 480.0 -> -1.0
                frequencyHz in 900.0..4300.0 -> 2.0
                frequencyHz > 6500.0 -> 1.0
                else -> 0.0
            }
            "R&B" -> when {
                frequencyHz < 150 -> 3.5
                frequencyHz < 600 -> 1.0
                frequencyHz in 1800.0..4000.0 -> 1.0
                else -> 0.0
            }
            "Rock" -> when {
                frequencyHz < 160 -> 2.5
                frequencyHz in 750.0..3600.0 -> 1.5
                frequencyHz > 5700 -> 1.0
                else -> 0.0
            }
            "Classical" -> when {
                frequencyHz < 170 -> 0.5
                frequencyHz in 1200.0..4100.0 -> 1.0
                frequencyHz > 6500 -> 1.0
                else -> 0.0
            }
            "Subwoofer" -> when {
                frequencyHz < 95 -> 4.0
                frequencyHz < 240 -> 2.0
                frequencyHz in 250.0..1000.0 -> -1.0
                else -> 0.0
            }
            "Loudness EQ" -> when {
                frequencyHz < 150 -> 2.0
                frequencyHz > 4500 -> 1.5
                else -> 0.0
            }
            "Stereo Clarity" -> when {
                frequencyHz < 150 -> -0.5
                frequencyHz in 1200.0..5000.0 -> 1.5
                else -> 0.0
            }
            "3D Bass EQ" -> when {
                frequencyHz < 160 -> 3.0
                frequencyHz in 300.0..1500.0 -> -1.0
                frequencyHz > 6500 -> 1.0
                else -> 0.0
            }
            // EQ approximation only: not object-based Dolby Atmos rendering.
            "Cinema Spatial EQ" -> when {
                frequencyHz < 160 -> 2.5
                frequencyHz in 500.0..1100.0 -> -1.0
                frequencyHz > 4500 -> 1.5
                else -> 0.0
            }
            // Original tunings. Not licensed Beats, Bose, or Altec Lansing processing.
            "Studio Beats EQ" -> when {
                frequencyHz < 140 -> 3.5
                frequencyHz in 700.0..3000.0 -> 0.5
                frequencyHz > 5000 -> 1.5
                else -> 0.0
            }
            "Balanced Speaker" -> when {
                frequencyHz < 90 -> -2.0
                frequencyHz in 1400.0..4000.0 -> 1.0
                else -> 0.0
            }
            "Live Concert" -> when {
                frequencyHz < 140 -> 1.5
                frequencyHz in 1800.0..4000.0 -> 2.0
                frequencyHz > 6400 -> 1.0
                else -> 0.0
            }
            // Gentle low/high attenuation can soften rumble/hiss but does not restore MP3 data.
            "Noise Softening" -> when {
                frequencyHz < 110 -> -4.0
                frequencyHz < 220 -> -1.5
                frequencyHz > 7500 -> -4.0
                frequencyHz > 5200 -> -1.5
                else -> 0.0
            }
            "Custom" -> when {
                frequencyHz < 250 -> customBass.coerceIn(-6, 6).toDouble()
                frequencyHz < 4000 -> customMid.coerceIn(-6, 6).toDouble()
                else -> customTreble.coerceIn(-6, 6).toDouble()
            }
            else -> throw IllegalArgumentException("Unknown preset: $name")
        }
    }
}