# AI Pulse source research manifest

This manifest is an index, not a vendored dependency. Nothing listed below has been downloaded or bundled by this commit. Verify the license of the exact revision and all transitive assets before integration.

| Project | Upstream | Intended use | Integration status |
|---|---|---|---|
| Google Oboe | https://github.com/google/oboe | Low-latency Android PCM playback | Research candidate |
| libsndfile | https://github.com/libsndfile/libsndfile | Audio file I/O | Research candidate; review LGPL compliance |
| Rubber Band | https://github.com/breakfastquay/rubberband | Time stretch / pitch shift | Research candidate; review GPL/commercial licensing |
| FluidSynth | https://github.com/FluidSynth/fluidsynth | SoundFont-based synthesis | Research candidate; SoundFont rights separate |
| Magenta DDSP | https://github.com/magenta/ddsp | Neural instrument synthesis | Research candidate; evaluate runtime and weights |
| Meta AudioCraft | https://github.com/facebookresearch/audiocraft | Optional remote generative backend | Research only; model-weight licensing differs from code |
| MusicBrainz | https://musicbrainz.org/doc/MusicBrainz_API | Recording metadata | Adapter implemented |
| Freesound | https://freesound.org/docs/api/ | Sound discovery | Research candidate; per-sound license and AI preference |
| Wikimedia Commons | https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia | Public media discovery | Research candidate; per-file attribution/license |

## Guardrail checklist
1. Identify exact repository revision, SPDX license and required notices.
2. Inspect dependencies, model weights, datasets and SoundFonts separately.
3. Reject unknown-license downloads, protected-media extraction and terms-of-service circumvention.
4. Require explicit permission for model training; never infer it from metadata visibility.
5. Scan dependencies, verify checksums, and run license and build checks in CI.
6. Keep network sources optional; core composition and project files must work offline.

## Present implementation
The app currently generates original procedural stereo PCM with separate synthesized voices. This is not a neural music model, professional sampler, DAW, or trained voice clone.
