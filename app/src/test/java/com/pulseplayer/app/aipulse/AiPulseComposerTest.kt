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
            val rate = java.nio.ByteBuffer.wrap(bytes, 24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            assertEquals(44100, rate)
            assertEquals(44L + 44100L * 2 * 2, file.length())
            assertTrue(bytes.drop(44).any { it.toInt() != 0 })
        } finally { file.delete() }
    }

    @Test fun genreChangesArrangement() {
        val pop = File.createTempFile("pulse-pop-", ".wav")
        val edm = File.createTempFile("pulse-edm-", ".wav")
        try {
            AiPulseComposer.render(AiPulseComposer.Request("Pop", genre = "Pop Rock", seconds = 2), pop)
            AiPulseComposer.render(AiPulseComposer.Request("EDM", genre = "EDM", seconds = 2), edm)
            assertEquals(pop.length(), edm.length())
            assertTrue("Different genres should not produce identical PCM",
                !pop.readBytes().contentEquals(edm.readBytes()))
        } finally {
            pop.delete()
            edm.delete()
        }
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
