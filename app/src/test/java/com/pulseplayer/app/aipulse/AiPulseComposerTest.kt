package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class AiPulseComposerTest {
    @Test fun createsValidPcmWave() {
        val file = File.createTempFile("ai-pulse-", ".wav")
        try {
            val req = AiPulseComposer.Request("Demo", bpm = 120, seconds = 2)
            AiPulseComposer.render(req, file)
            val bytes = file.readBytes()
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
            assertEquals("data", String(bytes.copyOfRange(36, 40), Charsets.US_ASCII))
            assertEquals(44L + 22050L * 2 * 2, file.length())
            assertTrue(bytes.drop(44).any { it.toInt() != 0 })
        } finally { file.delete() }
    }

    @Test fun rejectsOutOfBoundsGeneration() {
        try {
            AiPulseComposer.validate(AiPulseComposer.Request("Test", seconds = 3600))
            fail("Expected bounds validation")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun cancellationCleansTemporaryFile() {
        val file = File.createTempFile("ai-pulse-cancel-", ".wav")
        file.delete()
        try {
            AiPulseComposer.render(AiPulseComposer.Request("Cancel"), file) { true }
            fail("Expected cancellation")
        } catch (_: InterruptedException) {
            assertTrue(!File(file.absolutePath + ".part").exists())
        } finally { file.delete() }
    }
}
