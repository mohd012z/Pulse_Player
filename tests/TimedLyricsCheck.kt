package com.pulseplayer.app

fun main() {
    val text = """
        [ar:Test Artist]
        [offset:100]
        [00:01.50][00:02.750]Hello
        [00:03.1]World
        [01:70.00]Invalid
        [00:04.05]
        [00:05.00]Last
    """.trimIndent()
    val lines = TimedLyrics.parse(text)
    check(lines.size == 4)
    check(lines[0] == TimedLyrics.Line(1600, "Hello"))
    check(lines[1] == TimedLyrics.Line(2850, "Hello"))
    check(lines[2] == TimedLyrics.Line(3200, "World"))
    check(lines[3] == TimedLyrics.Line(5100, "Last"))
    check(TimedLyrics.activeIndex(lines, 0) == -1)
    check(TimedLyrics.activeIndex(lines, 1599) == -1)
    check(TimedLyrics.activeIndex(lines, 1600) == 0)
    check(TimedLyrics.activeIndex(lines, 3000) == 1)
    check(TimedLyrics.activeIndex(lines, 3200) == 2)
    check(TimedLyrics.activeIndex(lines, 10000) == 3)
    check(TimedLyrics.parse("[00:00.00]Song").single().text == "Song")
    check(TimedLyrics.parse("[00:00.00]Song")[0].atMs == 0L)
    println("PASS: 12 synchronized lyrics parser assertions")
}