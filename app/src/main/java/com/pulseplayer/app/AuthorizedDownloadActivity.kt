package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Downloads only a user-supplied direct HTTPS media file with explicit authorization. */
class AuthorizedDownloadActivity : Activity() {
    private lateinit var urlField: EditText
    private lateinit var permitted: CheckBox
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 30, 26, 22)
        }
        root.addView(TextView(this).apply {
            text = "Authorized direct media download"
            textSize = 21f
        })
        root.addView(TextView(this).apply {
            text = "Enter a direct HTTPS link to a downloadable MP3, M4A, WAV or MP4 file. Streaming-service pages and protected streams are not supported."
            textSize = 14f
            setPadding(0, 12, 0, 12)
        })
        urlField = EditText(this).apply {
            hint = "https://example.org/audio.mp3"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        root.addView(urlField)
        permitted = CheckBox(this).apply {
            text = "I own this file or have permission to download it."
        }
        root.addView(permitted)
        status = TextView(this).apply { text = "Files are saved in Downloads / PulsePlayer" }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Download original file"
            setOnClickListener { confirmDownload() }
        })
        setContentView(root)
    }

    private fun confirmDownload() {
        if (!permitted.isChecked) {
            status.text = "Confirm that downloading this file is authorized."
            return
        }
        val uri = Uri.parse(urlField.text.toString().trim())
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val blocked = listOf("youtube.com", "youtu.be", "googlevideo.com",
            "spotify.com", "scdn.co", "joox.com", "music.youtube.com")
        val blockedHost = blocked.any { host == it || host.endsWith(".$it") }
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (uri.scheme != "https" || host.isBlank() || blockedHost ||
            extension !in setOf("mp3", "m4a", "wav", "mp4", "aac", "flac", "ogg")) {
            status.text = "Use a direct, authorized HTTPS media-file URL (not a streaming-service page)."
            return
        }
        val filename = uri.lastPathSegment.orEmpty()
            .substringBefore('?').replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
        AlertDialog.Builder(this)
            .setTitle("Download original quality")
            .setMessage("Save $filename to Downloads/PulsePlayer? The file will not be converted or re-encoded.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Download") { _, _ ->
                runCatching {
                    val request = DownloadManager.Request(uri)
                        .setTitle(filename)
                        .setDescription("Authorized media download")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PulsePlayer/$filename")
                        .setAllowedOverMetered(true)
                    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                }.onSuccess { status.text = "Download queued. Check Android Downloads for progress." }
                    .onFailure { status.text = "Download could not start: ${it.message}" }
            }.show()
    }
}
