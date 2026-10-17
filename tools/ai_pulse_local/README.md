# AI Pulse offline WAV proof of concept

This example generates a real PCM WAV without GGUF, GPU, API credentials or external libraries. It is **not neural singing**.

Run: `python tools/ai_pulse_local/local_wav.py --output song.wav --seconds 8`.

Neural singing requires a trained singer/acoustic model and an on-device inference runtime (for example ONNX Runtime), appropriate licensed weights, and note/phoneme conditioning.
