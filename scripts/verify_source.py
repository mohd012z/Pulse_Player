#!/usr/bin/env python3
"""Fail-closed offline checks; not an Android compilation or runtime test."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
for xml in (ROOT / "app/src/main/res").rglob("*.xml"):
    ET.parse(xml)
permissions = {
    child.get(ANDROID + "name")
    for child in manifest.findall("uses-permission")
}
for required in (
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.INTERNET",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
):
    assert required in permissions, f"Missing {required}"
services = manifest.findall(".//service")
player_services = [item for item in services if item.get(ANDROID + "name") == ".PlaybackService"]
assert len(player_services) == 1, "Expected exactly one PlaybackService"
service = player_services[0]
assert not any(item.get(ANDROID + "name") == ".SpokenNotificationService" for item in services), "Spoken notifications must remain unregistered in rollback build"
assert not any(item.get(ANDROID + "permission") == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" for item in services), "Notification listener binding must be absent"
assert service.get(ANDROID + "name") == ".PlaybackService"
assert service.get(ANDROID + "foregroundServiceType") == "mediaPlayback"
intents = [a.get(ANDROID + "name") for a in service.findall(".//action")]
assert "androidx.media3.session.MediaSessionService" in intents

source = ROOT / "app/src/main/java/com/pulseplayer/app"
expected = {"MainActivity", "PlaybackService", "AudioEffectsManager", "MusicRepository", "SoundPresets",
            "FolderAudioRepository", "RadioRepository", "PlaybackInputs", "MiniPlayerGestures",
            "TimedLyrics", "SpectrumView", "MalaysiaRadioDirectory"}
actual = {file.stem for file in source.glob("*.kt")}
assert expected <= actual, f"Missing Kotlin modules: {expected - actual}"
service_text = (source / "PlaybackService.kt").read_text()
activity_text = (source / "MainActivity.kt").read_text()
constants = dict(re.findall(r'const val (CMD_[A-Z_]+) = "([^"]+)"', service_text))
for command in ("CMD_SET_PRESET", "CMD_SET_BASS", "CMD_SET_SLEEP", "CMD_STATUS"):
    assert command in constants
    assert "PlaybackService." + command in activity_text
assert "MediaController.Builder" in activity_text
assert "MediaSession.Builder" in service_text
assert "setHandleAudioBecomingNoisy(true)" in service_text
assert "ACTION_OPEN_DOCUMENT" in activity_text
assert "setDynamicShortcuts" in activity_text
for feature in ("ACTION_OPEN_DOCUMENT_TREE", "ACTION_SEND", "ACTION_BLUETOOTH_SETTINGS",
                "ACTION_WIFI_SETTINGS", "showSystemOutputSwitcher", "rotatePlaylist", "addRadioDialog"):
    assert feature in activity_text, f"Missing feature: {feature}"
assert "androidx.media3:media3-exoplayer-hls" in (ROOT / "app/build.gradle.kts").read_text()
assert "RadioRepository" in activity_text and "FolderAudioRepository" in activity_text
# v3 compact UI: independent list scroll, pinned mini dock, swipe and timeline seek.
for feature in ("isFastScrollEnabled = true", "LinearLayout.LayoutParams(-1, 0, 1f)",
                "GestureDetector", "onFling", "showMoreMenu", "showPlaylistDialog",
                "MY RADIO", "music.youtube.com", "www.joox.com", "radio.rtm.gov.my"):
    assert feature in activity_text, f"Missing v3 feature: {feature}"
presets = (source / "SoundPresets.kt").read_text()
for profile in ("Immersive", "NovaStream TV"):
    assert profile in presets
assert "SYSTEM_ALERT_WINDOW" not in permissions
assert "android.permission.RECORD_AUDIO" in permissions
for feature in ("REQUEST_LYRICS", "TimedLyrics.activeIndex", "discoverMalaysiaRadio", "showCustomEqualizer", "requestSpectrum", "Spotify", "Lyrics / subtitles"):
    assert feature in activity_text, f"Missing v4 feature: {feature}"
assert "bycountrycodeexact/MY" in (source / "MalaysiaRadioDirectory.kt").read_text()
spectrum_source = (source / "SpectrumView.kt").read_text()
assert "onFftDataCapture" in spectrum_source
assert "enum class DisplayMode { FREQUENCY_BARS, PIXEL_WATER, MIRROR_BARS, OSCILLOSCOPE }" in spectrum_source, "Expected four supported spectrum modes"
for mode in ("FREQUENCY_BARS", "PIXEL_WATER", "MIRROR_BARS", "OSCILLOSCOPE"):
    assert mode in spectrum_source, f"Missing spectrum mode: {mode}"
assert "SpectrumView.DisplayMode.entries" in activity_text, (
    "Main player must cycle the available spectrum modes"
)
assert 'getString("display_mode", "FREQUENCY_BARS")' in activity_text, (
    "Frequency bars must remain the default visualization"
)
assert 'putString("display_mode"' in activity_text, "Visualization selection must persist"
assert "true, true)" in spectrum_source, "Both waveform and FFT captures must be enabled"
assert "spectrum[i] = maxOf(next[i]" in spectrum_source, "FFT data must update visible spectrum"
assert "CMD_SET_CUSTOM" in service_text
assert "PlaybackService.CMD_SET_CUSTOM" in activity_text
assert (ROOT / "app/src/test/java/com/pulseplayer/app/TimedLyricsTest.kt").is_file()

assert "onConnectAsync" in service_text
assert "controller.packageName == packageName" in service_text
build = (ROOT / "app/build.gradle.kts").read_text()
assert 'val media3Version = "1.11.1"' in build
assert "targetSdk = 36" in build
assert "versionCode = 4" in build
workflow = (ROOT / ".github/workflows/android-apk.yml").read_text()
assert ":app:testDebugUnitTest :app:assembleDebug" in workflow
print("PASS: v4 XML/resources, permissions, service registration, modules, commands, workflow")
print("NOTE: Android compilation, instrumentation, playback and APK generation are NOT tested here")