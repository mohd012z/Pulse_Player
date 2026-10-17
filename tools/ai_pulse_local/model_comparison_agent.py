"""Evidence-only paired music model comparison. No subjective scores invented."""
import argparse
import json
from pathlib import Path
from music_evaluation_agent import evaluate_wav

def compare(manifest):
    if not isinstance(manifest, dict) or not isinstance(manifest.get("pairs"), list) or not manifest["pairs"]:
        raise ValueError("Expected nonempty pairs")
    results = []
    for pair in manifest["pairs"]:
        if not all(k in pair for k in ("prompt_id", "seed", "baseline_wav", "candidate_wav")):
            raise ValueError("Pair requires prompt_id, seed, baseline_wav, candidate_wav")
        baseline = evaluate_wav(pair["baseline_wav"])
        candidate = evaluate_wav(pair["candidate_wav"])
        results.append({
            "prompt_id": pair["prompt_id"], "seed": pair["seed"],
            "baseline": baseline, "candidate": candidate,
            "duration_delta_seconds": round(candidate["duration_seconds"] - baseline["duration_seconds"], 3),
            "rms_delta_db": round(candidate["rms_dbfs"] - baseline["rms_dbfs"], 2),
            "candidate_technical_pass": not candidate["issues"],
            "perceptual_winner": "UNDETERMINED",
        })
    return {"status": "TECHNICAL_COMPARISON_ONLY", "pairs_evaluated": len(results),
            "results": results, "model_improvement_proven": False,
            "missing_evidence": ["matched generation configuration", "audio-text alignment",
                                 "lyric accuracy", "blind listening assessment",
                                 "latency and memory measurements"]}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.manifest.read_text(encoding="utf-8"))
    result = compare(data)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "pairs_evaluated": result["pairs_evaluated"]}))

if __name__ == "__main__":
    main()
