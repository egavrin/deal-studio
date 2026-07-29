#!/usr/bin/env python3
import argparse
import hashlib
import json
import random
from collections import Counter
from pathlib import Path

import onnx
import numpy as np
import onnxruntime as ort
import torch
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset, random_split
from transformers import AutoModel, AutoTokenizer


INTENTS = [
    "get_current_time",
    "get_weather",
    "set_timer",
    "set_alarm",
    "create_reminder",
    "create_note",
    "calculate",
    "open_app",
    "help",
    "web_search",
    "web_research",
    "unknown",
]

SLOT_NAMES = [
    "duration",
    "time",
    "date",
    "location",
    "text",
    "reminder_text",
    "app_name",
    "expression",
    "label",
    "repeat",
    "package_name",
]
SLOT_LABELS = ["O"] + [f"{prefix}-{name}" for name in SLOT_NAMES for prefix in ("B", "I")]


EXPORT_INTENT_CHECKS = [
    ("Покажи погоду в Санкт-Петербурге", "get_weather", 0.8, True),
    ("Будильник на 08.15", "set_alarm", 0.8, True),
    ("Поставь таймер на час", "set_timer", 0.8, True),
    ("Сколько будет 18 плюс 24", "calculate", 0.75, True),
    ("Поставь таймер", "set_timer", 0.75, False),
    ("Какая погода сегодня?", "get_weather", 0.75, False),
    ("Помощь", "help", 0.75, False),
    ("Напомни через час проверить духовку", "create_reminder", 0.75, True),
    ("Разбуди меня завтра", "set_alarm", 0.75, False),
    ("Что нового в Android 17", "web_search", 0.75, False),
    ("Проведи исследование конкурентов Perplexity", "web_research", 0.75, False),
]

DEFAULT_BASE_MODEL = "cointegrated/rubert-tiny2"
DEFAULT_BASE_MODEL_REVISION = "e8ed3b0c8bbf4fb6984c3de043bf7d2f4e5969ae"


def resolve_base_model_revision(base_model, requested_revision):
    if requested_revision is not None:
        return requested_revision
    if base_model == DEFAULT_BASE_MODEL:
        return DEFAULT_BASE_MODEL_REVISION
    return None


class IntentDataset(Dataset):
    def __init__(self, rows, tokenizer, max_length):
        self.rows = rows
        self.tokenizer = tokenizer
        self.max_length = max_length
        self.label_to_id = {label: index for index, label in enumerate(INTENTS)}

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        row = self.rows[index]
        encoded = self.tokenizer(
            row["text"],
            max_length=self.max_length,
            padding="max_length",
            truncation=True,
            return_tensors="pt",
            return_offsets_mapping=True,
        )
        offsets = encoded.pop("offset_mapping").squeeze(0).tolist()
        item = {key: value.squeeze(0) for key, value in encoded.items()}
        item["intent_labels"] = torch.tensor(self.label_to_id[row["intent"]], dtype=torch.long)
        item["slot_labels"] = torch.tensor(
            slot_labels_for_text(row, offsets, item["attention_mask"].tolist()),
            dtype=torch.long,
        )
        return item


class JointIntentSlotModel(torch.nn.Module):
    def __init__(self, base_model_name=None, *, revision=None, encoder=None):
        super().__init__()
        if encoder is None:
            if base_model_name is None:
                raise ValueError("base_model_name or encoder is required")
            encoder = AutoModel.from_pretrained(
                base_model_name,
                revision=revision,
                trust_remote_code=False,
                use_safetensors=True,
            )
        self.encoder = encoder
        hidden_size = self.encoder.config.hidden_size
        self.dropout = torch.nn.Dropout(0.1)
        self.intent_classifier = torch.nn.Linear(hidden_size, len(INTENTS))
        self.slot_classifier = torch.nn.Linear(hidden_size, len(SLOT_LABELS))

    def features(self, input_ids, attention_mask, token_type_ids=None):
        output = self.encoder(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
        )
        sequence_output = self.dropout(output.last_hidden_state)
        return sequence_output, sequence_output[:, 0]

    def forward(self, input_ids, attention_mask, token_type_ids=None):
        sequence_output, pooled_output = self.features(input_ids, attention_mask, token_type_ids)
        intent_logits = self.intent_classifier(pooled_output)
        slot_logits = self.slot_classifier(sequence_output)
        return intent_logits, slot_logits

class JointOnnxWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, token_type_ids):
        return self.model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
        )


def slot_labels_for_text(row, offsets, attention_mask):
    labels = [SLOT_LABELS.index("O")] * len(offsets)
    spans = explicit_slot_spans(row) or infer_slot_spans(row["text"])
    for index, ((start, end), is_active) in enumerate(zip(offsets, attention_mask)):
        if not is_active or start == end:
            labels[index] = -100
            continue
        for slot_name, span_start, span_end in spans:
            if start >= span_start and end <= span_end:
                prefix = "B" if start == span_start else "I"
                label = f"{prefix}-{slot_name}"
                labels[index] = SLOT_LABELS.index(label)
                break
    return labels


def explicit_slot_spans(row):
    spans = []
    for slot in row.get("slots", []):
        name = slot["name"]
        if name not in SLOT_NAMES:
            raise ValueError(f"Unsupported slot label: {name}")
        spans.append((name, int(slot["start"]), int(slot["end"])))
    return spans


def infer_slot_spans(text):
    lower = text.lower()
    spans = []
    add_regex_span(spans, lower, r"\b\d+\s*(?:минут|мин|мину|час|часа|часов)\b", "duration")
    add_regex_span(
        spans,
        lower,
        r"\b(?:одну|один|две|два|три|четыре|пять|десять|семь)\s*(?:минуту|минуты|минут|час|часа|часов|утра)\b",
        "duration",
    )
    add_regex_span(spans, lower, r"\bчас\b", "duration")
    add_regex_span(spans, lower, r"\b(?:[01]?\d|2[0-3])[:.]([0-5]\d)\b", "time")
    add_weather_location_span(spans, text, lower)
    add_after_marker(spans, text, lower, ["открой приложение ", "открой ", "запусти "], "app_name")
    add_after_marker(spans, text, lower, ["запиши заметку ", "создай заметку ", "заметка: ", "заметка ", "запиши "], "text")
    add_after_marker(spans, text, lower, ["создай напоминание ", "напоминание на вечер ", "напомни завтра ", "напомни через час ", "напомни "], "reminder_text")
    add_regex_span(
        spans,
        lower,
        r"\b\d+\s*(?:умножить на|разделить на|поделить на|делить на|\*|/|÷|плюс|\+|минус|-)\s*\d+\b",
        "expression",
    )
    return spans


def add_regex_span(spans, lower, pattern, slot_name):
    import re
    match = re.search(pattern, lower)
    if match:
        spans.append((slot_name, match.start(), match.end()))


def add_after_marker(spans, original, lower, markers, slot_name):
    for marker in markers:
        index = lower.find(marker)
        if index >= 0:
            start = index + len(marker)
            end = len(original.rstrip("?.! "))
            if start < end:
                spans.append((slot_name, start, end))
            return


def add_weather_location_span(spans, original, lower):
    for marker in ["погода в ", "погоду в "]:
        index = lower.find(marker)
        if index < 0:
            continue
        start = index + len(marker)
        end = len(original.rstrip("?.! "))
        tail = lower[start:end]
        for stop_word in [" сегодня", " сейчас", " завтра"]:
            stop_index = tail.find(stop_word)
            if stop_index >= 0:
                end = start + stop_index
                break
        if start < end:
            spans.append(("location", start, end))
        return


def load_rows(path):
    rows = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                row = json.loads(line)
                if row["intent"] not in INTENTS:
                    raise ValueError(f"Unsupported intent in dataset: {row['intent']}")
                rows.append(row)
    if len(rows) < len(INTENTS):
        raise ValueError("Dataset is too small to cover all intents")
    return rows


