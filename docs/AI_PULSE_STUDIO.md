# AI Pulse Studio — verified scope and implementation plan

This branch adds an **original offline instrumental WAV renderer**, not a Suno clone or trained text-to-music model. It synthesizes deterministic short melodic phrases from mood, BPM and seed, bounded to 2–30 seconds at 22.05 kHz mono PCM. It writes a temporary file, checks cancellation and publishes only after completion.

## Current implementation
- `AiPulseComposer.Request` accepts title, mood, BPM, duration and seed.
- `AiPulseComposer.render` writes a playable 16-bit WAV to an app-authorized output file.
- Unit tests cover RIFF structure, duration, non-silence, input bounds and cancellation.
- No model weights, copyrighted Suno code, unapproved downloads, cookies or credentials are included.

## Required integration work (not yet complete)
1. Connect the composer to a Create tab and user-selected output destination.
2. Run generation on a background dispatcher/worker with progress and cancel controls.
3. Index successful WAV files in Pulse Player's existing library.
4. Add an audio-analysis stage (loudness, clipping, true peak) and optional licensed encoders.
5. Implement a `MusicGenerationBackend` interface for independently licensed neural models or opt-in authorized cloud providers.
6. Validate GPU/RAM limits, licensing, source provenance, voice consent, and device builds.

## APK study boundaries
Use package metadata, permissions, framework inventory and public product behavior as references. Do not copy Suno proprietary bytecode, model weights, private endpoints, access tokens, or download bypasses. Suno's published 2026 terms prohibit unauthorized scraping and stream ripping.

## Validation
Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` and test on an Android device. No successful Android build or device run is claimed in this branch.
