## v6: Download MP3 with verified quality options

- `WORLD` music now displays a compact **↓ MP3** button and an MP3 action on track long-press. Tap a Jamendo track first, then choose **Standard MP3 (96 kbps)** or **High MP3 (VBR, good quality)**. Jamendo documents `audiodlformat=mp31/mp32`; it **does not promise a fixed 320 kbps** for its VBR option. The app does not transcode or make low-quality audio genuinely high-fidelity.
- **Artist consent is mandatory.** The app checks Jamendo `audiodownload_allowed` in the catalog AND again in the quality-specific API result before queuing. If the artist disables downloads or the URL is absent, MP3 download is unavailable.
- Android `DownloadManager` queues direct provider-authorized MP3 links. Android 10+ stores downloads under **Downloads/PulsePlayer** with a notification; Android 8–9 uses app-scoped external Downloads to avoid broad legacy storage permissions. The system download notification shows completion; a queued task is **not** claimed as a completed file.
- Spotify, YouTube, YouTube Music, and JOOX downloads/extractions are **not supported**. Respect provider licensing and terms; no DRM circumvention or protected stream extraction.
- Build is **unverified Android source** until CI succeeds, then verify download completion and file validity on a physical device.

## v5 — Unified local library + authorized world-music discovery

- **All Local** combines the phone's indexed audio (including MP3/FLAC/AAC/WAV audio not classified as `IS_MUSIC`), user-approved imported folders and selected files. Duplicate content URIs are removed from the combined queue.
- **Picked Files** uses Android's `ACTION_OPEN_DOCUMENT` with `EXTRA_ALLOW_MULTIPLE`. The app requests persistent grants and remembers eligible selected songs across launches (500-file limit). Open File also supports one-time playback from providers without persistent grants; it never reads arbitrary files without permission. `More → Import / file manager → Clear picked file list` removes the saved index without deleting original audio. Folders remain separately managed via `ACTION_OPEN_DOCUMENT_TREE` with recursion limits.
- **Independent world music** uses the documented Jamendo tracks API to discover popular music, search titles/artists/genres and stream permitted track URLs through ExoPlayer. The user must enter their own Jamendo developer `client_id` in `More → World music catalog (Jamendo) → Set Jamendo client ID`. After configuration, tap `World ♫ → Browse world music` to fetch a discovery page. To search, enter a phrase then press the keyboard Search action or choose `Search current text`; use `Load next 50 tracks` for pagination. Internet access is required. User accounts, licensing, rate limits and geographic restrictions remain subject to the provider. **No commercial-song catalog is bundled.**
- **Spotify and JOOX** open their *official* destinations, and Spotify searches use the text from the search box. Spotify/JOOX streams, subscription content, logins and DRM are not passed into ExoPlayer. Official SDK playback/control integrations require developer registration, credentials and provider permission.
- **Online music is streamed, not secretly downloaded.** Local file sharing applies only to user-authorized files. Jamendo Creative Commons track licensing and any distribution restrictions remain in effect.
- Build output is **source only** until an Android SDK-backed Gradle run passes. No automatic APK generation is asserted.

# PulsePlayer v5 — Unified Android ExoPlayer Music Player

Standalone native Kotlin Android project. Package `com.pulseplayer.app`. Android 8.0+ (min SDK 26), target SDK 36. AndroidX Media3 1.11.1.

> **Build status:** The generated archive is Android project **source, not an APK**. Kotlin-only logic and static XML checks passed in this environment. A Gradle/Android SDK-backed build, automated Android tests and physical-device testing have NOT been executed here. The included GitHub Actions workflow is intended to produce `app-debug.apk` when run in an Android-capable builder; a successful future build cannot be assumed.

## v3 — fewer buttons, more library space

