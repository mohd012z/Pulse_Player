"""P2: Convert validated, authorized singing recordings to an auditable
DiffSinger-style transcription index. This is NOT model training.
"""
import argparse
import json
from pathlib import Path
from voice_dataset import inspect_dataset


def export(dataset: Path, destination: Path):
    manifest = inspect_dataset(dataset)
    lines = []
    for clip in manifest["clips"]:
        wav = dataset / clip["audio"]
        label = json.loads((dataset / clip["annotation"]).read_text(encoding="utf-8"))
        notes = label["notes"]
        ph_seq = [n["phoneme"] for n in notes]
        ph_dur = [round(float(n["end"]) - float(n["start"]), 6) for n in notes]
        assert len(ph_seq) == len(ph_dur) and all(v > 0 for v in ph_dur)
        lines.append({
            "name": wav.stem,
            "wav_path": str(wav.resolve()),
            "ph_seq": " ".join(ph_seq),
            "ph_dur": ph_dur,
            "singer_id": manifest["singer_id"],
            "source_sha256": clip["sha256"],
        })
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in lines),
        encoding="utf-8",
    )
    return len(lines)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(f"Exported {export(args.dataset, args.output)} labeled clips")
