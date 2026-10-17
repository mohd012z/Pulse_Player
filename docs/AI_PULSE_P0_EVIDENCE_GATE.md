# AI Pulse P0 — evidence gate (do not mark green without audio)

## Evidence levels
1. **Source**: implementation committed; proves existence only.
2. **Build**: Android Gradle compile/tests and APK artifact succeed.
3. **Model integrity**: license/authorization documented; SHA-256 of acoustic and vocoder verified.
4. **Graph compatibility**: ONNX Runtime loads BOTH models and reports names, types and shapes. A name-only match is NOT sufficient.
5. **Real inference**: acoustic produces finite mel frames; vocoder produces finite, nonempty samples. Record sample rate, hop size, output shape, render latency and WAV SHA-256.
6. **Audio acceptance**: playback verified, non-silent audio, correct duration and pitch; independent listening evaluation. This cannot be inferred from a WAV header or nonzero samples.
7. **Android proof**: instrumented on-device run with device/ABI, peak RSS, elapsed time, model hashes, APK commit and exported WAV checksum.

## Mandatory P0 rejection cases
- Missing or unlicensed voicebank, unverifiable weights, unsupported ONNX operators.
- Unsupported input names, tensor dtypes, shapes, additional required model inputs.
- Acoustic mel dimension inconsistent with vocoder mel bins.
- F0 representation or hop size mismatch.
- Invalid/NaN samples, silence, clipped output, implausible duration.
- APK compilation succeeds but no real model execution evidence exists.

## Source evidence
- ONNX Runtime Java OrtSession API: https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.html
- OpenUtau DiffSinger renderer: https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/DiffSinger/DiffSingerRenderer.cs
- OpenUtau DiffSinger config: https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/DiffSinger/DiffSingerConfig.cs
- OpenUtau DiffSinger support: https://github.com/openutau/OpenUtau/wiki/DiffSinger-support

## Known implementation limitation
Current AiPulseDiffSinger only supports exact input names tokens,durations,f0,speedup and mel,f0.
The inspector currently tests input/output names and displays shape/type, but does NOT certify tensor shape, datatype, hop-size or real inference compatibility. Its supported_signature field must never be interpreted as proof of working synthesis.
A real, authorized compatible model pack and device render are not yet attached to this repository. **P0 = NOT PROVEN**.
