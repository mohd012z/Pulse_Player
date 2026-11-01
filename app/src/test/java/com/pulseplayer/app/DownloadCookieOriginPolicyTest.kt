package com.pulseplayer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCookieOriginPolicyTest {
    @Test fun permitsSameHttpsOrigin() {
        assertTrue(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", "https://media.example/file.mp4"))
    }

    @Test fun treatsExplicitDefaultPortAsSameOrigin() {
        assertTrue(DownloadCookieOriginPolicy.canForwardCookies(
            "https://MEDIA.EXAMPLE:443/view", "https://media.example/file.mp4"))
    }

    @Test fun deniesDifferentSourcePort() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example:8443/view", "https://media.example/file.mp4"))
    }

    @Test fun deniesDifferentDownloadPort() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", "https://media.example:8443/file.mp4"))
    }

    @Test fun deniesDifferentHostAndSubdomain() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", "https://cdn.media.example/file.mp4"))
    }

    @Test fun deniesDowngradeToHttp() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", "http://media.example/file.mp4"))
    }

    @Test fun deniesHttpPage() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "http://media.example/view", "https://media.example/file.mp4"))
    }

    @Test fun deniesEmbeddedCredentials() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://user:pass@media.example/view", "https://media.example/file.mp4"))
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", "https://user@media.example/file.mp4"))
    }

    @Test fun deniesInvalidPorts() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", "https://media.example:0/file.mp4"))
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", "https://media.example:99999/file.mp4"))
    }

    @Test fun deniesMalformedAndMissingUrls() {
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "not a URL", "https://media.example/file.mp4"))
        assertFalse(DownloadCookieOriginPolicy.canForwardCookies(
            "https://media.example/view", ""))
    }
}
