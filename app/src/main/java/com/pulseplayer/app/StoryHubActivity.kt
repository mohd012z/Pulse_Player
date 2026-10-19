package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.res.Configuration
import android.view.WindowInsets
import android.view.Gravity
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
    private lateinit var content: LinearLayout
    private var currentScreen = "hub"
    private var activeNovel: Uri? = null
    private var activeVideo: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF10141F.toInt()
        window.navigationBarColor = 0xFF10141F.toInt()
        window.decorView.systemUiVisibility = 0
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFF10141F.toInt())
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(maxOf(dp(12), bars.left), maxOf(dp(12), bars.top), maxOf(dp(12), bars.right), maxOf(dp(12), bars.bottom))
                insets
            }
        } else {
            root.setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        setContentView(root)
        showHub()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (currentScreen) {
            "drama" -> showDrama()
            "novel" -> activeNovel?.let { showNovel(it) } ?: showHub()
            "player" -> playEpisode()
            else -> showHub()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun action(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = dp(if (isTelevision()) 64 else 50)
            maxLines = 2
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun isTelevision(): Boolean =
        (getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager)
            .currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

    private fun header(title: String) {
        activeVideo?.stopPlayback()
        activeVideo = null
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = title
            textSize = if (isTelevision()) 27f else 22f
            setTextColor(-1)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        })
    }

    private fun showHub() {
        currentScreen = "hub"
        header("Pulse · Stories")
        content.addView(action("🎬  Short Drama · My episodes") { showDrama() })
        content.addView(action("📖  Novel · My bookshelf") { importNovel() })
        content.addView(action("ℹ  Content & privacy") {
            AlertDialog.Builder(this).setTitle("Your content")
                .setMessage("Import videos and TXT books that you own or are authorized to use. Files stay on your device. No MovieBox credentials, private APIs, or hidden streaming endpoints are used.")
                .setPositiveButton("OK", null).show()
        })
        content.getChildAt(1)?.requestFocus()
    }

    private fun showDrama() {
        currentScreen = "drama"
        header("🎬 Short Drama")
        content.addView(action("＋ Import episodes (video files)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }, requestVideos)
        })
        if (videos.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "No episodes imported yet. Select your own MP4 or supported video files."
                setTextColor(-1)
            })
        }
        videos.forEachIndexed { index, uri ->
            content.addView(action("▶ Episode ${index + 1} · ${uri.lastPathSegment?.takeLast(30) ?: "Video"}") {
                videoIndex = index
                playEpisode()
            })
        }
        content.addView(action("← Stories") { showHub() })
    }

    private fun playEpisode() {
        currentScreen = "player"
        val uri = videos.getOrNull(videoIndex) ?: return
        header("Episode ${videoIndex + 1} / ${videos.size}")
        val video = VideoView(this)
        activeVideo = video
        val width = resources.displayMetrics.widthPixels - dp(36)
        val videoHeight = (width * 9 / 16).coerceIn(dp(180), dp(if (isTelevision()) 540 else 400))
        content.addView(video, LinearLayout.LayoutParams(-1, videoHeight))
        video.setVideoURI(uri)
        video.setMediaController(android.widget.MediaController(this).apply { setAnchorView(video) })
        video.setOnPreparedListener { video.start() }
        video.setOnCompletionListener {
            if (videoIndex + 1 < videos.size) {
                videoIndex++
                playEpisode()
            }
        }
        content.addView(action("⏮ Previous episode") {
            video.stopPlayback()
            videoIndex = (videoIndex - 1).coerceAtLeast(0)
            playEpisode()
        })
        content.addView(action("⏭ Next episode") {
            video.stopPlayback()
            videoIndex = (videoIndex + 1).coerceAtMost(videos.lastIndex)
            playEpisode()
        })
        content.addView(action("← Episode list") {
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
        currentScreen = "novel"
        activeNovel = uri
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
        content.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        content.addView(action("A−  Smaller text") { page.textSize = (page.textSize / resources.displayMetrics.scaledDensity - 2).coerceAtLeast(12f) })
        content.addView(action("A＋  Larger text") { page.textSize = (page.textSize / resources.displayMetrics.scaledDensity + 2).coerceAtMost(36f) })
        content.addView(action("← Stories") { showHub() })
        scroll.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        activeVideo?.pause()
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
