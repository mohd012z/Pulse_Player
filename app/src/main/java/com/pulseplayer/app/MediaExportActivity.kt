package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Local, authorized MP4-to-M4A audio export.
 * Copies the original AAC audio samples without re-encoding or DRM circumvention.
 */
class MediaExportActivity : Activity() {
    private var input: Uri? = null
    private var pendingName = "PulseAudio.m4a"
    private lateinit var status: TextView
    private val pickInput = 701
    private val createOutput = 702

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
        }
        root.addView(TextView(this).apply {
            text = "Download & Convert · Local media"
            textSize = 21f
        })
        root.addView(TextView(this).apply {
            text = "Select a video you own or are permitted to convert. AAC audio from MP4 is saved as M4A at original quality. MP3, bitrate changes and streaming-service extraction are not supported."
            textSize = 14f
            setPadding(0, 18, 0, 18)
        })
        status = TextView(this).apply { text = "No source selected"; textSize = 14f }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Choose local MP4 video"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "video/mp4"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, pickInput)
            }
        })
        root.addView(Button(this).apply {
            text = "Format & quality: M4A · Original AAC"
            setOnClickListener {
                AlertDialog.Builder(this@MediaExportActivity)
                    .setTitle("Export format and quality")
                    .setMessage("M4A (AAC) · Original source quality. Audio is copied without recompression. MP3 / 128–320 kbps requires a separate encoder and is not offered by this build.")
                    .setPositiveButton("OK", null).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Export audio to M4A"
            setOnClickListener {
                if (input == null) {
                    status.text = "Choose an MP4 video first"
                } else {
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "audio/mp4"
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, pendingName)
                    }, createOutput)
                }
            }
        })
        setContentView(root)
    }

    @Deprecated("Legacy Activity picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == pickInput) {
            input = uri
            val name = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: "PulseAudio.mp4"
            pendingName = name.substringBeforeLast(".").take(90) + ".m4a"
            status.text = "Selected: $name"
        } else if (requestCode == createOutput) {
            val source = input ?: return
            status.text = "Exporting audio…"
            thread(name = "PulseLocalAudioExport") {
                val result = runCatching { exportAac(source, uri) }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    status.text = result.fold(
                        { "Saved original AAC audio as M4A" },
                        { "Export failed: ${it.message ?: "Unsupported media"}" }
                    )
                }
            }
        }
    }

    private fun exportAac(source: Uri, target: Uri) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            contentResolver.openFileDescriptor(source, "r")!!.use { sourceFd ->
                extractor.setDataSource(sourceFd.fileDescriptor)
                val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
                } ?: error("No AAC audio track found; only AAC-in-MP4 is supported")
                val format = extractor.getTrackFormat(audioTrack)
                extractor.selectTrack(audioTrack)
                contentResolver.openFileDescriptor(target, "rw")!!.use { outputFd ->
                    muxer = MediaMuxer(outputFd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    val writer = muxer!!
                    val destinationTrack = writer.addTrack(format)
                    writer.start()
                    val buffer = ByteBuffer.allocateDirect(1024 * 1024)
                    val info = android.media.MediaCodec.BufferInfo()
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                        writer.writeSampleData(destinationTrack, buffer, info)
                        extractor.advance()
                    }
                    writer.stop()
                }
            }
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }
}