- **Permanent controls (visible):** playlist selector, Malaysia radio shortcut, EQ shortcut, More menu, search, scrollable track list, fixed bottom mini-player with previous/play-pause/next, and draggable position seek bar.
- **Independent MP3 scrolling:** weighted `ListView` with native fast-scroll thumb and scroll bar, so the bottom player stays pinned. Library results are sorted by title (MediaStore) or audio filename (imported folders). Search local title/artist, imported filename/folder and radio names.
- **Mini-player gestures:** swipe left over track/title text to skip to next queue item; right to previous. Tap the title to open playback options. Scrub the dedicated seek bar to seek within the current song. Gestures are conservatively filtered to ignore small, slow and diagonal movement. Radio live streams cannot be scrubbed.
- **Playlist/source shortcut:** Device / Folders / Malaysia Radio / All Radio / Next Source-and-play. This changes the current browsed source, and tapping a track loads that source's queue. User-created saved named playlists are NOT yet included.
- **More menu:** import / open single file / remove imported permissions; official music services; Malaysian radio; audio mixer; speed/shuffle/repeat/sleep; Bluetooth/Wi-Fi/output; file sharing.
- Existing Android media-service background playback, sharing and library/folder/file access remain. The MediaSessionService owns playback; an Activity is only a controller.

## Third-party music services — links, not unauthorized audio extraction

- YouTube — opens `https://www.youtube.com/` in the user's official-capable app/browser.
- YouTube Music — opens `https://music.youtube.com/` in the system's capable app/browser.
- JOOX — opens `https://www.joox.com/` in the system browser/app.
- These destinations **do not stream into PulsePlayer's ExoPlayer**, import proprietary catalogs, share private login credentials, modify streaming audio, or circumvent subscriptions/DRM. They do NOT provide a combined playback queue, universal remote control, audio-only YouTube decoding, YouTube background playback, or JOOX SDK integration.
- JOOX publishes an Android SDK, but genuine in-app integration requires appropriate partner credentials/permissions, separate SDK lifecycle and compatible licensing.
- YouTube API rules prohibit third-party extraction/separation of audiovisual components and unapproved YouTube background players. Use official playback surfaces rather than NewPipe/extractor libraries.

## Malaysian radio

- `MY RADIO` list includes **MY · BFM 89.9**, using a public StreamTheWorld HTTPS URL reported by a third-party station directory. **Its availability, streaming authorization and device compatibility have not been runtime verified.**
- `More > Malaysian radio > Open RTM official radio` opens https://radio.rtm.gov.my/ (the verified official national/state station portal), where users can access RTM streams using the official player.
- BFM, ERA and MY FM official websites can be opened separately. ERA and MY FM use their official player pages, not unverified raw ExoPlayer streams.
- `Add HTTPS stream` saves other direct MP3/AAC/HLS radio URLs that users are authorized to access. Do not paste station website URLs as a fake audio stream. Only user-approved direct stream URLs are played by ExoPlayer.
- Radio is streaming over internet, not built-in FM hardware. No broadcast or copyrighted music is bundled, downloaded or redistributed.

## Audio presets

Android `Equalizer` (if present) offers **Flat, Warm, Vocal, Bright, Studio Punch, Cinema, Night, Airy, Immersive, NovaStream TV**. Optional `BassBoost` slider is also platform-dependent.

- `Immersive`: bass/body and upper-treble emphasis. **Not a Dolby Atmos renderer** and not HRTF/object-based audio.
- `NovaStream TV`: original TV/cinema-style frequency balance highlighting speech and controlled low-end. **Not supplied by/affiliated with any TV or audio vendor** and no verified reproduction of an actual NovaStream device.
- Do not label these presets as licensed Dolby Atmos, DTS:X, THX or Beats technology. Actual system spatial audio and supported Dolby bitstreams are device/content/output-dependent and outside this EQ-only enhancement implementation.
- EQ and BassBoost can clip at high gains. Start low. Proper dynamic limiting and audio enhancement require additional software DSP and measurements.

## Build on GitHub Actions

1. Make a **separate** dedicated GitHub repository for this player.
2. Extract `PulsePlayer-v3` and upload the files inside that directory to repository root (include `.github/workflows/android-apk.yml`).
3. Open Actions → **Build PulsePlayer APK** → **Run workflow** (or push to `main`).
4. Verify all steps are green, especially `:app:testDebugUnitTest :app:assembleDebug` and APK archive validation.
5. Download the `pulseplayer-v3-debug-apk` build artifact. It should contain `app-debug.apk` if that CI build passed.
6. Test on a spare device before release. A debug APK is not a store-ready signed release.

