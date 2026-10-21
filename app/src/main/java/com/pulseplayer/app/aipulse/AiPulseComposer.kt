package com.pulseplayer.app.aipulse

import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Offline multi-voice instrumental synth. Deterministic (seeded) and original.
 *
 * Voices: lead (additive harmonics, per-note ADSR), bass (sine/square, plucked),
 * chord pad (triangle, per-bar), drums (phase-swept kick, resonant snare, HP
 * hats), plus a tempo-synced feedback delay and a 3-comb feedback reverb.
 *
 * Not a neural music model — no server, no GPU.
 */
object AiPulseComposer {
    data class Request(
        val title: String,
        val mood: String = "bright",
        val bpm: Int = 110,
        val seconds: Int = 16,
        val seed: Int = 42,
        val genre: String = "Pop Rock"
    )

    fun validate(request: Request) {
        require(request.title.isNotBlank() && request.title.length <= 100) { "Title must be 1–100 characters" }
        require(request.mood.length <= 40) { "Mood is too long" }
        require(request.bpm in 60..180) { "BPM must be 60–180" }
        require(request.genre in setOf("Pop Rock", "Ballad", "R&B", "Acoustic", "EDM")) { "Unsupported genre" }
        require(request.seconds in 2..AiPulseRenderDuration.MAX_SECONDS) { "Duration must be 2–180 seconds" }
    }

    private data class GenreVoice(
        val leadGain: Double, val bassGain: Double, val padGain: Double, val drumGain: Double,
        val hatDensity: Double, val delayMix: Double, val reverbMix: Double,
        val bassSquare: Boolean, val leadEdge: Double, val kickOnBeat: DoubleArray
    )

    private fun voice(genre: String): GenreVoice = when (genre) {
        "Ballad" -> GenreVoice(0.34, 0.30, 0.16, 0.42, 0.25, 0.28, 0.24, false, 0.45,
            doubleArrayOf(0.0, 2.5))
        "R&B" -> GenreVoice(0.30, 0.32, 0.18, 0.70, 0.75, 0.30, 0.22, true, 0.55,
            doubleArrayOf(0.0, 0.75, 2.0, 2.75))
        "EDM" -> GenreVoice(0.26, 0.34, 0.22, 1.0, 1.0, 0.24, 0.20, true, 0.8,
            doubleArrayOf(0.0, 1.0, 2.0, 3.0))
        "Acoustic" -> GenreVoice(0.32, 0.28, 0.14, 0.50, 0.5, 0.22, 0.26, false, 0.5,
            doubleArrayOf(0.0, 2.5))
        else -> GenreVoice(0.30, 0.34, 0.16, 0.80, 1.0, 0.26, 0.22, true, 0.7,
            doubleArrayOf(0.0, 1.0, 2.0, 3.0))
    }

