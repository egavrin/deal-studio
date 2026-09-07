#!/usr/bin/env python3
import hashlib
import json
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "training" / "data" / "evaluation_manifest.json"


def main():
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    dataset = ROOT / manifest["dataset"]
    digest = hashlib.sha256(dataset.read_bytes()).hexdigest()
    require(digest == manifest["sha256"], "evaluation dataset digest differs from immutable manifest")

    records = [
        json.loads(line)
        for line in dataset.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    counts = Counter(record["intent"] for record in records)
    expected = set(manifest["expected_intents"])
    require(set(counts) == expected, f"intent set differs: actual={sorted(counts)}")
    minimum = int(manifest["minimum_examples_per_intent"])
    below_minimum = {intent: count for intent, count in counts.items() if count < minimum}
    require(not below_minimum, f"intent support below {minimum}: {below_minimum}")

    for index, record in enumerate(records, start=1):
        text = record["text"]
        require(text.strip() == text and text, f"invalid text at line {index}")
        for slot in record.get("slots", []):
            start = int(slot["start"])
            end = int(slot["end"])
            require(0 <= start < end <= len(text), f"invalid slot range at line {index}")
            require(
                text[start:end] == slot["value"],
                f"slot text mismatch at line {index}: {slot}",
            )

    gates = manifest["quality_gates"]
    for name in ("intent_accuracy", "intent_macro_f1", "slot_f1", "validation_pass_rate"):
        require(name in gates and 0.0 < float(gates[name]) <= 1.0, f"invalid quality gate: {name}")

    print(
        f"nlu_eval_manifest_ok name={manifest['name']} cases={len(records)} "
        f"intents={len(counts)} sha256={digest}"
    )


def require(condition, message):
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    main()
