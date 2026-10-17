package com.pulseplayer.app.aipulse

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline two-track PCM16 mixer with adjustable vocal gain and delay.
 * Requires matching sample rates and channel counts; caller must resample first.
 */
object AiPulseVoiceMixer {
    data class Settings(val vocalGain: Double = 1.0, val musicGain: Double = 0.65, val delayMs: Int = 0) {
        fun validate() {
            require(vocalGain in 0.0..2.0 && musicGain in 0.0..2.0)
            require(delayMs in 0..30000)
        }
    }
    fun mix(music: File, voice: File, output: File, settings: Settings,
            cancelled: () -> Boolean = { false }): File {
        settings.validate()
        require(output.canonicalPath != music.canonicalPath && output.canonicalPath != voice.canonicalPath)
        val m = AiPulseWaveInspector.inspect(music)
        val v = AiPulseWaveInspector.inspect(voice)
        require(m.sampleRate == v.sampleRate && m.channels == v.channels) {
            "Voice and music formats differ: resampling/channel conversion required"
        }
        val channels = m.channels
        val bytesPerFrame = channels * 2
        val delayFrames = settings.delayMs.toLong() * m.sampleRate / 1000
        val musicFrames = (music.length() - 44) / bytesPerFrame
        val voiceFrames = (voice.length() - 44) / bytesPerFrame
        val totalFrames = max(musicFrames, voiceFrames + delayFrames)
        require(totalFrames * bytesPerFrame <= 100_000_000L) { "Mix exceeds WAV limit" }
        val temp = File(output.absolutePath + ".part")
        try {
            FileInputStream(music).buffered().use { mi ->
                FileInputStream(voice).buffered().use { vi ->
                    FileOutputStream(temp).buffered().use { out ->
                        mi.skip(44); vi.skip(44)
                        writeHeader(out, totalFrames * bytesPerFrame, m.sampleRate, channels)
                        var frame = 0L
                        val musicBytes = ByteArray(bytesPerFrame)
                        val voiceBytes = ByteArray(bytesPerFrame)
                        val result = ByteArray(bytesPerFrame)
                        while (frame < totalFrames) {
                            if (cancelled()) throw InterruptedException("Mix cancelled")
                            val hasMusic = frame < musicFrames
                            val hasVoice = frame >= delayFrames && frame - delayFrames < voiceFrames
                            if (hasMusic) readFrame(mi, musicBytes)
                            if (hasVoice) readFrame(vi, voiceBytes)
                            for (ch in 0 until channels) {
                                val idx = ch * 2
                                fun sample(bytes: ByteArray): Int =
                                    ((bytes[idx].toInt() and 255) or (bytes[idx + 1].toInt() shl 8)).toShort().toInt()
                                val musicSample = if (hasMusic) sample(musicBytes) else 0
                                val voiceSample = if (hasVoice) sample(voiceBytes) else 0
                                val combined = musicSample * settings.musicGain + voiceSample * settings.vocalGain
                                val pcm = combined.toInt().coerceIn(-32768, 32767)
                                result[idx] = pcm.toByte()
                                result[idx + 1] = (pcm ushr 8).toByte()
                            }
                            out.write(result)
                            frame++
                        }
                    }
                }
            }
            if (!temp.renameTo(output)) { temp.copyTo(output, overwrite = true); temp.delete() }
            return output
        } catch (error: Exception) { temp.delete(); throw error }
    }
    private fun readFrame(input: java.io.InputStream, bytes: ByteArray) {
        var pos = 0
        while (pos < bytes.size) {
            val n = input.read(bytes, pos, bytes.size - pos)
            require(n > 0) { "Truncated audio frame" }
            pos += n
        }
    }
    private fun writeHeader(out: java.io.OutputStream, bytes: Long, rate: Int, channels: Int) {
        fun word(v: Int) { out.write(v and 255); out.write(v ushr 8 and 255) }
        fun dword(v: Long) { word((v and 65535).toInt()); word((v ushr 16 and 65535).toInt()) }
        out.write("RIFF".toByteArray()); dword(bytes + 36)
        out.write("WAVEfmt ".toByteArray()); dword(16)
        word(1); word(channels); dword(rate.toLong()); dword(rate.toLong() * channels * 2)
        word(channels * 2); word(16)
        out.write("data".toByteArray()); dword(bytes)
    }
}
