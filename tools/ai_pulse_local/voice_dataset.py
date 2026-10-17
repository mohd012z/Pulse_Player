"""Validate consented singing recordings and prepare a reproducible training manifest.
No model training is claimed; this is the data-ingestion stage.
"""
import argparse
import hashlib
import json
import wave
from pathlib import Path

def inspect_dataset(root: Path):
    root = root.resolve()
    metadata = root / "consent.json"
    if not metadata.is_file():
        raise ValueError("consent.json is required")
    consent = json.loads(metadata.read_text(encoding="utf-8"))
    if consent.get("voice_consent") not in ("owner", "licensed"):
        raise ValueError("Explicit owner or licensed consent required")
    if not consent.get("license") or not consent.get("singer_id"):
        raise ValueError("Missing license or singer_id")
    records = []
    for audio in sorted(root.glob("audio/*.wav")):
        with wave.open(str(audio), "rb") as wav:
            channels = wav.getnchannels()
            rate = wav.getframerate()
            width = wav.getsampwidth()
            frames = wav.getnframes()
            if channels != 1 or width != 2 or rate not in (22050, 24000, 44100, 48000):
                raise ValueError(f"Unsupported PCM WAV format: {audio.name}")
            if frames <= 0 or frames / rate > 60:
                raise ValueError(f"Invalid clip duration: {audio.name}")
        annotation = root / "labels" / (audio.stem + ".json")
        if not annotation.is_file():
            raise ValueError(f"Missing note/phoneme annotation: {annotation.name}")
        labels = json.loads(annotation.read_text(encoding="utf-8"))
        notes = labels.get("notes", [])
        if not notes:
            raise ValueError(f"No aligned notes: {annotation.name}")
        previous = 0.0
        for note in notes:
            start, end = float(note["start"]), float(note["end"])
            pitch = int(note["midi"])
            if not (0 <= previous <= start < end <= frames / rate and 0 <= pitch <= 127):
                raise ValueError(f"Invalid alignment: {annotation.name}")
            if not isinstance(note.get("phoneme"), str) or not note["phoneme"].strip():
                raise ValueError(f"Missing phoneme: {annotation.name}")
            previous = end
        records.append({"audio": str(audio.relative_to(root)),
                        "annotation": str(annotation.relative_to(root)),
                        "seconds": round(frames / rate, 3), "sample_rate": rate,
                        "sha256": hashlib.sha256(audio.read_bytes()).hexdigest()})
    if not records:
        raise ValueError("No singing WAV recordings found")
    return {"singer_id": consent["singer_id"], "license": consent["license"],
            "voice_consent": consent["voice_consent"], "clips": records}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = inspect_dataset(args.dataset)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Validated {len(result['clips'])} singing clips; manifest: {args.output}")

if __name__ == "__main__":
    main()
