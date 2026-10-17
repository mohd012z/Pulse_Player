package com.pulseplayer.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build

/**
 * Queries the Android spatial audio engine; this app must not claim Dolby
 * Atmos decoding or enable privileged platform controls behind the user's back.
 */
internal object PlatformSpatialAudio {
    data class Status(
        val supported: Boolean,
        val available: Boolean,
        val enabled: Boolean,
        val stereoEligible: Boolean,
        val surround51Eligible: Boolean,
        val headTrackerAvailable: Boolean
    )

    fun query(manager: AudioManager): Status? {
        if (Build.VERSION.SDK_INT < 32) return null
        val spatializer = manager.spatializer
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        fun eligible(mask: Int) = runCatching {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000)
                .setChannelMask(mask)
                .build()
            spatializer.canBeSpatialized(attrs, format)
        }.getOrDefault(false)
        return Status(
            supported = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE,
            available = spatializer.isAvailable,
            enabled = spatializer.isEnabled,
            stereoEligible = eligible(AudioFormat.CHANNEL_OUT_STEREO),
            surround51Eligible = eligible(AudioFormat.CHANNEL_OUT_5POINT1),
            headTrackerAvailable = runCatching { spatializer.isHeadTrackerAvailable }.getOrDefault(false)
        )
    }
}
