package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Test

class AiPulseProjectCompatibilityTest {
    @Test fun legacyConstructorUsesDefaultMixer() {
        val project = AiPulseProject(
            title = "Legacy", idea = "Test", genre = "Acoustic",
            language = "English", mood = "bright",
            instruments = listOf("guitar"), lyrics = "Original", bpm = 100
        )
        assertEquals(100, project.mixer.lead)
        assertEquals(65, project.mixer.musicGain)
        assertEquals(AiPulseTrackMixer.Track.NONE, project.mixer.solo)
    }

    @Test fun customMixerCanBeAttachedToProject() {
        val mixer = AiPulseMixPreset(lead = 45, muteBass = true,
            solo = AiPulseTrackMixer.Track.PAD, vocalDelayMs = 500)
        val project = AiPulseProject(
            title = "Arrangement", idea = "Test", genre = "Pop Rock",
            language = "English", mood = "bright",
            instruments = emptyList(), lyrics = "", bpm = 120, mixer = mixer
        )
        assertEquals(45, project.mixer.lead)
        assertEquals(true, project.mixer.muteBass)
        assertEquals(500, project.mixer.vocalDelayMs)
    }
}
