package com.pulseplayer.app

fun main() {
    var count = 0
    fun verify(ok: Boolean) { check(ok); count++ }
    verify(Mp3DownloadInputs.Quality.STANDARD.apiValue == "mp31")
    verify(Mp3DownloadInputs.Quality.HIGH.apiValue == "mp32")
    verify(Mp3DownloadInputs.validTrackId("1848357"))
    verify(!Mp3DownloadInputs.validTrackId("id/../123"))
    verify(!Mp3DownloadInputs.validTrackId(""))
    val filename = Mp3DownloadInputs.filename("Artist/Example", "Track: #1", "123", Mp3DownloadInputs.Quality.HIGH)
    verify(filename.endsWith(" - VBR.mp3"))
    verify(!filename.contains("/"))
    verify(!filename.contains(":"))
    println("PASS: $count MP3 download input assertions")
}