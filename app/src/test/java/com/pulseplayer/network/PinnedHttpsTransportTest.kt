package com.pulseplayer.network

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class PinnedHttpsTransportTest {
    @Test fun rejectsNonHttpsBeforeDns() {
        val transport = PinnedHttpsTransport { error("DNS should not be called") }
        try { transport.open("http://example.com/video.mp4"); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun rejectsMixedPublicAndPrivateDns() {
        val transport = PinnedHttpsTransport {
            listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"))
        }
        try { transport.open("https://example.com/video.mp4"); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun rejectsLocalOnlyDns() {
        val transport = PinnedHttpsTransport { listOf(InetAddress.getByName("192.168.0.1")) }
        try { transport.open("https://example.com/video.mp4"); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
    }
}
