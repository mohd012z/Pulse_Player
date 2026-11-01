package com.pulseplayer.app

internal object MoreMenuContract {
    enum class Action(val label: String) {
        IMPORT("Import / file manager"),
        ONLINE("Online music services"),
        YOUTUBE("YouTube / YT Music — download"),
        RADIO("Malaysian radio"),
        AUDIO("Audio mixer / effects"),
        LYRICS("Lyrics / subtitles"),
        SPECTRUM("Live audio spectrum"),
        PLAYBACK("Playback preferences"),
        CONNECTION("Bluetooth / Wi-Fi / output"),
        SHARE("Share current song"),
        POWER("Power saving mode"),
        INSPECT("Inspect media URL"),
        STORY("🎬 Short Drama & 📖 Novel")
    }

    fun labels(): Array<String> = Action.entries.map { it.label }.toTypedArray()

    fun actionAt(index: Int): Action? = Action.entries.getOrNull(index)
}
