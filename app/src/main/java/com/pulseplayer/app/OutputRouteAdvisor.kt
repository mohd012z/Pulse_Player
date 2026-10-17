package com.pulseplayer.app

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Conservative route-aware EQ recommendations. API 33+ reports anticipated
 * *media* routes, not a guaranteed physical measurement of the AudioTrack output.
 * Older devices can report connected outputs only. Never silently force routing.
 */
internal object OutputRouteAdvisor {
    data class Advice(
        val devices: List<AudioDeviceInfo>,
        val predictedMediaRoute: Boolean,
        val profile: String
    )

    fun inspect(manager: AudioManager): Advice {
        val predicted = if (Build.VERSION.SDK_INT >= 33) runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            manager.getAudioDevicesForAttributes(attrs)
        }.getOrNull().orEmpty() else emptyList()
        val isPredicted = predicted.isNotEmpty()
        val devices = if (isPredicted) predicted.toList()
            else manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val types = devices.map { it.type }.toSet()
        val headphoneTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )
        // When several outputs are possible, avoid choosing a bass-enhancing curve.
        val profile = when {
            types.isEmpty() -> "Flat"
            types.all { it in headphoneTypes } -> "Stereo Clarity"
            types == setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) -> "Balanced Speaker"
            else -> "Flat"
        }
        return Advice(devices, isPredicted, profile)
    }
}
