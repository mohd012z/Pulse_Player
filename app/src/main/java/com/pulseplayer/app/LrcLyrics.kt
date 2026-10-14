package com.pulseplayer.app

/** Reads locally supplied LRC lyrics. No lyric scraping or copyrighted database access. */
object LrcLyrics {
    data class Line(val atMs: Long, val text: String)
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?]")

    fun parse(text: String): List<Line> {
        require(text.length <= 2_000_000) { "Lyrics file exceeds 2 MB" }
        val result = mutableListOf<Line>()
        for (line in text.lineSequence()) {
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) continue
            val words = line.substring(matches.last().range.last + 1).trim().take(400)
            for (match in matches) {
                val minute = match.groupValues[1].toLongOrNull() ?: continue
                val second = match.groupValues[2].toLongOrNull() ?: continue
                if (second >= 60) continue
                val fraction = match.groupValues[3].let { if (it.isEmpty()) 0L else (it + "00").take(3).toLongOrNull() ?: 0L }
                result += Line((minute * 60 + second) * 1000 + fraction, words)
            }
        }
        return result.sortedBy { it.atMs }.take(20_000)
    }

    fun at(lines: List<Line>, positionMs: Long): String? {
        if (lines.isEmpty() || positionMs < lines.first().atMs) return null
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].atMs <= positionMs) low = mid + 1 else high = mid - 1
        }
        return lines.getOrNull(high)?.text
    }
}