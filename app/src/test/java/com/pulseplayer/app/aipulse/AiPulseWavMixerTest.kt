package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPulseWavMixerTest {
    @Test fun mixesTwoStereoWavsAndKeepsSources() {
        val music = File.createTempFile("pulse-music", ".wav")
        val voice = File.createTempFile("pulse-voice", ".wav")
        val output = File.createTempFile("pulse-mixed", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Music", seconds = 2), music)
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Voice", seconds = 2, seed = 7), voice)
            val sizeBefore = music.length()
            AiPulseWavMixer.mix(music, voice, output)
            assertEquals(sizeBefore, music.length())
            assertEquals(44L + 44100L * 2 * 4, output.length())
            val header = output.readBytes().copyOfRange(0, 4)
            assertEquals("RIFF", String(header, Charsets.US_ASCII))
        } finally { music.delete(); voice.delete(); output.delete() }
    }

    @Test fun mismatchedRatesRejectedAndTempRemoved() {
        val music = File.createTempFile("pulse-music", ".wav")
        val voice = File.createTempFile("pulse-voice", ".wav")
        val output = File.createTempFile("pulse-mixed", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Music", seconds = 2), music)
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Voice", seconds = 2), voice)
            java.io.RandomAccessFile(voice, "rw").use { f ->
                f.seek(24)
                f.write(byteArrayOf(0x80.toByte(), 0x3e, 0, 0)) // 16000 Hz
            }
            val result = runCatching { AiPulseWavMixer.mix(music, voice, output) }
            assertTrue(result.isFailure)
            assertTrue(!File(output.absolutePath + ".part").exists())
        } finally { music.delete(); voice.delete(); output.delete() }
    }
}
