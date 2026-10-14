import com.pulseplayer.app.PlaybackInputs

fun main() {
    check(PlaybackInputs.isAudioFile("MUSIC.FLAC", null))
    check(PlaybackInputs.isAudioFile("track.bin", "audio/flac"))
    check(!PlaybackInputs.isAudioFile("picture.jpg", "image/jpeg"))
    check(PlaybackInputs.isValidRadioStream("https://ice5.somafm.com/groovesalad-128-mp3"))
    check(PlaybackInputs.isValidRadioStream("https://example.com/live.m3u8"))
    check(!PlaybackInputs.isValidRadioStream("http://example.com/live.mp3"))
    check(!PlaybackInputs.isValidRadioStream("https://user:pass@example.com/live"))
    check(!PlaybackInputs.isValidRadioStream("https://example.com/live.pls"))
    check(!PlaybackInputs.isValidRadioStream("https://example.com/live.m3u"))
    check(!PlaybackInputs.isValidRadioStream("https://example.com/live.mp3#fragment"))
    check(!PlaybackInputs.isValidRadioStream("https://example.com /live"))
    check(!PlaybackInputs.isValidRadioStream("https://example.com:0/live"))
    println("PlaybackInputs: 12 assertions PASSED")
}