Local Android Studio: install Android SDK 36/JDK 17/Gradle 8.13, open the project directory containing `settings.gradle.kts`, sync dependencies and run `gradle :app:testDebugUnitTest :app:assembleDebug` from the project root. The archive does **not** bundle `gradlew` or the Gradle wrapper binary; generate the official wrapper from your trusted installed Gradle distribution if required.

## Offline checks already run

```bash
python3 scripts/verify_source.py
kotlinc app/src/main/java/com/pulseplayer/app/SoundPresets.kt tests/SoundPresetsCheck.kt -include-runtime -d /tmp/presets.jar
java -jar /tmp/presets.jar
kotlinc app/src/main/java/com/pulseplayer/app/PlaybackInputs.kt tests/PlaybackInputsCheck.kt -include-runtime -d /tmp/inputs.jar
java -jar /tmp/inputs.jar
kotlinc app/src/main/java/com/pulseplayer/app/MiniPlayerGestures.kt tests/MiniPlayerGesturesCheck.kt -include-runtime -d /tmp/gestures.jar
java -jar /tmp/gestures.jar
```

Observed: XML/source check **PASS**, preset test **15 PASS**, input validation **12 PASS**, gesture classifier **8 PASS**.

**Not verified:** actual Media3 Kotlin Android compilation, service runtime, real radio endpoints, gesture behavior on touchscreens, Bluetooth/Wi-Fi output routing, external apps availability, and audio quality.

## Prioritized device test matrix

1. Android 8, 12, 14 and 16: launch, permission denied/granted, short screens, landscape rotation, soft keyboard with library scrolling.
2. 1, 1,000 and 5,000 MP3s: scroll performance, alphabetical order, search matching, selection index and queue contents. Imported folder access recovery after app restart.
3. Mini-player swipe left/right and seekbar drag: fast and slow gestures, first/last queue track, live radio timeline disabled, track changes while activity is backgrounded.
4. Audio focus, call interruptions, Bluetooth connect/disconnect, USB DAC, notification/lock screen and normal background playback.
5. EQ baseline vs presets on several routes: per-route availability, clipping/volume, effect attachment on session ID change. Confirm unsupported devices fail gracefully.
6. Radio: Malaysia stream and user-entered HLS/MP3 URLs; TLS and network recovery; stream metadata. Verify station terms.
7. Official service links: launch correct app/browser without modifying protected playback; no overlay permissions, no site audio extraction.

## Release gates and gaps

- **No automatic substitution of an unrelated v2 APK:** An older compiled `app-debug(5).apk` was audited and contained modules not in the supplied v2 source. This v3 source must be built independently and verified against the resulting APK.
- **Not built in v3:** licensed Dolby Atmos, YouTube/JOOX catalog playback in ExoPlayer, named persistent playlists, Chromecast/DLNA, true multichannel surround engine, loudness metering, limiter, album-art grid, gapless verification.
- Do not infer permission to share songs simply because Android offers a share target; user and platform rights apply.

## v4 incremental enhancements (source-level)
- Added local `.lrc` synchronized lyric import and timestamp lookup from the More menu. Imported lyrics are session-scoped and displayed on demand. This is **not** speech-to-text transcription or karaoke.
- Added EQ-only presets: R&B, Rock, Classical, Subwoofer, Loudness, Stereo Clarity, 3D Bass, Cinema Surround, Deep Bass, Hi-Fi Reference, Custom (neutral placeholder). These are frequency curves, **not** actual Dolby Atmos, Bose, Beats, Altec Lansing, spatial rendering, stereo widening, or subwoofer virtualization.
- Radio remains a saved manual stream list. Automatic Malaysian station discovery and stream validation are not yet implemented.
- HQ restoration, machine-learning noise reduction, and a synchronized music spectrum visualizer are not yet implemented. Lossy MP3 detail cannot be perfectly recreated.
- YouTube, YouTube Music and JOOX should use their official experiences. Spotify App Remote integration requires registered OAuth client ID and app permissions; do not decode or extract its stream.