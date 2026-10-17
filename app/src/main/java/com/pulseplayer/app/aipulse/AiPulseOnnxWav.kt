package com.pulseplayer.app.aipulse

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Writes validated mono float PCM to a standard 16-bit RIFF/WAVE file. */
internal object AiPulseOnnxWav {
    fun write(output: File, samples: FloatArray, sampleRate: Int): File {
        require(sampleRate in 8000..192000) { "Unsupported sample rate" }
        require(samples.isNotEmpty() && samples.size <= 20_000_000) { "Invalid audio length" }
        require(samples.all { it.isFinite() }) { "Non-finite PCM sample" }
        output.parentFile?.mkdirs()
        val temp = File(output.absolutePath + ".partial")
        try {
            RandomAccessFile(temp, "rw").use { file ->
                file.setLength(0)
                fun ascii(s: String) = file.write(s.toByteArray(Charsets.US_ASCII))
                fun le16(v: Int) { file.writeByte(v and 255); file.writeByte((v ushr 8) and 255) }
                fun le32(v: Int) { le16(v and 65535); le16(v ushr 16) }
                val bytes = samples.size * 2
                ascii("RIFF"); le32(36 + bytes); ascii("WAVE")
                ascii("fmt "); le32(16); le16(1); le16(1)
                le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
                ascii("data"); le32(bytes)
                for (sample in samples) {
                    val pcm = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt()
                    le16(pcm)
                }
            }
            require(temp.renameTo(output)) { "Unable to finalize WAV" }
            return output
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}
