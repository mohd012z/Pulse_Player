package com.pulseplayer.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Separate video session: never replaces PlaybackService's music queue or EQ session. */
class LocalVideoActivity : Activity() {
    private var player: ExoPlayer? = null
    private var view: PlayerView? = null
    private val requestVideo = 8122
    private var playingUri: Uri? = null
    private var playingMime: String? = null

    companion object {
        private const val EXTRA_URI = "movie_uri"
        private const val EXTRA_MIME = "movie_mime"
        private const val EXTRA_POS = "movie_pos"
    }

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
        val urlInput = EditText(this).apply {
            hint = "Paste authorized HTTPS video URL (.mp4 / .m3u8 / .mpd)"
            setSingleLine(true)
        }
        layout.addView(urlInput)
        layout.addView(Button(this).apply {
            text = "Play online URL"
            setOnClickListener {
                val url = urlInput.text.toString().trim()
                when (VideoUrlRouter.classify(url)) {
                    VideoUrlRouter.Kind.HLS -> play(Uri.parse(url), MimeTypes.APPLICATION_M3U8)
                    VideoUrlRouter.Kind.DASH -> play(Uri.parse(url), MimeTypes.APPLICATION_MPD)
                    VideoUrlRouter.Kind.VIDEO -> play(Uri.parse(url), null)
                    VideoUrlRouter.Kind.YOUTUBE -> startActivity(
                        Intent(this@LocalVideoActivity, ProviderBrowserActivity::class.java)
                            .putExtra(ProviderBrowserActivity.EXTRA_PROVIDER, "YouTube"))
                    VideoUrlRouter.Kind.WEBPAGE -> Toast.makeText(this@LocalVideoActivity,
                        "Webpage is not a direct video stream", Toast.LENGTH_LONG).show()
                    VideoUrlRouter.Kind.INVALID -> Toast.makeText(this@LocalVideoActivity,
                        "Use a valid public HTTPS media URL", Toast.LENGTH_LONG).show()
                }
            }
        })
        view = PlayerView(this).apply { useController = true }
        layout.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        restorePlayingMovie(savedInstanceState)
    }

    private fun restorePlayingMovie(savedInstanceState: Bundle?) {
        val saved = savedInstanceState ?: return
        if (!saved.containsKey(EXTRA_URI)) return
        val uri = runCatching { Uri.parse(saved.getString(EXTRA_URI) ?: return) }.getOrNull() ?: return
        val mime = saved.getString(EXTRA_MIME)
        val pos = saved.getLong(EXTRA_POS, 0L)
        // Rotation (configChanges) normally keeps the same player instance; this is the
        // safety net for process-death or unexpected recreation.
        play(uri, mime, pos)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val p = player
        if (p != null && playingUri != null) {
            outState.putString(EXTRA_URI, playingUri.toString())
            outState.putString(EXTRA_MIME, playingMime)
            outState.putLong(EXTRA_POS, p.currentPosition.coerceAtLeast(0L))
        }
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Activity result API maintained for compatibility with existing Activity code")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestVideo || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (uri.scheme != "content") return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } // Some document providers only grant a temporary read permission.
        play(uri, null)
    }
    private fun play(uri: Uri, mimeType: String?, startPositionMs: Long = 0L) {
        playingUri = uri
        playingMime = mimeType
        player?.release()
        player = ExoPlayer.Builder(this).build().also {
            view?.player = it
            it.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@LocalVideoActivity,
                        "Playback failed: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                }
            })
            it.setMediaItem(MediaItem.Builder().setUri(uri).apply { if (mimeType != null) setMimeType(mimeType) }.build())
            it.prepare()
            if (startPositionMs > 0L) it.seekTo(startPositionMs)
            it.playWhenReady = true
        }
    }
    override fun onStop() {
        // Pause only when the user leaves the video screen — not during rotation,
        // where the activity briefly passes through STOP while staying visible.
        if (!isChangingConfigurations()) player?.pause()
        super.onStop()
    }
    override fun onDestroy() {
        view?.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
