"""Offline model evidence gate: inspect safetensors without executing model code."""
import argparse
import json
import struct
from pathlib import Path

MAX_HEADER = 100 * 1024 * 1024
DTYPE_BYTES = {"F64": 8, "F32": 4, "F16": 2, "BF16": 2, "I64": 8,
               "I32": 4, "I16": 2, "I8": 1, "U8": 1, "BOOL": 1,
               "F8_E4M3": 1, "F8_E5M2": 1}


def inspect_safetensors(path):
    path = Path(path)
    if not path.is_file() or path.is_symlink():
        raise ValueError("Expected a regular safetensors file")
    size = path.stat().st_size
    with path.open("rb") as stream:
        header_size_bytes = stream.read(8)
        if len(header_size_bytes) != 8:
            raise ValueError("Missing safetensors header")
        header_size = struct.unpack("<Q", header_size_bytes)[0]
        if not 2 <= header_size <= MAX_HEADER or 8 + header_size > size:
            raise ValueError("Invalid safetensors header size")
        header = json.loads(stream.read(header_size))
    if not isinstance(header, dict):
        raise ValueError("Invalid safetensors header")
    payload_size = size - 8 - header_size
    spans = []
    params = 0
    dtypes = set()
    for name, spec in header.items():
        if name == "__metadata__":
            continue
        if not isinstance(spec, dict) or spec.get("dtype") not in DTYPE_BYTES:
            raise ValueError("Unsupported tensor metadata")
        shape = spec.get("shape")
        offsets = spec.get("data_offsets")
        if not isinstance(shape, list) or len(shape) > 12 or any(type(n) is not int or n < 0 for n in shape):
            raise ValueError("Invalid tensor shape")
        if not isinstance(offsets, list) or len(offsets) != 2 or any(type(n) is not int for n in offsets):
            raise ValueError("Invalid tensor offsets")
        start, end = offsets
        count = 1
        for dim in shape:
            count *= dim
        if not 0 <= start <= end <= payload_size or end - start != count * DTYPE_BYTES[spec["dtype"]]:
            raise ValueError("Tensor byte count mismatch")
        spans.append((start, end))
        params += count
        dtypes.add(spec["dtype"])
    spans.sort()
    if any(a[1] > b[0] for a, b in zip(spans, spans[1:])):
        raise ValueError("Overlapping tensor storage")
    return {"status": "STRUCTURE_VALID_NOT_INFERENCE_PROVEN", "tensor_count": len(spans),
            "parameter_count": params, "dtypes": sorted(dtypes), "file_bytes": size}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model", type=Path)
    args = parser.parse_args()
    print(json.dumps(inspect_safetensors(args.model), indent=2))


if __name__ == "__main__":
    main()
