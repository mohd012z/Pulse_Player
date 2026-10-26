package com.pulseplayer.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
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
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 14)
            setBackgroundColor(this@TvActivity.background)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val brand = label("♫  PULSE PLAYER", 27f, accent).apply {
            setTypeface(null, Typeface.BOLD)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, 64, 1f))
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
        root.addView(search, LinearLayout.LayoutParams(-1, 56).apply { bottomMargin = 14 })

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = label("Your Music", 24f).apply { setTypeface(null, Typeface.BOLD) }
        left.addView(title, LinearLayout.LayoutParams(-1, 52))
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
        left.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(left, LinearLayout.LayoutParams(0, -1, 3f).apply { marginEnd = 18 })

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = surface()
        }
        right.addView(label("MOVIE MELAYU  •  YOUTUBE", 21f, accent).apply {
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, 54))
        right.addView(label("Find Malay films from YouTube's own search results. Availability depends on the uploader and region.", 16f, Color.LTGRAY),
            LinearLayout.LayoutParams(-1, 90))
        val genres = listOf("Filem Melayu penuh", "Drama Melayu", "Seram Melayu", "Komedi Melayu", "Aksi Melayu", "Filem Melayu klasik")
        genres.forEach { query ->
            val b = button("▶  $query") { openYouTubeSearch(query) }
            right.addView(b, LinearLayout.LayoutParams(-1, 55).apply { bottomMargin = 9 })
        }
        content.addView(right, LinearLayout.LayoutParams(0, -1, 2f))
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
        root.addView(player, LinearLayout.LayoutParams(-1, 64).apply { topMargin = 12 })

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(nav, "♫ Music") { currentTab = "Music"; loadMusic() }
        addButton(nav, "▣ Radio") { currentTab = "Radio"; loadRadio() }
        addButton(nav, "▶ YouTube") { currentTab = "YouTube"; openYouTubeSearch("muzik Malaysia") }
        addButton(nav, "▤ Movie Melayu") {
            currentTab = "Movie Melayu"
            title.text = "Movie Melayu • Open YouTube results"
            openYouTubeSearch("filem Melayu penuh rasmi")
        }
        addButton(nav, "☰ More") { startActivity(Intent(this, MainActivity::class.java)) }
        root.addView(nav, LinearLayout.LayoutParams(-1, 60).apply { topMargin = 8 })
        setContentView(root)
        nav.getChildAt(0).requestFocus()
        loadRadio()
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
        results.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            items.map { it.mediaMetadata.title?.toString() ?: "Untitled" })
    }

    private fun filterLocal(query: String) {
        if (query.isBlank()) {
            if (currentTab == "Radio") loadRadio() else loadMusic()
            return
        }
        val filtered = entries.filter {
            it.mediaMetadata.title?.toString()?.contains(query, ignoreCase = true) == true ||
                it.mediaMetadata.artist?.toString()?.contains(query, ignoreCase = true) == true
        }
        showItems(filtered)
    }

    private fun loadRadio() {
        generation++
        currentTab = "Radio"
        title.text = "Radio Stations"
        showItems(RadioRepository(this).stations().map { it.toMediaItem() })
    }

    private fun loadMusic() {
        currentTab = "Music"
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
                if (!isFinishing && !isDestroyed && generation == request && currentTab == "Music") {
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
