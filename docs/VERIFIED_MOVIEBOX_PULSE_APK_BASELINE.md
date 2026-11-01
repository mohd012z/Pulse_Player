# Verified APK baseline — 2026-10-19

Inspected actual user-provided binary archives, not build output estimates.

| Metric | Pulse Player 4.0.0 | MovieBox 3.0.15.0513.03 |
|---|---:|---:|
| APK bytes | 79,899,701 | 58,133,502 |
| DEX files | 5 | 8 |
| Native .so files | 8 | 54 |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 | arm64-v8a, armeabi-v7a |
| SHA256 | be5339eda9090d39da4e9e63aaaf7b284dccbea1ff733c1bcf2092dfe12e4f78 | 0da240bfa3966465c08fd626c8dae0d45c63d3c1529687447040ad6250ce9ba5 |

Pulse's eight native libraries are ONNX Runtime and ONNX Runtime JNI for four ABIs; these materially increase its APK size. MovieBox includes native codec/player libraries such as libavcodec.so, libavformat.so, libalivcffmpeg.so, libsaasCorePlayer.so and libsaasDownloader.so. This is **archive inventory only**; not evidence of permission to redistribute libraries or of accessible streaming endpoints.

## Release blockers

- Previous user device screenshots show local-only Stories and empty Short Drama, not MovieBox-style online discovery.
- Current Cinema code explicitly states no licensed online movie catalog is connected; its Movies rails are placeholders.
- No evidence yet that new build preserves all old APK features, ONNX integrations, radio, EQ, lyrics, background playback, and downloads.
- No physical Android phone / TV smoke-test evidence.
- Do not mark feature-complete, merge, or publish until these are addressed.
- AI Pulse Studio PR #6 was cancelled; do not reintegrate it.

## Required independent verification

Compare the built APK manifest, DEX, native libraries, supported ABIs, package ID, signing certificate and UI feature inventory against the previous binary. Test music, radio, EQ, spectrum, lyrics, mini-player, background playback, downloads, Cinema, ShortTV and TV remote on real devices. Test actual licensed playable titles, not category labels or placeholder dialogs.

MovieBox is an architecture reference, not an authorized catalog, source-code repository or binary redistribution license.
