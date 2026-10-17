package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPulseStemExportTest {
    @Test fun allStemsAreValidAlignedStereoWavs() {
        val request = AiPulseComposer.Request("Stem regression", seconds = 2)
        val files = mutableListOf<File>()
        try {
            for (stem in AiPulseStereoRenderer.Stem.entries) {
                val wav = File.createTempFile("pulse-${stem.name.lowercase()}-", ".wav")
                files.add(wav)
                AiPulseStereoRenderer.render(request, wav, stem)
                assertEquals(44L + 44100L * 2 * 4, wav.length())
                val head = wav.inputStream().use { it.readNBytes(44) }
                assertEquals("RIFF", String(head.copyOfRange(0, 4), Charsets.US_ASCII))
                assertEquals(2, head[22].toInt())
                assertEquals(16, head[34].toInt())
                assertTrue(wav.inputStream().use { stream ->
                    stream.skip(44)
                    stream.readNBytes(8192).any { it.toInt() != 0 }
                })
            }
        } finally { files.forEach { it.delete() } }
    }
}
