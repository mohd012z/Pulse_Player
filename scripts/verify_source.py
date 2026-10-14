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
assert len(services) == 1, "Expected exactly one player service"
service = services[0]
assert service.get(ANDROID + "name") == ".PlaybackService"
assert service.get(ANDROID + "foregroundServiceType") == "mediaPlayback"
intents = [a.get(ANDROID + "name") for a in service.findall(".//action")]
assert "androidx.media3.session.MediaSessionService" in intents

source = ROOT / "app/src/main/java/com/pulseplayer/app"
expected = {"MainActivity", "PlaybackService", "AudioEffectsManager", "MusicRepository", "SoundPresets",
            "FolderAudioRepository", "RadioRepository", "PlaybackInputs", "MiniPlayerGestures", "LyricsRepository", "SpectrumView"}
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
assert "scanMalaysiaRadio" in activity_text and "LyricsRepository.at" in activity_text
assert "CMD_SET_CUSTOM" in constants and "PlaybackService.CMD_SET_CUSTOM" in activity_text
assert "SpectrumView" in activity_text and "showCustomEq" in activity_text
assert "scanMalaysia" in (source / "RadioRepository.kt").read_text()
for profile in ("R&B", "Rock", "Classical", "Subwoofer", "Noise Softener", "Custom"):
    assert profile in presets

assert "onConnectAsync" in service_text
assert "controller.packageName == packageName" in service_text
build = (ROOT / "app/build.gradle.kts").read_text()
assert 'val media3Version = "1.11.1"' in build
assert "targetSdk = 36" in build
workflow = (ROOT / ".github/workflows/android-apk.yml").read_text()
assert ":app:testDebugUnitTest :app:assembleDebug" in workflow
print("PASS: XML/resources, permissions, service registration, modules, commands, workflow")
print("NOTE: Android compilation, instrumentation, playback and APK generation are NOT tested here")