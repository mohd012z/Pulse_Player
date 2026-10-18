package com.pulseplayer.network

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class PublicEndpointPolicyTest {
    @Test fun rejectsPrivateAndLoopback() {
        listOf("127.0.0.1", "10.1.2.3", "172.16.1.1", "192.168.1.1",
            "169.254.1.2", "100.64.0.1", "0.0.0.0", "::1", "fc00::1", "fe80::1")
            .forEach { assertFalse(it, PublicEndpointPolicy.isAllowed(InetAddress.getByName(it))) }
    }
    @Test fun acceptsOrdinaryPublicIpv4() {
        assertTrue(PublicEndpointPolicy.isAllowed(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun rejectsMixedDnsResults() {
        try {
            PublicEndpointPolicy.validateResolved(arrayOf(
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")))
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun rejectsInsecureUrls() {
        try { PublicEndpointPolicy.validateUrl("http://example.com/video.mp4"); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
    }
}
