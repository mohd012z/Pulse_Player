# AI Pulse: offline neural singing implementation blueprint

## Decision
Use licensed DiffSinger-compatible ONNX acoustic and vocoder models with ONNX Runtime Android CPU/XNNPACK. No GGUF, GPU server, or API key. The existing offline instrumental WAV renderer remains a fallback, never represented as neural singing.

## Verified references
- https://github.com/openutau/OpenUtau/wiki/DiffSinger-support
- https://openutau.com/Adding-support-for-a-new-synthesis-engine/
- https://onnxruntime.ai/docs/tutorials/mobile/
- https://github.com/openutau/OpenUtau/blob/master/OpenUtau.Core/DiffSinger/DiffSingerBasePhonemizer.cs

## Model packaging (planned)
Each singer pack contains manifest.json (name, languages, license, checksum, required input/output tensor metadata), acoustic.onnx, vocoder.onnx, phoneme dictionary and normalization configuration. Do not bundle third-party weights before verifying the specific voicebank's license and consent.

## Offline rendering sequence
1. Generate or import a note timeline: pitch in MIDI notes, onset/duration, lyric syllables.
2. Convert syllables to language-specific phonemes and align to notes; fail explicitly if unsupported.
3. Validate model manifest, hashes, tensor names/shapes, available RAM and audio sample rate.
4. Load acoustic ONNX session; process phrases/chunks with explicit memory and cancellation limits.
5. Pass acoustic features to matching vocoder ONNX session.
6. Normalize and concatenate PCM chunks with overlap smoothing; retain original timestamps.
7. Mix with instrumental PCM using bounded gain; export RIFF PCM16 WAV and verify header, duration, silence and clipping.
8. Persist metadata (model id/version, license, prompt, seed, render duration, checksums).

## Android implementation order
P0: add model pack import/validation and status UI, with no fake 'model loaded' state.
P1: integrate onnxruntime-android CPU session and instrumented tensor-shape tests using a permissively licensed tiny test ONNX model.
P2: implement note/phoneme adapter matching the selected DiffSinger voicebank.
P3: integrate acoustic + vocoder sessions; render one licensed 3-second phrase and compare PCM metrics on a physical device.
P4: add voice+instrument mix, full WAV export, cancellation, thermal/memory monitoring.
P5: profile CPU vs XNNPACK and optional NNAPI, only where supported.

## Acceptance evidence
A genuine local neural singing claim requires: identified licensed model files, verified SHA-256, successful offline airplane-mode render on Android, nonempty voiced PCM, reproducible build/run logs, memory/latency measurements, and an independently playable WAV artifact. Procedural vowels or mock server output do not qualify.

## Risks
- Voicebank license may restrict commercial use or redistribution.
- A trained acoustic model and a compatible neural vocoder are both required.
- Android CPU inference can be slow and may exceed device RAM; model compatibility must be measured, not assumed.
- English and Malay need independently verified phoneme dictionaries or multilingual singer support.
- Cover/repaint/stem separation require separate models and must not be marked complete merely because a task parameter exists.
