package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class BrowserPolicyTest {
    @Test fun blocksKnownTrackersOnlyWhenEnabled() {
        assertTrue(BrowserPolicy.blockTracker("stats.g.doubleclick.net", false, true))
        assertTrue(BrowserPolicy.blockTracker("sub.connect.facebook.net", false, true))
        assertFalse(BrowserPolicy.blockTracker("stats.g.doubleclick.net", true, true))
        assertFalse(BrowserPolicy.blockTracker("stats.g.doubleclick.net", false, false))
        assertFalse(BrowserPolicy.blockTracker("music.youtube.com", false, true))
    }
    @Test fun domainMatchingResistsSuffixSpoofing() {
        assertTrue(BrowserPolicy.matchesDomain("open.spotify.com", "spotify.com"))
        assertTrue(BrowserPolicy.matchesDomain("SPOTIFY.COM", "spotify.com"))
        assertFalse(BrowserPolicy.matchesDomain("evilspotify.com", "spotify.com"))
        assertFalse(BrowserPolicy.matchesDomain("spotify.com.evil.org", "spotify.com"))
    }
    @Test fun navigationRequiresHttps() {
        assertTrue(BrowserPolicy.allowWebNavigation("https", "youtube.com"))
        assertFalse(BrowserPolicy.allowWebNavigation("javascript", "youtube.com"))
        assertFalse(BrowserPolicy.allowWebNavigation("file", ""))
        assertFalse(BrowserPolicy.allowWebNavigation("http", "youtube.com"))
    }
    @Test fun spotifyHandoffOnlyAcceptsSpotifyHttps() {
        assertTrue(BrowserPolicy.allowSpotifyHandoff("https", "open.spotify.com"))
        assertFalse(BrowserPolicy.allowSpotifyHandoff("https", "spotify.com.evil.org"))
        assertFalse(BrowserPolicy.allowSpotifyHandoff("http", "spotify.com"))
    }
}
