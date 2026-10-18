package com.pulseplayer.app

import java.util.Locale

/** Conservative notification-title filter: fallback is always app-only. */
internal object NotificationSenderPolicy {
    fun safeTitle(value: String?): String? {
        val title = value?.trim() ?: return null
        if (title.isEmpty() || title.length > 60) return null
        if (title.contains(':') || title.contains("http", ignoreCase = true)) return null
        if (title.any { it.isISOControl() }) return null
        if (title.lowercase(Locale.ROOT).contains("verification code")) return null
        return title
    }
}
