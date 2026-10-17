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

    @Test fun acceptsMetadataChunkBeforePcmData() {
        val original = File.createTempFile("pulse-original-", ".wav")
        val tagged = File.createTempFile("pulse-tagged-", ".wav")
        val converted = File.createTempFile("pulse-converted-", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Tagged", seconds = 2), original)
            val bytes = original.readBytes()
            // Insert an odd-length LIST metadata chunk between fmt and data.
            val metadata = byteArrayOf(
                'L'.code.toByte(), 'I'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(),
                5, 0, 0, 0, 1, 2, 3, 4, 5, 0
            )
            val taggedBytes = bytes.copyOfRange(0, 36) + metadata +
                bytes.copyOfRange(36, bytes.size)
            val riffSize = taggedBytes.size - 8
            for (i in 0..3) taggedBytes[4 + i] = (riffSize ushr (8 * i)).toByte()
            tagged.writeBytes(taggedBytes)

            assertEquals(44100, AiPulseWavResampler.sampleRate(tagged))
            AiPulseWavResampler.convert(tagged, converted, 22050)
            assertEquals(22050, AiPulseWavResampler.sampleRate(converted))
            assertEquals(44L + 22050L * 2 * 4, converted.length())
        } finally {
            original.delete()
            tagged.delete()
            converted.delete()
        }
    }

    @Test fun rejectsWavChunkThatExceedsRiffBoundary() {
        val original = File.createTempFile("pulse-original-", ".wav")
        val corrupt = File.createTempFile("pulse-corrupt-", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Corrupt", seconds = 2), original)
            val bytes = original.readBytes()
            // The data chunk begins at offset 36 in the canonical renderer WAV.
            for (i in 0..3) bytes[40 + i] = 0xFF.toByte()
            corrupt.writeBytes(bytes)
            assertTrue(runCatching { AiPulseWavResampler.sampleRate(corrupt) }.isFailure)
        } finally {
            original.delete()
            corrupt.delete()
        }
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
