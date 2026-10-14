package com.pulseplayer.app

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import kotlin.math.roundToInt

/** Service-owned optional platform effects. Never share across playback sessions. */
class AudioEffectsManager {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId = C.AUDIO_SESSION_ID_UNSET

    var preset: String = "Flat"
        private set
    var bass: Int = 0
        private set

    val hasEqualizer: Boolean get() = equalizer != null
    val hasBassBoost: Boolean get() = bassBoost != null

    fun attach(sessionId: Int) {
        if (sessionId == currentSessionId) return
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        currentSessionId = sessionId
        equalizer = runCatching { Equalizer(0, sessionId) }.onFailure {
            Log.w("PulseAudio", "Equalizer unavailable: ${it.message}")
        }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }.onFailure {
            Log.w("PulseAudio", "BassBoost unavailable: ${it.message}")
        }.getOrNull()
        setPreset(preset)
        setBass(bass)
    }

    fun setPreset(name: String): Boolean {
        if (name !in SoundPresets.names) return false
        preset = name
        val effect = equalizer ?: return false
        return runCatching {
            if (name == "Flat") {
                effect.enabled = false
            } else {
                val range = effect.bandLevelRange
                for (index in 0 until effect.numberOfBands.toInt()) {
                    val centerHz = effect.getCenterFreq(index.toShort()) / 1000.0
                    val gain = (SoundPresets.gainDb(name, centerHz) * 100.0)
                        .roundToInt()
                        .coerceIn(range[0].toInt(), range[1].toInt())
                    effect.setBandLevel(index.toShort(), gain.toShort())
                }
                effect.enabled = true
            }
            true
        }.onFailure { Log.w("PulseAudio", "EQ preset failed", it) }.getOrDefault(false)
    }

    fun setBass(level: Int): Boolean {
        bass = level.coerceIn(0, 1000)
        val effect = bassBoost ?: return false
        return runCatching {
            if (bass == 0) {
                effect.enabled = false
            } else {
                effect.setStrength(bass.toShort())
                effect.enabled = true
            }
            true
        }.onFailure { Log.w("PulseAudio", "BassBoost failed", it) }.getOrDefault(false)
    }

    fun release() {
        runCatching { equalizer?.enabled = false }
        runCatching { bassBoost?.enabled = false }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        currentSessionId = C.AUDIO_SESSION_ID_UNSET
    }
}