#!/usr/bin/env python3
import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "synthetic_intents.jsonl")
    args = parser.parse_args()

    source = ROOT / "data" / "synthetic_intents.jsonl"
    if source.resolve() == args.output.resolve():
        print(f"synthetic dataset already exists: {args.output}")
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
    print(f"wrote {args.output}")


if __name__ == "__main__":
    main()
