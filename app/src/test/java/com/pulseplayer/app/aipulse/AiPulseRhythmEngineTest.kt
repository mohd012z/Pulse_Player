package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseRhythmEngineTest {
    @Test fun rhythmOutputIsDeterministicAndBounded() {
        for (genre in listOf("Pop Rock", "Ballad", "R&B", "Acoustic", "EDM")) {
            val values = (0..100).map {
                AiPulseRhythmEngine.sample(genre, it / 20.0, it / 40.0, 42)
            }
            assertEquals(values, (0..100).map {
                AiPulseRhythmEngine.sample(genre, it / 20.0, it / 40.0, 42)
            })
            assertTrue(values.all { it.isFinite() && it in -0.25..0.25 })
            assertTrue(values.any { it != 0.0 })
        }
    }
    @Test fun invalidTimingCannotEnterRenderer() {
        assertTrue(runCatching {
            AiPulseRhythmEngine.sample("EDM", Double.NaN, 0.0, 1)
        }.isFailure)
        assertTrue(runCatching {
            AiPulseRhythmEngine.sample("EDM", 1.0, -1.0, 1)
        }.isFailure)
    }
}
