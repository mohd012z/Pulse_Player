package com.pulseplayer.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** One service/player/session owns all playback and audio effects, even without an Activity. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    companion object {
        const val CMD_SET_PRESET = "com.pulseplayer.command.PRESET"
        const val CMD_SET_BASS = "com.pulseplayer.command.BASS"
        const val CMD_SET_CUSTOM = "com.pulseplayer.command.CUSTOM_EQ"
        const val CMD_SET_SLEEP = "com.pulseplayer.command.SLEEP"
        const val CMD_STATUS = "com.pulseplayer.command.STATUS"
        const val ARG_PRESET = "preset"
        const val ARG_BASS = "bass"
        const val ARG_CUSTOM_BASS = "custom_bass"
        const val ARG_CUSTOM_MID = "custom_mid"
        const val ARG_CUSTOM_TREBLE = "custom_treble"
        const val ARG_AUDIO_SESSION_ID = "audio_session_id"
        const val ARG_MINUTES = "minutes"
        const val ARG_EQ_SUPPORTED = "eq_supported"
        const val ARG_BASS_SUPPORTED = "bass_supported"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val effects = AudioEffectsManager()
    private val preferences by lazy { getSharedPreferences("player_state", MODE_PRIVATE) }
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private var sleepTask: Runnable? = null
    private var sleepUntilMillis: Long = 0L

    private val checkpoint = object : Runnable {
        override fun run() {
            persistState()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        effects.setPreset(preferences.getString("preset", "Flat") ?: "Flat")
        effects.setCustom(
            preferences.getInt("custom_bass", 0),
            preferences.getInt("custom_mid", 0),
            preferences.getInt("custom_treble", 0)
        )
        effects.setPreset(preferences.getString("preset", "Flat") ?: "Flat")
        effects.setBass(preferences.getInt("bass", 0))
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setAudioAttributes(attrs, true)
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        effects.attach(audioSessionId)
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        persistState()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        persistState()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("PulsePlayer", "Playback failed: ${error.errorCodeName}", error)
                        persistState()
                    }
                })
            }
        restoreState()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(sessionCallback)
            .build()
        handler.postDelayed(checkpoint, 15_000L)
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            // Local UI alone may change service DSP/timers. External media controls retain
            // normal transport functionality, but cannot change private settings.
            if (controller.packageName == packageName) {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(SessionCommand(CMD_SET_PRESET, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SET_BASS, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SET_CUSTOM, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SET_SLEEP, Bundle.EMPTY))
                    .add(SessionCommand(CMD_STATUS, Bundle.EMPTY))
                    .build()
                builder.setAvailableSessionCommands(commands)
            }
            return Futures.immediateFuture(builder.build())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (controller.packageName != packageName) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
            }
            val success = when (customCommand.customAction) {
                CMD_SET_PRESET -> {
                    val name = args.getString(ARG_PRESET) ?: ""
                    if (name !in SoundPresets.names) false else {
                        val applied = effects.setPreset(name)
                        preferences.edit().putString("preset", name).apply()
                        applied
                    }
                }
                CMD_SET_BASS -> {
                    val strength = args.getInt(ARG_BASS, -1)
                    if (strength !in 0..1000) false else {
                        val applied = effects.setBass(strength)
                        preferences.edit().putInt("bass", strength).apply()
                        applied
                    }
                }
                CMD_SET_CUSTOM -> {
                    val low = args.getInt(ARG_CUSTOM_BASS, Int.MIN_VALUE)
                    val mid = args.getInt(ARG_CUSTOM_MID, Int.MIN_VALUE)
                    val high = args.getInt(ARG_CUSTOM_TREBLE, Int.MIN_VALUE)
                    if (listOf(low, mid, high).any { it !in -6..6 }) false else {
                        val applied = effects.setCustom(low, mid, high)
                        preferences.edit()
                            .putInt("custom_bass", low)
                            .putInt("custom_mid", mid)
                            .putInt("custom_treble", high)
                            .putString("preset", "Custom")
                            .apply()
                        applied
                    }
                }
                CMD_SET_SLEEP -> {
                    val minutes = args.getInt(ARG_MINUTES, -1)
                    if (minutes !in listOf(0, 15, 30, 60)) false else {
                        scheduleSleep(minutes)
                        true
                    }
                }
                CMD_STATUS -> true
                else -> false
            }
            val data = Bundle().apply {
                putBoolean(ARG_EQ_SUPPORTED, effects.hasEqualizer)
                putBoolean(ARG_BASS_SUPPORTED, effects.hasBassBoost)
                putString(ARG_PRESET, effects.preset)
                putInt(ARG_BASS, effects.bass)
                putInt(ARG_CUSTOM_BASS, effects.customBassDb)
                putInt(ARG_CUSTOM_MID, effects.customMidDb)
                putInt(ARG_CUSTOM_TREBLE, effects.customTrebleDb)
                putInt(ARG_AUDIO_SESSION_ID, player.audioSessionId)
                val left = (sleepUntilMillis - android.os.SystemClock.elapsedRealtime())
                    .coerceAtLeast(0L)
                putInt(ARG_MINUTES, if (left == 0L) 0 else ((left + 59_999L) / 60_000L).toInt())
            }
            return Futures.immediateFuture(
                SessionResult(
                    if (success) SessionResult.RESULT_SUCCESS
                    else SessionResult.RESULT_ERROR_NOT_SUPPORTED,
                    data
                )
            )
        }
    }

    private fun scheduleSleep(minutes: Int) {
        sleepTask?.let(handler::removeCallbacks)
        sleepTask = null
        sleepUntilMillis = 0L
        if (minutes == 0) return
        sleepUntilMillis = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
        val task = Runnable {
            player.pause()
            sleepUntilMillis = 0L
            sleepTask = null
            persistState()
        }
        sleepTask = task
        handler.postDelayed(task, minutes * 60_000L)
    }

    private fun persistState() {
        if (!::player.isInitialized) return
        val item = player.currentMediaItem
        val uri = item?.localConfiguration?.uri?.toString()
        preferences.edit()
            .putString("uri", uri)
            .putString("title", item?.mediaMetadata?.title?.toString())
            .putString("artist", item?.mediaMetadata?.artist?.toString())
            .putLong("position", player.currentPosition.coerceAtLeast(0L))
            .putInt("repeat", player.repeatMode)
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .putFloat("speed", player.playbackParameters.speed)
            .apply()
    }

    private fun restoreState() {
        player.repeatMode = preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
        player.shuffleModeEnabled = preferences.getBoolean("shuffle", false)
        player.setPlaybackParameters(
            PlaybackParameters(preferences.getFloat("speed", 1f).coerceIn(0.5f, 2f))
        )
        val stored = preferences.getString("uri", null) ?: return
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(preferences.getString("title", "Previous track"))
            .setArtist(preferences.getString("artist", ""))
            .build()
        val item = MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build()
        player.setMediaItem(item)
        player.seekTo(preferences.getLong("position", 0L).coerceAtLeast(0L))
        // Do not prepare on restart: remote radio URIs otherwise initiate network requests.
        player.pause() // Explicit user action prepares and starts the saved item.
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        handler.removeCallbacks(checkpoint)
        sleepTask?.let(handler::removeCallbacks)
        sleepTask = null
        persistState()
        session?.release()
        session = null
        effects.release()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}