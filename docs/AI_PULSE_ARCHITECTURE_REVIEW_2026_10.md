# AI Pulse / PULSE-NOVA architecture review (2026-10-18)

## Evidence labels
- VERIFIED IN REPOSITORY: code exists on feature branch, not necessarily tested.
- RESEARCH: external publication; not a benchmark of AI Pulse.
- BLOCKED: requires real model, licensed dataset, GPU or Android verification.

## Target architecture
1. **Research agent**: collect public official model metadata and technical literature. Web content is untrusted; never run downloaded code automatically.
2. **Rights and model registry**: record source revision, source/weights license, dataset provenance, consent and hashes. Source license does not imply checkpoint license.
3. **Dataset pipeline**: validate consent, deduplicate by audio fingerprints, check train/validation separation, cache compatible audio latents.
4. **Training agent**: require operator approval, immutable revisions, authorized dataset, resource limits, checkpoint inventory, reproducible configuration.
5. **Inference gateway**: route jobs to actual verified backends (ACE-Step or approved alternatives), verify tensor contract and WAV output.
6. **Evaluation agent**: measure technical audio validity, latency, resource usage; add lyric accuracy, text-audio alignment, structural consistency and blinded listening tests before claiming model quality.
7. **Android app**: separate mobile playback/editing from GPU-heavy training and generation; support offline only for verified on-device models.
8. **Release gate**: require end-to-end generation and device evidence, not merely metadata or successful process exit.

## Research comparison
| Project | Architecture to study | Practical priority | Restrictions |
|---|---|---|---|
| ACE-Step 1.5 | LM planning + diffusion transformer; LoRA | Primary backend | Verify each model-weight license |
| HeartMuLa | HeartCLAP + transcription + codec + song LM | Audio understanding and alignment | Verify source and model cards |
| YuE | Long-form lyric-conditioned autoregressive modeling | Song structure/lyric evaluation | High compute; inspect weights terms |
| DiffSinger | Phonemes, MIDI, pitch, diffusion acoustic synthesis | Singing pipeline | Voicebank consent and license |
| AudioCraft MusicGen | EnCodec + autoregressive token model | Research baseline | MusicGen weights CC BY-NC 4.0 |
| Stable Audio Open | Latent diffusion audio synthesis | Instrumental/SFX alternative | Model-specific license review |

## Highest-priority gaps
- P0: Resolve actual checkpoint identities, download from official source and prove inference.
- P0: Fix CI PR path filter for tools/ai_pulse_local (fixed in commit ad4c5c3).
- P1: Verify exact Side-Step CLI against pinned upstream revision; current flags are unverified.
- P1: Create typed job contracts for model capabilities, sample rates, durations, and artifact locations.
- P2: Measure training improvement against held-out licensed audio; prevent leakage.
- P3: Integrate generation output with WAV evaluation and listening review.
- P4: Verify Android controls and installable APK against a real inference backend.

## Acceptance tests
- Official model provenance and license approval recorded.
- Checkpoint loaded and generates non-silent, unclipped PCM WAV from a fixed seed/prompt.
- Training creates an inspectable checkpoint with optimizer step count, loss curves and held-out comparison.
- End-to-end audio generation produces reproducible evidence, latency and memory measurements.
- Android app can submit a generation job, receive audio and play it without regressions.
- CI and physical-device tests pass; manual listening assessment completed.

## Sources
- https://github.com/ace-step/ACE-Step
- https://arxiv.org/abs/2602.00744
- https://arxiv.org/abs/2601.10547
- https://arxiv.org/abs/2503.08638
- https://github.com/MoonInTheRiver/DiffSinger
- https://github.com/facebookresearch/audiocraft

**Status:** architecture review and code inspection, not proof of trained PULSE-NOVA weights.
