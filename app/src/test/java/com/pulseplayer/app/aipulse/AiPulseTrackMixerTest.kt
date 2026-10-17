package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Test

class AiPulseTrackMixerTest {
    @Test fun mutePreservesOtherFaderLevels() {
        val base = AiPulseStereoRenderer.Levels(0.8, 0.7, 0.6, 0.5)
        val mixed = AiPulseTrackMixer.apply(base, AiPulseTrackMixer.Settings(bassMuted = true))
        assertEquals(0.8, mixed.lead, 0.0)
        assertEquals(0.0, mixed.bass, 0.0)
        assertEquals(0.6, mixed.pad, 0.0)
        assertEquals(0.5, mixed.drums, 0.0)
        assertEquals(0.7, base.bass, 0.0)
    }
    @Test fun soloOverridesMuteAndSilencesOtherTracks() {
        val base = AiPulseStereoRenderer.Levels(0.8, 0.7, 0.6, 0.5)
        val mixed = AiPulseTrackMixer.apply(base, AiPulseTrackMixer.Settings(
            leadMuted = true, solo = AiPulseTrackMixer.Track.LEAD))
        assertEquals(0.8, mixed.lead, 0.0)
        assertEquals(0.0, mixed.bass, 0.0)
        assertEquals(0.0, mixed.pad, 0.0)
        assertEquals(0.0, mixed.drums, 0.0)
    }
    @Test fun defaultPolicyLeavesMixUnchanged() {
        val base = AiPulseStereoRenderer.Levels(0.2, 0.4, 0.6, 0.8)
        assertEquals(base, AiPulseTrackMixer.apply(base, AiPulseTrackMixer.Settings()))
    }
}