    fun render(request: Request, output: File, cancelled: () -> Boolean = { false }): File {
        validate(request)
        require(output.extension.lowercase() == "wav") { "Output must be WAV" }
        val sampleRate = 44100
        val total = sampleRate * request.seconds
        val beatSeconds = 60.0 / request.bpm
        val beatHz = 1.0 / beatSeconds
        val v = voice(request.genre)
        val cheerful = request.mood.lowercase() !in setOf("sad", "dark", "minor", "melancholy")
        val leadPattern = if (cheerful)
            intArrayOf(0, 4, 7, 12, 7, 4, 2, 7, 4, 7, 12, 15, 12, 7, 4, 7)
        else
            intArrayOf(0, 3, 7, 10, 7, 3, 5, 7, 3, 7, 10, 12, 10, 7, 5, 7)
        val root = 48 + Math.floorMod(request.seed, 12)
        val plannedBars = max(8, (((request.seconds / beatSeconds / 4.0).toInt() + 3) / 4) * 4)
        val arrangement = AiPulseBrain.plan(
            AiPulseBrain.Input(request.genre, request.mood, request.bpm, plannedBars, request.seed)
        )
        val chordRoots = when (request.genre) {
            "Ballad" -> intArrayOf(0, 7, 9, 5)
            "R&B" -> intArrayOf(0, 5, 9, 7)
            "EDM" -> intArrayOf(0, 9, 5, 7)
            "Acoustic" -> intArrayOf(0, 5, 7, 0)
            else -> intArrayOf(0, 7, 5, 9)
        }
        val rng = SeededRng(request.seed.toLong() * 6364136223846793005L or 1L)

        // Per-beat plan: lead note, rest flags, hat level, kick/snare triggers.
        val totalBeats = (request.seconds * beatHz).toInt() + 4
        val leadBeat = IntArray(totalBeats)
        val leadRest = BooleanArray(totalBeats)
        val hatBeat = DoubleArray(totalBeats)
        val kickBeat = DoubleArray(totalBeats)
        val snareBeat = DoubleArray(totalBeats)
        for (b in 0 until totalBeats) {
            val bar = (b / 4).toInt()
            val beatInBar = b % 4
            val section = arrangement.segments.lastOrNull { bar >= it.startBar }
                ?: arrangement.segments.first()
            val rest = when (request.genre) {
                "Ballad" -> beatInBar >= 2
                "R&B" -> (beatInBar == 1 || beatInBar == 3) && section.energy < 0.7
                "EDM" -> beatInBar == 1
                else -> section.energy < 0.35 && beatInBar % 2 == 1
            }
            leadRest[b] = rest
            leadBeat[b] = leadPattern[(b * 2) % leadPattern.size]
            hatBeat[b] = if (rng.nextDouble() < v.hatDensity) 1.0 else 0.0
            for (kb in v.kickOnBeat) if (beatInBar.toDouble() == kb) kickBeat[b] += 1.0
            if (beatInBar in intArrayOf(1, 3) && section.energy > 0.4) snareBeat[b] = 1.0
        }

        // Effect state.
        val delayTaps = (beatHz * sampleRate / 2.0).toInt().coerceIn(256, 16384) // 8th notes
        val delayBuf = DoubleArray(delayTaps)
        var delayIdx = 0
        // Three feedback combs (Schroeder-style) for the reverb tail.
        val combDelays = intArrayOf(291, 371, 419).map { (it.toLong() * sampleRate / 44100).toInt() }
        val combBufs = Array(3) { DoubleArray(combDelays[it]) }
        val combIdx = IntArray(3)
        val combFeedback = doubleArrayOf(0.38, 0.35, 0.32)
        var lp1 = 0.0; var lp2 = 0.0
        // RBJ 2-pole low-pass, fc = 6.5 kHz (bright but not harsh).
        val fc = 6500.0
        val omega = 2.0 * PI * fc / sampleRate
        val alpha = sin(omega) / (2.0 * 0.7 + 1.0) // Q ≈ 0.7
        // Drum state.
        var kickPhase = 0.0
        var snareState = 0.0
        var hatLp = 0.0

        output.parentFile?.mkdirs()
        val temp = File(output.absolutePath + ".part")
        try {
            FileOutputStream(temp).use { stream ->
                writeHeader(stream, total, sampleRate)
                val buffer = ByteArray(4096)
                var pos = 0
                while (pos < total) {
                    if (cancelled()) throw InterruptedException("Generation cancelled")
                    val count = min(buffer.size / 2, total - pos)
                    for (j in 0 until count) {
                        val index = pos + j
                        val t = index.toDouble() / sampleRate
                        val beatPos = t * beatHz
                        val b = beatPos.toInt()
                        if (b >= totalBeats) {
                            buffer[j * 2] = 0; buffer[j * 2 + 1] = 0
                            continue
                        }
                        val beatFrac = beatPos - b
                        val bar = (b / 4).toInt()
                        val section = arrangement.segments.lastOrNull { bar >= it.startBar }
                            ?: arrangement.segments.first()
                        val chordInterval = chordRoots[bar % chordRoots.size]
                        val freq = 440.0 * 2.0.pow((root + chordInterval + leadBeat[b] - 69) / 12.0)
                        val gate = beatSeconds

                        // Lead: fundamental + 2nd/3rd harmonics, per-note ADSR.
                        var lead = 0.0
                        if (!leadRest[b]) {
                            val env = adsr(beatFrac * gate, gate, 0.008, 0.35, 0.45, 0.05)
                            lead = sin(2 * PI * freq * t)
                            lead += 0.35 * v.leadEdge * sin(2 * PI * 2.0 * freq * t)
                            lead += 0.18 * v.leadEdge * sin(2 * PI * 3.0 * freq * t)
                            lead *= env * v.leadGain
                        }

                        // Bass: plucked root one octave down; square when voiced.
                        val bassFreq = 440.0 * 2.0.pow((root - 12 + chordInterval - 69) / 12.0)
                        val bassEnv = exp(-7.0 * beatFrac)
                        val square = 2.0 * (floor(bassFreq * t) + 0.5) - 1.0
                        val bass = (if (v.bassSquare) 0.5 * sin(2 * PI * bassFreq * t) + 0.5 * square
                                    else sin(2 * PI * bassFreq * t)) * bassEnv * v.bassGain * 0.8

                        // Pad: triangle chord (root + major 3rd + 5th), per-bar fade.
                        val barFrac = (beatPos % 4.0) / 4.0
                        val padEnv = min(1.0, barFrac / 0.08) * min(1.0, (1.0 - barFrac) / 0.08)
                        var pad = 0.0
                        for (interval in intArrayOf(0, 4, 7)) {
                            val pf = 440.0 * 2.0.pow((root + chordInterval + interval - 69) / 12.0)
                            val ph = pf * t
                            val tri = 4.0 * abs(ph - 2.0 * floor(0.5 * ph)) - 1.0
                            pad += tri * 0.33
                        }
                        pad *= padEnv * v.padGain * section.energy

                        // Drums.
                        var kick = 0.0; var snare = 0.0; var hat = 0.0
                        if (kickBeat[b] > 0.0) kickPhase = 0.0
                        kickPhase += 2.0 * PI * (45.0 + 110.0 * exp(-40.0 * beatFrac)) / sampleRate
                        kick = sin(kickPhase) * exp(-16.0 * beatFrac) * kickBeat[b]
                        val noise = rng.nextGaussian()
                        snareState = 0.55 * snareState + 0.45 * noise
                        snare = snareState * exp(-22.0 * beatFrac) * snareBeat[b]
                        hatLp = 0.5 * hatLp + 0.5 * noise
                        hat = (noise - hatLp) * exp(-50.0 * beatFrac) * hatBeat[b]

                        // Genre dynamics: EDM builds, drops on alternate 8-bar blocks.
                        val genreGain = when (request.genre) {
                            "EDM" -> if (request.seconds > 16 && (bar / 8) % 2 == 0) 0.62 else 1.0
                            "Ballad" -> 0.70
                            "Acoustic" -> 0.80
                            else -> 0.92
                        }

                        // Reverb: three feedback combs on lead + snare.
                        var reverb = 0.0
                        val reverbIn = lead + snare * 0.5
                        for (c in 0..2) {
                            val buf = combBufs[c]
                            val idx = combIdx[c]
                            val delayed = buf[idx]
                            buf[idx] = delayed * combFeedback[c] + reverbIn * 0.4
                            combIdx[c] = (idx + 1) % buf.size
                            reverb += delayed
                        }

                        val intro = (t / 1.5).coerceIn(0.0, 1.0)
                        val outro = ((request.seconds - t) / 1.0).coerceIn(0.0, 1.0)
                        var dry = (lead + bass + pad +
                            (kick * 0.5 + snare * 0.4 + hat * 0.25) * v.drumGain) *
                            section.energy * genreGain * intro * outro

                        // Tempo-synced feedback delay (8th notes) on the lead.
                        // Read first, then advance — the write targets the NEXT slot
                        // so the output is one slot behind the input.
                        delayIdx = (delayIdx + 1) % delayTaps
                        val delayOut = delayBuf[delayIdx]
                        delayBuf[delayIdx] = lead * v.delayMix * 0.5 + delayOut * 0.35
                        dry += delayOut * 0.4

                        val s = dry + reverb * v.reverbMix
                        lp1 += alpha * (s - lp1)
                        lp2 += alpha * (lp1 - lp2)
                        val sample = (lp2 * 0.42).coerceIn(-0.9, 0.9)
                        val pcm = (sample * 32767).toInt()
                        buffer[j * 2] = (pcm and 255).toByte()
                        buffer[j * 2 + 1] = ((pcm ushr 8) and 255).toByte()
                    }
                    stream.write(buffer, 0, count * 2)
                    pos += count
                }
            }
            if (!temp.renameTo(output)) {
                temp.copyTo(output, overwrite = true)
                temp.delete()
            }
            return output
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun adsr(t: Double, gate: Double, attack: Double, decay: Double, sustain: Double, release: Double): Double {
        return when {
            t < attack -> t / attack
            t < attack + decay -> 1.0 - (1.0 - sustain) * ((t - attack) / decay)
            t < gate - release -> sustain
            else -> sustain * ((gate - t) / release).coerceIn(0.0, 1.0)
        }
    }

    /** Deterministic RNG so renders are reproducible per seed. */
    private class SeededRng(var state: Long) {
        fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }
        fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()
        fun nextGaussian(): Double = (nextDouble() + nextDouble() + nextDouble() - 1.5) / 1.5
    }

    private fun writeHeader(out: FileOutputStream, samples: Int, rate: Int) {
        val bytes = samples * 2
        fun word(value: Int) {
            out.write(value and 255)
            out.write((value ushr 8) and 255)
        }
        fun dword(value: Int) {
            word(value and 65535)
            word((value ushr 16) and 65535)
        }
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        dword(36 + bytes)
        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        dword(16)
        word(1)
        word(1)
        dword(rate)
        dword(rate * 2)
        word(2)
        word(16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        dword(bytes)
    }
}
