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

/** Foreground-only official YouTube embed. No audio extraction or hidden playback. */
class YouTubePlayerActivity : Activity() {
    private var player: WebView? = null

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
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "Open in official YouTube"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(original)))
            }
        })
        setContentView(root)
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
