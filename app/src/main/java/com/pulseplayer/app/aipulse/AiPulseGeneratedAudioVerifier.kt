package com.pulseplayer.app.aipulse

import java.io.File
import java.io.FileInputStream

/**
 * Validate actual generated PCM WAV output before reporting a generation as successful.
 * This is intentionally strict about the RIFF/WAVE container; it is not a perceptual quality score.
 */
object AiPulseGeneratedAudioVerifier {
    data class Report(
        val valid: Boolean,
        val reason: String,
        val sampleRateHz: Long = 0,
        val channels: Int = 0,
        val durationMs: Long = 0,
        val pcmBytes: Long = 0
    )

    fun inspect(file: File): Report {
        if (!file.isFile || file.length() < 44L) return Report(false, "Missing or truncated WAV")
        return try {
            FileInputStream(file).use { stream ->
                val header = ByteArray(12)
                if (!readFully(stream, header)) return Report(false, "Truncated RIFF header")
                if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                    String(header, 8, 4, Charsets.US_ASCII) != "WAVE")
                    return Report(false, "Not a RIFF/WAVE file")
                var format = -1
                var channels = 0
                var sampleRate = 0L
                var byteRate = 0L
                var pcmBytes = 0L
                var bytesRemaining = file.length() - 12L
                while (bytesRemaining >= 8L) {
                    val chunk = ByteArray(8)
                    if (!readFully(stream, chunk)) return Report(false, "Truncated chunk header")
                    bytesRemaining -= 8
                    val chunkSize = u32(chunk, 4)
                    val padded = chunkSize + (chunkSize and 1L)
                    if (padded > bytesRemaining) return Report(false, "Truncated WAV chunk")
                    when (String(chunk, 0, 4, Charsets.US_ASCII)) {
                        "fmt " -> {
                            if (chunkSize < 16) return Report(false, "Invalid format chunk")
                            val fmt = ByteArray(16)
                            if (!readFully(stream, fmt)) return Report(false, "Truncated format chunk")
                            format = u16(fmt, 0)
                            channels = u16(fmt, 2)
                            sampleRate = u32(fmt, 4)
                            byteRate = u32(fmt, 8)
                            if (!skipFully(stream, padded - 16)) return Report(false, "Truncated format extension")
                        }
                        "data" -> {
                            pcmBytes += chunkSize
                            if (!skipFully(stream, padded)) return Report(false, "Truncated PCM data")
                        }
                        else -> if (!skipFully(stream, padded)) return Report(false, "Truncated WAV metadata")
                    }
                    bytesRemaining -= padded
                }
                if (format != 1 && format != 3) return Report(false, "Unsupported WAV encoding")
                if (channels !in 1..8 || sampleRate !in 8000L..384000L || byteRate <= 0L)
                    return Report(false, "Invalid audio parameters")
                if (pcmBytes <= 0L) return Report(false, "Empty WAV audio")
                val duration = pcmBytes * 1000L / byteRate
                if (duration <= 0L) return Report(false, "Audio too short")
                Report(true, "Valid WAV container", sampleRate, channels, duration, pcmBytes)
            }
        } catch (e: Exception) {
            Report(false, "WAV inspection failed: ${e.javaClass.simpleName}")
        }
    }

    private fun u16(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 255) or ((b[offset + 1].toInt() and 255) shl 8)

    private fun u32(b: ByteArray, offset: Int): Long =
        (u16(b, offset).toLong() or (u16(b, offset + 2).toLong() shl 16)) and 0xffffffffL

    private fun readFully(stream: FileInputStream, b: ByteArray): Boolean {
        var offset = 0
        while (offset < b.size) {
            val count = stream.read(b, offset, b.size - offset)
            if (count <= 0) return false
            offset += count
        }
        return true
    }

    private fun skipFully(stream: FileInputStream, count: Long): Boolean {
        if (count < 0) return false
        var remaining = count
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) remaining -= skipped
            else if (stream.read() == -1) return false
            else remaining--
        }
        return true
    }
}
