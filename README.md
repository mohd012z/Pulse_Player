# PulsePlayer v4 — compact native Android music player

Native Android/Kotlin + AndroidX Media3 ExoPlayer app; Android 8.0+ (minSdk 26), target SDK 36, Java 17, Gradle 8.13. Built for local audio and direct internet-radio streams.

## Implemented features

- Background playback with `MediaSessionService`, Bluetooth/lock-screen media controls, shuffle/repeat, playback-speed selection, sleep timer and persistent resume state.
- Compact native interface, scrollable MP3 library, independent fixed mini-player, swipe gestures for next/previous track, and seekbar scrubbing.
- Open local audio documents, persist read access when supported, recursively import chosen folders using Android's Storage Access Framework, and share a locally accessible audio URI through Android's chooser.
- Optional hardware audio Equalizer and BassBoost on the **active playback audio session**. Profiles: Flat, Warm, Vocal, Bright, Studio Punch, Cinema, Night, Airy, Immersive, NovaStream TV, R&B, Rock, Classical, Subwoofer, Loudness EQ, Stereo Clarity, 3D Bass EQ, Cinema Spatial EQ, Studio Beats EQ, Balanced Speaker, Live Concert, Noise Softening and Custom. Custom EQ adjusts bass/mid/treble ±6 dB. Audio effects are device-dependent.
- Attach `.lrc` synchronized lyric files to a playing local song through the file picker. Lyrics display in sync in a compact line above the mini-player; tap the line or use More → Lyrics to view text. This does not automatically transcribe speech, scrape lyrics or synchronize plain text.
- Optional **real FFT music spectrum visualizer**, attached to the current playing session. Android requires `RECORD_AUDIO` permission; it is requested only after the user selects the visualization feature and accepts an explanation. No files or recordings are created; capture is released when closed or the activity stops. Some outputs/offload paths do not support Visualizer.
- Malaysian radio directory discovery from **Radio Browser** public API (countrycode MY, recent working entries, HTTPS only). Imported results are cached on-device. Long-press a station to add/remove local favorites. Manual HTTPS stream bookmarks and selected sample internet radio streams remain available. The directory is third-party and is not guaranteed to be complete or always reachable.
- Official app/browser destinations for YouTube, YouTube Music, JOOX and Spotify. This is not a license to extract protected media or mix service audio into ExoPlayer.

## Accuracy and limitations

- The EQ curves are independent tunings. **No Dolby Atmos, Bose, Beats by Dr. Dre or Altec Lansing licensed algorithms** are bundled. Stereo Clarity and 3D Bass EQ alter tone, not channel geometry or object-based spatial audio.
- Noise Softening is **frequency attenuation** for some rumble/hiss, not true adaptive denoising, lossless upscaling, frequency recovery, or AI remastering. An MP3 cannot be made objectively lossless after data was discarded by compression.
- Radio auto-discovery is an internet directory lookup, not broadcast FM antenna scanning. Streaming requires an internet connection and valid station streams. Playing official third-party app content is delegated to those apps.
- Spectrum capture uses a sensitive Android permission, and must remain an opt-in function. It does not use a microphone to record and does not persist captured waveforms.
- A source-level check and standalone Kotlin logic tests passed in this environment. **Full Android compilation, device playback, radio network access, and APK runtime tests require a real Android SDK or GitHub Actions.** Do not mark the APK verified until the workflow passes.

## Build

Use Android Studio with Android SDK 36, Gradle 8.13, Android Gradle Plugin 8.13.2 and JDK 17. Run `gradle :app:testDebugUnitTest :app:assembleDebug` (or project GitHub Actions workflow), and find the debug APK at `app/build/outputs/apk/debug/app-debug.apk` after a green build.

The workflow is `.github/workflows/android-apk.yml`. It validates source structure, runs JVM tests, compiles a debug APK, checks its ZIP integrity, prints package metadata and uploads the APK artifact `pulseplayer-v4-debug-apk`.

## QA checklist for device release

1. Long-playing music in background, notification controls, audio focus interruptions and headset unplug.
2. Import individual MP3 plus selected folder; test revoked URI permission and missing song recovery.
3. Assign `.lrc` with multiple stamps and offset; seek and transition song; verify subtitle sync.
4. Visualizer permission denied/granted, switching outputs, pausing, closing UI, screen lock; verify release and no audio recording.
5. Switch EQ presets during playback and change device output to one with no hardware effects; confirm the app degrades gracefully.
6. Discover Malaysian radio, favorites, radio network failure, invalid stream; verify cached station list survives restart.
7. Test Android 8/12/14/16 and phone landscape/portrait, Bluetooth headphones, wired audio and speaker.

## Provenance and security

No API secrets, unrestricted app overlays, media extraction tools, or proprietary codec packages are included. Radio stations from the public directory are filtered to HTTPS and rendered as untrusted external stream endpoints. If publishing to Play Store, review RECORD_AUDIO and the streaming-content permissions, and obtain appropriate rights for any copyrighted station logos or integration marks.