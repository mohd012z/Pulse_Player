package com.pulseplayer.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Device-aware defaults. User preference overrides automatic classification. */
object DevicePerformanceProfile {
    enum class Tier { LITE, BALANCED, HIGH }
    fun detect(context: Context): Tier {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val ramMb = ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem / (1024L * 1024L)
        return when {
            manager.isLowRamDevice || ramMb < 3000L -> Tier.LITE
            ramMb < 6000L -> Tier.BALANCED
            else -> Tier.HIGH
        }
    }
    fun selected(context: Context): Tier {
        val prefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        return runCatching { Tier.valueOf(prefs.getString("performance_tier", "") ?: "") }
            .getOrDefault(detect(context))
    }
    fun enableNativeDspByDefault(context: Context): Boolean = false
    fun recommendedSpectrumFps(context: Context): Int = when(selected(context)) {
        Tier.LITE -> 15
        Tier.BALANCED -> 30
        Tier.HIGH -> 60
    }
    fun description(context: Context): String = when(selected(context)) {
        Tier.LITE -> "Lite: reduced visual workload, DSP optional"
        Tier.BALANCED -> "Balanced: standard visual workload"
        Tier.HIGH -> "High: smoother visualization; DSP optional"
    }
}
