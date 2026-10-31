package com.pulseplayer.app

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Owns a muted secondary decoder for the next local track.
 * Never requests audio focus; primary MediaSession player remains authoritative.
 * All methods must be called on the application main thread.
 */
@UnstableApi
class CrossfadePreloader(private val context: Context) {
    private var secondary: ExoPlayer? = null
    private var preparedItem: MediaItem? = null
    private var generation = 0L

    val isPrepared: Boolean
        get() = secondary?.playbackState == Player.STATE_READY && preparedItem != null

    fun prepare(item: MediaItem): Long {
        cancel()
        val token = generation
        val uri = item.localConfiguration?.uri
        if (uri == null || (uri.scheme != "content" && uri.scheme != "file")) return token
        val player = ExoPlayer.Builder(context).build()
        secondary = player
        preparedItem = item
        player.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false
        )
        player.volume = 0f
        player.playWhenReady = false
        player.setMediaItem(item)
        player.prepare()
        return token
    }

    fun playerIfReady(token: Long): ExoPlayer? =
        if (token == generation && isPrepared) secondary else null

    fun cancel() {
        generation++
        preparedItem = null
        secondary?.release()
        secondary = null
    }

    fun release() = cancel()
}
