package com.pulseplayer.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlin.concurrent.thread

/**
 * 10-foot Android TV home screen. The existing PlaybackService owns playback;
 * no YouTube media is extracted, downloaded or passed to ExoPlayer.
 */
class TvActivity : Activity() {
    private val background = Color.rgb(6, 17, 32)
    private val panel = Color.rgb(22, 39, 59)
    private val accent = Color.rgb(0, 218, 220)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var entries: List<MediaItem> = emptyList()
    private var unfilteredEntries: List<MediaItem> = emptyList()
    private lateinit var cinemaSections: LinearLayout
    private lateinit var librarySection: LinearLayout
    private lateinit var nav: LinearLayout
    private var currentTab = "Music"
    private var generation = 0
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var results: ListView
    private lateinit var search: EditText
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private fun surface(color: Int = panel): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = 18f
            setStroke(1, Color.rgb(42, 68, 86))
        }

    private fun label(value: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(value: String, action: () -> Unit): Button =
        Button(this).apply {
            text = value
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = surface()
            isFocusable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { v, focused ->
                v.background = surface(if (focused) Color.rgb(0, 106, 120) else panel)
                v.scaleX = if (focused) 1.04f else 1f
                v.scaleY = if (focused) 1.04f else 1f
            }
        }

    private fun addButton(row: LinearLayout, text: String, action: () -> Unit): Button {
        val b = button(text, action)
        row.addView(b, LinearLayout.LayoutParams(0, 58, 1f).apply {
            marginEnd = 7
        })
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 14)
            setBackgroundColor(this@TvActivity.background)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = label("♫  PULSE PLAYER", 27f, accent).apply {
            setTypeface(null, Typeface.BOLD)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, 60, 1f))
        header.addView(label("MUSIC  •  RADIO  •  YOUTUBE  •  MOVIES", 13f, Color.LTGRAY))
        root.addView(header)

        search = EditText(this).apply {
            setSingleLine(true)
            textSize = 17f
            hint = "Search songs, artists or Malay movies on YouTube"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            background = surface()
            setPadding(22, 4, 22, 4)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ ->
                if (currentTab == "Movie Melayu" || currentTab == "YouTube") {
                    openYouTubeSearch(text.toString())
                } else {
                    filterLocal(text.toString())
                }
                true
            }
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 52).apply { bottomMargin = 9 })

        // Reference-inspired featured hero and horizontal movie discovery shelves.
        // These are YouTube search categories, not an unverified film catalogue.
        val content = ScrollView(this).apply { isFillViewport = true; isFocusable = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cinemaSections = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        librarySection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(26, 18, 26, 18)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(12, 27, 44), Color.rgb(43, 64, 77), Color.rgb(10, 23, 41))
            ).apply { cornerRadius = 22f }
        }
        val featureText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        featureText.addView(label("MOVIE MELAYU", 14f, accent))
        featureText.addView(label("Filem Melayu • YouTube", 30f).apply {
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, 60))
        featureText.addView(label("Browse Malay-language movies, drama and classics using official YouTube search.", 16f, Color.LTGRAY),
            LinearLayout.LayoutParams(-1, 70))
        featureText.addView(label("Select a category to discover videos", 15f, accent))
        hero.addView(featureText, LinearLayout.LayoutParams(0, -2, 3f))
        hero.addView(label("♫  PULSE CINEMA", 26f, accent).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -1, 2f))
        cinemaSections.addView(hero, LinearLayout.LayoutParams(-1, 188).apply { bottomMargin = 10 })

        fun shelf(heading: String, queries: List<Pair<String, String>>) {
            cinemaSections.addView(label(heading, 22f).apply { setTypeface(null, Typeface.BOLD) },
                LinearLayout.LayoutParams(-1, 43))
            val scroller = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            queries.forEachIndexed { index, item ->
                val tile = button(item.first) { openYouTubeSearch(item.second) }
                tile.gravity = Gravity.BOTTOM or Gravity.START
                tile.setPadding(14, 12, 14, 14)
                tile.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.rgb(23 + index * 5, 44 + index * 3, 69 + index * 2),
                        Color.rgb(12, 24, 41)
                    )
                ).apply {
                    cornerRadius = 16f
                    setStroke(1, Color.rgb(55, 79, 98))
                }
                tiles.addView(tile, LinearLayout.LayoutParams(220, 118).apply { marginEnd = 12 })
            }
            scroller.addView(tiles)
            cinemaSections.addView(scroller, LinearLayout.LayoutParams(-1, 130).apply { bottomMargin = 12 })
        }

        shelf("Popular Movie Melayu • Discover", listOf(
            "Drama" to "filem Melayu drama penuh rasmi",
            "Seram" to "filem Melayu seram penuh rasmi",
            "Aksi" to "filem Melayu aksi penuh rasmi",
            "Komedi" to "filem Melayu komedi penuh rasmi",
            "Klasik" to "filem Melayu klasik rasmi",
            "Sejarah" to "filem Melayu sejarah penuh rasmi"
        ))
        shelf("YouTube Movie Melayu • Categories", listOf(
            "Filem Penuh" to "filem Melayu penuh rasmi",
            "Drama Bersiri" to "drama Melayu episod penuh rasmi",
            "Filem Keluarga" to "filem Melayu keluarga rasmi",
            "Filem 90-an" to "filem Melayu 1990an rasmi",
            "Filem 2000-an" to "filem Melayu 2000an rasmi",
            "Drama Korea" to "drama Korea official episodes",
            "Short Drama" to "short drama Melayu episod",
            "Nasyid" to "nasyid Malaysia rasmi",
            "Al-Quran" to "tilawah al quran rasmi",
            "Paranormal" to "kisah mistik paranormal dokumentari Melayu",
            "5-Minute Crafts" to "5 minute crafts official",
            "MotoGP" to "MotoGP official highlights",
            "Formula 1" to "Formula 1 official highlights",
            "Ceramah" to "ceramah agama Malaysia rasmi"
        ))

        title = label("Music Library", 22f).apply { setTypeface(null, Typeface.BOLD) }
        librarySection.addView(title, LinearLayout.LayoutParams(-1, 48))
        results = ListView(this).apply {
            dividerHeight = 2
            setBackgroundColor(this@TvActivity.background)
            isFocusable = true
            setOnItemClickListener { _, _, position, _ ->
                controller?.apply {
                    setMediaItems(entries, position, 0L)
                    prepare()
                    play()
                }
            }
        }
        // ListView inside a scrolling home page needs a bounded height for remote navigation.
        librarySection.addView(results, LinearLayout.LayoutParams(-1, 240))
        body.addView(cinemaSections)
        body.addView(librarySection)
        content.addView(body)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val player = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = surface()
            setPadding(12, 4, 12, 4)
        }
        status = label("Connecting to playback…", 15f)
        player.addView(status, LinearLayout.LayoutParams(0, 56, 2f))
        addButton(player, "◀◀") { controller?.seekToPreviousMediaItem() }
        addButton(player, "▶ / ❚❚") { toggle() }
        addButton(player, "▶▶") { controller?.seekToNextMediaItem() }
        root.addView(player, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 6 })

        nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(nav, "⌂ Home") { showSection("Home") }
        addButton(nav, "♫ Music") { showSection("Music") }
        addButton(nav, "▣ Radio") { showSection("Radio") }
        addButton(nav, "▶ YouTube") { openYouTubeSearch("muzik Malaysia") }
        addButton(nav, "▤ Movie Melayu") { showSection("Movie Melayu") }
        addButton(nav, "☰ More") { startActivity(Intent(this, MainActivity::class.java)) }
        root.addView(nav, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 5 })
        setContentView(root)
        nav.getChildAt(0).requestFocus()
        showSection("Home")
    }

    private fun showSection(section: String) {
        currentTab = section
        cinemaSections.visibility = if (section == "Home" || section == "Movie Melayu") View.VISIBLE else View.GONE
        librarySection.visibility = if (section == "Movie Melayu") View.GONE else View.VISIBLE
        when (section) {
            "Home", "Music" -> loadMusic()
            "Radio" -> loadRadio()
            "Movie Melayu" -> title.text = "Movie Melayu"
        }
    }

    private fun openYouTubeSearch(query: String) {
        val q = query.trim().ifBlank { "filem Melayu penuh rasmi" }
        val url = Uri.parse("https://www.youtube.com/results").buildUpon()
            .appendQueryParameter("search_query", q).build()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
            .onFailure { status.text = "Install a browser or YouTube app to open results" }
    }

    private fun showItems(items: List<MediaItem>) {
        entries = items
        unfilteredEntries = items
        results.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            items.map { it.mediaMetadata.title?.toString() ?: "Untitled" })
    }

    private fun filterLocal(query: String) {
        if (query.isBlank()) {
            if (currentTab == "Radio") loadRadio() else loadMusic()
            return
        }
        val filtered = unfilteredEntries.filter {
            it.mediaMetadata.title?.toString()?.contains(query, ignoreCase = true) == true ||
                it.mediaMetadata.artist?.toString()?.contains(query, ignoreCase = true) == true
        }
        entries = filtered
        results.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            filtered.map { it.mediaMetadata.title?.toString() ?: "Untitled" })
    }

    private fun loadRadio() {
        generation++
        currentTab = "Radio"
        title.text = "Radio Stations"
        showItems(RadioRepository(this).stations().map { it.toMediaItem() })
    }

    private fun loadMusic() {
        if (currentTab != "Home") currentTab = "Music"
        title.text = "Your Music"
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 201)
            return
        }
        val request = ++generation
        thread(name = "TvMusicScan") {
            val songs = runCatching { MusicRepository(applicationContext).scan().map { it.toMediaItem() } }
            runOnUiThread {
                if (!isFinishing && !isDestroyed && generation == request && (currentTab == "Music" || currentTab == "Home")) {
                    songs.onSuccess { showItems(it) }
                        .onFailure { status.text = "Music scan failed: ${it.message}" }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 201 && grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) loadMusic()
    }

    override fun onStart() {
        super.onStart()
        val f = MediaController.Builder(this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        future = f
        f.addListener({
            if (future !== f || isFinishing || isDestroyed) return@addListener
            runCatching { f.get() }
                .onSuccess { controller = it; it.addListener(listener); refresh() }
                .onFailure { status.text = "Playback unavailable: ${it.message}" }
        }, java.util.concurrent.Executor { Handler(Looper.getMainLooper()).post(it) })
    }

    private fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun refresh() {
        val c = controller ?: return
        status.text = "${c.mediaMetadata.title ?: "Choose a track"}  •  ${if (c.isPlaying) "Playing" else "Paused"}"
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { toggle(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { controller?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { controller?.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { controller?.seekToNextMediaItem(); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { controller?.seekToPreviousMediaItem(); return true }
        }
        return super.onKeyDown(code, event)
    }

    override fun onStop() {
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        super.onStop()
    }
}
