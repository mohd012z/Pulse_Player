package com.pulseplayer.app.aipulse

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.content.Context
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Explicit capabilities prevent the UI from mislabeling procedural output as neural songs. */
enum class AiPulseCapability { INSTRUMENTAL, LYRICS_TO_SONG, REFERENCE_AUDIO, VOCALS, EXTEND, STEMS }

data class AiPulseGenerationSpec(
    val title: String,
    val description: String,
    val genre: String,
    val lyrics: String,
    val instruments: List<String>,
    val bpm: Int,
    val seconds: Int,
    val referenceAudio: Uri? = null
) {
    fun validate() {
        require(title.isNotBlank() && title.length <= 100) { "Invalid title" }
        require(description.length <= 2000 && lyrics.length <= 12000) { "Prompt or lyrics too long" }
        require(bpm in 60..180 && seconds in 2..600) { "Invalid tempo or duration" }
        require(instruments.size <= 32) { "Too many instruments" }
    }
}

interface AiPulseGenerationBackend {
    val name: String
    val capabilities: Set<AiPulseCapability>
    fun generate(spec: AiPulseGenerationSpec, destination: File, cancelled: () -> Boolean = { false }): File
}

/** Existing lightweight offline synthesizer; deliberately rejects unsupported AI requests. */
class AiPulseProceduralBackend : AiPulseGenerationBackend {
    override val name = "Offline instrumental demo"
    override val capabilities = setOf(AiPulseCapability.INSTRUMENTAL)
    override fun generate(spec: AiPulseGenerationSpec, destination: File, cancelled: () -> Boolean): File {
        spec.validate()
        require(spec.seconds <= 30) { "Offline demo supports up to 30 seconds" }
        require(spec.referenceAudio == null) { "Reference-audio conditioning requires a neural backend" }
        require(spec.lyrics.isBlank()) { "This engine cannot sing lyrics; choose a vocal-capable backend" }
        return AiPulseComposer.render(
            AiPulseComposer.Request(spec.title, spec.genre, spec.bpm, spec.seconds),
            destination, cancelled
        )
    }
}

/** Audits PCM WAV output before user export; does not pretend to measure perceptual quality. */
object AiPulseWaveInspector {
    data class Report(val sampleRate: Int, val channels: Int, val durationSeconds: Double,
                      val peak: Double, val rms: Double, val clippedSamples: Long)
    fun inspect(file: File): Report {
        require(file.length() in 45..100_000_000) { "WAV size out of bounds" }
        file.inputStream().buffered().use { input ->
            val header = ByteArray(44)
            require(input.read(header) == 44) { "Truncated WAV" }
            fun u16(i: Int) = (header[i].toInt() and 255) or ((header[i+1].toInt() and 255) shl 8)
            fun u32(i: Int) = (u16(i).toLong() or (u16(i+2).toLong() shl 16))
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF")
            require(String(header, 8, 4, Charsets.US_ASCII) == "WAVE")
            require(String(header, 12, 4, Charsets.US_ASCII) == "fmt ")
            require(u16(20) == 1 && u16(34) == 16) { "Only 16-bit PCM WAV supported" }
            val channels = u16(22)
            val rate = u32(24).toInt()
            require(channels in 1..2 && rate in 8000..192000)
            require(String(header, 36, 4, Charsets.US_ASCII) == "data")
            val dataSize = u32(40)
            require(dataSize > 0 && dataSize % 2L == 0L && dataSize <= file.length() - 44)
            val bytes = ByteArray(8192)
            var remaining = dataSize
            var peak = 0
            var sumSquares = 0.0
            var count = 0L
            var clipped = 0L
            while (remaining > 0) {
                val size = input.read(bytes, 0, minOf(bytes.size.toLong(), remaining).toInt())
                require(size > 0 && size % 2 == 0) { "Truncated samples" }
                for (i in 0 until size step 2) {
                    val sample = ((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toInt()
                    val magnitude = abs(sample)
                    peak = max(peak, magnitude)
                    if (magnitude >= 32767) clipped++
                    sumSquares += sample.toDouble() * sample
                    count++
                }
                remaining -= size
            }
            return Report(rate, channels, count.toDouble() / channels / rate,
                peak / 32768.0, sqrt(sumSquares / count) / 32768.0, clipped)
        }
    }
}
