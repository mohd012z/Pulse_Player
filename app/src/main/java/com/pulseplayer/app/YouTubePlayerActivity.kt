package com.pulseplayer.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.view.Gravity
import android.view.View

/** Foreground-only official YouTube embed. No audio extraction or hidden playback. */
class YouTubePlayerActivity : Activity() {
    private var player: WebView? = null
    private var compact = false
    private var playerContainer: LinearLayout? = null
    private var toggleButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val original = intent.getStringExtra(EXTRA_URL).orEmpty()
        val embed = YouTubeLinkParser.embedUrl(original)
        if (embed == null) {
            finish()
            return
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val heading = TextView(this).apply {
            text = "YouTube · Official embedded player"
            textSize = 18f
            setPadding(20, 24, 20, 16)
        }
        root.addView(heading)
        root.addView(Button(this).apply {
            text = "Mini player · keep current movie visible"
            toggleButton = this
            setOnClickListener { setCompact(!compact) }
        })
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setBackgroundColor(android.graphics.Color.BLACK)
            loadUrl(embed, mapOf("Referer" to "https://${packageName}/"))
        }
        player = web
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        playerContainer = holder
        holder.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "Open in official YouTube"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(original)))
            }
        })
        setContentView(root)
        setCompact(false)
    }

    /** Resize the existing WebView; do not recreate it or reload the current video. */
    private fun setCompact(enabled: Boolean) {
        compact = enabled
        val holder = playerContainer ?: return
        val height = if (enabled) (resources.displayMetrics.widthPixels * 9 / 16).coerceAtLeast((200 * resources.displayMetrics.density).toInt()) else 0
        holder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, if (enabled) 0f else 1f)
        toggleButton?.text = if (enabled) "Expand current movie" else "Mini player · keep current movie visible"
        player?.visibility = View.VISIBLE
    }

    override fun onPause() {
        player?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.onResume()
    }

    override fun onDestroy() {
        player?.stopLoading()
        player?.loadUrl("about:blank")
        player?.destroy()
        player = null
        super.onDestroy()
    }

    companion object { const val EXTRA_URL = "youtube_url" }
}