def train(args):
    random.seed(args.seed)
    torch.manual_seed(args.seed)
    rows = load_rows(args.dataset)
    random.shuffle(rows)
    base_model_revision = resolve_base_model_revision(args.base_model, args.base_model_revision)

    tokenizer = AutoTokenizer.from_pretrained(
        args.base_model,
        revision=base_model_revision,
        trust_remote_code=False,
    )
    model = JointIntentSlotModel(args.base_model, revision=base_model_revision)
    if args.resume_from is not None:
        state = torch.load(args.resume_from, map_location="cpu", weights_only=True)
        model.load_state_dict(state)
        print(f"resumed_from={args.resume_from}")

    dataset = IntentDataset(rows, tokenizer, args.max_length)
    train_size = max(1, int(len(dataset) * 0.85))
    eval_size = len(dataset) - train_size
    train_dataset, eval_dataset = random_split(
        dataset,
        [train_size, eval_size],
        generator=torch.Generator().manual_seed(args.seed),
    )

    loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate)
    intent_counts = Counter(row["intent"] for row in rows)
    intent_class_weights = torch.tensor(
        [
            min(3.0, max(0.5, len(rows) / (len(INTENTS) * intent_counts[intent])))
            for intent in INTENTS
        ],
        dtype=torch.float,
    )
    slot_class_weights = torch.ones(len(SLOT_LABELS), dtype=torch.float)
    slot_class_weights[SLOT_LABELS.index("O")] = args.outside_slot_weight
    for index, label in enumerate(SLOT_LABELS):
        if label != "O":
            slot_class_weights[index] = args.named_slot_weight
    model.train()
    for epoch in range(args.epochs):
        total_loss = 0.0
        for batch in loader:
            optimizer.zero_grad()
            intent_labels = batch.pop("intent_labels")
            slot_labels = batch.pop("slot_labels")
            intent_logits, slot_logits = model(**batch)
            intent_loss = F.cross_entropy(
                intent_logits,
                intent_labels,
                weight=intent_class_weights.to(intent_logits.device),
            )
            slot_loss = F.cross_entropy(
                slot_logits.view(-1, len(SLOT_LABELS)),
                slot_labels.view(-1),
                weight=slot_class_weights.to(slot_logits.device),
                ignore_index=-100,
            )
            loss = intent_loss + (args.slot_loss_weight * slot_loss)
            loss.backward()
            optimizer.step()
            total_loss += float(loss.detach())
        print(f"epoch={epoch + 1} loss={total_loss / max(1, len(loader)):.4f}")

    if eval_size > 0:
        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for batch in DataLoader(eval_dataset, batch_size=args.batch_size):
                intent_labels = batch.pop("intent_labels")
                batch.pop("slot_labels")
                intent_logits, _ = model(**batch)
                correct += int((intent_logits.argmax(dim=-1) == intent_labels).sum())
                total += int(intent_labels.numel())
        print(f"eval_accuracy={correct / max(1, total):.3f}")

    export_bundle(model, tokenizer, args)


def export_bundle(model, tokenizer, args):
    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    tokenizer.save_pretrained(output_dir)
    ensure_special_tokens_map(tokenizer, output_dir)
    ordered_vocab = sorted(tokenizer.get_vocab().items(), key=lambda item: item[1])
    (output_dir / "vocab.txt").write_text(
        "".join(f"{token}\n" for token, _ in ordered_vocab),
        encoding="utf-8",
    )

    (output_dir / "intent_labels.txt").write_text("\n".join(INTENTS) + "\n", encoding="utf-8")
    (output_dir / "slot_labels.txt").write_text("\n".join(SLOT_LABELS) + "\n", encoding="utf-8")
    torch.save(model.state_dict(), output_dir / "training-checkpoint.pt")

    sample = tokenizer(
        "Поставь таймер на 5 минут",
        max_length=args.max_length,
        padding="max_length",
        truncation=True,
        return_tensors="pt",
    )
    onnx_path = output_dir / "rubert-tiny2-intent-slots.onnx"
    export_joint_onnx(model, sample, onnx_path)
    verify_onnx(onnx_path, sample, tokenizer, args.max_length)
    write_training_manifest(
        args=args,
        rows=load_rows(args.dataset),
        base_model_revision=resolve_base_model_revision(args.base_model, args.base_model_revision),
        output_dir=output_dir,
    )
    print(f"exported={output_dir}")


