package com.pulseplayer.app

import android.app.Activity
import android.content.Intent
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
import android.webkit.WebResourceRequest
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
            "YouTube Music" to "https://music.youtube.com/"
        )
    }

    private lateinit var browser: WebView
    private lateinit var controls: LinearLayout
    private lateinit var controlsContainer: HorizontalScrollView
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private var controlsVisible = true
    private var home = ""
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
        window.statusBarColor = Color.rgb(16, 20, 31)
        window.navigationBarColor = Color.rgb(16, 20, 31)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 20, 31))
            setPadding(0, 0, 0, 0)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
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

        status = TextView(this).apply {
            text = "Web player: some services require opening in their official app."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(12, 4, 12, 6)
        }
        root.addView(status)
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
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme != "https") {
                        openExternal(uri)
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
        root.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
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
        button("Back") { if (browser.canGoBack()) browser.goBack() else finish() }
        button("Forward") { if (browser.canGoForward()) browser.goForward() }
        button("Home") { browser.loadUrl(home) }
        button("Reload") { browser.reload() }
        button("External", 1.3f) { openExternal(Uri.parse(browser.url ?: home)) }
        controlsContainer = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        }
        root.addView(controlsContainer)
        toggle.setOnClickListener {
            controlsVisible = !controlsVisible
            controlsContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
            toggle.text = if (controlsVisible) "Hide tools" else "Show tools"
            scheduleAutoHide()
        }
        setContentView(root)
        browser.loadUrl(home)
        scheduleAutoHide()
    }

    private fun openExternal(uri: Uri) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        }.onFailure { status.text = "No browser available for this link." }
    }

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX migration")
    override fun onBackPressed() {
        if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        hideHandler.removeCallbacks(hideControls)
        // Preserve the page and playback state; providers control whether background audio is allowed.
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::controlsContainer.isInitialized) scheduleAutoHide()
    }

    override fun onDestroy() {
        hideHandler.removeCallbacks(hideControls)
        if (::browser.isInitialized) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading()
            browser.destroy()
        }
        super.onDestroy()
    }
}
