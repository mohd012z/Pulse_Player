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

## Pulse Sound Studio and video matching (feature branch)

- **Sound Studio → Auto sound on/off:** Opt-in, conservative EQ recommendations. On Android 13+ the app queries anticipated media routing via `AudioManager.getAudioDevicesForAttributes`; on earlier Android versions it uses connected devices as a fallback. Auto mode does not force Bluetooth routing and does not guarantee the playback track's exact physical path.
- **Sound Studio → Dynamic-range compressor:** Optional attenuation-only linked stereo 2:1 processing for decoded PCM16 stereo in Media3. **PCM safety limiter** is a separate optional sample-peak ceiling at 30000/32768. Neither repairs pre-existing clipping, performs true-peak reconstruction or prevents downstream hardware EQ from clipping. Both are off until enabled.
- **Sound Studio → Device speaker / spatial / refresh scan:** Platform support checks for Android spatializer availability, enabled state, stereo/5.1 compatibility, head tracking, audio output, and display refresh modes. Android controls spatialization; Pulse Player does not bundle Dolby Atmos decoding, licensed sound brands or proprietary object-audio technology.
- **Provider video → FPS:** Choose source frame rate (system automatic, 24/25/30/50/60). On provider fullscreen, request an integer-multiple refresh mode at the **same display resolution** when Android reports one; restore previous preference on exit. This is only a display hint. It does not change YouTube video FPS or eliminate advertisements.
- **YouTube Live TV:** Category and custom searches plus saved favorite search terms. This is YouTube discovery, not a verified live-channel directory, program guide, or advertisement blocker.
- Provider WebView audio is **not** available for Pulse Player's private Media3 EQ, compressor, or limiter, because its playback session belongs to the provider.

## Persistent signed APK Releases

The normal pull-request build uploads a **temporary 14-day artifact**. The installed APK itself never expires.

The version-tag workflow `.github/workflows/publish-release.yml` publishes a signed release APK as a GitHub Release, subject to one-time setup by the repository owner:

1. Create a secure Android signing keystore, backed up outside the repository. Never commit it or its passwords.
2. Add repository Actions secrets: `PULSE_UPLOAD_KEYSTORE_BASE64` (base64-encoded keystore), `PULSE_UPLOAD_KEYSTORE_PASSWORD`, `PULSE_UPLOAD_KEY_ALIAS`, and `PULSE_UPLOAD_KEY_PASSWORD`.
3. Merge verified code to the default branch and push a version tag (for example `v4.1.0`). CI runs JVM tests, `assembleRelease`, `apksigner verify`, and publishes the APK plus a SHA-256 checksum file.
4. Install future updates using the **same signing key and application ID**. A key mismatch prevents normal in-place updates; losing the key can prevent distributing compatible updates.

Signing secrets, a green release workflow, an actual pushed version tag, and a published GitHub Release are **not included** in source-code completion. They must be verified separately.

## Additional on-device audio/video QA

Use a phone and headphones to check auto sound on speaker → wired → Bluetooth, output switching during playback, EQ session recreation, quiet/loud transitions, no clicks, steady sound level, and graceful effects fallback. Compare compressor/limiter off vs on with authorized local audio. Test fullscreen video 24/30/60 fps on a device with multiple refresh modes and check the system's observed mode; then exit fullscreen and verify the previous mode returns. Test no-match mode, rotation and provider-controlled playback. Complete listening and display tests before declaring sound fidelity improved.

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