package com.pulseplayer.network

import org.junit.Assert.*
import org.junit.Test

class RedirectPolicyTest {
    @Test fun relativeRedirectStaysHttps() {
        assertEquals("https://example.com/media/video.mp4",
            RedirectPolicy.resolve("https://example.com/index", "/media/video.mp4", 0))
    }
    @Test fun rejectsDowngrade() {
        assertRejected { RedirectPolicy.resolve("https://example.com/a", "http://example.com/b", 0) }
    }
    @Test fun rejectsCredentials() {
        assertRejected { RedirectPolicy.resolve("https://example.com/a", "https://user:secret@example.com/b", 0) }
    }
    @Test fun rejectsRedirectLimit() {
        assertRejected { RedirectPolicy.resolve("https://example.com/a", "/b", 5) }
    }
    @Test fun rejectsNonHttpsSchemes() {
        assertRejected { RedirectPolicy.resolve("https://example.com/a", "file:///etc/passwd", 0) }
    }
    private fun assertRejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
    }
}
