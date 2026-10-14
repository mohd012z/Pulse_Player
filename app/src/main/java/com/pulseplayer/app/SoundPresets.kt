package com.pulseplayer.app

/** Original equalizer tunings in decibels; labels do NOT indicate licensed brand technology. */
object SoundPresets {
    val names = listOf(
        "Flat", "Warm", "Vocal", "Bright", "Studio Punch", "Cinema", "Night", "Airy",
        "Immersive", "NovaStream TV", "R&B", "Rock", "Classical", "Subwoofer",
        "Loudness", "Stereo Clarity", "3D Bass", "Cinema Surround", "Studio Bass",
        "Hi-Fi Warm", "Wide Stage", "Noise Softener", "Custom"
    )

    fun gainDb(name: String, frequencyHz: Double): Double {
        require(frequencyHz.isFinite() && frequencyHz >= 0) { "Invalid frequency" }
        val bass = frequencyHz < 180.0
        val lowMid = frequencyHz < 800.0
        val presence = frequencyHz in 1800.0..5500.0
        val treble = frequencyHz > 6500.0
        val value = when (name) {
            "Flat", "Custom" -> 0.0
            "Warm" -> when { bass -> 3.0; lowMid -> 1.5; treble -> -1.5; else -> 0.0 }
            "Vocal" -> when { bass -> -2.0; presence -> 3.0; treble -> 0.5; else -> 0.0 }
            "Bright" -> when { bass -> -1.0; treble -> 3.0; presence -> 2.0; else -> 0.0 }
            "Studio Punch" -> when { bass -> 4.0; lowMid -> 1.5; presence -> 2.0; else -> 0.0 }
            "Cinema" -> when { bass -> 3.0; lowMid -> -1.0; treble -> 2.0; else -> 0.0 }
            "Night" -> when { bass -> -2.0; treble -> -2.5; else -> 0.0 }
            "Airy" -> when { bass -> -1.5; treble -> 3.0; else -> 0.0 }
            "Immersive" -> when { bass -> 2.0; lowMid -> -1.0; presence -> 1.5; treble -> 2.0; else -> 0.0 }
            "NovaStream TV" -> when { bass -> 2.5; lowMid -> -1.0; presence -> 2.0; treble -> 1.0; else -> 0.0 }
            "R&B" -> when { bass -> 3.0; lowMid -> 1.0; presence -> 1.0; treble -> 1.0; else -> 0.0 }
            "Rock" -> when { bass -> 2.0; lowMid -> -1.0; presence -> 2.5; treble -> 2.0; else -> 0.0 }
            "Classical" -> when { bass -> 0.5; lowMid -> 0.5; presence -> 1.0; treble -> 1.5; else -> 0.0 }
            "Subwoofer" -> when { bass -> 4.0; lowMid -> 1.5; presence -> -1.0; treble -> -1.0; else -> 0.0 }
            "Loudness" -> when { bass -> 2.5; lowMid -> 0.0; presence -> 0.5; treble -> 2.0; else -> 0.0 }
            "Stereo Clarity" -> when { bass -> -1.0; presence -> 2.0; treble -> 1.0; else -> 0.0 }
            "3D Bass" -> when { bass -> 3.5; lowMid -> -1.0; presence -> 1.0; treble -> 1.5; else -> 0.0 }
            "Cinema Surround" -> when { bass -> 2.5; lowMid -> -1.5; presence -> 2.0; treble -> 1.0; else -> 0.0 }
            "Studio Bass" -> when { bass -> 3.5; lowMid -> 0.0; presence -> 1.0; treble -> 2.0; else -> 0.0 }
            "Hi-Fi Warm" -> when { bass -> 2.0; lowMid -> 1.0; presence -> 0.5; treble -> 1.0; else -> 0.0 }
            "Wide Stage" -> when { bass -> 0.5; lowMid -> -1.0; presence -> 1.5; treble -> 2.5; else -> 0.0 }
            // Only a high-frequency shelf reduction for hiss *perception*, not source denoising.
            "Noise Softener" -> when { bass -> 0.0; presence -> -1.5; treble -> -5.0; else -> 0.0 }
            else -> throw IllegalArgumentException("Unknown preset: $name")
        }
        return value.coerceIn(-6.0, 6.0)
    }
}