package com.pulseplayer.network

import java.net.URI

/** Strict redirect target resolution; network code must separately pin and validate DNS per hop. */
object RedirectPolicy {
    const val MAX_REDIRECTS = 5

    fun resolve(currentUrl: String, location: String, redirectsFollowed: Int): String {
        require(redirectsFollowed in 0 until MAX_REDIRECTS) { "Redirect limit reached" }
        require(location.isNotBlank()) { "Missing Location" }
        val base = PublicEndpointPolicy.validateUrl(currentUrl)
        val next = base.resolve(location)
        require(next.scheme.equals("https", ignoreCase = true)) { "HTTPS redirect required" }
        require(next.rawUserInfo == null && next.rawFragment == null) { "Unsupported redirect authority or fragment" }
        PublicEndpointPolicy.validateUrl(next.toString())
        return next.toString()
    }
}
