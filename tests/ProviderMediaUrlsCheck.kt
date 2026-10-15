package com.pulseplayer.app

fun main() {
    val youtube = ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.YOUTUBE, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    check(youtube?.mode == ProviderMediaUrls.Mode.YOUTUBE_EMBED)
    check(youtube?.url?.contains("/embed/dQw4w9WgXcQ") == true)
    check(ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.YOUTUBE, "https://youtu.be/dQw4w9WgXcQ") != null)
    check(ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.YOUTUBE, "https://music.youtube.com/playlist?list=PL1234567890AB") != null)
    val spotify = ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.SPOTIFY, "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=2")
    check(spotify?.mode == ProviderMediaUrls.Mode.SPOTIFY_EMBED)
    check(spotify?.url == "https://open.spotify.com/embed/track/4cOdK2wGLETKBW3PvgPWqT")
    check(ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.JOOX, "https://www.joox.com/") != null)
    for (name in ProviderMediaUrls.Provider.values()) {
        check(ProviderMediaUrls.resolve(name, "javascript:alert(1)") == null)
        check(ProviderMediaUrls.resolve(name, "https://www.youtube.com:444/watch?v=dQw4w9WgXcQ") == null)
        check(ProviderMediaUrls.resolve(name, "http://www.youtube.com/watch?v=dQw4w9WgXcQ") == null)
    }
    check(ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.JOOX, "https://www.joox.com.bad.tld/") == null)
    check(ProviderMediaUrls.resolve(ProviderMediaUrls.Provider.SPOTIFY, "https://open.spotify.com/search/anything") == null)
    println("PASS: official embedded service URL parsing and invalid/unsafe link rejection")
}