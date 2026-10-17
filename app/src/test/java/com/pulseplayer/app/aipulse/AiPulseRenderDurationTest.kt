package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseRenderDurationTest {
    @Test fun durationCoversEveryBarAtDifferentTempos() {
        for (bpm in listOf(60, 90, 120, 180)) {
            val plan = AiPulseBrain.plan(AiPulseBrain.Input("Pop Rock", "bright", bpm, 32))
            val seconds = AiPulseRenderDuration.secondsFor(plan)
            assertTrue(seconds.toDouble() >= 32 * 4.0 * 60.0 / bpm)
            assertTrue(seconds in 2..AiPulseRenderDuration.MAX_SECONDS)
        }
    }

    @Test fun rejectsTimelinesBeyondThreeMinutes() {
        val plan = AiPulseBrain.plan(AiPulseBrain.Input("Ballad", "bright", 60, 128))
        assertTrue(runCatching { AiPulseRenderDuration.secondsFor(plan) }.isFailure)
    }

    @Test fun incompatibleTimelinesAreNotSilentlyIgnored() {
        val plan = AiPulseBrain.plan(AiPulseBrain.Input("EDM", "bright", 120, 16))
        assertTrue(AiPulseRenderDuration.compatible(plan, "EDM", 120))
        assertFalse(AiPulseRenderDuration.compatible(plan, "Ballad", 120))
        assertFalse(AiPulseRenderDuration.compatible(plan, "EDM", 121))
    }
}
