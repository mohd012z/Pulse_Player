package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null
    private var previousOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

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
            "Live Sports" to "https://www.youtube.com/results?search_query=official+live+sports",
            "Ceramah Agama" to "https://www.youtube.com/results?search_query=ceramah+agama+rasmi+Malaysia",
            "Sidang Media Malaysia" to "https://www.youtube.com/results?search_query=sidang+media+Malaysia+BERNAMA+RTM",
            "Sejarah Malaysia" to "https://www.youtube.com/results?search_query=dokumentari+sejarah+Malaysia+Arkib+Negara",
            "5-Minute Crafts" to "https://www.youtube.com/channel/UC295-Dw_tDNtZXFeAPAW6Aw",
            "Live Streaming" to "https://rtmklik.rtm.gov.my/",
            "Drama China (Pelakon Sebenar)" to "https://www.youtube.com/results?search_query=Chinese+short+drama+live+action+official+-AI+-animated+-animation+-generated",
            "Radio Malaysia (RTM)" to "https://radio.rtm.gov.my/"
        )
        val name = intent.getStringExtra("provider").orEmpty()
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val url = when (name) {
            "YouTube Movies" -> "https://www.youtube.com/results?search_query=$year+new+full+movies+official"
            "Public Domain Movies" -> "https://www.youtube.com/results?search_query=$year+public+domain+movies+new+uploads"
            else -> providers[name]
        } ?: run { finish(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10141F.toInt())
            fitsSystemWindows = true
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
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || fullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                previousOrientation = requestedOrientation
                fullscreenView = view
                fullscreenCallback = callback
                val container = FrameLayout(this@OnlineMediaActivity).apply {
                    setBackgroundColor(Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                fullscreenContainer = container
                addContentView(container, ViewGroup.LayoutParams(-1, -1))
                // Allow both portrait short dramas and landscape movies without forcing a reload.
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        AlertDialog.Builder(this)
            .setTitle("Official $name website")
            .setMessage("This service will open inside Pulse Player. Some playback, login or protected content may require its official app or browser.")
            .setPositiveButton("Continue") { _, _ -> web.loadUrl(url) }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun exitFullscreen() {
        val container = fullscreenContainer ?: return
        fullscreenContainer = null
        fullscreenView = null
        (container.parent as? ViewGroup)?.removeView(container)
        val callback = fullscreenCallback
        fullscreenCallback = null
        requestedOrientation = previousOrientation
        callback?.onCustomViewHidden()
    }

    override fun onBackPressed() {
        if (fullscreenView != null) {
            exitFullscreen()
            return
        }
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
        exitFullscreen()
        browser?.apply { stopLoading(); destroy() }
        browser = null
        super.onDestroy()
    }
}
