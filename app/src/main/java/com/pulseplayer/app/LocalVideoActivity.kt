package com.pulseplayer.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Separate video session: never replaces PlaybackService's music queue or EQ session. */
class LocalVideoActivity : Activity() {
    private var player: ExoPlayer? = null
    private var view: PlayerView? = null
    private val requestVideo = 8122
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(TextView(this).apply {
            text = "Local video · separate player (music queue preserved)"
            textSize = 17f
        })
        layout.addView(Button(this).apply {
            text = "Choose a video"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "video/*"
                }, requestVideo)
            }
        })
        view = PlayerView(this).apply { useController = true }
        layout.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
    }
    @Deprecated("Activity result API maintained for compatibility with existing Activity code")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestVideo || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (uri.scheme != "content") return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        player?.release()
        player = ExoPlayer.Builder(this).build().also {
            view?.player = it
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.playWhenReady = true
        }
    }
    override fun onStop() {
        player?.pause()
        super.onStop()
    }
    override fun onDestroy() {
        view?.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
