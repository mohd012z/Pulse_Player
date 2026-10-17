package com.pulseplayer.app.aipulse

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Decode an app-owned voice recording to mono PCM WAV using Android's platform decoder.
 * Intended for offline audition/mixing; does not upload voice data.
 */
object AiPulseVoiceDecoder {
    fun decode(context: Context, source: Uri, output: File, cancelled: () -> Boolean = { false }): File {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val part = File(output.absolutePath + ".part")
        try {
            extractor.setDataSource(context, source, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i; format = candidate; break
                }
            }
            require(track >= 0) { "No audio track found" }
            extractor.selectTrack(track)
            val mime = requireNotNull(format).getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(channels in 1..2)
            var outputFormatKnown = false
            var doneInput = false
            var doneOutput = false
            val info = MediaCodec.BufferInfo()
            FileOutputStream(part).use { stream ->
                stream.write(ByteArray(44))
                var written = 0L
                while (!doneOutput) {
                    if (cancelled()) throw InterruptedException("Voice decode cancelled")
                    if (!doneInput) {
                        val index = decoder.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val buffer = decoder.getInputBuffer(index)!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                doneInput = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val index = decoder.dequeueOutputBuffer(info, 10000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = decoder.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                            require(encoding == 2) { "Only PCM16 decoder output supported" }
                            require(channels in 1..2)
                            outputFormatKnown = true
                        }
                        else -> if (index >= 0) {
                            require(outputFormatKnown) { "Decoder output format not known" }
                            val buffer = decoder.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buffer.get(bytes)
                            stream.write(bytes)
                            written += bytes.size
                            require(written <= 100_000_000L) { "Recording too large" }
                            doneOutput = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
                require(written > 0 && written % (channels * 2) == 0L)
            }
            java.io.RandomAccessFile(part, "rw").use { wav ->
                val length = wav.length() - 44
                wav.seek(0)
                fun put16(v: Int) { wav.write(v and 255); wav.write(v ushr 8 and 255) }
                fun put32(v: Long) { put16((v and 65535).toInt()); put16((v ushr 16 and 65535).toInt()) }
                wav.writeBytes("RIFF"); put32(length + 36); wav.writeBytes("WAVEfmt ")
                put32(16); put16(1); put16(channels); put32(rate.toLong())
                put32(rate.toLong() * channels * 2); put16(channels * 2); put16(16)
                wav.writeBytes("data"); put32(length)
            }
            if (!part.renameTo(output)) { part.copyTo(output, overwrite = true); part.delete() }
            return output
        } catch (error: Exception) { part.delete(); throw error }
        finally { runCatching { codec?.stop() }; codec?.release(); extractor.release() }
    }
}
