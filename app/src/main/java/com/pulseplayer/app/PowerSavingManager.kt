package com.pulseplayer.app

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager

/** Controls optional UI work only; never stops audio playback. */
class PowerSavingManager(context: Context) {
    enum class Mode { AUTO, ECO, ULTRA, OFF }
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("power_saving", Context.MODE_PRIVATE)

    var mode: Mode
        get() = runCatching { Mode.valueOf(prefs.getString("mode", Mode.AUTO.name) ?: Mode.AUTO.name) }
            .getOrDefault(Mode.AUTO)
        set(value) { prefs.edit().putString("mode", value.name).apply() }

    fun effectiveMode(): Mode {
        if (mode != Mode.AUTO) return mode
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return when {
            power?.isPowerSaveMode == true || level in 0..15 -> Mode.ULTRA
            level in 16..35 -> Mode.ECO
            else -> Mode.OFF
        }
    }

    fun refreshIntervalMillis(isPlaying: Boolean): Long = when (effectiveMode()) {
        Mode.ULTRA -> if (isPlaying) 3_000L else 6_000L
        Mode.ECO -> if (isPlaying) 1_500L else 4_000L
        Mode.OFF, Mode.AUTO -> if (isPlaying) 750L else 2_000L
    }

    fun allowSpectrum(): Boolean = effectiveMode() != Mode.ULTRA
}
