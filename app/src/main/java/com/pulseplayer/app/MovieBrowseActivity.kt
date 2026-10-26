package com.pulseplayer.app

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Responsive movie discovery, shared by phone and Android TV.
 * Opens the official YouTube website without intercepting streams or ads.
 */
class MovieBrowseActivity : Activity() {
    private val bg = Color.rgb(7, 17, 31)
    private val tile = Color.rgb(22, 38, 57)
    private val cyan = Color.rgb(0, 220, 224)
    private var browser: WebView? = null
    private var overlay: FrameLayout? = null
    private lateinit var root: FrameLayout

    private val categories = listOf(
        "New Movies" to "filem baru rasmi trailer",
        "Movie Melayu" to "filem Melayu penuh rasmi",
        "Drama Melayu" to "drama Melayu episod penuh rasmi",
        "Short Drama" to "short drama Melayu episod rasmi",
        "Drama Korea" to "Korean drama official episodes",
        "Al-Quran" to "tilawah Al Quran rasmi",
        "Nasyid" to "nasyid Malaysia rasmi",
        "Paranormal & Mistik" to "kisah paranormal mistik dokumentari",
        "5-Minute Crafts" to "5 minute crafts official",
        "MotoGP" to "MotoGP official highlights",
        "Formula One" to "Formula 1 official highlights",
        "Ceramah" to "ceramah Malaysia rasmi",
        "Documentaries" to "dokumentari bahasa Melayu rasmi"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun panel(): GradientDrawable = GradientDrawable().apply {
        setColor(tile)
        cornerRadius = dp(12).toFloat()
    }
    private fun text(value: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun action(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        isFocusable = true
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        setContentView(root)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(18))
        }
        root.addView(scroll)
        scroll.addView(page)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(text("♫ PULSE CINEMA", 22f, cyan).apply {
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        heading.addView(action("✕ Close") { finish() },
            LinearLayout.LayoutParams(dp(90), dp(42)))
        page.addView(heading)

        val search = EditText(this).apply {
            hint = "Search movies, drama, creators and videos…"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            background = panel()
            setPadding(dp(14), 0, dp(14), 0)
            setOnEditorActionListener { _, _, _ ->
                openSearch(text.toString())
                true
            }
        }
        page.addView(search, LinearLayout.LayoutParams(-1, dp(46)))

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF163C55.toInt(), 0xFF0C1729.toInt())
            ).apply { cornerRadius = dp(14).toFloat() }
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        hero.addView(text("MOVIES • SERIES • SHORT DRAMA", 12f, cyan))
        hero.addView(text("Discover and watch", if (landscape) 27f else 23f).apply {
            setTypeface(null, Typeface.BOLD)
        })
        hero.addView(text("Browse official video results. Subtitles depend on the video and YouTube availability.", 13f))
        hero.addView(action("▶ Explore Movie Melayu") { openSearch("filem Melayu penuh rasmi") },
            LinearLayout.LayoutParams(if (landscape) dp(220) else -1, dp(42)))
        page.addView(hero, LinearLayout.LayoutParams(-1, if (landscape) dp(160) else dp(178)).apply {
            topMargin = dp(10)
        })

        fun shelf(title: String, items: List<Pair<String, String>>) {
            page.addView(text(title, 18f).apply { setTypeface(null, Typeface.BOLD) },
                LinearLayout.LayoutParams(-1, dp(40)))
            val strip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            strip.addView(row)
            for ((name, query) in items) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = panel()
                    isFocusable = true
                    isClickable = true
                    setPadding(dp(9), dp(7), dp(9), dp(7))
                    setOnClickListener { openSearch(query) }
                }
                val poster = text("▶", 28f, cyan).apply {
                    gravity = Gravity.CENTER
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xFF27526B.toInt(), 0xFF111E33.toInt())
                    ).apply { cornerRadius = dp(8).toFloat() }
                }
                card.addView(poster, LinearLayout.LayoutParams(-1, dp(if (landscape) 88 else 108)))
                card.addView(text(name, 13f), LinearLayout.LayoutParams(-1, dp(35)))
                row.addView(card, LinearLayout.LayoutParams(dp(if (landscape) 154 else 142), -2).apply {
                    rightMargin = dp(8)
                })
            }
            page.addView(strip)
        }
        shelf("Movies & Drama", categories.take(5))
        shelf("Music, Learning & Sports", categories.drop(5))
    }

    private fun openSearch(query: String) {
        if (query.isBlank()) return
        closeBrowser()
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            isFocusable = true
            isFocusableInTouchMode = true
        }
        frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        frame.addView(action("✕ Close") { closeBrowser() },
            FrameLayout.LayoutParams(dp(100), dp(44), Gravity.TOP or Gravity.END))
        root.addView(frame, FrameLayout.LayoutParams(-1, -1))
        browser = web
        overlay = frame
        val url = Uri.parse("https://www.youtube.com/results").buildUpon()
            .appendQueryParameter("search_query", query).build().toString()
        web.loadUrl(url)
    }

    private fun closeBrowser() {
        val web = browser
        browser = null
        overlay?.let { root.removeView(it) }
        overlay = null
        web?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
    }
    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (overlay != null) closeBrowser() else super.onBackPressed()
    }
    override fun onDestroy() {
        closeBrowser()
        super.onDestroy()
    }
}
