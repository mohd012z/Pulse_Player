package com.pulseplayer.app.aipulse

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline, non-destructive mastering for canonical PCM16 RIFF WAV (44-byte header).
 * Peak-safe gain, gentle saturation and channel-preserving stereo width.
 * Not Dolby Atmos, not true-peak or LUFS certified.
 */
object AiPulseMastering {
    enum class Preset(val targetDbfs: Double, val drive: Double, val width: Double) {
        CLEAN(-1.5, 0.0, 1.0),
        WARM(-1.5, 0.14, 1.0),
        PUNCH(-1.2, 0.25, 1.0),
        WIDE(-1.5, 0.10, 1.15)
    }
    data class Report(val inputPeak: Double, val outputPeak: Double, val outputRms: Double, val frames: Long)
    fun process(input: File, output: File, preset: Preset, cancelled: () -> Boolean = { false }): Report {
        require(input.canonicalPath != output.canonicalPath) { "Output must differ from input" }
        val original = AiPulseWaveInspector.inspect(input)
        require(original.channels in 1..2 && input.length() <= 100_000_000L)
        val target = 10.0.pow(preset.targetDbfs / 20.0)
        val gain = if (original.peak > 0.000001) min(4.0, target / original.peak) else 1.0
        val temp = File(output.absolutePath + ".part")
        var peak = 0.0
        var squares = 0.0
        var samples = 0L
        try {
            RandomAccessFile(input, "r").use { source ->
                RandomAccessFile(temp, "rw").use { dest ->
                    val header = ByteArray(44)
                    source.readFully(header)
                    dest.write(header)
                    val frameBytes = original.channels * 2
                    val buffer = ByteArray(frameBytes * 2048)
                    var remaining = input.length() - 44
                    while (remaining > 0) {
                        if (cancelled()) throw InterruptedException("Mastering cancelled")
                        val n = min(buffer.size.toLong(), remaining).toInt()
                        source.readFully(buffer, 0, n)
                        require(n % frameBytes == 0) { "Incomplete PCM frame" }
                        for (i in 0 until n step frameBytes) {
                            fun sample(offset: Int): Double {
                                val raw = ((buffer[offset].toInt() and 255) or
                                    (buffer[offset + 1].toInt() shl 8)).toShort().toInt()
                                return raw / 32768.0
                            }
                            val left = sample(i)
                            val right = if (original.channels == 2) sample(i + 2) else left
                            val mid = (left + right) * 0.5
                            val side = (left - right) * 0.5 * preset.width
                            val values = if (original.channels == 2) doubleArrayOf(mid + side, mid - side)
                                else doubleArrayOf(left)
                            for (channel in values.indices) {
                                val x = values[channel] * gain
                                // Bounded polynomial saturation with predictable low drive.
                                val y = (x / (1.0 + preset.drive * abs(x))).coerceIn(-target, target)
                                peak = max(peak, abs(y))
                                squares += y * y
                                samples++
                                val pcm = (y * 32767.0).toInt().coerceIn(-32768, 32767)
                                val index = i + channel * 2
                                buffer[index] = pcm.toByte()
                                buffer[index + 1] = (pcm ushr 8).toByte()
                            }
                        }
                        dest.write(buffer, 0, n)
                        remaining -= n
                    }
                }
            }
            if (!temp.renameTo(output)) { temp.copyTo(output, overwrite = true); temp.delete() }
            return Report(original.peak, peak, sqrt(squares / max(1L, samples)), samples / original.channels)
        } catch (error: Exception) { temp.delete(); throw error }
    }
}
