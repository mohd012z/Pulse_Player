package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPulseTimbreEngineTest {
    @Test fun eachInstrumentIsDeterministicAndBounded() {
        for (voice in AiPulseTimbreEngine.Voice.entries) {
            for (genre in listOf("Pop Rock", "Ballad", "R&B", "Acoustic", "EDM")) {
                val samples = (1..64).map { i ->
                    AiPulseTimbreEngine.sample(voice, genre, 220.0, i / 44100.0, 0.1)
                }
                assertTrue(samples.any { it != 0.0 })
                assertTrue(samples.all { it.isFinite() && it in -0.20..0.20 })
                assertEquals(samples, (1..64).map { i ->
                    AiPulseTimbreEngine.sample(voice, genre, 220.0, i / 44100.0, 0.1)
                })
            }
        }
    }

    @Test fun voiceAndGenreProduceDifferentTimbres() {
        val pianoLike = AiPulseTimbreEngine.sample(
            AiPulseTimbreEngine.Voice.LEAD, "Ballad", 220.0, 0.0015, 0.1)
        val synth = AiPulseTimbreEngine.sample(
            AiPulseTimbreEngine.Voice.LEAD, "EDM", 220.0, 0.0015, 0.1)
        assertTrue(pianoLike != synth)
    }

    @Test fun invalidFrequencyAndTimingAreRejected() {
        assertTrue(runCatching {
            AiPulseTimbreEngine.sample(AiPulseTimbreEngine.Voice.BASS, "EDM",
                Double.NaN, 1.0, 0.1)
        }.isFailure)
        assertTrue(runCatching {
            AiPulseTimbreEngine.sample(AiPulseTimbreEngine.Voice.PAD, "EDM",
                220.0, -1.0, 0.1)
        }.isFailure)
    }
}
