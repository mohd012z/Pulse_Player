package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * In-app browser for official music/video websites.
 * Providers can restrict WebView playback, login or DRM. No bypass is attempted.
 */
class OnlineMediaActivity : Activity() {
    private var browser: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providers = mapOf(
            "YouTube" to "https://www.youtube.com/",
            "YouTube Music" to "https://music.youtube.com/",
            "YouTube Shorts" to "https://www.youtube.com/shorts",
            "JOOX" to "https://www.joox.com/",
            "Spotify" to "https://open.spotify.com/",
            "YouTube Movies" to "https://www.youtube.com/results?search_query=official+full+movies",
            "Public Domain Movies" to "https://www.youtube.com/results?search_query=public+domain+full+movies",
            "Live TV" to "https://www.youtube.com/results?search_query=official+live+tv+channel",
            "Live Sports" to "https://www.youtube.com/results?search_query=official+live+sports"
        )
        val name = intent.getStringExtra("provider").orEmpty()
        val url = providers[name] ?: run { finish(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10141F.toInt())
        }
        val top = LinearLayout(this)
        val back = Button(this).apply { text = "← Back"; setOnClickListener { finish() } }
        val title = TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val external = Button(this).apply {
            text = "Open official app"
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        top.addView(back)
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(external)
        root.addView(top)
        val web = WebView(this)
        browser = web
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.settings.setSupportMultipleWindows(false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val target = request?.url ?: return true
                return target.scheme != "https"
            }
        }
        web.webChromeClient = WebChromeClient()
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        AlertDialog.Builder(this)
            .setTitle("Official $name website")
            .setMessage("This service will open inside Pulse Player. Some playback, login or protected content may require its official app or browser.")
            .setPositiveButton("Continue") { _, _ -> web.loadUrl(url) }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    override fun onBackPressed() {
        val web = browser
        if (web?.canGoBack() == true) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        browser?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        browser?.onResume()
    }

    override fun onDestroy() {
        browser?.apply { stopLoading(); destroy() }
        browser = null
        super.onDestroy()
    }
}
