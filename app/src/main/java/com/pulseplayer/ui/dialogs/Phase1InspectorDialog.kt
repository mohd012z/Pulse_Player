package com.pulseplayer.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.pulseplayer.inspector.SourceType
import com.pulseplayer.inspector.UrlInspector
import java.net.URI

/** Phase 1: classification only; no network calls or downloads. */
object Phase1InspectorDialog {
    fun show(context: Context) {
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val input = EditText(context).apply {
            hint = "https://www.youtube.com/watch?v=..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Inspect media URL")
            .setMessage("Classify a URL without downloading or contacting the server.")
            .setView(container)
            .setPositiveButton("Inspect", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val result = UrlInspector.inspect(input.text.toString())
                val message = when (val type = result.sourceType) {
                    is SourceType.YouTubeWatch -> "YouTube Watch\nVideo ID: ${type.videoId}\nTimestamp: ${type.timestampSeconds ?: 0}s\nResolver required."
                    is SourceType.YouTubeShorts -> "YouTube Shorts\nVideo ID: ${type.videoId}\nAuthorized resolver required."
                    is SourceType.YouTubeLive -> "YouTube Live\nVideo ID: ${type.videoId}\nAuthorized resolver required."
                    is SourceType.YouTubePlaylist -> "YouTube Playlist\nPlaylist ID: ${type.playlistId}\nPlaylist enumeration not implemented."
                    is SourceType.DirectMedia -> {
                        val display = try {
                            val uri = URI(result.normalizedUrl)
                            val base = "${uri.scheme}://${uri.rawAuthority}${uri.rawPath}"
                            if (uri.rawQuery == null) base else "$base?[QUERY REDACTED]"
                        } catch (_: Exception) { "[Protected media URL]" }
                        "Direct media candidate\nExtension: .${type.extension}\nURL: $display\nSigned query retained internally. Network not inspected."
                    }
                    is SourceType.Unsupported -> "Unsupported URL\nReason: ${type.reason}"
                }
                dialog.dismiss()
                AlertDialog.Builder(context).setTitle("Inspection result")
                    .setMessage(message).setPositiveButton("OK", null).show()
            }
        }
        dialog.show()
    }
}
