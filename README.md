# Pulse Player — Android ExoPlayer

PulsePlayer v3 compact music player (Kotlin, AndroidX Media3 ExoPlayer).

Features: local MP3 browsing, folder import, background playback, scrollable music library, swipe-enabled mini player, media controls, Bluetooth audio routing through Android, radio streams, and configurable EQ presets. YouTube Music and JOOX use official application/browser destinations rather than unauthorized stream extraction.

The source is imported using the GitHub Actions bootstrap workflow and then compiled by `.github/workflows/android-apk.yml`. APK publication depends on a successful Actions build, not on this README.

The immersive and NovaStream TV sound presets are independent EQ tunings; they do not constitute licensed Dolby Atmos or Beats technology.

Target: Android API 36; minSdk 26. Unlicensed third-party audio sources are not bundled.
