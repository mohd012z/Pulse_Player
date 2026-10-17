package com.pulseplayer.app.aipulse

import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Deterministic, offline, original instrumental demo. Not a neural music model. */
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

    fun render(request: Request, output: File, cancelled: () -> Boolean = { false }): File {
        validate(request)
        require(output.extension.lowercase() == "wav") { "Output must be WAV" }
        val sampleRate = 44100
        val total = sampleRate * request.seconds
        val beatSeconds = 60.0 / request.bpm
        val cheerful = request.mood.lowercase() !in setOf("sad", "dark", "minor", "melancholy")
        val notes = if (cheerful) intArrayOf(0, 4, 7, 12, 7, 4, 2, 7)
                    else intArrayOf(0, 3, 7, 10, 7, 3, 5, 7)
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
                        val beat = t / beatSeconds
                        val noteIndex = (beat.toInt() / 2) % notes.size
                        val bar = (beat / 4.0).toInt()
                        val section = arrangement.segments.lastOrNull { bar >= it.startBar }
                            ?: arrangement.segments.first()
                        val chord = chordRoots[bar % chordRoots.size]
                        val freq = 440.0 * Math.pow(2.0, (root + chord + notes[noteIndex] - 69) / 12.0)
                        val phase = beat % 2.0
                        val envelope = exp(-1.2 * phase)
                        val lead = sin(2 * PI * freq * t) * envelope * 0.28
                        val harmony = sin(2 * PI * freq * 0.5 * t) * 0.13
                        val kickPhase = beat % 1.0
                        val kick = sin(2 * PI * (55.0 + 75.0 * exp(-25.0 * kickPhase)) * t) *
                            exp(-18.0 * kickPhase) * 0.23
                        val intro = (t / 1.5).coerceIn(0.0, 1.0)
                        val outro = ((request.seconds - t) / 1.0).coerceIn(0.0, 1.0)
                        val genreGain = when (request.genre) {
                            "Ballad" -> 0.60
                            "Acoustic" -> 0.72
                            "R&B" -> 0.82
                            "EDM" -> if ((beat / 4.0).toInt() % 8 < 2) 0.55 else 1.0
                            else -> 0.90
                        }
                        val syncopation = if (request.genre == "R&B")
                            sin(2 * PI * 2.0 * beat) * 0.025 else 0.0
                        val shimmer = if (request.genre == "EDM")
                            sin(2 * PI * freq * 2.0 * t) * 0.055 * envelope else 0.0
                        val sample = ((lead + harmony + kick + syncopation + shimmer) *
                            genreGain * section.energy * intro * outro).coerceIn(-0.9, 0.9)
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
