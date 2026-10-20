package com.pulseplayer.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Button
import android.graphics.Color

/** First-party shell for Archive.org's public website, not a scraped or cloned catalog. */
class ArchiveBrowserActivity : Activity() {
    private lateinit var browser: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val back = Button(this).apply {
            text = "← Pulse Cinema"
            setOnClickListener { finish() }
        }
        root.addView(back, LinearLayout.LayoutParams(-1, -2))
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true // Archive.org requires JS for its player.
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val host = uri.host?.lowercase() ?: return true
                    if (uri.scheme == "https" && (host == "archive.org" || host.endsWith(".archive.org"))) return false
                    if (uri.scheme == "https") {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    return true
                }
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val requested = intent.getStringExtra(EXTRA_URL) ?: FILMS
        val uri = Uri.parse(requested)
        browser.loadUrl(if (uri.scheme == "https" && uri.host == "archive.org") requested else FILMS)
    }
    override fun onBackPressed() {
        if (browser.canGoBack()) browser.goBack() else super.onBackPressed()
    }
    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
    companion object {
        const val EXTRA_URL = "archive_url"
        const val FILMS = "https://archive.org/details/feature_films"
        const val ANIMATION = "https://archive.org/details/animationandcartoons"
    }
}
