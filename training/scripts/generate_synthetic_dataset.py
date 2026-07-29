#!/usr/bin/env python3
import runpy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


if __name__ == "__main__":
    runpy.run_path(
        str(ROOT / "training" / "rubert" / "generate_core_dataset.py"),
        run_name="__main__",
    )
