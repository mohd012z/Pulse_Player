package com.pulseplayer.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView

/** First-party navigation shell. Providers may require an external browser for sign-in/playback. */
class ProviderBrowserActivity : Activity() {
    companion object {
        const val EXTRA_PROVIDER = "provider"
        private val providers = mapOf(
            "Spotify" to "https://open.spotify.com/",
            "JOOX" to "https://www.joox.com/",
            "YouTube" to "https://www.youtube.com/",
            "YouTube Music" to "https://music.youtube.com/",
            "YouTube Shorts" to "https://www.youtube.com/shorts",
            "YouTube Live TV" to "https://www.youtube.com/results?search_query=live+tv+streaming&sp=EgJAAQ%253D%253D",
            "Short Drama" to "https://www.youtube.com/results?search_query=short+drama"
        )
    }

    private lateinit var browser: WebView
    private lateinit var rootLayout: LinearLayout
    private lateinit var videoHost: FrameLayout
    private lateinit var exitCinemaButton: Button
    private lateinit var headerView: View
    private lateinit var statusView: View
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var portraitCinema = false
    private var videoFullscreenMode = false
    private var landscapeRequested = false
    private var drama = false
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var previousUiFlags = 0
    private lateinit var controls: LinearLayout
    private lateinit var controlsContainer: HorizontalScrollView
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private var controlsVisible = true
    private var home = ""
    private var privacyFilterEnabled = true

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable {
        controlsContainer.visibility = View.GONE
        controlsVisible = false
        toggle.text = "Show tools"
    }
    private fun scheduleAutoHide() {
        hideHandler.removeCallbacks(hideControls)
        if (controlsVisible) hideHandler.postDelayed(hideControls, 5000L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        home = providers[provider] ?: run { finish(); return }
        drama = provider == "Short Drama" || provider == "YouTube Shorts"
        previousOrientation = requestedOrientation
        previousUiFlags = window.decorView.systemUiVisibility
        window.statusBarColor = Color.rgb(16, 20, 31)
        window.navigationBarColor = Color.rgb(16, 20, 31)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 20, 31))
            setPadding(0, 0, 0, 0)
        }
        rootLayout = root
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30 && !portraitCinema) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = provider
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(16, 10, 8, 10)
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toggle = Button(this).apply { text = "Hide tools" }
        header.addView(toggle)
        root.addView(header)
        headerView = header

        status = TextView(this).apply {
            text = "Web player: some services require opening in their official app."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(12, 4, 12, 6)
        }
        root.addView(status)
        statusView = status
        if (provider == "Spotify") {
            status.text = "Spotify playback may require the official app or a supported browser."
            root.addView(Button(this).apply {
                text = "Open in Spotify app / supported browser"
                setOnClickListener {
                    val current = Uri.parse(browser.url ?: home)
                    openSpotify(current)
                }
            })
        }
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || fullscreenView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    fullscreenView = view
                    fullscreenCallback = callback
                    videoHost.addView(view, videoHost.indexOfChild(exitCinemaButton), FrameLayout.LayoutParams(-1, -1))
                    browser.visibility = View.GONE
                    enterPortraitCinema(providerFullscreen = true)
                }
                override fun onHideCustomView() { leaveFullscreenVideo() }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (BrowserPolicy.blockTracker(request.url.host.orEmpty(), request.isForMainFrame, privacyFilterEnabled)) {
                        return WebResourceResponse("text/plain", "UTF-8",
                            java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (!BrowserPolicy.allowWebNavigation(uri.scheme, uri.host)) {
                        status.text = "Only HTTPS navigation is supported."
                        return true
                    }
                    return false
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest,
                    error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) status.text = "Page unavailable. Try Open externally."
                }
            }
        }
        videoHost = FrameLayout(this)
        videoHost.addView(browser, FrameLayout.LayoutParams(-1, -1))
        exitCinemaButton = Button(this).apply {
            text = "✕"
            contentDescription = "Exit cinema or video fullscreen"
            visibility = View.GONE
            setOnClickListener { leavePortraitCinema() }
        }
        videoHost.addView(exitCinemaButton, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END))
        root.addView(videoHost, LinearLayout.LayoutParams(-1, 0, 1f))
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun button(label: String, weight: Float = 1f, action: () -> Unit) {
            controls.addView(Button(this).apply {
                text = label
                textSize = 11f
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight))
        }
        if (drama) button("Cinema", 1.3f) {
            if (portraitCinema) leavePortraitCinema() else enterPortraitCinema()
        }
        if (drama) button("Landscape", 1.4f) {
            landscapeRequested = !landscapeRequested
            requestedOrientation = if (landscapeRequested)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            status.text = if (landscapeRequested)
                "Landscape enabled. Tap the YouTube player's fullscreen icon for video-only view."
            else "Automatic orientation restored."
        }
        button("Back") { if (browser.canGoBack()) browser.goBack() else finish() }
        button("Forward") { if (browser.canGoForward()) browser.goForward() }
        button("Home") { browser.loadUrl(home) }
        button("Reload") { browser.reload() }
        if (provider in setOf("YouTube", "YouTube Shorts", "Short Drama")) {
            button("Shorts") { browser.loadUrl("https://www.youtube.com/shorts") }
            button("Drama") { browser.loadUrl("https://www.youtube.com/results?search_query=short+drama") }
        }
        button("CC") { status.text = "Use the video player's CC control for available subtitles." }
        button("Privacy") {
            privacyFilterEnabled = !privacyFilterEnabled
            status.text = if (privacyFilterEnabled) "Tracker filtering ON; reload to apply"
                else "Tracker filtering OFF; reload to apply"
        }
        button("External", 1.3f) { openExternal(Uri.parse(browser.url ?: home)) }
        controlsContainer = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        }
        root.addView(controlsContainer)
        if (drama) header.addView(Button(this).apply {
            text = "⛶"
            contentDescription = "Portrait cinema"
            setOnClickListener { if (portraitCinema) leavePortraitCinema() else enterPortraitCinema() }
        })
        toggle.setOnClickListener {
            controlsVisible = !controlsVisible
            controlsContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
            toggle.text = if (controlsVisible) "Hide tools" else "Show tools"
            scheduleAutoHide()
        }
        setContentView(root)
        if (savedInstanceState == null || browser.restoreState(savedInstanceState) == null) browser.loadUrl(home)
        scheduleAutoHide()
    }

    private fun enterPortraitCinema(providerFullscreen: Boolean = false) {
        portraitCinema = true
        videoFullscreenMode = providerFullscreen
        headerView.visibility = View.GONE
        statusView.visibility = View.GONE
        controlsContainer.visibility = View.GONE
        if (providerFullscreen) {
            // The provider may display landscape video even from the Short Drama page.
            requestedOrientation = if (landscapeRequested)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else if (drama) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        exitCinemaButton.visibility = View.VISIBLE
        exitCinemaButton.bringToFront()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        rootLayout.setPadding(0, 0, 0, 0)
        hideHandler.removeCallbacks(hideControls)
    }

    private fun leaveFullscreenVideo() {
        val video = fullscreenView ?: return
        videoHost.removeView(video)
        fullscreenView = null
        browser.visibility = View.VISIBLE
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
        leavePortraitCinema()
    }

    private fun leavePortraitCinema() {
        if (fullscreenView != null) { leaveFullscreenVideo(); return }
        portraitCinema = false
        videoFullscreenMode = false
        exitCinemaButton.visibility = View.GONE
        headerView.visibility = View.VISIBLE
        statusView.visibility = View.VISIBLE
        controlsContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        window.decorView.systemUiVisibility = previousUiFlags
        requestedOrientation = if (landscapeRequested)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else previousOrientation
        rootLayout.requestApplyInsets()
        scheduleAutoHide()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::browser.isInitialized) browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun openSpotify(uri: Uri) {
        val safe = if (BrowserPolicy.allowSpotifyHandoff(uri.scheme, uri.host)) uri else Uri.parse(home)
        val app = Intent(Intent.ACTION_VIEW, safe).apply {
            setPackage("com.spotify.music")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching { startActivity(app) }.onFailure { openExternal(safe) }
    }

    private fun openExternal(uri: Uri) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        }.onFailure { status.text = "No browser available for this link." }
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX migration")
    override fun onBackPressed() {
        when {
            fullscreenView != null -> leaveFullscreenVideo()
            portraitCinema -> leavePortraitCinema()
            ::browser.isInitialized && browser.canGoBack() -> browser.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        hideHandler.removeCallbacks(hideControls)
        // Preserve the page and playback state; providers control whether background audio is allowed.
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::controlsContainer.isInitialized && !portraitCinema) scheduleAutoHide()
    }

    override fun onDestroy() {
        hideHandler.removeCallbacks(hideControls)
        if (fullscreenView != null) leaveFullscreenVideo()
        if (::browser.isInitialized) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading()
            browser.destroy()
        }
        super.onDestroy()
    }
}
