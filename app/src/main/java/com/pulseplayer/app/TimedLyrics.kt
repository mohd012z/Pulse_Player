package com.pulseplayer.app

/** Local .lrc subtitles; no remote lyrics scraping and no audio transcription claims. */
object TimedLyrics {
    data class Line(val atMs: Long, val text: String)
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val offset = Regex("""\[offset:([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    fun parse(source: String): List<Line> {
        val lines = ArrayList<Line>()
        val shift = offset.find(source)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-30_000, 30_000) ?: 0L
        for (raw in source.lineSequence().take(12_000)) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val words = stamp.replace(raw, "").trim().take(600)
            if (words.isEmpty()) continue
            for (match in stamps) {
                val m = match.groupValues[1].toLongOrNull() ?: continue
                val sec = match.groupValues[2].toLongOrNull() ?: continue
                if (sec > 59) continue
                val fraction = match.groupValues[3]
                val ms = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                val timestamp = (m * 60_000 + sec * 1000 + ms + shift).coerceAtLeast(0L)
                lines.add(Line(timestamp, words))
            }
            if (lines.size > 12_000) break
        }
        return lines.sortedBy { it.atMs }
    }

    fun activeIndex(lines: List<Line>, positionMs: Long): Int {
        var low = 0
        var high = lines.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (lines[mid].atMs <= positionMs) low = mid + 1 else high = mid
        }
        return low - 1
    }
}