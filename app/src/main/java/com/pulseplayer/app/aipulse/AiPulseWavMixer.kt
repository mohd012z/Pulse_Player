package com.pulseplayer.app.aipulse

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

/**
 * Mixes two PCM WAV files without decoding compressed recordings.
 * Input format: RIFF/WAVE, 16-bit little-endian PCM, mono or stereo,
 * same sample rate. Output is stereo 16-bit PCM with conservative headroom.
 * Never modifies source files.
 */
object AiPulseWavMixer {
    data class Settings(
        val musicGain: Double = 0.65,
        val voiceGain: Double = 0.75,
        val voiceDelayMs: Int = 0
    )
    private data class Format(val rate: Int, val channels: Int, val frames: Long, val dataOffset: Long)
    private fun readFormat(file: RandomAccessFile): Format {
        require(file.length() >= 44) { "WAV header is incomplete" }
        fun bytes(n: Int) = ByteArray(n).also { file.readFully(it) }
        fun word(): Int { val a = file.readUnsignedByte(); return a or (file.readUnsignedByte() shl 8) }
        fun dword(): Long = word().toLong() or (word().toLong() shl 16)
        require(String(bytes(4), Charsets.US_ASCII) == "RIFF")
        dword()
        require(String(bytes(4), Charsets.US_ASCII) == "WAVE")
        var rate = 0
        var channels = 0
        var valid = false
        var offset = -1L
        var length = 0L
        while (file.filePointer + 8 <= file.length()) {
            val id = String(bytes(4), Charsets.US_ASCII)
            val size = dword()
            val start = file.filePointer
            require(size <= file.length() - start) { "Invalid WAV chunk length" }
            if (id == "fmt " && size >= 16) {
                val encoding = word()
                channels = word()
                rate = dword().toInt()
                dword()
                word()
                val bits = word()
                valid = encoding == 1 && bits == 16 && channels in 1..2 && rate in 8000..192000
            }
            if (id == "data") { offset = start; length = size; break }
            file.seek(start + size + (size and 1))
        }
        require(valid && offset >= 0 && length % (channels * 2) == 0L) { "Unsupported PCM WAV format" }
        return Format(rate, channels, length / (channels * 2), offset)
    }
    fun mix(music: File, voice: File, output: File, settings: Settings = Settings(),
            cancelled: () -> Boolean = { false }): File {
        require(settings.musicGain in 0.0..1.0 && settings.voiceGain in 0.0..1.0)
        require(settings.voiceDelayMs in 0..30000)
        require(output.extension.equals("wav", true))
        require(output.canonicalPath != music.canonicalPath && output.canonicalPath != voice.canonicalPath)
        val temp = File(output.absolutePath + ".part")
        try {
            RandomAccessFile(music, "r").use { a ->
                RandomAccessFile(voice, "r").use { b ->
                    val af = readFormat(a)
                    val bf = readFormat(b)
                    require(af.rate == bf.rate) { "Sample rates must match; resample before mixing" }
                    val delayFrames = af.rate.toLong() * settings.voiceDelayMs / 1000
                    val frames = max(af.frames, bf.frames + delayFrames)
                    require(frames <= (Int.MAX_VALUE - 44L) / 4) { "Output exceeds WAV size limit" }
                    output.parentFile?.mkdirs()
                    RandomAccessFile(temp, "rw").use { out ->
                        out.setLength(0)
                        fun word(n: Int) { out.write(n and 255); out.write((n ushr 8) and 255) }
                        fun dword(n: Long) { word((n and 65535).toInt()); word(((n ushr 16) and 65535).toInt()) }
                        out.write("RIFF".toByteArray(Charsets.US_ASCII))
                        dword(36 + frames * 4)
                        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                        dword(16); word(1); word(2); dword(af.rate.toLong()); dword(af.rate.toLong() * 4)
                        word(4); word(16)
                        out.write("data".toByteArray(Charsets.US_ASCII))
                        dword(frames * 4)
                        fun sample(file: RandomAccessFile): Int {
                            val low = file.readUnsignedByte()
                            return (low or (file.readUnsignedByte() shl 8)).toShort().toInt()
                        }
                        for (frame in 0 until frames) {
                            if (frame % 4096L == 0L && cancelled()) throw InterruptedException("Mix cancelled")
                            val leftA: Int
                            val rightA: Int
                            if (frame < af.frames) {
                                leftA = sample(a)
                                rightA = if (af.channels == 2) sample(a) else leftA
                            } else { leftA = 0; rightA = 0 }
                            val voiceFrame = frame - delayFrames
                            val leftB: Int
                            val rightB: Int
                            if (voiceFrame >= 0 && voiceFrame < bf.frames) {
                                leftB = sample(b)
                                rightB = if (bf.channels == 2) sample(b) else leftB
                            } else { leftB = 0; rightB = 0 }
                            fun mixed(x: Int, y: Int): Int = (x * settings.musicGain + y * settings.voiceGain)
                                .toInt().coerceIn(-32768, 32767)
                            word(mixed(leftA, leftB) and 65535)
                            word(mixed(rightA, rightB) and 65535)
                        }
                    }
                }
            }
            if (!temp.renameTo(output)) { temp.copyTo(output, overwrite = true); temp.delete() }
            return output
        } catch (error: Exception) { temp.delete(); throw error }
    }
}
