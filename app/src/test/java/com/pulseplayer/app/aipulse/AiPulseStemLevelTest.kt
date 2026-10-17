package com.pulseplayer.app.aipulse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPulseStemLevelTest {
    @Test fun mutedAllStemsProduceSilentWav() {
        val output = File.createTempFile("pulse-muted-", ".wav")
        try {
            AiPulseStereoRenderer.render(
                AiPulseComposer.Request("Muted", seconds = 2), output,
                levels = AiPulseStereoRenderer.Levels(0.0, 0.0, 0.0, 0.0)
            )
            val data = output.readBytes()
            assertEquals(44L + 44100L * 2 * 4, output.length())
            assertTrue(data.drop(44).all { it.toInt() == 0 })
        } finally { output.delete() }
    }

    @Test fun mutedIndividualStemIsSilentButDefaultIsNot() {
        val muted = File.createTempFile("pulse-bass-off-", ".wav")
        val original = File.createTempFile("pulse-bass-on-", ".wav")
        try {
            val request = AiPulseComposer.Request("Bass", seconds = 2)
            AiPulseStereoRenderer.render(request, muted,
                AiPulseStereoRenderer.Stem.BASS, AiPulseStereoRenderer.Levels(bass = 0.0))
            AiPulseStereoRenderer.render(request, original, AiPulseStereoRenderer.Stem.BASS)
            assertTrue(muted.readBytes().drop(44).all { it.toInt() == 0 })
            assertTrue(original.readBytes().drop(44).any { it.toInt() != 0 })
        } finally { muted.delete(); original.delete() }
    }

    @Test fun outOfRangeLevelsAreRejected() {
        val file = File.createTempFile("pulse-invalid-", ".wav")
        try {
            val outcome = runCatching {
                AiPulseStereoRenderer.render(
                    AiPulseComposer.Request("Invalid", seconds = 2),
                    file, levels = AiPulseStereoRenderer.Levels(lead = Double.NaN)
                )
            }
            assertTrue(outcome.isFailure)
        } finally { file.delete() }
    }
}
