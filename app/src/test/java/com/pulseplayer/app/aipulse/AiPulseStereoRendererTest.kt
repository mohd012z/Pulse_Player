package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPulseStereoRendererTest {
    @Test fun producesValidStereoPcm() {
        val output = File.createTempFile("pulse-stereo-", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Stereo test", seconds = 2), output)
            val bytes = output.readBytes()
            fun u16(i: Int) = (bytes[i].toInt() and 255) or ((bytes[i + 1].toInt() and 255) shl 8)
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
            assertEquals(2, u16(22))
            assertEquals(16, u16(34))
            assertEquals(44L + 44100L * 2 * 4, output.length())
            assertTrue(bytes.drop(44).any { it.toInt() != 0 })
        } finally { output.delete() }
    }
    @Test fun cancellationRemovesPartialFile() {
        val output = File.createTempFile("pulse-cancel-", ".wav")
        output.delete()
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Cancel"), output) { true }
            throw AssertionError("Expected cancellation")
        } catch (_: InterruptedException) {
            assertTrue(!File(output.absolutePath + ".part").exists())
        } finally { output.delete() }
    }
}
