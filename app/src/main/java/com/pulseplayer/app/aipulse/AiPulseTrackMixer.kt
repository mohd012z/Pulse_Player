package com.pulseplayer.app.aipulse

/**
 * Pure offline track-level mute/solo control.
 * SOLO overrides mute on the selected track; all other tracks become silent.
 * Instrument fader values remain unchanged so users can restore their mix.
 */
object AiPulseTrackMixer {
    enum class Track { NONE, LEAD, BASS, PAD, DRUMS }
    data class Settings(
        val leadMuted: Boolean = false,
        val bassMuted: Boolean = false,
        val padMuted: Boolean = false,
        val drumsMuted: Boolean = false,
        val solo: Track = Track.NONE
    )

    fun apply(base: AiPulseStereoRenderer.Levels, settings: Settings): AiPulseStereoRenderer.Levels {
        base.validate()
        fun level(track: Track, original: Double, muted: Boolean): Double =
            if (settings.solo != Track.NONE) {
                if (settings.solo == track) original else 0.0
            } else if (muted) 0.0 else original
        return AiPulseStereoRenderer.Levels(
            lead = level(Track.LEAD, base.lead, settings.leadMuted),
            bass = level(Track.BASS, base.bass, settings.bassMuted),
            pad = level(Track.PAD, base.pad, settings.padMuted),
            drums = level(Track.DRUMS, base.drums, settings.drumsMuted)
        )
    }
}