def ensure_special_tokens_map(tokenizer, output_dir):
    """Keeps the runtime bundle stable across Transformers tokenizer serializers."""
    path = output_dir / "special_tokens_map.json"
    if path.is_file():
        return
    path.write_text(
        json.dumps(tokenizer.special_tokens_map, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_training_manifest(args, rows, base_model_revision, output_dir):
    bundle_names = (
        "rubert-tiny2-intent-slots.onnx",
        "vocab.txt",
        "intent_labels.txt",
        "slot_labels.txt",
        "tokenizer.json",
        "tokenizer_config.json",
        "special_tokens_map.json",
    )
    missing = [name for name in bundle_names if not (output_dir / name).is_file()]
    if missing:
        raise RuntimeError(f"Cannot write RuBERT manifest; missing bundle files: {missing}")
    dataset_path = args.dataset.resolve()
    resume_path = args.resume_from.resolve() if args.resume_from is not None else None
    manifest = {
        "schema_version": 1,
        "task": "joint_intent_and_bio_slot_classification",
        "base_model": args.base_model,
        "base_model_revision": base_model_revision,
        "dataset": str(args.dataset),
        "dataset_sha256": sha256_file(dataset_path),
        "dataset_rows": len(rows),
        "intent_count": len(INTENTS),
        "slot_label_count": len(SLOT_LABELS),
        "training": {
            "epochs": args.epochs,
            "batch_size": args.batch_size,
            "learning_rate": args.learning_rate,
            "max_length": args.max_length,
            "seed": args.seed,
            "slot_loss_weight": args.slot_loss_weight,
            "outside_slot_weight": args.outside_slot_weight,
            "named_slot_weight": args.named_slot_weight,
            "resumed_from_sha256": sha256_file(resume_path) if resume_path else None,
        },
        "onnx_contract": {
            "opset": 18,
            "inputs": ["input_ids", "attention_mask", "token_type_ids"],
            "outputs": ["intent_logits", "slot_logits"],
        },
        "bundle_sha256": {
            name: sha256_file(output_dir / name)
            for name in bundle_names
        },
    }
    (output_dir / "rubert-training-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def export_joint_onnx(model, sample, onnx_path):
    model.eval()
    wrapper = JointOnnxWrapper(model).eval()
    export_sample = {
        "input_ids": ensure_dynamic_batch(sample["input_ids"]),
        "attention_mask": ensure_dynamic_batch(sample["attention_mask"]),
        "token_type_ids": ensure_dynamic_batch(
            sample.get("token_type_ids", torch.zeros_like(sample["input_ids"]))
        ),
    }
    values = (
        export_sample["input_ids"],
        export_sample["attention_mask"],
        export_sample["token_type_ids"],
    )
    if modern_onnx_export_available():
        batch = torch.export.Dim("batch", min=1, max=64)
        sequence = torch.export.Dim(
            "sequence",
            min=2,
            max=model.encoder.config.max_position_embeddings,
        )
        torch.onnx.export(
            wrapper,
            values,
            onnx_path,
            input_names=["input_ids", "attention_mask", "token_type_ids"],
            output_names=["intent_logits", "slot_logits"],
            dynamic_shapes={
                "input_ids": {0: batch, 1: sequence},
                "attention_mask": {0: batch, 1: sequence},
                "token_type_ids": {0: batch, 1: sequence},
            },
            opset_version=18,
            dynamo=True,
            external_data=False,
        )
    else:
        torch.onnx.export(
            wrapper,
            values,
            onnx_path,
            input_names=["input_ids", "attention_mask", "token_type_ids"],
            output_names=["intent_logits", "slot_logits"],
            dynamic_axes={
                "input_ids": {0: "batch", 1: "sequence"},
                "attention_mask": {0: "batch", 1: "sequence"},
                "token_type_ids": {0: "batch", 1: "sequence"},
                "intent_logits": {0: "batch"},
                "slot_logits": {0: "batch", 1: "sequence"},
            },
            opset_version=18,
            dynamo=False,
        )
    onnx.checker.check_model(str(onnx_path))


def modern_onnx_export_available():
    try:
        import onnxscript
        major, minor = (int(value) for value in onnxscript.__version__.split(".")[:2])
        return (major, minor) >= (0, 7)
    except (ImportError, AttributeError, ValueError):
        return False


def ensure_dynamic_batch(value):
    if value.shape[0] != 1:
        return value
    repeats = [1] * value.dim()
    repeats[0] = 2
    return value.repeat(*repeats)


def create_onnx_session(onnx_path):
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    input_names = [value.name for value in session.get_inputs()]
    output_names = [value.name for value in session.get_outputs()]
    if input_names != ["input_ids", "attention_mask", "token_type_ids"]:
        raise RuntimeError(f"Unexpected ONNX inputs: {input_names}")
    if output_names != ["intent_logits", "slot_logits"]:
        raise RuntimeError(f"Unexpected ONNX outputs: {output_names}")
    return session


def run_onnx_contract(session, sample):
    inputs = {
        "input_ids": sample["input_ids"].detach().cpu().numpy().astype("int64"),
        "attention_mask": sample["attention_mask"].detach().cpu().numpy().astype("int64"),
        "token_type_ids": sample.get("token_type_ids", torch.zeros_like(sample["input_ids"]))
        .detach()
        .cpu()
        .numpy()
        .astype("int64"),
    }
    intent_logits, slot_logits = session.run(["intent_logits", "slot_logits"], inputs)
    batch_size, sequence_length = inputs["input_ids"].shape
    expected_intent_shape = (batch_size, len(INTENTS))
    expected_slot_shape = (batch_size, sequence_length, len(SLOT_LABELS))
    if intent_logits.shape != expected_intent_shape:
        raise RuntimeError(f"Unexpected ONNX intent logits shape: {intent_logits.shape}")
    if slot_logits.shape != expected_slot_shape:
        raise RuntimeError(f"Unexpected ONNX slot logits shape: {slot_logits.shape}")
    return intent_logits, slot_logits


def verify_onnx(onnx_path, sample, tokenizer, max_length):
    session = create_onnx_session(onnx_path)
    run_onnx_contract(session, sample)
    for text, expected_intent, minimum_confidence, requires_slot in EXPORT_INTENT_CHECKS:
        encoded = tokenizer(
            text,
            max_length=max_length,
            padding="max_length",
            truncation=True,
            return_tensors="np",
        )
        check_inputs = {
            "input_ids": encoded["input_ids"].astype("int64"),
            "attention_mask": encoded["attention_mask"].astype("int64"),
            "token_type_ids": encoded.get("token_type_ids", np.zeros_like(encoded["input_ids"])).astype("int64"),
        }
        check_intent_logits, check_slot_logits = session.run(["intent_logits", "slot_logits"], check_inputs)
        probabilities = softmax(check_intent_logits[0])
        intent_index = int(probabilities.argmax())
        actual_intent = INTENTS[intent_index]
        confidence = float(probabilities[intent_index])
        if actual_intent != expected_intent or confidence < minimum_confidence:
            raise RuntimeError(
                f"Export check failed for {text!r}: expected {expected_intent} >= {minimum_confidence}, "
                f"got {actual_intent} confidence={confidence:.3f}"
            )
        if requires_slot and np.argmax(check_slot_logits[0], axis=-1).max() <= SLOT_LABELS.index("O"):
            raise RuntimeError(f"Export check failed for {text!r}: slot classifier returned only O labels")


def softmax(values):
    shifted = values - values.max()
    exps = np.exp(shifted)
    return exps / exps.sum()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-model", default=DEFAULT_BASE_MODEL)
    parser.add_argument(
        "--base-model-revision",
        default=None,
        help="Hugging Face revision; the default RuBERT model uses the repository-pinned revision",
    )
    parser.add_argument("--dataset", type=Path, default=Path("training/rubert/synthetic_intents.jsonl"))
    parser.add_argument("--output-dir", type=Path, default=Path("models/generated/rubert"))
    parser.add_argument("--epochs", type=int, default=14)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
    parser.add_argument("--max-length", type=int, default=64)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument(
        "--resume-from",
        type=Path,
        default=None,
        help="Optional compatible training checkpoint used for controlled continued training",
    )
    parser.add_argument("--slot-loss-weight", type=float, default=3.0)
    parser.add_argument("--outside-slot-weight", type=float, default=0.15)
    parser.add_argument("--named-slot-weight", type=float, default=6.0)
    return parser.parse_args()


if __name__ == "__main__":
    train(parse_args())
