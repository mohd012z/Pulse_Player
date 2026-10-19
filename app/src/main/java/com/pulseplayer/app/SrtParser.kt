package com.pulseplayer.app

/** Standalone SRT parser for local, user-provided subtitle files. */
internal object SrtParser {
    data class Cue(val startMs: Long, val endMs: Long, val text: String)
    private val timing = Regex("""(\d{2}):(\d{2}):(\d{2})[,\.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,\.](\d{3})""")
    private fun time(groups: List<String>, offset: Int): Long {
        val h = groups[offset].toLong()
        val m = groups[offset + 1].toLong()
        val s = groups[offset + 2].toLong()
        val ms = groups[offset + 3].toLong()
        return ((h * 60 + m) * 60 + s) * 1000 + ms
    }
    fun parse(input: String): List<Cue> = input.replace("\r\n", "\n")
        .replace("\r", "\n").split(Regex("\\n\\s*\\n")).mapNotNull { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            val index = lines.indexOfFirst { timing.matches(it) }
            if (index < 0) return@mapNotNull null
            val values = timing.matchEntire(lines[index])!!.groupValues.drop(1)
            val start = time(values, 0)
            val end = time(values, 4)
            val text = lines.drop(index + 1).joinToString("\n").take(4000)
            if (end <= start || text.isBlank()) null else Cue(start, end, text)
        }.sortedBy { it.startMs }
    fun at(cues: List<Cue>, positionMs: Long): String =
        cues.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }?.text.orEmpty()
}
