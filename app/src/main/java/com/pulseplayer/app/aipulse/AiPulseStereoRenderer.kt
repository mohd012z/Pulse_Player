package com.pulseplayer.app.aipulse

import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Original procedural 44.1 kHz / 16-bit stereo renderer.
 * Separate lead, bass, chord and percussion voices with fixed safe headroom.
 * No third-party samples, proprietary soundfonts or neural weights.
 */
object AiPulseStereoRenderer {
    enum class Stem { MIX, LEAD, BASS, PAD, DRUMS }
    data class Levels(
        val lead: Double = 1.0,
        val bass: Double = 1.0,
        val pad: Double = 1.0,
        val drums: Double = 1.0
    ) {
        fun validate() = require(listOf(lead, bass, pad, drums).all { it.isFinite() && it in 0.0..1.0 }) {
            "Stem levels must be between 0.0 and 1.0"
        }
    }

    fun render(request: AiPulseComposer.Request, output: File,
               stem: Stem = Stem.MIX, levels: Levels = Levels(),
               arrangement: AiPulseBrain.Plan? = null,
               cancelled: () -> Boolean = { false }): File {
        AiPulseComposer.validate(request)
        levels.validate()
        require(output.extension.equals("wav", true))
        val rate = 44100
        val frames = rate * request.seconds
        val secondsPerBeat = 60.0 / request.bpm
        val bars = maxOf(8, (((request.seconds / secondsPerBeat / 4).toInt() + 3) / 4) * 4)
        val plan = if (arrangement == null) {
            AiPulseBrain.plan(AiPulseBrain.Input(request.genre, request.mood, request.bpm, bars, request.seed))
        } else {
            require(arrangement.bpm == request.bpm && arrangement.genre == request.genre) {
                "Arrangement tempo and genre must match the current track"
            }
            AiPulseArrangementEditor.rebuild(arrangement)
        }
        val root = 48 + Math.floorMod(request.seed, 12)
        val temp = File(output.absolutePath + ".part")
        output.parentFile?.mkdirs()
        try {
            FileOutputStream(temp).use { stream ->
                fun word(value: Int) {
                    stream.write(value and 255)
                    stream.write((value ushr 8) and 255)
                }
                fun dword(value: Int) { word(value and 65535); word(value ushr 16) }
                stream.write("RIFF".toByteArray(Charsets.US_ASCII))
                dword(36 + frames * 4)
                stream.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                dword(16); word(1); word(2); dword(rate); dword(rate * 4); word(4); word(16)
                stream.write("data".toByteArray(Charsets.US_ASCII))
                dword(frames * 4)
                val buffer = ByteArray(4096)
                var pos = 0
                while (pos < frames) {
                    if (cancelled()) throw InterruptedException("Audio rendering cancelled")
                    val count = min(buffer.size / 4, frames - pos)
                    for (i in 0 until count) {
                        val t = (pos + i).toDouble() / rate
                        val beat = t / secondsPerBeat
                        val bar = (beat / 4).toInt()
                        val segment = plan.segments.lastOrNull { bar >= it.startBar } ?: plan.segments.first()
                        val chord = segment.chordRoots[bar % segment.chordRoots.size]
                        val hz = 440.0 * Math.pow(2.0, (root + chord - 69) / 12.0)
                        val beatPhase = beat % 1.0
                        val lead = sin(2 * PI * hz * 2 * t) * exp(-2.0 * beatPhase) * 0.17 * levels.lead
                        val bass = sin(2 * PI * hz * 0.5 * t) * 0.17 * levels.bass
                        val pad = (sin(2 * PI * hz * t) + sin(2 * PI * hz * 1.5 * t)) * 0.055 * levels.pad
                        val kick = AiPulseRhythmEngine.sample(request.genre, beat, t, request.seed) * levels.drums
                        val fade = min(1.0, min(t / 0.35, (request.seconds - t) / 0.5)).coerceAtLeast(0.0)
                        val energy = AiPulseSectionEnvelope.energyAt(plan, beat) * fade
                        val leftVoice = when (stem) {
                            Stem.MIX -> lead * 0.90 + bass * 0.72 + pad * 1.05 + kick
                            Stem.LEAD -> lead * 0.90
                            Stem.BASS -> bass * 0.72
                            Stem.PAD -> pad * 1.05
                            Stem.DRUMS -> kick
                        }
                        val rightVoice = when (stem) {
                            Stem.MIX -> lead * 1.05 + bass * 0.72 + pad * 0.90 + kick
                            Stem.LEAD -> lead * 1.05
                            Stem.BASS -> bass * 0.72
                            Stem.PAD -> pad * 0.90
                            Stem.DRUMS -> kick
                        }
                        val left = (leftVoice * energy).coerceIn(-0.92, 0.92)
                        val right = (rightVoice * energy).coerceIn(-0.92, 0.92)
                        val l = (left * 32767).toInt()
                        val r = (right * 32767).toInt()
                        val p = i * 4
                        buffer[p] = l.toByte()
                        buffer[p + 1] = (l ushr 8).toByte()
                        buffer[p + 2] = r.toByte()
                        buffer[p + 3] = (r ushr 8).toByte()
                    }
                    stream.write(buffer, 0, count * 4)
                    pos += count
                }
            }
            if (!temp.renameTo(output)) { temp.copyTo(output, overwrite = true); temp.delete() }
            return output
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }
}
