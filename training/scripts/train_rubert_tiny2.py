#!/usr/bin/env python3
import runpy
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


if __name__ == "__main__":
    if "--dataset" not in sys.argv:
        sys.argv.extend(["--dataset", str(ROOT / "training" / "data" / "synthetic_intents.jsonl")])
    runpy.run_path(str(ROOT / "training" / "rubert" / "train_export.py"), run_name="__main__")
