package com.pulseplayer.app

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.content.pm.PackageManager
import android.util.Rational
import android.content.res.Configuration
import android.content.Intent
import android.provider.Settings
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
import android.widget.SeekBar
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
    private var miniControls: View? = null
    private var miniSeek: SeekBar? = null
    private var miniProgress: TextView? = null
    private var updatingSeek = false
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

    private fun rememberVideo(uri: Uri, title: String? = null) {
        val id = youtubeId(uri) ?: return
        val old = readHistory()
        val previous = (0 until old.length()).mapNotNull { old.optJSONObject(it) }
            .firstOrNull { it.optString("id") == id }
        val cleanedTitle = title?.trim()?.take(160)?.takeIf {
            it.isNotBlank() && it != "YouTube" && it != "YouTube - YouTube"
        }
        val next = JSONArray()
        next.put(JSONObject()
            .put("id", id)
            .put("title", cleanedTitle ?: previous?.optString("title").orEmpty())
            .put("posterUrl", "https://i.ytimg.com/vi/$id/hqdefault.jpg")
            .put("visitedAt", System.currentTimeMillis()))
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("id") != id && next.length() < 100) next.put(item)
        }
        historyStore.edit().putString("videos", next.toString()).apply()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun showPosterGrid() {
        val items = readHistory()
        val entries = (0 until minOf(items.length(), 60)).mapNotNull { index ->
            items.optJSONObject(index)?.takeIf {
                it.optString("id").matches(Regex("[A-Za-z0-9_-]{11}"))
            }
        }
        if (entries.isEmpty()) {
            Toast.makeText(this, "Watch videos to populate the poster grid", Toast.LENGTH_SHORT).show()
            return
        }
        val cards = entries.joinToString("") { entry ->
            val id = entry.optString("id")
            val title = escapeHtml(entry.optString("title").ifBlank { "YouTube video · $id" })
            val poster = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val date = java.text.DateFormat.getDateInstance().format(
                java.util.Date(entry.optLong("visitedAt").coerceAtLeast(0L)))
            "<a href=\"https://www.youtube.com/watch?v=$id\" aria-label=\"$title\">" +
                "<img loading=\"lazy\" alt=\"Thumbnail: $title\" src=\"$poster\"/>" +
                "<strong>$title</strong><small>Watched ${escapeHtml(date)}</small></a>"
        }
        val html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>body{background:#10141f;color:white;font-family:sans-serif;margin:16px}" +
            ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:14px}" +
            "a{color:white;text-decoration:none;background:#1b2331;border-radius:12px;overflow:hidden;padding:8px}" +
            "a{display:block;min-height:180px}a:focus,a:focus-visible{outline:5px solid #75e2c0;outline-offset:3px;transform:scale(1.025)}" +
            "img{width:100%;aspect-ratio:16/9;object-fit:cover}" +
            "strong,small{display:block;padding:5px 8px;overflow-wrap:anywhere}small{color:#b6c3cd}" +
            "</style></head><body><h2>Recently watched videos</h2>" +
            "<div class=\"grid\">$cards</div>" +
            "<script>document.querySelector(\'.grid a\')?.focus();</script></body></html>"
        browser?.apply { isFocusable = true; isFocusableInTouchMode = true
            loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
            requestFocus()
        }
    }

    private fun savePlaybackPosition(videoId: String, seconds: Double) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}")) ||
            !seconds.isFinite() || seconds < 0.0 || seconds > 86400.0) return
        historyStore.edit().putLong("position:$videoId", seconds.toLong()).apply()
    }

    private var awaitingCastReturn = false

    private fun openYouTubeForCasting() {
        val current = browser?.url?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val videoId = current?.let { youtubeId(it) }
        val destination = if (videoId != null) {
            val seconds = historyStore.getLong("position:$videoId", 0L).coerceIn(0L, 86400L)
            "https://www.youtube.com/watch?v=$videoId" +
                if (seconds > 0L) "&t=${seconds}s" else ""
        } else "https://www.youtube.com/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destination)).apply {
            setPackage("com.google.android.youtube")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val launched = runCatching { startActivity(intent) }.isSuccess
        if (!launched) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination))) }
                .onFailure {
                    Toast.makeText(this, "No app available to open YouTube", Toast.LENGTH_LONG).show()
                }
                .onSuccess { awaitingCastReturn = true }
        } else {
            awaitingCastReturn = true
            Toast.makeText(this, "Use the Cast icon in YouTube to select your TV", Toast.LENGTH_LONG).show()
        }
    }

    private fun showContinueWatching() {
        val items = readHistory()
        if (items.length() == 0) {
            Toast.makeText(this, "No videos to continue", Toast.LENGTH_SHORT).show()
            return
        }
        val entries = (0 until minOf(items.length(), 24)).mapNotNull { index ->
            items.optJSONObject(index)?.optString("id")
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }
        if (entries.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Continue Watching · Recently opened")
            .setItems(entries.map { id ->
                val position = historyStore.getLong("position:$id", 0L)
                if (position > 0) "YouTube · $id · resume at ${position}s" else "YouTube · $id"
            }.toTypedArray()) { _, index ->
                val id = entries[index]
                val position = historyStore.getLong("position:$id", 0L).coerceIn(0L, 86400L)
                val target = "https://www.youtube.com/watch?v=$id" +
                    if (position > 0) "&t=${position}s" else ""
                browser?.loadUrl(target)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            "$date · ${entry?.optString("title")?.takeIf { it.isNotBlank() } ?: entry?.optString("id").orEmpty()}"
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
        requestedOrientation = DeviceFormFactor.preferredOrientation(this)
        // Dedicated browser screen: avoid OEM edge-to-edge status icons obscuring controls.
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val providers = mapOf(
            "YouTube" to "https://www.youtube.com/",
            "RTM Klik" to "https://rtmklik.rtm.gov.my/",
            "Vidio" to "https://www.vidio.com/",
            "YouTube Music" to "https://music.youtube.com/",
            "YouTube Shorts" to "https://www.youtube.com/shorts",
            "JOOX" to "https://www.joox.com/",
            "Spotify" to "https://open.spotify.com/",
            "YouTube Movies" to "https://www.youtube.com/results?search_query=official+full+movies",
            "Movie Melayu" to "https://www.youtube.com/results?search_query=filem+melayu+penuh+rasmi",
            "Movie Indonesia" to "https://www.youtube.com/results?search_query=film+indonesia+full+movie+resmi",
            "Public Domain Movies" to "https://www.youtube.com/results?search_query=public+domain+full+movies",
            "Live TV" to "https://www.youtube.com/results?search_query=official+live+tv+channel",
            "Live Sports" to "https://www.youtube.com/results?search_query=official+live+sports",
            "Ceramah Agama" to "https://www.youtube.com/results?search_query=ceramah+agama+rasmi+Malaysia",
            "Sidang Media Malaysia" to "https://www.youtube.com/results?search_query=sidang+media+Malaysia+BERNAMA+RTM",
            "Sejarah Malaysia" to "https://www.youtube.com/results?search_query=dokumentari+sejarah+Malaysia+Arkib+Negara",
            "5-Minute Crafts" to "https://www.youtube.com/channel/UC295-Dw_tDNtZXFeAPAW6Aw",
            "Live Streaming" to "https://rtmklik.rtm.gov.my/",
            "Drama China (Pelakon Sebenar)" to "https://www.youtube.com/results?search_query=Chinese+short+drama+live+action+official+-AI+-animated+-animation+-generated",
            "Drama Melayu" to "https://www.youtube.com/results?search_query=drama+melayu+episod+penuh+rasmi",
            "Drama Korea" to "https://www.youtube.com/results?search_query=korean+drama+full+episodes+official",
            "Al-Quran" to "https://www.youtube.com/results?search_query=bacaan+al+quran+rasmi",
            "Nasyid" to "https://www.youtube.com/results?search_query=nasyid+malaysia+official",
            "Tarannum" to "https://www.youtube.com/results?search_query=tarannum+al+quran+Malaysia",
            "Selawat" to "https://www.youtube.com/results?search_query=selawat+rasmi+Malaysia",
            "Netflix on YouTube" to "https://www.youtube.com/results?search_query=Netflix+official+trailers+clips",
            "Netflix Movies" to "https://www.netflix.com/browse",
            "Radio Malaysia (RTM)" to "https://radio.rtm.gov.my/"
        )
        val name = intent.getStringExtra("provider").orEmpty()
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val url = when (name) {
            "YouTube Movies" -> "https://www.youtube.com/results?search_query=$year+new+full+movies+official"
            "Public Domain Movies" -> "https://www.youtube.com/results?search_query=$year+public+domain+movies+new+uploads"
            else -> if (name.startsWith("Radio: ")) {
                intent.getStringExtra("url")?.takeIf { value ->
                    val uri = Uri.parse(value)
                    uri.scheme == "https" && uri.host in setOf("audio1.syok.my", "dengar.suria.my", "radio.rtm.gov.my")
                }
            } else if (name.startsWith("Misteri Indonesia: ")) {
                val creator = name.removePrefix("Misteri Indonesia: ").trim()
                val allowed = setOf("Kang Ujang Busthomi", "Mamaz Karyo", "Gus Nur Syamsudin",
                    "Padepokan Nur Dzat Sejati", "Gus Samsudin", "Gus Idris",
                    "Jurnalrisa", "Sara Wijayanto", "Kisah Tanah Jawa",
                    "Nessie Judge", "Nadia Omara", "Ewing HD", "Lentera Malam")
                if (creator in allowed) "https://www.youtube.com/results?search_query=" + Uri.encode(creator + " official")
                else null
            } else providers[name]
        } ?: run { finish(); return }
        val movieQuery = intent.getStringExtra("movie_search_query")
            ?.takeIf { name in setOf("Movie Melayu", "Movie Indonesia") && it.length in 1..300 }
        val resolvedUrl = if (movieQuery != null) {
            "https://www.youtube.com/results?search_query=" + Uri.encode(movieQuery)
        } else url
        activeProvider = name
        homeUrl = resolvedUrl
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10141F.toInt())
            fitsSystemWindows = true
        }
        // Use density-independent available width rather than a fixed handset model.
        // Narrow phones get two compact rows; tablets get one proportional toolbar.
        val tvMode = DeviceFormFactor.isTelevision(this)
        val isTablet = tvMode || resources.configuration.screenWidthDp >= 600
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        playerChrome = top
        pipEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        fun toolbarButton(textValue: String, description: String, action: () -> Unit) =
            Button(this).apply {
                text = textValue
                textSize = if (tvMode) 18f else if (isTablet) 12f else 11f
                setAllCaps(false)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(3), 0, dp(3), 0)
                contentDescription = description
                setOnClickListener { action() }
                if (tvMode) {
                    isFocusable = true
                    isFocusableInTouchMode = false
                    setOnFocusChangeListener { view, focused ->
                        view.alpha = if (focused) 1f else 0.8f
                        view.scaleX = if (focused) 1.06f else 1f
                        view.scaleY = if (focused) 1.06f else 1f
                    }
                }
            }
        val back = toolbarButton("‹ Back", "Back to Pulse Player") { finish() }
        val title = TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = if (isTablet) 16f else 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val historyButton = toolbarButton("History", "Watch history") { showWatchHistory() }
        val continueButton = toolbarButton("Continue", "Continue recently opened videos") { showContinueWatching() }
        val posterButton = toolbarButton("Posters", "Browse recently watched video thumbnails") { showPosterGrid() }
        val external = toolbarButton("Official app", "Open official app") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        val mirrorButton = toolbarButton("Mirror", "Open screen mirroring and routing options") {
            val options = arrayOf("YouTube Cast · Open official YouTube app", "Screen mirror · Android Cast settings", "Audio output · Android settings", "Compatibility and troubleshooting")
            AlertDialog.Builder(this)
                .setTitle("Mirror Cast")
                .setItems(options) { _, selected ->
                    when (selected) {
                        0 -> openYouTubeForCasting()
                        1 -> runCatching { startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) }
                            .onFailure { Toast.makeText(this, "Cast settings unavailable on this device", Toast.LENGTH_LONG).show() }
                        2 -> runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                            .onFailure { Toast.makeText(this, "Audio output settings unavailable", Toast.LENGTH_LONG).show() }
                        3 -> AlertDialog.Builder(this)
                            .setTitle("Connection help")
                            .setMessage("For YouTube, open the official YouTube app, tap its Cast icon, and choose a supported TV on the same network. Screen mirroring requires compatible phone and TV casting protocols. Use the same Wi-Fi when required, enable screen sharing on the TV, then select the receiver in Android settings. Audio routing is separate from video mirroring. DRM-protected playback may be blocked. No protected playback is bypassed.")
                            .setPositiveButton("OK", null).show()
                    }
                }
                .setNegativeButton("Cancel", null).show()
        }
        val compactButton = toolbarButton("Mini", "Floating mini player") { enterFloatingMini() }
        val downloadButton = toolbarButton("Download", "Download available media") { showMediaDownloadOptions() }
        compactButton.setOnLongClickListener {
            Toast.makeText(this, "Use the Home gesture for floating MINI where supported", Toast.LENGTH_LONG).show()
            true
        }
        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        firstRow.addView(back, LinearLayout.LayoutParams(dp(if (tvMode) 130 else if (isTablet) 88 else 70), dp(if (tvMode) 68 else 38)))
        firstRow.addView(title, LinearLayout.LayoutParams(0, dp(if (tvMode) 68 else 38), 1f))
        if (isTablet) {
            firstRow.addView(posterButton, LinearLayout.LayoutParams(dp(if (tvMode) 130 else 94), dp(if (tvMode) 68 else 38)))
            firstRow.addView(continueButton, LinearLayout.LayoutParams(dp(if (tvMode) 130 else 94), dp(if (tvMode) 68 else 38)))
            firstRow.addView(historyButton, LinearLayout.LayoutParams(dp(if (tvMode) 130 else 94), dp(if (tvMode) 68 else 38)))
            firstRow.addView(mirrorButton, LinearLayout.LayoutParams(dp(if (tvMode) 130 else 94), dp(if (tvMode) 68 else 38)))
            firstRow.addView(external, LinearLayout.LayoutParams(dp(if (tvMode) 160 else 114), dp(if (tvMode) 68 else 38)))
            firstRow.addView(downloadButton, LinearLayout.LayoutParams(dp(if (tvMode) 150 else 64), dp(if (tvMode) 68 else 38)))
        }
        firstRow.addView(compactButton, LinearLayout.LayoutParams(dp(64), dp(38)))
        top.addView(firstRow)
        if (tvMode) downloadButton.requestFocus()
        if (!isTablet) {
            val secondRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            secondRow.addView(posterButton, LinearLayout.LayoutParams(0, dp(36), 1f))
            secondRow.addView(continueButton, LinearLayout.LayoutParams(0, dp(36), 1f))
            secondRow.addView(historyButton, LinearLayout.LayoutParams(0, dp(36), 1f))
            secondRow.addView(mirrorButton, LinearLayout.LayoutParams(0, dp(36), 1f))
            secondRow.addView(external, LinearLayout.LayoutParams(0, dp(36), 1f))
            secondRow.addView(downloadButton, LinearLayout.LayoutParams(0, dp(36), 1f))
            top.addView(secondRow)
        }
        root.addView(top)
        val providerNote = TextView(this).apply {
            text = "Background playback depends on the provider and Android."
            setTextColor(0xFFB6C3CD.toInt())
            textSize = 12f
            visibility = View.GONE
        }
        root.addView(providerNote)
        val web = WebView(this)
        browser = web
        web.settings.javaScriptEnabled = true
        // Observe HTML5 playback state without injecting play commands or bypassing provider controls.
        web.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun report(playing: Boolean) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && allowedSessionUrl(browser?.url)) {
                        providerVideoPlaying = playing
                    }
                }
            }
        }, "PulsePlaybackObserver")
        web.settings.domStorageEnabled = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
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
                // Read-only HTML5 position observation; provider restrictions are respected.
                val observedUri = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                val observedId = observedUri?.let { youtubeId(it) }
                if (observedId != null) {
                    view?.evaluateJavascript(
                        "(function(){var v=document.querySelector('video');return v && isFinite(v.currentTime) ? v.currentTime : -1;})()"
                    ) { raw ->
                        val seconds = raw?.toDoubleOrNull()
                        if (seconds != null && view.url == url) savePlaybackPosition(observedId, seconds)
                    }
                }
                url?.let { pageUrl ->
                    val pageUri = runCatching { Uri.parse(pageUrl) }.getOrNull()
                    if (pageUri != null && youtubeId(pageUri) != null) {
                        rememberVideo(pageUri)
                        view?.evaluateJavascript("document.title") { jsonTitle ->
                            val title = runCatching {
                                JSONArray("[$jsonTitle]").optString(0)
                            }.getOrNull()
                            // Ignore stale asynchronous title callbacks after navigation.
                            if (view.url == pageUrl && !isFinishing && !isDestroyed) {
                                rememberVideo(pageUri, title)
                            }
                        }
                    }
                }
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
                requestedOrientation = DeviceFormFactor.preferredOrientation(this@OnlineMediaActivity)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        // Small in-app transport controls; only act on accessible HTML5 media.
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD10141F.toInt())
            visibility = View.VISIBLE
        }
        miniControls = controls
        val seek = SeekBar(this).apply { max = 1000 }
        miniSeek = seek
        controls.addView(seek, LinearLayout.LayoutParams(-1, (30 * resources.displayMetrics.density).toInt()))
        val buttons = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER }
        fun control(label: String, action: String) {
            buttons.addView(Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(2, 0, 2, 0)
                setOnClickListener { mediaCommand(action) }
            }, LinearLayout.LayoutParams(0, (36 * resources.displayMetrics.density).toInt(), 1f))
        }
        control("−10s", "back")
        control("◀◀", "previous")
        control("▶/Ⅱ", "toggle")
        control("▶▶", "next")
        control("+10s", "forward")
        controls.addView(buttons)
        val progress = TextView(this).apply {
            text = "00:00 / --:--"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        miniProgress = progress
        controls.addView(progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar?) { updatingSeek = true }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                val position = (bar?.progress ?: 0) / 1000.0
                browser?.evaluateJavascript("""(function(){var v=document.querySelector('video,audio');if(v&&isFinite(v.duration)&&v.duration>0)v.currentTime=v.duration*$position;})();""", null)
                updatingSeek = false
            }
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {}
        })
        root.addView(controls)
        val poll = object : Runnable {
            override fun run() {
                if (browser == null || isFinishing) return
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPictureInPictureMode) {
                    browser?.evaluateJavascript("""(function(){var v=document.querySelector('video,audio');return v?JSON.stringify({t:v.currentTime||0,d:isFinite(v.duration)?v.duration:0}):'';})();""") { raw ->
                        val value = runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull()
                        val data = runCatching { JSONObject(value ?: "") }.getOrNull()
                        if (data != null) {
                            val duration = data.optDouble("d", 0.0)
                            val position = data.optDouble("t", 0.0)
                            if (duration > 0 && !updatingSeek) miniSeek?.progress = (position / duration * 1000).toInt().coerceIn(0,1000)
                            fun clock(s: Double): String = "%02d:%02d".format((s / 60).toInt(), s.toInt() % 60)
                            miniProgress?.text = clock(position) + " / " + if (duration > 0) clock(duration) else "--:--"
                        }
                    }
                }
                miniControls?.postDelayed(this, 1000)
            }
        }
        controls.post(poll)

        compactButton.setOnClickListener { enterFloatingMini() }
        compactButton.setOnLongClickListener {
            Toast.makeText(this, "Tip: use the Home gesture to enter floating MINI on supported Android versions", Toast.LENGTH_LONG).show()
            true
        }
        setContentView(root)
        // Open embedded content directly; do not interrupt every launch with a warning dialog.
        if (savedInstanceState == null) {
            val last = sessionStore.getString("page:$name", null)
            web.loadUrl(if (allowedSessionUrl(last)) last!! else resolvedUrl)
        } else if (web.restoreState(savedInstanceState) == null) {
            web.loadUrl(resolvedUrl)
        }
    }

    private fun mediaCommand(action: String) {
        val script = when (action) {
            "back" -> "v.currentTime=Math.max(0,v.currentTime-10)"
            "forward" -> "v.currentTime=Math.min(v.duration||Infinity,v.currentTime+10)"
            "toggle" -> "if(v.paused)v.play();else v.pause()"
            else -> ""
        }
        if (script.isEmpty()) {
            Toast.makeText(this, "Use the provider's own previous/next controls", Toast.LENGTH_SHORT).show()
            return
        }
        browser?.evaluateJavascript("(function(){var v=document.querySelector('video,audio');if(v){$script}})();", null)
    }

    private fun showMediaDownloadOptions() {
        val current = browser?.url.orEmpty()
        val host = runCatching { Uri.parse(current).host?.lowercase().orEmpty() }.getOrDefault("")
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") {
            AlertDialog.Builder(this)
                .setTitle("YouTube download")
                .setMessage("YouTube watch pages are streaming pages, not downloadable media files. For videos with offline access, use YouTube's official Download feature where available. Pulse Player can save direct, authorized MP3/MP4 files.")
                .setPositiveButton("My uploads · Studio") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/")))
                }
                .setNeutralButton("Direct file URL") { _, _ -> showDirectMediaDownload() }
                .setNegativeButton("Cancel", null)
                .show()
        } else showDirectMediaDownload()
    }

    private fun showDirectMediaDownload() {
        val detected = browser?.url.orEmpty()
        val input = android.widget.EditText(this).apply {
            setSingleLine(true)
            setText(detected)
            hint = "Direct HTTPS MP3, M4A, MP4 or WEBM URL"
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Save media · auto-detected URL")
            .setMessage("Paste a direct authorized HTTPS MP3/MP4 file URL. YouTube watch pages are not direct file links.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val raw = input.text.toString().trim()
                val uri = runCatching { Uri.parse(raw) }.getOrNull()
                val extension = uri?.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
                val allowed = mapOf("mp3" to "audio/mpeg", "m4a" to "audio/mp4",
                    "aac" to "audio/aac", "wav" to "audio/wav",
                    "mp4" to "video/mp4", "webm" to "video/webm")
                if (uri?.scheme != "https" || uri.host.isNullOrBlank() || extension !in allowed) {
                    Toast.makeText(this, "Not a direct media file. Paste an authorized HTTPS file link.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                AlertDialog.Builder(this)
                    .setTitle("Quality and destination")
                    .setSingleChoiceItems(arrayOf("Original source quality (no conversion)"), 0) { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton("Music") { _, _ -> enqueueMediaDownload(uri!!, extension, allowed.getValue(extension), true) }
                    .setNeutralButton("Downloads") { _, _ -> enqueueMediaDownload(uri!!, extension, allowed.getValue(extension), false) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }.show()
    }

    private fun enqueueMediaDownload(uri: Uri, extension: String, mime: String, music: Boolean) {
        val filename = "Pulse_" + System.currentTimeMillis() + "." + extension
        val directory = if (music) android.os.Environment.DIRECTORY_MUSIC else android.os.Environment.DIRECTORY_DOWNLOADS
        val request = android.app.DownloadManager.Request(uri)
            .setTitle(filename)
            .setMimeType(mime)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(directory, filename)
        val manager = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
        runCatching { manager.enqueue(request) }
            .onSuccess { downloadId ->
                Toast.makeText(this, "Download queued: $directory/$filename", Toast.LENGTH_LONG).show()
                watchDownloadStatus(manager, downloadId)
            }
            .onFailure { Toast.makeText(this, "Download unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    /**
     * Android DownloadManager enqueue() only confirms scheduling, not successful transfer.
     * Query the actual transfer result and report failures to the user.
     */
    private fun watchDownloadStatus(manager: android.app.DownloadManager, downloadId: Long) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val check = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                val status = runCatching {
                    manager.query(android.app.DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val state = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON))
                        state to reason
                    }
                }.getOrNull()
                when (status?.first) {
                    android.app.DownloadManager.STATUS_SUCCESSFUL ->
                        Toast.makeText(this@OnlineMediaActivity, "Download completed. Check Music or Downloads.", Toast.LENGTH_LONG).show()
                    android.app.DownloadManager.STATUS_FAILED ->
                        Toast.makeText(this@OnlineMediaActivity, "Download failed: ${downloadFailureReason(status.second)}", Toast.LENGTH_LONG).show()
                    null -> Toast.makeText(this@OnlineMediaActivity, "Download record unavailable.", Toast.LENGTH_LONG).show()
                    else -> if (android.os.SystemClock.elapsedRealtime() - startedAt < 30L * 60L * 1000L) {
                        handler.postDelayed(this, 2000L)
                    } else {
                        Toast.makeText(this@OnlineMediaActivity, "Download still pending. Check Android Downloads for progress.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        handler.postDelayed(check, 2000L)
    }

    private fun downloadFailureReason(reason: Int): String = when (reason) {
        android.app.DownloadManager.ERROR_CANNOT_RESUME -> "Server does not support resuming this transfer"
        android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage unavailable"
        android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Destination file already exists"
        android.app.DownloadManager.ERROR_FILE_ERROR -> "Unable to write the downloaded file"
        android.app.DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP connection or response error"
        android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient device storage"
        android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many server redirects"
        android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server rejected the download (HTTP error)"
        android.app.DownloadManager.ERROR_UNKNOWN -> "Unknown Android download error"
        in 400..599 -> "HTTP $reason from the media server"
        else -> "Android reason code $reason"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

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
        if (!awaitingCastReturn && pipEnabled && fullscreenView == null && providerVideoPlaying) enterFloatingMini()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        enteringPip = false
        playerChrome?.visibility = if (inPip) View.GONE else View.VISIBLE
        miniControls?.visibility = if (inPip) View.GONE else View.VISIBLE
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
        if (awaitingCastReturn) {
            awaitingCastReturn = false
            browser?.requestFocus()
            Toast.makeText(this, "Returned from YouTube · Pulse Player ready", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        exitFullscreen()
        browser?.apply { stopLoading(); destroy() }
        browser = null
        super.onDestroy()
    }
}
