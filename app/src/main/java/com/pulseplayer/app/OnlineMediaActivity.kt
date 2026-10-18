package com.pulseplayer.app

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.content.pm.PackageManager
import android.util.Rational
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Color
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AlertDialog
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-app browser for official music/video websites.
 * Providers can restrict WebView playback, login or DRM. No bypass is attempted.
 */
class OnlineMediaActivity : Activity() {
    private var browser: WebView? = null
    private var playerChrome: View? = null
    private var pipEnabled = false
    private var enteringPip = false
    private var providerVideoPlaying = false
    private val sessionStore by lazy { getSharedPreferences("pulse_browser_session", MODE_PRIVATE) }
    private var activeProvider = ""
    private var homeUrl = ""

    private fun allowedSessionUrl(value: String?): Boolean {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val homeHost = Uri.parse(homeUrl).host?.lowercase()
        val youtubeHosts = setOf("www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
        return uri.scheme == "https" && (host == homeHost ||
            (homeHost in youtubeHosts && host in youtubeHosts))
    }

    private fun persistPage(value: String?) {
        if (activeProvider.isNotBlank() && allowedSessionUrl(value)) {
            sessionStore.edit().putString("page:$activeProvider", value).apply()
        }
    }

    private val historyStore by lazy { getSharedPreferences("pulse_video_history", MODE_PRIVATE) }
    private fun readHistory(): JSONArray = try {
        JSONArray(historyStore.getString("videos", "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun youtubeId(uri: Uri): String? {
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val id = when (host) {
            "youtube.com", "m.youtube.com" -> when {
                uri.path == "/watch" -> uri.getQueryParameter("v")
                uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
                uri.path?.startsWith("/live/") == true -> uri.pathSegments.getOrNull(1)
                else -> null
            }
            "youtu.be" -> uri.pathSegments.firstOrNull()
            else -> null
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    private fun rememberVideo(uri: Uri) {
        val id = youtubeId(uri) ?: return
        val old = readHistory()
        val next = JSONArray()
        next.put(JSONObject().put("id", id).put("visitedAt", System.currentTimeMillis()))
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("id") != id && next.length() < 100) next.put(item)
        }
        historyStore.edit().putString("videos", next.toString()).apply()
    }

    private fun showWatchHistory() {
        val items = readHistory()
        if (items.length() == 0) {
            Toast.makeText(this, "No videos in history yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = (0 until items.length()).map { i ->
            val entry = items.optJSONObject(i)
            val date = java.text.DateFormat.getDateTimeInstance().format(
                java.util.Date(entry?.optLong("visitedAt") ?: 0L))
            "$date · ${entry?.optString("id").orEmpty()}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Watch history")
            .setItems(labels) { _, index ->
                val id = items.optJSONObject(index)?.optString("id").orEmpty()
                if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                    browser?.loadUrl("https://www.youtube.com/watch?v=$id")
                }
            }
            .setNeutralButton("Clear history") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("Clear Pulse Player watch history?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ ->
                        historyStore.edit().remove("videos").apply()
                    }.show()
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null
    private var previousOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dedicated browser screen: avoid OEM edge-to-edge status icons obscuring controls.
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
            else -> if (name.startsWith("Misteri Indonesia: ")) {
                val creator = name.removePrefix("Misteri Indonesia: ").trim()
                val allowed = setOf("Kang Ujang Busthomi", "Mamaz Karyo", "Gus Nur Syamsudin",
                    "Padepokan Nur Dzat Sejati", "Gus Samsudin", "Gus Idris",
                    "Jurnalrisa", "Sara Wijayanto", "Kisah Tanah Jawa",
                    "Nessie Judge", "Nadia Omara", "Ewing HD", "Lentera Malam")
                if (creator in allowed) "https://www.youtube.com/results?search_query=" + Uri.encode(creator + " official")
                else null
            } else providers[name]
        } ?: run { finish(); return }
        activeProvider = name
        homeUrl = url
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10141F.toInt())
            fitsSystemWindows = true
        }
        val top = LinearLayout(this)
        playerChrome = top
        pipEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
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
        val historyButton = Button(this).apply {
            text = "History"
            setOnClickListener { showWatchHistory() }
        }
        top.addView(historyButton)
        top.addView(external)
        root.addView(top)
        // Compact mode keeps the provider WebView visible and active while this
        // activity is foregrounded; it does not bypass provider background rules.
        // All supported online providers and movie categories can use compact mode.
        val compactButton = Button(this).apply { text = "Mini" }
        val providerNote = TextView(this).apply {
            text = "Mini mode keeps this page visible while Pulse Player is open. Background playback depends on the provider and Android."
            setTextColor(0xFFB6C3CD.toInt())
            textSize = 12f
            setPadding(12, 4, 12, 4)
            visibility = View.GONE
        }
        top.addView(compactButton)
        root.addView(providerNote)
        val web = WebView(this)
        browser = web
        web.settings.javaScriptEnabled = true
        // Observe HTML5 playback state without injecting play commands or bypassing provider controls.
        web.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun report(playing: Boolean) {
                runOnUiThread { providerVideoPlaying = playing }
            }
        }, "PulsePlaybackObserver")
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.settings.setSupportMultipleWindows(false)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                persistPage(url)
                // Read-only observation; YouTube may use a player implementation that does not expose HTML5 video.
                view?.evaluateJavascript("""(function(){
                  if(window.__pulseObserved)return;window.__pulseObserved=true;
                  function report(){var v=document.querySelector('video');if(v)PulsePlaybackObserver.report(!v.paused&&!v.ended);}
                  document.addEventListener('play',report,true);
                  document.addEventListener('pause',report,true);
                  document.addEventListener('ended',report,true);
                  report();
                })();""", null)
                url?.let { runCatching { rememberVideo(Uri.parse(it)) } }
            }

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
        compactButton.setOnClickListener { enterFloatingMini() }
        compactButton.setOnLongClickListener {
            Toast.makeText(this, "Tip: use the Home gesture to enter floating MINI on supported Android versions", Toast.LENGTH_LONG).show()
            true
        }
        setContentView(root)
        // Open embedded content directly; do not interrupt every launch with a warning dialog.
        if (savedInstanceState == null) {
            val last = sessionStore.getString("page:$name", null)
            web.loadUrl(if (allowedSessionUrl(last)) last!! else url)
        } else if (web.restoreState(savedInstanceState) == null) {
            web.loadUrl(url)
        }
    }

    private fun enterFloatingMini() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Floating MINI is not supported on this device", Toast.LENGTH_LONG).show()
            return
        }
        if (fullscreenView != null) {
            Toast.makeText(this, "Exit fullscreen before using MINI", Toast.LENGTH_SHORT).show()
            return
        }
        val rect = android.graphics.Rect()
        browser?.getGlobalVisibleRect(rect)
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16))
        if (!rect.isEmpty) params.setSourceRectHint(rect)
        enteringPip = true
        val entered = runCatching { enterPictureInPictureMode(params.build()) }.getOrDefault(false)
        if (!entered) enteringPip = false
        if (!entered) Toast.makeText(this, "Unable to open floating MINI", Toast.LENGTH_LONG).show()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEnabled && fullscreenView == null && providerVideoPlaying) enterFloatingMini()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        enteringPip = false
        playerChrome?.visibility = if (inPip) View.GONE else View.VISIBLE
        if (inPip) browser?.onResume()
        if (!inPip) providerVideoPlaying = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistPage(browser?.url)
        browser?.saveState(outState)
        super.onSaveInstanceState(outState)
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
        persistPage(browser?.url)
        // Android invokes Activity.onPause on PiP entry and screen lock.
        // Do not explicitly suspend the embedded player here.
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
