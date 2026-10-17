package com.pulseplayer.app.aipulse

import android.app.Activity
import android.content.Intent
import android.os.Build
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
    private val legacySaveRequest = 7812
    private val origin = Uri.parse("https://appassets.androidplatform.net")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = PulseNativeExport(applicationContext) { name ->
            if (Build.VERSION.SDK_INT < 29) {
                val safe = name.take(60).replace(Regex("[^a-zA-Z0-9 _-]"), "_").ifBlank { "PulseNova" }
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/wav"
                    putExtra(Intent.EXTRA_TITLE, "$safe.wav")
                }
                try { startActivityForResult(intent, legacySaveRequest) }
                catch (_: Exception) { bridge.finishLegacySave(null) }
            }
        }
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
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
        val style = intent.getStringExtra("pulse_style_tags").orEmpty()
        val lyrics = intent.getStringExtra("pulse_lyrics").orEmpty()
        val enabled = intent.getBooleanExtra("pulse_lyric_mode", false)
        web.loadUrl("https://appassets.androidplatform.net/assets/pulse_nova.html")
        if (style.isNotBlank() || lyrics.isNotBlank()) {
            val payload = org.json.JSONObject().put("style", style).put("lyrics", lyrics).put("enabled", enabled)
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
                override fun onPageFinished(view: WebView, url: String) {
                    if (url == "https://appassets.androidplatform.net/assets/pulse_nova.html") {
                        view.evaluateJavascript("window.pulsePrefill && window.pulsePrefill(${payload})", null)
                    }
                }
            }
        }
    }
    @Deprecated("Legacy SAF callback for API 26–28")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == legacySaveRequest) {
            bridge.finishLegacySave(if (resultCode == RESULT_OK) data?.data else null)
        }
    }
    override fun onDestroy() {
        bridge.shutdown()
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
