#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


DEFAULT_MIN_CONFIDENCE = 0.75
STRICT_MIN_CONFIDENCE = 0.8
STRICT_INTENTS = {
    "get_weather",
    "set_timer",
    "set_alarm",
    "calculate",
    "create_note",
    "create_reminder",
    "open_app",
}


def main():
    import onnxruntime as ort
    from transformers import AutoTokenizer

    args = parse_args()
    model_dir = args.model_dir
    eval_cases = load_eval_cases(args.eval_set)
    intent_labels = read_labels(model_dir / "intent_labels.txt")
    slot_labels = read_labels(model_dir / "slot_labels.txt")
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    session = ort.InferenceSession(
        str(model_dir / "rubert-tiny2-intent-slots.onnx"),
        providers=["CPUExecutionProvider"],
    )
    output_names = [output.name for output in session.get_outputs()]
    if "intent_logits" not in output_names or "slot_logits" not in output_names:
        raise RuntimeError(f"Expected intent_logits and slot_logits outputs, got {output_names}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    results = []
    with args.output.open("w", encoding="utf-8") as handle:
        for case in eval_cases:
            result = evaluate_case(
                case=case,
                tokenizer=tokenizer,
                session=session,
                intent_labels=intent_labels,
                slot_labels=slot_labels,
                max_length=args.max_length,
            )
            results.append(result)
            public_result = {key: value for key, value in result.items() if not key.startswith("_")}
            handle.write(json.dumps(public_result, ensure_ascii=False, sort_keys=True) + "\n")

    metrics = summarize_results(results)
    metrics_output = args.metrics_output or args.output.with_name(f"{args.output.stem}-metrics.json")
    metrics_output.parent.mkdir(parents=True, exist_ok=True)
    metrics_output.write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    failures = [result for result in results if result["gate"] and not result["exact"]]
    metric_failures = metric_gate_failures(
        metrics,
        min_intent_accuracy=args.min_intent_accuracy,
        min_macro_f1=args.min_macro_f1,
        min_slot_f1=args.min_slot_f1,
        min_validation_pass_rate=args.min_validation_pass_rate,
    )

    if failures or metric_failures:
        summary = "; ".join(
            f"{item['text']}: intent={item['actual_intent']} confidence={item['confidence']:.3f} slots={item['actual_slots']}"
            for item in failures
        )
        details = "; ".join(part for part in (summary, "; ".join(metric_failures)) if part)
        raise RuntimeError(f"RuBERT host export eval failed: {details}")

    print(
        f"rubert_host_eval cases={len(results)} exact={metrics['exact_match_count']} "
        f"regression_gate={metrics['regression_gate_pass_count']}/{metrics['regression_gate_count']} "
        f"intent_accuracy={metrics['intent_accuracy']:.3f} macro_f1={metrics['intent_macro_f1']:.3f} "
        f"slot_f1={metrics['slot_f1']:.3f} validation_pass_rate={metrics['validation_pass_rate']:.3f} "
        f"output={args.output} metrics={metrics_output}"
    )


def evaluate_case(case, tokenizer, session, intent_labels, slot_labels, max_length):
    import numpy as np

    text = case["text"]
    encoded = tokenizer(
        text,
        max_length=max_length,
        padding="max_length",
        truncation=True,
        return_tensors="np",
        return_offsets_mapping=True,
    )
    offsets = encoded.pop("offset_mapping")[0]
    inputs = {key: value.astype("int64") for key, value in encoded.items()}
    inputs.setdefault("token_type_ids", np.zeros_like(inputs["input_ids"]))

    intent_logits, slot_logits = session.run(["intent_logits", "slot_logits"], inputs)
    probabilities = softmax(intent_logits[0])
    intent_index = int(probabilities.argmax())
    actual_intent = intent_labels[intent_index]
    confidence = float(probabilities[intent_index])
    actual_slots = decode_slots(text, offsets, inputs["attention_mask"][0], slot_logits[0], slot_labels)
    slot_counts = compare_slot_labels(
        intent=case["intent"],
        raw_slots=case["raw_slots"],
        offsets=offsets,
        attention_mask=inputs["attention_mask"][0],
        predicted_logits=slot_logits[0],
        slot_labels=slot_labels,
    )
    expected_slots = case["slots"]
    exact = (
        actual_intent == case["intent"]
        and confidence >= case["min_confidence"]
        and (
            not case["require_slots"]
            or all(actual_slots.get(key) == value for key, value in expected_slots.items())
        )
    )
    return {
        "text": text,
        "expected_intent": case["intent"],
        "actual_intent": actual_intent,
        "confidence": confidence,
        "expected_slots": expected_slots,
        "actual_slots": actual_slots,
        "exact": exact,
        "gate": case["gate"],
        "_slot_tp": slot_counts["tp"],
        "_slot_fp": slot_counts["fp"],
        "_slot_fn": slot_counts["fn"],
    }


def decode_slots(text, offsets, attention_mask, slot_logits, slot_labels):
    spans = []
    active_name = None
    active_start = -1
    active_end = -1

    def flush():
        nonlocal active_name, active_start, active_end
        if active_name and active_start >= 0 and active_end > active_start:
            spans.append((active_name, active_start, active_end))
        active_name = None
        active_start = -1
        active_end = -1

    for index, ((start, end), is_active) in enumerate(zip(offsets, attention_mask)):
        start = int(start)
        end = int(end)
        if not is_active or start == end:
            continue
        label = slot_labels[int(slot_logits[index].argmax())]
        if label == "O" or "-" not in label:
            flush()
            continue
        prefix, name = label.split("-", 1)
        if prefix == "B" or active_name != name or start > active_end + 1:
            flush()
            active_name = name
            active_start = start
            active_end = end
        else:
            active_end = end
    flush()

    slots = {}
    for name, start, end in spans:
        raw = text[start:end].strip().rstrip("?.!,")
        if not any(character.isalnum() for character in raw):
            continue
        if name == "duration":
            value = normalize_duration_seconds(raw)
            if value:
                slots["duration_seconds"] = str(value)
        elif name == "time":
            value = normalize_time(raw)
            if value:
                slots["time"] = value
        elif name == "location":
            slots["location"] = raw
        elif name == "expression":
            value = normalize_expression(raw)
            if value:
                slots["expression"] = value
        elif name == "note_text":
            slots["text"] = raw
        elif name == "reminder_text":
            slots["reminder_text"] = raw
        elif name == "app_name":
            slots["app_name"] = raw
        elif name == "percent":
            value = first_int(raw)
            if value is not None:
                slots["percent"] = str(value)
        else:
            slots[name] = raw
    return slots


def normalize_duration_seconds(raw):
    lower = raw.lower().strip()
    number = first_int(lower)
    if number is None:
        if "одну" in lower or "один" in lower:
            number = 1
        elif "две" in lower or "два" in lower:
            number = 2
        elif "три" in lower:
            number = 3
        elif "четыре" in lower:
            number = 4
        elif "пять" in lower:
            number = 5
        elif "семь" in lower:
            number = 7
        elif "десять" in lower:
            number = 10
        elif "час" in lower:
            number = 1
    if number is None:
        return None
    return number * 3600 if "час" in lower else number * 60


def normalize_time(raw):
    import re

    match = re.search(r"([01]?\d|2[0-3])[:.]([0-5]\d)", raw)
    if not match:
        return None
    return f"{int(match.group(1)):02d}:{int(match.group(2)):02d}"


def normalize_expression(raw):
    import re

    match = re.search(
        r"(\d+)\s*(умножить на|разделить на|поделить на|делить на|\*|/|÷|плюс|\+|минус|-)\s*(\d+)",
        raw.lower(),
    )
    if not match:
        return None
    operator = {
        "умножить на": "*",
        "*": "*",
        "разделить на": "/",
        "поделить на": "/",
        "делить на": "/",
        "/": "/",
        "÷": "/",
        "плюс": "+",
        "+": "+",
        "минус": "-",
        "-": "-",
    }[match.group(2)]
    return f"{match.group(1)} {operator} {match.group(3)}"


def first_int(text):
    import re

    match = re.search(r"\d+", text)
    return int(match.group(0)) if match else None


def load_eval_cases(path):
    cases = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            row = json.loads(line)
            raw_slots = row.get("slots", [])
            cases.append(
                {
                    "text": row["text"],
                    "intent": row["intent"],
                    "min_confidence": (
                        STRICT_MIN_CONFIDENCE if row["intent"] in STRICT_INTENTS else DEFAULT_MIN_CONFIDENCE
                    ),
                    "slots": normalize_expected_slots(row["intent"], raw_slots),
                    "raw_slots": raw_slots,
                    "gate": row.get("gate", row["intent"] in STRICT_INTENTS),
                    "require_slots": row.get("require_slots", True),
                    "line_number": line_number,
                }
            )
    if not cases:
        raise RuntimeError(f"No evaluation cases found in {path}")
    return cases


def normalize_expected_slots(intent, raw_slots):
    normalized = {}
    for slot in raw_slots:
        name = slot["name"]
        value = slot["value"]
        if name == "duration":
            duration = normalize_duration_seconds(value)
            if duration is not None:
                normalized["duration_seconds"] = str(duration)
        elif name == "time":
            time = normalize_time(value)
            if time is not None:
                normalized["time"] = time
        elif name == "location":
            normalized["location"] = value
        elif name == "expression":
            expression = normalize_expression(value)
            if expression is not None:
                normalized["expression"] = expression
        elif name in {"text", "note_text"} and intent == "create_note":
            normalized["text"] = value
        elif name in {"text", "reminder_text"} and intent == "create_reminder":
            normalized["reminder_text"] = value
        elif name == "app_name":
            normalized["app_name"] = value
        elif name == "percent":
            percent = first_int(value)
            if percent is not None:
                normalized["percent"] = str(percent)
        else:
            normalized[name] = value
    return normalized


def compare_slot_labels(intent, raw_slots, offsets, attention_mask, predicted_logits, slot_labels):
    expected = expected_slot_labels(intent, raw_slots, offsets, attention_mask)
    predicted = [slot_labels[int(logits.argmax())] for logits in predicted_logits]
    true_positive = 0
    false_positive = 0
    false_negative = 0
    for index, ((start, end), is_active) in enumerate(zip(offsets, attention_mask)):
        if not is_active or int(start) == int(end):
            continue
        expected_label = expected[index]
        predicted_label = predicted[index]
        expected_named = expected_label != "O"
        predicted_named = predicted_label != "O"
        if expected_named and predicted_label == expected_label:
            true_positive += 1
        else:
            if predicted_named:
                false_positive += 1
            if expected_named:
                false_negative += 1
    return {"tp": true_positive, "fp": false_positive, "fn": false_negative}


def expected_slot_labels(intent, raw_slots, offsets, attention_mask):
    labels = ["O"] * len(offsets)
    for index, ((start, end), is_active) in enumerate(zip(offsets, attention_mask)):
        start = int(start)
        end = int(end)
        if not is_active or start == end:
            continue
        for slot in raw_slots:
            slot_start = int(slot["start"])
            slot_end = int(slot["end"])
            if start >= slot_start and end <= slot_end:
                slot_name = dataset_slot_label(intent, slot["name"])
                prefix = "B" if start == slot_start else "I"
                labels[index] = f"{prefix}-{slot_name}"
                break
    return labels


def dataset_slot_label(intent, name):
    if name == "text":
        return "reminder_text" if intent == "create_reminder" else "text"
    if name == "datetime":
        return "date"
    return name


def summarize_results(results):
    case_count = len(results)
    intents = sorted(
        {result["expected_intent"] for result in results}
        | {result["actual_intent"] for result in results}
    )
    correct = sum(result["expected_intent"] == result["actual_intent"] for result in results)
    per_intent = {}
    confusion = {}
    f1_values = []
    for intent in intents:
        true_positive = sum(
            result["expected_intent"] == intent and result["actual_intent"] == intent for result in results
        )
        false_positive = sum(
            result["expected_intent"] != intent and result["actual_intent"] == intent for result in results
        )
        false_negative = sum(
            result["expected_intent"] == intent and result["actual_intent"] != intent for result in results
        )
        precision = ratio(true_positive, true_positive + false_positive)
        recall = ratio(true_positive, true_positive + false_negative)
        f1 = ratio(2 * precision * recall, precision + recall)
        f1_values.append(f1)
        per_intent[intent] = {
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "support": sum(result["expected_intent"] == intent for result in results),
        }
    for result in results:
        expected = result["expected_intent"]
        actual = result["actual_intent"]
        confusion.setdefault(expected, {})[actual] = confusion.setdefault(expected, {}).get(actual, 0) + 1

    slot_tp = sum(result["_slot_tp"] for result in results)
    slot_fp = sum(result["_slot_fp"] for result in results)
    slot_fn = sum(result["_slot_fn"] for result in results)
    slot_precision = ratio(slot_tp, slot_tp + slot_fp)
    slot_recall = ratio(slot_tp, slot_tp + slot_fn)
    slot_f1 = ratio(2 * slot_precision * slot_recall, slot_precision + slot_recall)
    return {
        "schema_version": 1,
        "case_count": case_count,
        "intent_accuracy": ratio(correct, case_count),
        "intent_macro_f1": ratio(sum(f1_values), len(f1_values)),
        "slot_precision": slot_precision,
        "slot_recall": slot_recall,
        "slot_f1": slot_f1,
        "validation_pass_rate": ratio(sum(result["exact"] for result in results), case_count),
        "exact_match_count": sum(result["exact"] for result in results),
        "regression_gate_count": sum(result["gate"] for result in results),
        "regression_gate_pass_count": sum(result["gate"] and result["exact"] for result in results),
        "per_intent": per_intent,
        "confusion_matrix": confusion,
    }


def ratio(numerator, denominator):
    return float(numerator) / float(denominator) if denominator else 0.0


def metric_gate_failures(
    metrics,
    min_intent_accuracy=0.0,
    min_macro_f1=0.0,
    min_slot_f1=0.0,
    min_validation_pass_rate=0.0,
):
    thresholds = {
        "intent_accuracy": min_intent_accuracy,
        "intent_macro_f1": min_macro_f1,
        "slot_f1": min_slot_f1,
        "validation_pass_rate": min_validation_pass_rate,
    }
    return [
        f"{name}={metrics[name]:.3f} below {minimum:.3f}"
        for name, minimum in thresholds.items()
        if metrics[name] < minimum
    ]


def read_labels(path):
    return [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def softmax(values):
    import numpy as np

    shifted = values - values.max()
    exps = np.exp(shifted)
    return exps / exps.sum()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, default=Path("models/generated/rubert"))
    parser.add_argument("--eval-set", type=Path, default=Path("training/data/eval_set.jsonl"))
    parser.add_argument("--output", type=Path, default=Path("build/rubert-host-eval.jsonl"))
    parser.add_argument("--metrics-output", type=Path)
    parser.add_argument("--max-length", type=int, default=64)
    parser.add_argument("--min-intent-accuracy", type=float, default=0.0)
    parser.add_argument("--min-macro-f1", type=float, default=0.0)
    parser.add_argument("--min-slot-f1", type=float, default=0.0)
    parser.add_argument("--min-validation-pass-rate", type=float, default=0.0)
    return parser.parse_args()


if __name__ == "__main__":
    main()
