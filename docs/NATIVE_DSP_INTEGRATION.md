# Pulse Player — native DSP integration contract

## Current architecture (verified from source)
- PlaybackService owns Media3 ExoPlayer and MediaSession.
- AudioEffectsManager attaches Android Equalizer and BassBoost to the current audio session.
- app/src/main/cpp/StereoDSP.hpp is standalone C++17 DSP, verified by a native CI smoke test.
- The DSP is **not** currently connected to ExoPlayer's PCM output. Never label it active in the APK.

## Required integration before enabling
1. Add Android NDK/CMake target, compile JNI wrapper, and load the library with failure-safe fallback.
2. Implement a Media3 PCM AudioProcessor and insert it via a custom DefaultAudioSink and DefaultRenderersFactory. Verify the exact APIs against the pinned Media3 version before coding.
3. Support signed 16-bit and float PCM correctly, with channel-count checks and correct ByteBuffer positions/limits. Avoid silently corrupting unsupported formats.
4. Convert 16-bit samples to float, process interleaved L/R with StereoDSP, then convert with clipping/dither where appropriate.
5. Preserve strict stereo: no upmix, no downmix, no L/R mixing; unsupported channel configurations bypass safely.
6. Manage sample-rate changes, flush, seek, reset, and audio-track recreation. No memory allocations, locks, or JNI control updates on the real-time processing thread.
7. Smooth filter parameters and gain; assess oversampling for nonlinear stages, clipping/limiting, and output headroom.
8. Provide an explicit DSP mode selector: Platform EQ (current default), Native DSP (only after verified), or Bypass. Never stack platform EQ/BassBoost with native processing.
9. Add native tests for silence, impulse, channel isolation, parameter bounds, nonfinite input, 44.1/48/96 kHz, peak behavior and continuous processing across buffer boundaries.
10. Add Android instrumentation testing with real PCM playback and headphones/Bluetooth, then confirm audible output and no regressions.

## UI acceptance criteria
- Large touch targets, clear labels, consistent colours and icons, TalkBack descriptions.
- Native DSP unavailable must be displayed as unavailable, never a fake switch.
- Presets and controls must identify their active engine.
- Keep compact navigation and avoid extra permanently visible controls.
