package com.pulseplayer.app.aipulse

import java.io.InputStream
import java.io.OutputStream

/** Streams user-selected media with an enforced upper byte limit. */
internal object AiPulseBoundedCopy {
    fun copy(input: InputStream, output: OutputStream, limit: Long = 64L * 1024 * 1024): Long {
        require(limit > 0L)
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (count == 0) continue
            require(count <= limit - total) { "Selected file exceeds import size limit" }
            output.write(buffer, 0, count)
            total += count
        }
        return total
    }
}
