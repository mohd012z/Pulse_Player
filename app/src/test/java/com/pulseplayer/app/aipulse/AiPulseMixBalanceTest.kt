package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPulseMixBalanceTest {
    @Test fun mutedInputsProduceSilence() {
        val music = File.createTempFile("pulse-music-", ".wav")
        val vocal = File.createTempFile("pulse-vocal-", ".wav")
        val result = File.createTempFile("pulse-mix-", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Instrumental", seconds = 2), music)
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Voice", seconds = 2), vocal)
            AiPulseWavMixer.mix(music, vocal, result,
                AiPulseWavMixer.Settings(musicGain = 0.0, voiceGain = 0.0))
            val bytes = result.readBytes()
            assertTrue(bytes.drop(44).all { it.toInt() == 0 })
            assertEquals(44L + 44100L * 2 * 4, result.length())
        } finally { music.delete(); vocal.delete(); result.delete() }
    }

    @Test fun delayAddsAudioFramesToOutput() {
        val music = File.createTempFile("pulse-music-", ".wav")
        val vocal = File.createTempFile("pulse-vocal-", ".wav")
        val result = File.createTempFile("pulse-mix-", ".wav")
        try {
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Instrumental", seconds = 2), music)
            AiPulseStereoRenderer.render(AiPulseComposer.Request("Voice", seconds = 2), vocal)
            AiPulseWavMixer.mix(music, vocal, result,
                AiPulseWavMixer.Settings(voiceDelayMs = 500))
            assertEquals(44L + (44100L * 5 / 2) * 4, result.length())
        } finally { music.delete(); vocal.delete(); result.delete() }
    }
}
