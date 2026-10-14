# Pulse Player — Android ExoPlayer

Native Android music player, using Kotlin and AndroidX Media3. This repository is initialized with an Android SDK 36 / Java 17 / Gradle 8.13 APK build workflow.

## Source import status

**The complete v3 source ZIP has not yet been committed to this repository.** The APK workflow is ready to consume the exact source archive under the filename `PulsePlayer-v3-Compact-MiniPlayer-Source.zip` at the repository root. It extracts the `PulsePlayer-v3/` folder into the build workspace, runs `scripts/verify_source.py` and the Android unit tests, and builds `app-debug.apk`. Extraction in CI does not automatically commit extracted source files into the repository.

Required source archive SHA-256:
`d812d1242f438b7a236bb4acbe61c4d8db5aeb07595c4235cf65b6b8e8c014a1`

To complete the upload, add the matching source ZIP to the repository root (GitHub: Add file → Upload files), commit to `main`, and follow Actions → Build PulsePlayer APK. Upon a green run, download the `pulseplayer-v3-debug-apk` artifact. This is a debug build; it has not been released or device-tested.

## Features in the supplied v3 source

- Media3/ExoPlayer foreground background playback and lock-screen media transport
- Scrollable local music library, music folder import and Android file sharing
- Compact player with swipe gestures and seek timeline
- Playback speed, shuffle/repeat, playlist switching and launcher shortcuts
- Radio presets and official YouTube Music / JOOX app destinations
- Optional device EQ and bass boost, including Immersive and NovaStream TV-inspired EQ presets

These presets are independent equalizer curves, **not** licensed Dolby Atmos or Beats processing. YouTube Music and JOOX audio is not extracted by this application.

## Build details

- Android Gradle Plugin 8.13.2, Kotlin plugin 2.2.20
- Compile SDK 36, minSdk 26, targetSdk 36
- Java 17 and Gradle 8.13
- Media3 ExoPlayer 1.11.1

Build status remains **unverified** until the full source is uploaded and GitHub Actions completes successfully.
