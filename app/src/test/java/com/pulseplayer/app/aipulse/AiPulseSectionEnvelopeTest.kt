package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseSectionEnvelopeTest {
    private val plan = AiPulseBrain.Plan(
        bpm = 120,
        genre = "Pop Rock",
        segments = listOf(
            AiPulseBrain.Segment(AiPulseBrain.Section.VERSE, 0, 4,
                listOf(0, 7, 5, 9), 0.3, listOf("guitar")),
            AiPulseBrain.Segment(AiPulseBrain.Section.CHORUS, 4, 4,
                listOf(0, 7, 5, 9), 0.9, listOf("guitar"))
        )
    )

    @Test fun preservesEnergyExactlyAtBoundary() {
        assertEquals(0.3, AiPulseSectionEnvelope.energyAt(plan, 16.0), 1e-9)
    }

    @Test fun transitionsSmoothlyWithoutOvershoot() {
        val values = (0..25).map { i ->
            AiPulseSectionEnvelope.energyAt(plan, 16.0 + i / 100.0)
        }
        assertTrue(values.all { it in 0.3..0.9 })
        assertTrue(values.zipWithNext().all { (before, after) -> after >= before })
        assertEquals(0.9, values.last(), 1e-9)
    }

    @Test fun reachesSteadyLevelAndRejectsInvalidBeats() {
        assertEquals(0.9, AiPulseSectionEnvelope.energyAt(plan, 20.0), 1e-9)
        assertTrue(runCatching { AiPulseSectionEnvelope.energyAt(plan, Double.NaN) }.isFailure)
        assertTrue(runCatching { AiPulseSectionEnvelope.energyAt(plan, -1.0) }.isFailure)
    }
}
