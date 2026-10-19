package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView

/**
 * Local-first Short Drama and Novel hub.
 * Imports only user-selected documents. No private MovieBox endpoints or credentials.
 */
class StoryHubActivity : Activity() {
    private val requestVideos = 4101
    private val requestNovel = 4102
    private val videos = mutableListOf<Uri>()
    private var videoIndex = 0
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFF10141F.toInt())
        }
        setContentView(root)
        showHub()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun action(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = dp(if (isTelevision()) 64 else 50)
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun isTelevision(): Boolean =
        (getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager)
            .currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

    private fun header(title: String) {
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = title
            textSize = if (isTelevision()) 27f else 22f
            setTextColor(-1)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        })
    }

    private fun showHub() {
        header("Pulse · Stories")
        root.addView(action("🎬  Short Drama · My episodes") { showDrama() })
        root.addView(action("📖  Novel · My bookshelf") { importNovel() })
        root.addView(action("ℹ  Content & privacy") {
            AlertDialog.Builder(this).setTitle("Your content")
                .setMessage("Import videos and TXT books that you own or are authorized to use. Files stay on your device. No MovieBox credentials, private APIs, or hidden streaming endpoints are used.")
                .setPositiveButton("OK", null).show()
        })
        root.getChildAt(1)?.requestFocus()
    }

    private fun showDrama() {
        header("🎬 Short Drama")
        root.addView(action("＋ Import episodes (video files)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }, requestVideos)
        })
        if (videos.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No episodes imported yet. Select your own MP4 or supported video files."
                setTextColor(-1)
            })
        }
        videos.forEachIndexed { index, uri ->
            root.addView(action("▶ Episode ${index + 1} · ${uri.lastPathSegment?.takeLast(30) ?: "Video"}") {
                videoIndex = index
                playEpisode()
            })
        }
        root.addView(action("← Stories") { showHub() })
    }

    private fun playEpisode() {
        val uri = videos.getOrNull(videoIndex) ?: return
        header("Episode ${videoIndex + 1} / ${videos.size}")
        val video = VideoView(this)
        root.addView(video, LinearLayout.LayoutParams(-1, 0, 1f))
        video.setVideoURI(uri)
        video.setMediaController(android.widget.MediaController(this).apply { setAnchorView(video) })
        video.setOnPreparedListener { video.start() }
        video.setOnCompletionListener {
            if (videoIndex + 1 < videos.size) {
                videoIndex++
                playEpisode()
            }
        }
        root.addView(action("⏮ Previous episode") {
            video.stopPlayback()
            videoIndex = (videoIndex - 1).coerceAtLeast(0)
            playEpisode()
        })
        root.addView(action("⏭ Next episode") {
            video.stopPlayback()
            videoIndex = (videoIndex + 1).coerceAtMost(videos.lastIndex)
            playEpisode()
        })
        root.addView(action("← Episode list") {
            video.stopPlayback()
            showDrama()
        })
        video.requestFocus()
    }

    private fun importNovel() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, requestNovel)
    }

    private fun showNovel(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                // Cap reads to 2 million characters to avoid freezing on oversized documents.
                val buffer = CharArray(8192)
                val out = StringBuilder()
                while (out.length < 2_000_000) {
                    val n = reader.read(buffer, 0, minOf(buffer.size, 2_000_000 - out.length))
                    if (n <= 0) break
                    out.append(buffer, 0, n)
                }
                out.toString()
            } ?: "Unable to read this document."
        } catch (e: Exception) {
            "Unable to open document: ${e.message ?: "Unknown error"}"
        }
        header("📖 Novel Reader")
        val scroll = ScrollView(this)
        val page = TextView(this).apply {
            this.text = text
            textSize = 19f
            setTextColor(-1)
            setLineSpacing(dp(5).toFloat(), 1f)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(action("A−  Smaller text") { page.textSize = (page.textSize / resources.displayMetrics.scaledDensity - 2).coerceAtLeast(12f) })
        root.addView(action("A＋  Larger text") { page.textSize = (page.textSize / resources.displayMetrics.scaledDensity + 2).coerceAtMost(36f) })
        root.addView(action("← Stories") { showHub() })
        scroll.requestFocus()
    }

    @Deprecated("Uses legacy result API for compatibility with existing Activity architecture")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == requestVideos) {
            val chosen = mutableListOf<Uri>()
            data.clipData?.let { clips ->
                for (i in 0 until clips.itemCount) chosen.add(clips.getItemAt(i).uri)
            }
            data.data?.let { chosen.add(it) }
            chosen.distinct().forEach { uri ->
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                if (!videos.contains(uri)) videos.add(uri)
            }
            showDrama()
        } else if (requestCode == requestNovel) {
            data.data?.let { showNovel(it) }
        }
    }
}
