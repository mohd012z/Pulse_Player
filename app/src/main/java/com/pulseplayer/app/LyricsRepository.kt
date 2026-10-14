package com.pulseplayer.app

/** Parses user-supplied synchronized .lrc text; does not transcribe or download lyrics. */
object LyricsRepository {
    data class Line(val millis: Long, val text: String)
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    private val offset = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)

    fun parse(input: String): List<Line> {
        require(input.length <= 524_288) { "LRC file exceeds 512 KiB" }
        val shift = offset.find(input)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-60_000L, 60_000L) ?: 0L
        val output = ArrayList<Line>()
        for (line in input.lineSequence()) {
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) continue
            val value = timestamp.replace(line, "").trim().take(512)
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                if (seconds !in 0..59) continue
                val fraction = match.groupValues[3]
                val fractionMs = when(fraction.length) {
                    1 -> fraction.toInt() * 100L
                    2 -> fraction.toInt() * 10L
                    3 -> fraction.toLong()
                    else -> 0L
                }
                val ms = minutes * 60_000L + seconds * 1000L + fractionMs + shift
                output += Line(ms.coerceAtLeast(0L), value)
            }
        }
        return output.distinctBy { it.millis to it.text }.sortedBy { it.millis }.take(5000)
    }

    fun at(lines: List<Line>, positionMs: Long): Int {
        var low = 0
        var high = lines.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (lines[mid].millis <= positionMs) low = mid + 1 else high = mid
        }
        return low - 1
    }
}