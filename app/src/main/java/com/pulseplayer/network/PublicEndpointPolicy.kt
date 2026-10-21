package com.pulseplayer.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Conservative IP policy for untrusted media URLs.
 * This is a pure validation component, NOT a pinned HTTPS transport.
 */
object PublicEndpointPolicy {
    fun isAllowed(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress) return false
        val bytes = address.address
        if (address is Inet4Address) {
            val a = bytes[0].toInt() and 255
            val b = bytes[1].toInt() and 255
            // Reject special-use IPv4 networks conservatively.
            if (a == 0 || a == 10 || a == 127 || a >= 224 ||
                a == 100 && b in 64..127 ||
                a == 169 && b == 254 ||
                a == 172 && b in 16..31 ||
                a == 192 && (b == 0 || b == 168) ||
                a == 198 && b in 18..19 ||
                a == 192 && b == 0 ||
                a == 192 && b == 0 && (bytes[2].toInt() and 255) == 2 ||
                a == 198 && b == 51 ||
                a == 203 && b == 0) return false
            return true
        }
        if (address is Inet6Address) {
            // Only globally scoped IPv6 candidates; block ULA, mapped and transition ranges.
            val first = bytes[0].toInt() and 255
            val second = bytes[1].toInt() and 255
            if (first and 0xfe == 0xfc) return false
            if (first != 0x20 && first != 0x26 && first != 0x2a && first != 0x24) return false
            if (first == 0x20 && second == 0x01) return false // special-use 2001::/16
            return true
        }
        return false
    }

    fun validateUrl(url: String): URI {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "HTTPS required" }
        require(uri.rawUserInfo == null && uri.host != null && uri.port in listOf(-1, 443)) { "Invalid authority" }
        return uri
    }

    fun validateResolved(addresses: Array<InetAddress>): InetAddress {
        require(addresses.isNotEmpty() && addresses.all(::isAllowed)) { "Unsafe DNS response" }
        return addresses.first()
    }
}
