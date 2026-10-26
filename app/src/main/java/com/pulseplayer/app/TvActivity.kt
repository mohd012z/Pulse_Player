package com.pulseplayer.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Remote-first entry point; playback remains owned by the existing service. */
class TvActivity : Activity() {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var status: TextView
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
            setBackgroundColor(Color.rgb(16, 20, 31))
        }
        fun button(title: String, action: () -> Unit): Button = Button(this).apply {
            text = title
            textSize = 22f
            isFocusable = true
            setOnClickListener { action() }
            root.addView(this, LinearLayout.LayoutParams(520, 76).apply { bottomMargin = 12 })
        }
        root.addView(TextView(this).apply {
            text = "PULSE PLAYER · TV"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            text = "Connecting to playback…"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 28)
        }
        root.addView(status)
        button("Play / Pause") { toggle() }
        button("Previous track") { controller?.seekToPreviousMediaItem() }
        button("Next track") { controller?.seekToNextMediaItem() }
        button("Open full music library and radio") { startActivity(Intent(this, MainActivity::class.java)) }
        setContentView(root)
        root.findFocus()?.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        val f = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        future = f
        f.addListener({
            if (future !== f || isFinishing || isDestroyed) return@addListener
            runCatching { f.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                refresh()
            }.onFailure { status.text = "Playback connection failed: ${it.message}" }
        }, mainExecutor)
    }

    private fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun refresh() {
        val c = controller ?: return
        status.text = "${c.mediaMetadata.title ?: "No track selected"} · ${if (c.isPlaying) "Playing" else "Paused"}"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { toggle(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { controller?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { controller?.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { controller?.seekToNextMediaItem(); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { controller?.seekToPreviousMediaItem(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        super.onStop()
    }
}
