package com.pulseplayer.app.aipulse

import java.io.File
import java.io.RandomAccessFile

/** Strict, bounded validator for PULSE-NOVA mono PCM16/22050Hz WAV exports. */
object PulseWavValidator {
    const val MAX_BYTES = 15 * 1024 * 1024
    data class Result(val valid: Boolean, val reason: String = "")
    private fun bad(reason: String) = Result(false, reason)
    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, i: Int): Long =
        (u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16))
    private fun tag(b: ByteArray, i: Int) = String(b, i, 4, Charsets.US_ASCII)

    fun validate(file: File, expected: Int): Result {
        if (expected < 46 || expected > MAX_BYTES || !file.isFile || file.length() != expected.toLong())
            return bad("Invalid file length")
        return try {
            RandomAccessFile(file, "r").use { f ->
                val header = ByteArray(12)
                f.readFully(header)
                if (tag(header, 0) != "RIFF" || tag(header, 8) != "WAVE") return bad("Invalid RIFF/WAVE")
                val end = u32(header, 4) + 8L
                if (end != expected.toLong()) return bad("RIFF size mismatch")
                var pos = 12L
                var fmt = false
                var data = false
                while (pos < end) {
                    if (end - pos < 8) return bad("Truncated chunk header")
                    f.seek(pos)
                    val ch = ByteArray(8)
                    f.readFully(ch)
                    val size = u32(ch, 4)
                    val payload = pos + 8
                    val padded = size + (size and 1L)
                    if (padded > end - payload) return bad("Chunk exceeds RIFF boundary")
                    when (tag(ch, 0)) {
                        "fmt " -> {
                            if (fmt || size != 16L) return bad("Duplicate or unsupported fmt")
                            val v = ByteArray(16)
                            f.readFully(v)
                            if (u16(v, 0) != 1 || u16(v, 2) != 1 ||
                                u32(v, 4) != 22050L || u32(v, 8) != 44100L ||
                                u16(v, 12) != 2 || u16(v, 14) != 16) return bad("Unsupported PCM format")
                            fmt = true
                        }
                        "data" -> {
                            if (data || size == 0L || size % 2L != 0L) return bad("Invalid data chunk")
                            data = true
                        }
                    }
                    pos = payload + padded
                }
                if (pos != end || !fmt || !data) bad("Missing chunks or trailing bytes") else Result(true)
            }
        } catch (e: Exception) { bad("Read failure: ${e.javaClass.simpleName}") }
    }
}
