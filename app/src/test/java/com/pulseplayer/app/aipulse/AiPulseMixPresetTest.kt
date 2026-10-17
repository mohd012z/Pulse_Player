package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseMixPresetTest {
    @Test fun defaultPresetPreservesPreviousMixValues() {
        val p = AiPulseMixPreset()
        p.validate()
        assertEquals(100, p.lead)
        assertEquals(65, p.musicGain)
        assertEquals(75, p.vocalGain)
        assertEquals(AiPulseTrackMixer.Track.NONE, p.solo)
    }

    @Test fun invalidFaderAndDelayAreRejected() {
        assertTrue(runCatching { AiPulseMixPreset(lead = 101).validate() }.isFailure)
        assertTrue(runCatching { AiPulseMixPreset(vocalGain = -1).validate() }.isFailure)
        assertTrue(runCatching { AiPulseMixPreset(vocalDelayMs = 30001).validate() }.isFailure)
    }
}
