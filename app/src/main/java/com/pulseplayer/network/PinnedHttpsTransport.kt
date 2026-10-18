package com.pulseplayer.network

import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Transport with per-request DNS pinning. TLS verification and SNI retain the URL hostname.
 * Redirects and proxy routing are disabled to prevent destination changes.
 *
 * Callers must close returned Response. This transport is not yet wired to downloads.
 */
class PinnedHttpsTransport(private val resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }) {
    fun open(url: String): Response {
        val uri = PublicEndpointPolicy.validateUrl(url)
        val host = requireNotNull(uri.host)
        val resolved = resolver(host)
        val pinned = PublicEndpointPolicy.validateResolved(resolved.toTypedArray())
        val client = OkHttpClient.Builder()
            .dns(Dns { requested ->
                if (!requested.equals(host, ignoreCase = true)) throw IOException("Unexpected DNS hostname")
                listOf(pinned)
            })
            .proxy(java.net.Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        if (response.code in 300..399) {
            response.close()
            throw IOException("Redirect rejected")
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("HTTP $code")
        }
        return response
    }
}
