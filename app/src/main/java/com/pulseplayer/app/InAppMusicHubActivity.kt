package com.pulseplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** In-app provider-owned music surfaces. Not an extractor or replacement streaming service. */
class InAppMusicHubActivity : Activity() {
    companion object {
        const val EXTRA_PROVIDER = "provider"
        private const val BG = 0xFF10141F.toInt()
        private const val PANEL = 0xFF1B2331.toInt()
    }

    private lateinit var web: WebView
    private lateinit var root: LinearLayout
    private lateinit var info: TextView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private var current = ProviderMediaUrls.Provider.YOUTUBE
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(8), dp(8), dp(8), dp(6))
        }
        setContentView(root)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, provider) in listOf(
            "YouTube" to ProviderMediaUrls.Provider.YOUTUBE,
            "YT Music" to ProviderMediaUrls.Provider.YOUTUBE_MUSIC,
            "Spotify" to ProviderMediaUrls.Provider.SPOTIFY,
            "JOOX" to ProviderMediaUrls.Provider.JOOX
        )) {
            tabs.addView(button(name) { choose(provider) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        root.addView(tabs)
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFB9C4CE.toInt())
            hint = "Paste an official video, playlist or track link"
            setPadding(dp(9), 0, dp(9), 0)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        toolbar.addView(button("Load") { loadInput() }, LinearLayout.LayoutParams(dp(64), dp(48)))
        root.addView(toolbar)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("Home") { load(ProviderMediaUrls.default(current)) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(button("Back") { if (web.canGoBack()) web.goBack() else finish() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(button("Official app") { openOfficial() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(actions)
        info = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFB7E9DB.toInt())
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        root.addView(info)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false; max = 100
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        web = WebView(this).apply {
            setBackgroundColor(PANEL)
            settings.javaScriptEnabled = true // Needed for official YouTube/Spotify embeds
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@InAppMusicHubActivity.progress.progress = newProgress
                    this@InAppMusicHubActivity.progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || callback == null || fullscreen != null) return
                    fullscreen = view
                    fullscreenCallback = callback
                    root.visibility = View.GONE
                    addContentView(view, ViewGroup.LayoutParams(-1, -1))
                }
                override fun onHideCustomView() { exitFullscreen() }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    val host = uri.host?.lowercase().orEmpty()
                    val permitted = uri.scheme == "https" && when (current) {
                        ProviderMediaUrls.Provider.YOUTUBE, ProviderMediaUrls.Provider.YOUTUBE_MUSIC -> host in setOf("www.youtube.com", "youtube.com", "m.youtube.com", "music.youtube.com", "www.youtube-nocookie.com")
                        ProviderMediaUrls.Provider.SPOTIFY -> host in setOf("open.spotify.com")
                        ProviderMediaUrls.Provider.JOOX -> host in setOf("www.joox.com", "joox.com")
                    }
                    if (!permitted) {
                        info.text = "Link requires the official browser/app. For safety it is not loaded here."
                        return true
                    }
                    return false
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) info.text = "Provider web playback unavailable here. Choose Official app."
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    this@InAppMusicHubActivity.progress.visibility = View.VISIBLE
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        current = runCatching {
            ProviderMediaUrls.Provider.valueOf(intent.getStringExtra(EXTRA_PROVIDER).orEmpty())
        }.getOrDefault(ProviderMediaUrls.Provider.YOUTUBE)
        choose(current)
    }

    private fun choose(provider: ProviderMediaUrls.Provider) {
        current = provider
        address.setText("")
        load(ProviderMediaUrls.default(provider))
    }

    private fun loadInput() {
        val value = address.text.toString()
        val destination = ProviderMediaUrls.resolve(current, value)
        if (destination == null) {
            AlertDialog.Builder(this).setTitle("Unsupported link")
                .setMessage("Use an HTTPS link belonging to ${current.name.replace('_', ' ')}. For YouTube, paste a video/playlist URL. For Spotify, paste a track, album or playlist share URL. JOOX accepts official website links.")
                .setPositiveButton("OK", null).show()
            return
        }
        load(destination)
    }

    private fun load(destination: ProviderMediaUrls.Destination) {
        info.text = destination.message
        // ExoPlayer playback is paused by the caller so two music sources do not overlap.
        when (destination.mode) {
            ProviderMediaUrls.Mode.YOUTUBE_EMBED -> {
                val url = destination.url
                val html = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"/></head><body style="margin:0;background:#111;height:100vh;display:flex;align-items:center;"><iframe title="YouTube official embedded player" src="$url" width="100%" height="100%" style="border:0;min-height:220px;" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></body></html>"""
                web.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
            }
            else -> web.loadUrl(destination.url)
        }
    }

    private fun openOfficial() {
        val destination = ProviderMediaUrls.resolve(current, address.text.toString())
            ?: ProviderMediaUrls.default(current)
        val target = if (destination.mode == ProviderMediaUrls.Mode.WEB) destination.url else when (current) {
            ProviderMediaUrls.Provider.YOUTUBE, ProviderMediaUrls.Provider.YOUTUBE_MUSIC -> "https://www.youtube.com/"
            ProviderMediaUrls.Provider.SPOTIFY -> destination.url.replace("/embed/", "/")
            ProviderMediaUrls.Provider.JOOX -> "https://www.joox.com/"
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
            .onFailure { info.text = "No compatible official browser or app installed" }
    }

    private fun exitFullscreen() {
        val view = fullscreen ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreen = null
        root.visibility = View.VISIBLE
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    @Deprecated("Activity onBackPressed used for API 26 compatibility")
    override fun onBackPressed() {
        when {
            fullscreen != null -> exitFullscreen()
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onPause() { super.onPause(); if (::web.isInitialized) web.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onDestroy() {
        exitFullscreen()
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.loadUrl("about:blank")
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }

    private fun button(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; isAllCaps = false; setOnClickListener { click() }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}