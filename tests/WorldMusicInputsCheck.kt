package com.pulseplayer.app

fun main() {
    var total = 0
    fun checkOk(value: Boolean) { check(value); total++ }
    checkOk(WorldMusicInputs.validClientId("client_id123"))
    checkOk(!WorldMusicInputs.validClientId(""))
    checkOk(!WorldMusicInputs.validClientId("bad key!"))
    checkOk(WorldMusicInputs.validSearch("jazz"))
    checkOk(!WorldMusicInputs.validSearch("a"))
    checkOk(!WorldMusicInputs.validSearch("a\ntrack"))
    checkOk(WorldMusicInputs.validAudioUrl("https://usercontent.jamendo.com/track.mp3"))
    checkOk(!WorldMusicInputs.validAudioUrl("http://example.com/song.mp3"))
    checkOk(!WorldMusicInputs.validAudioUrl("https://x@host.com/song.mp3"))
    checkOk(!WorldMusicInputs.validAudioUrl("https://host.com/track.mp3#fragment"))
    checkOk(!WorldMusicInputs.validAudioUrl("file:///music.mp3"))
    println("PASS: $total world catalog validation assertions")
}