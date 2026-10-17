package com.pulseplayer.app.aipulse

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Offline linear PCM16 resampler for locally owned vocal tracks.
 * Accepts canonical 44-byte PCM WAV, mono/stereo; retains channel count.
 * Linear interpolation is a compatibility fallback, not studio-quality
 * band-limited conversion (high-frequency content may alias).
 */
object AiPulseWavResampler {
    enum class Quality { LINEAR, WINDOWED_SINC }
    private data class Source(val rate: Int, val channels: Int, val frames: Long)

    private fun header(input: RandomAccessFile): Source {
        require(input.length() >= 44) { "Short WAV file" }
        fun four() = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
        fun u16(): Int = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
        fun u32(): Long = u16().toLong() or (u16().toLong() shl 16)
        require(four() == "RIFF")
        u32()
        require(four() == "WAVE" && four() == "fmt " && u32() == 16L)
        require(u16() == 1) { "Only PCM WAV supported" }
        val channels = u16()
        val rate = u32().toInt()
        u32()
        val alignment = u16()
        require(u16() == 16 && channels in 1..2 && rate in 8000..192000)
        require(alignment == channels * 2)
        require(four() == "data")
        val bytes = u32()
        require(bytes % alignment == 0L && bytes + 44 <= input.length()) { "Invalid WAV size" }
        return Source(rate, channels, bytes / alignment)
    }

    fun sampleRate(input: File): Int =
        RandomAccessFile(input, "r").use { header(it).rate }

    fun convert(input: File, output: File, targetRate: Int = 44100,
                quality: Quality = Quality.WINDOWED_SINC,
                cancelled: () -> Boolean = { false }): File {

        require(targetRate in 8000..192000)
        require(input.canonicalPath != output.canonicalPath)
        require(output.extension.equals("wav", true))
        val part = File(output.absolutePath + ".part")
        try {
            RandomAccessFile(input, "r").use { source ->
                val format = header(source)
                val frames = (format.frames.toDouble() * targetRate / format.rate).roundToLong()
                val payload = frames * format.channels * 2L
                require(frames > 0 && payload <= Int.MAX_VALUE - 44L) { "Converted WAV too large" }
                output.parentFile?.mkdirs()
                RandomAccessFile(part, "rw").use { result ->
                    result.setLength(0)
                    fun u16(n: Int) { result.write(n and 255); result.write((n ushr 8) and 255) }
                    fun u32(n: Long) { u16((n and 65535).toInt()); u16(((n ushr 16) and 65535).toInt()) }
                    result.write("RIFF".toByteArray(Charsets.US_ASCII))
                    u32(36 + payload)
                    result.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                    u32(16); u16(1); u16(format.channels); u32(targetRate.toLong())
                    u32(targetRate.toLong() * format.channels * 2)
                    u16(format.channels * 2); u16(16)
                    result.write("data".toByteArray(Charsets.US_ASCII)); u32(payload)

                    // Cache contiguous input frames to avoid a seek for every sinc tap.
                    val blockFrames = 4096
                    var cachedStart = -1L
                    var cachedCount = 0
                    val cache = ByteArray(blockFrames * format.channels * 2)
                    fun frame(index: Long): IntArray {
                        val pos = index.coerceIn(0, format.frames - 1)
                        if (cachedStart < 0 || pos < cachedStart || pos >= cachedStart + cachedCount) {
                            cachedStart = (pos / blockFrames) * blockFrames
                            cachedCount = min(blockFrames.toLong(), format.frames - cachedStart).toInt()
                            source.seek(44 + cachedStart * format.channels * 2)
                            source.readFully(cache, 0, cachedCount * format.channels * 2)
                        }
                        val offset = ((pos - cachedStart) * format.channels * 2).toInt()
                        return IntArray(format.channels) { channel ->
                            val j = offset + channel * 2
                            ((cache[j].toInt() and 255) or
                                ((cache[j + 1].toInt() and 255) shl 8)).toShort().toInt()
                        }
                    }
                    var lastIndex = -1L
                    var a = IntArray(format.channels)
                    var b = IntArray(format.channels)
                    for (i in 0 until frames) {
                        if (i % 4096L == 0L && cancelled()) throw InterruptedException("Resampling cancelled")
                        val original = i.toDouble() * format.rate / targetRate
                        val index = min(format.frames - 1, floor(original).toLong())
                        if (index != lastIndex) {
                            a = frame(index)
                            b = frame(min(index + 1, format.frames - 1))
                            lastIndex = index
                        }
                        val fraction = original - index
                        for (c in 0 until format.channels) {
                            val channel = c
                            val value = if (quality == Quality.WINDOWED_SINC) {
                                AiPulseResamplingKernel.interpolate(original, format.rate, targetRate) { position ->
                                    frame(position)[channel].toDouble()
                                }
                            } else (a[c] + (b[c] - a[c]) * fraction)
                            u16(value.toInt().coerceIn(-32768, 32767) and 65535)
                        }
                    }
                }
            }
            if (!part.renameTo(output)) { part.copyTo(output, overwrite = true); part.delete() }
            return output
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }
}
