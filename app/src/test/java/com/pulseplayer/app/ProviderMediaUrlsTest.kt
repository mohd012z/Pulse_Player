package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class ProviderMediaUrlsTest {
    @Test fun youtubeVideoAndPlaylist() {
        val provider = ProviderMediaUrls.Provider.YOUTUBE
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ?playsinline=1&rel=0", ProviderMediaUrls.resolve(provider, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")?.url)
        assertEquals(ProviderMediaUrls.Mode.YOUTUBE_EMBED, ProviderMediaUrls.resolve(provider, "https://youtu.be/dQw4w9WgXcQ")?.mode)
        assertEquals(ProviderMediaUrls.Mode.YOUTUBE_EMBED, ProviderMediaUrls.resolve(provider, "https://music.youtube.com/playlist?list=PL1234567890AB")?.mode)
    }
    @Test fun spotifyOfficialEmbeds() {
        val p = ProviderMediaUrls.Provider.SPOTIFY
        val id = "4cOdK2wGLETKBW3PvgPWqT"
        assertEquals("https://open.spotify.com/embed/track/$id", ProviderMediaUrls.resolve(p,"https://open.spotify.com/track/$id?si=x")?.url)
        assertEquals("https://open.spotify.com/embed/playlist/$id", ProviderMediaUrls.resolve(p,"https://open.spotify.com/intl-en/playlist/$id")?.url)
    }
    @Test fun jooxOnlyOfficialHost() {
        val p = ProviderMediaUrls.Provider.JOOX
        assertNotNull(ProviderMediaUrls.resolve(p, "https://www.joox.com/"))
        assertNull(ProviderMediaUrls.resolve(p, "https://joox.com.evil.test/song"))
    }
    @Test fun rejectsUnsafeOrUnsupportedInput() {
        for (p in ProviderMediaUrls.Provider.values()) {
            assertNull(ProviderMediaUrls.resolve(p, "http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
            assertNull(ProviderMediaUrls.resolve(p, "javascript:alert(1)"))
            assertNull(ProviderMediaUrls.resolve(p, "https://user:pass@www.youtube.com/watch?v=dQw4w9WgXcQ"))
            assertNull(ProviderMediaUrls.resolve(p, "https://www.youtube.com:8443/watch?v=dQw4w9WgXcQ"))
        }
        assertNull(ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.SPOTIFY, "https://open.spotify.com/evil/something"))
        assertNull(ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.YOUTUBE, "https://www.youtube.com/watch?v=invalid"))
    }
}