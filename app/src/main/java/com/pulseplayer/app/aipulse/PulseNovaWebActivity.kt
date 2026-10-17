package com.pulseplayer.app.aipulse

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Bundled offline PULSE-NOVA composer; external requests are never served. */
class PulseNovaWebActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var bridge: PulseNativeExport
    private val origin = Uri.parse("https://appassets.androidplatform.net")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = PulseNativeExport(applicationContext)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.scheme != "https" || url.host != origin.host || url.port != -1 ||
                    !url.path.orEmpty().startsWith("/assets/")) {
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                        mapOf("Cache-Control" to "no-store"), "".byteInputStream())
                }
                return loader.shouldInterceptRequest(url)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                return u.scheme != "https" || u.host != origin.host || u.port != -1 ||
                    !u.path.orEmpty().startsWith("/assets/")
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "AndroidBridge", setOf(origin.toString())) {
                    _, message, source, mainFrame, proxy ->
                if (mainFrame && source == origin) {
                    message.data?.let { bridge.accept(it, proxy) }
                }
            }
        }
        web.loadUrl("https://appassets.androidplatform.net/assets/pulse_nova.html")
    }
    override fun onDestroy() {
        bridge.shutdown()
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
