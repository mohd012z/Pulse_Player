package com.pulseplayer.app

/** Pure browser rules that can be regression-tested without an Android device. */
internal object BrowserPolicy {
    private val trackers = setOf("google-analytics.com", "analytics.google.com",
        "stats.g.doubleclick.net", "connect.facebook.net", "bat.bing.com",
        "static.hotjar.com", "script.hotjar.com", "cdn.segment.com")
    fun matchesDomain(host: String, domain: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        return normalized == domain || normalized.endsWith(".$domain")
    }
    fun blockTracker(host: String, isMainFrame: Boolean, enabled: Boolean): Boolean =
        enabled && !isMainFrame && trackers.any { matchesDomain(host, it) }

    fun allowWebNavigation(scheme: String?, host: String?): Boolean =
        scheme.equals("https", true) && !host.isNullOrBlank()

    fun allowSpotifyHandoff(scheme: String?, host: String?): Boolean =
        scheme.equals("https", true) && host != null && matchesDomain(host, "spotify.com")
}
