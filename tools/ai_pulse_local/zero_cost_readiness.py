"""Zero-cost PULSE-NOVA readiness gate. No cloud accounts, downloads or payments.

Distinguishes runnable prerequisites from actual neural inference proof.
"""
import argparse
import json
import platform
import shutil
import subprocess
from pathlib import Path

def assess(*, model_root=None, audio_path=None, disk_path=None):
    root = Path(model_root).expanduser() if model_root else None
    disk = Path(disk_path or ".").resolve()
    if not disk.exists():
        raise ValueError("Disk path does not exist")
    free_gib = round(shutil.disk_usage(disk).free / (1024 ** 3), 2)
    python = platform.python_version()
    gpu = {"cuda_available": None, "name": None, "reason": "torch_not_installed"}
    try:
        import torch
        gpu["cuda_available"] = bool(torch.cuda.is_available())
        gpu["name"] = torch.cuda.get_device_name(0) if gpu["cuda_available"] else None
        gpu["reason"] = "torch_checked"
    except ImportError:
        pass
    checkpoint_files = []
    if root and root.is_dir():
        checkpoint_files = sorted(str(x.relative_to(root)) for x in root.rglob("*.safetensors")
                                  if x.is_file() and not x.is_symlink())[:200]
    audio = None
    if audio_path:
        from music_evaluation_agent import evaluate_wav
        audio = evaluate_wav(audio_path)
    return {
        "mode": "ZERO_PAID_SERVICES",
        "system": platform.system(), "python": python,
        "free_disk_gib": free_gib, "gpu": gpu,
        "model_directory_exists": bool(root and root.is_dir()),
        "checkpoint_candidates": checkpoint_files,
        "audio_evidence": audio,
        "neural_inference_proven": False,
        "recommended_path": ("LOCAL_GPU_ACE_STEP" if gpu["cuda_available"] else
                             "LOCAL_CPU_ACE_STEP_SLOW"),
        "blockers": [
            *([] if free_gib >= 10 else ["LESS_THAN_10_GIB_FREE_DISK"]),
            *([] if checkpoint_files else ["NO_LOCAL_SAFETENSORS_CHECKPOINT"]),
            *([] if audio else ["NO_GENERATED_AUDIO_EVIDENCE"]),
        ],
        "disclaimer": "Readiness is not proof of neural model inference or training.",
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-root")
    parser.add_argument("--audio")
    parser.add_argument("--disk-path")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = assess(model_root=args.model_root, audio_path=args.audio,
                    disk_path=args.disk_path)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))

if __name__ == "__main__":
    main()
