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
    @Test fun channelsDifferAndPeakStaysWithinHeadroom() {
        val output = File.createTempFile("pulse-channel-", ".wav")
        try {
            AiPulseStereoRenderer.render(
                AiPulseComposer.Request("Stereo separation", genre = "R&B", seconds = 2), output
            )
            val bytes = output.readBytes()
            var differenceCount = 0
            var maxAbsolute = 0
            fun pcm(offset: Int): Int {
                val raw = (bytes[offset].toInt() and 255) or
                    ((bytes[offset + 1].toInt() and 255) shl 8)
                return raw.toShort().toInt()
            }
            for (i in 44 until bytes.size - 3 step 4) {
                val left = pcm(i)
                val right = pcm(i + 2)
                if (left != right) differenceCount++
                maxAbsolute = maxOf(maxAbsolute, kotlin.math.abs(left), kotlin.math.abs(right))
            }
            assertTrue("Stereo channels should differ", differenceCount > 100)
            assertTrue("Output must retain headroom", maxAbsolute < 32767)
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
