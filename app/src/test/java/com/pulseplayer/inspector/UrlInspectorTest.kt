package com.pulseplayer.inspector

import org.junit.Assert.*
import org.junit.Test

class UrlInspectorTest {
    @Test fun watchTimestampAndTracking() {
        val result = UrlInspector.inspect("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=tracking&t=1m30s")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90", result.normalizedUrl)
        assertEquals(90L, (result.sourceType as SourceType.YouTubeWatch).timestampSeconds)
    }
    @Test fun shortLink() {
        val result = UrlInspector.inspect("https://youtu.be/jNQXAC9IVRw?t=45")
        assertEquals("https://www.youtube.com/watch?v=jNQXAC9IVRw&t=45", result.normalizedUrl)
    }
    @Test fun shortsAndLive() {
        assertTrue(UrlInspector.inspect("https://www.youtube.com/shorts/AbCdEfGhIjK").sourceType is SourceType.YouTubeShorts)
        assertTrue(UrlInspector.inspect("https://www.youtube.com/live/AbCdEfGhIjK").sourceType is SourceType.YouTubeLive)
    }
    @Test fun playlist() {
        val result = UrlInspector.inspect("https://www.youtube.com/playlist?list=PL1234567890123")
        assertEquals("PL1234567890123", (result.sourceType as SourceType.YouTubePlaylist).playlistId)
    }
    @Test fun signedQueryPreservedAndFragmentRemoved() {
        val result = UrlInspector.inspect("https://cdn.example.com/file.mp4?token=a%2Bb&expires=123#fragment")
        assertEquals("https://cdn.example.com/file.mp4?token=a%2Bb&expires=123", result.normalizedUrl)
        assertTrue(result.sourceType is SourceType.DirectMedia)
    }
    @Test fun invalidAndUnsafeInputs() {
        listOf(null, "", "  ", "http://example.com/a.mp4", "ftp://example.com/a.mp4",
            "https://youtube.com.evil.org/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=bad", "https://example.com/file.pdf",
            "https://localhost/a.mp4", "https://127.0.0.1/a.mp4", "https://192.168.1.2/a.mp4",
            "https://example.com:8080/a.mp4", "https://user:pass@example.com/a.mp4")
            .forEach { assertTrue("Expected rejection: $it", UrlInspector.inspect(it).sourceType is SourceType.Unsupported) }
    }
    @Test fun supportedDirectExtensions() {
        listOf("mp4","webm","m3u8","mpd","m4a","opus","mp3","aac").forEach {
            assertTrue(UrlInspector.inspect("https://media.example.com/a.$it").sourceType is SourceType.DirectMedia)
        }
    }
}
