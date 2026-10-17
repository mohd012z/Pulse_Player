package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPulseWavResamplerTest {
    @Test fun resamplesStereoPcmAndRetainsDuration() {
        val original = File.createTempFile("pulse-original-", ".wav")
        val converted = File.createTempFile("pulse-converted-", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Resample", seconds = 2), original)
            AiPulseWavResampler.convert(original, converted, 22050)
            assertEquals(22050, AiPulseWavResampler.sampleRate(converted))
            assertEquals(44L + 22050L * 2 * 4, converted.length())
            assertEquals(44100, AiPulseWavResampler.sampleRate(original))
        } finally { original.delete(); converted.delete() }
    }

    @Test fun cancelledResampleDoesNotLeavePartialOutput() {
        val original = File.createTempFile("pulse-original-", ".wav")
        val converted = File.createTempFile("pulse-converted-", ".wav")
        converted.delete()
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Cancel", seconds = 2), original)
            val result = runCatching {
                AiPulseWavResampler.convert(original, converted, 16000) { true }
            }
            assertTrue(result.exceptionOrNull() is InterruptedException)
            assertFalse(File(converted.absolutePath + ".part").exists())
        } finally { original.delete(); converted.delete() }
    }
}
