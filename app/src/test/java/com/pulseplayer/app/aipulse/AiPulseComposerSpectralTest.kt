package com.pulseplayer.app.aipulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Regression tests that the offline composer output is a multi-voice mix, not a
 * single sine (the "MIDI beep" failure mode). Uses a small DFT over a render
 * window to assert energy is spread across multiple pitch harmonics.
 */
class AiPulseComposerSpectralTest {
    private fun readPcm(file: File): IntArray {
        val bytes = file.readBytes()
        val n = (bytes.size - 44) / 2
        val out = IntArray(n)
        var i = 44
        for (k in 0 until n) {
            val raw = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            out[k] = raw.toShort().toInt() // signed -32768..32767
            i += 2
        }
        return out
    }

    private fun dftMagnitude(x: DoubleArray, bins: Int): DoubleArray {
        val n = x.size
        val out = DoubleArray(bins)
        for (b in 0 until bins) {
            var re = 0.0
            var im = 0.0
            val w = 2.0 * Math.PI * b / n
            for (t in 0 until n) {
                re += x[t] * Math.cos(w * t)
                im -= x[t] * Math.sin(w * t)
            }
            out[b] = Math.hypot(re, im) / n
        }
        return out
    }

    @Test fun notASingleSineTone() {
        val file = File.createTempFile("ai-pulse-spectral-", ".wav")
        try {
            AiPulseComposer.render(
                AiPulseComposer.Request("Spectral", genre = "Pop Rock", bpm = 110, seconds = 4, seed = 7),
                file
            )
            val pcm = readPcm(file)
            // Windowed mid-section (avoid intro/outro ramps), 2048 samples.
            val win = 2048
            val start = pcm.size / 2
            val x = DoubleArray(win) { (pcm[start + it].toDouble() / 32768.0) * (0.5 + 0.5 * Math.cos(2.0 * Math.PI * it / win)) }

            // Level must be a healthy audible mix: not near-silence, and the
            // limiter must hold the peak below full scale (no digital clipping).
            var sumSq = 0.0
            var peak = 0
            for (s in pcm) { sumSq += s.toDouble() * s; val m = abs(s); if (m > peak) peak = m }
            val rms = Math.sqrt(sumSq / pcm.size) / 32768.0
            val peakNorm = peak / 32768.0
            assertTrue("RMS too low (near silence): $rms", rms > 0.03)
            assertTrue("Peak too high / limiter failed (clipping risk): $peakNorm", peakNorm < 0.92)
            assertTrue("Signal must not be DC / silence in window",
                x.sumOf { it * it } > 1e-3)

            // Spectrum: energy must be spread across the low-frequency bins
            // (fundamental + several harmonics + drum transients), not one spike.
            val mags = dftMagnitude(x, 1024)
            val floor = mags.max() * 0.01
            var significantBins = 0
            for (b in 1 until 256) if (mags[b] > floor) significantBins++
            assertTrue("Energy is too concentrated in one bin (single-sine / MIDI-like); significant bins=$significantBins",
                significantBins >= 6)

            // A single pure tone has near-zero spectral entropy spread; a mix does not.
            val band = mags.copyOfRange(1, 256)
            val top = band.max()
            val rest = band.sum() - top
            assertTrue("Rest-of-spectrum energy too low vs peak (peak share=${top / band.sum()}): MIDI-like",
                rest > top * 0.5)
        } finally {
            file.delete()
        }
    }

    @Test fun genresDifferSpectrally() {
        val a = File.createTempFile("ai-pulse-a-", ".wav")
        val b = File.createTempFile("ai-pulse-b-", ".wav")
        try {
            AiPulseComposer.render(AiPulseComposer.Request("A", genre = "EDM", seconds = 3, seed = 1), a)
            AiPulseComposer.render(AiPulseComposer.Request("B", genre = "Ballad", seconds = 3, seed = 1), b)
            val pa = readPcm(a)
            val pb = readPcm(b)
            assertEquals(pa.size, pb.size)
            // Same length, different PCM (drum/arrangement differences).
            assertTrue("EDM and Ballad should differ", !pa.contentEquals(pb))
        } finally {
            a.delete(); b.delete()
        }
    }
}
