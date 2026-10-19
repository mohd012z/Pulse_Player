# Pulse Player DSP integration audit (2026-10-16)

## Verified architecture
- Playback is owned by PlaybackService using Media3 ExoPlayer and MediaSession.
- Existing Android Equalizer/BassBoost are attached via AudioEffectsManager.
- Native StereoDSP.hpp is a separate C++17 engine with independent L/R state.
- New PulsePcmProcessor hooks decoded stereo PCM16 into a JNI bridge.
- Native processing is OFF by default; no user toggle is yet exposed.
- Unsupported PCM layouts and encodings are bypassed.

## Known limitations / next gates
1. **Build gate**: confirm GitHub Actions Android Gradle build for JNI/CMake changes.
2. **Runtime gate**: instrument processor configure/flush/queueInput, and verify it actually runs on physical Android hardware.
3. **Performance**: current bridge allocates ShortArray and native vectors per buffer; replace with reusable direct buffers / bounded allocation before enabling by default.
4. **DSP parameter updates**: native coefficients are rebuilt on every audio callback. Cache parameters and smooth changes to prevent clicks.
5. **Effects conflict**: avoid simultaneous Android Equalizer/BassBoost and native EQ when native processing is enabled.
6. **UI**: add a MediaSession command and switch, persist on/off state, show supported format and bypass reason.
7. **Compatibility**: test seeking, speed, Bluetooth, headphones, 44.1/48/96 kHz, mono and multichannel, gapless transitions, radio and DRM/offload behavior.
8. **Dead-code cleanup**: require static inspection and per-file references before deleting any source. Native DSP code is now referenced; other suspected unused code has NOT been removed.
9. **Signal integrity**: test no L/R leakage, clipping and intersample peaks, silence, DC offset, distortion and CPU/battery usage.

## Integration flow
Media3 decode -> DefaultAudioSink custom processor -> PCM16 stereo -> JNI -> StereoDSP -> PCM16 -> AudioTrack.

No playback claims should be made solely from successful compilation.
