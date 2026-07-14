#!/usr/bin/env python3
import argparse
import json
import random
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
    "unknown",
]

SLOT_LABELS = [
    "O",
    "B-duration",
    "I-duration",
    "B-time",
    "I-time",
    "B-date",
    "I-date",
    "B-location",
    "I-location",
    "B-note_text",
    "I-note_text",
    "B-reminder_text",
    "I-reminder_text",
    "B-app_name",
    "I-app_name",
    "B-expression",
    "I-expression",
]

EXPORT_INTENT_CHECKS = [
    ("Покажи погоду в Санкт-Петербурге", "get_weather", 0.8),
    ("Будильник на 08.15", "set_alarm", 0.8),
    ("Поставь таймер на час", "set_timer", 0.8),
    ("Сколько будет 18 плюс 24", "calculate", 0.75),
]


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
            slot_labels_for_text(row["text"], offsets, item["attention_mask"].tolist()),
            dtype=torch.long,
        )
        return item


class JointIntentSlotModel(torch.nn.Module):
    def __init__(self, base_model_name):
        super().__init__()
        self.encoder = AutoModel.from_pretrained(base_model_name)
        hidden_size = self.encoder.config.hidden_size
        self.dropout = torch.nn.Dropout(0.1)
        self.intent_classifier = torch.nn.Linear(hidden_size, len(INTENTS))
        self.slot_classifier = torch.nn.Linear(hidden_size, len(SLOT_LABELS))

    def forward(self, input_ids, attention_mask, token_type_ids=None):
        output = self.encoder(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
        )
        sequence_output = self.dropout(output.last_hidden_state)
        pooled_output = sequence_output[:, 0]
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


def slot_labels_for_text(text, offsets, attention_mask):
    labels = [SLOT_LABELS.index("O")] * len(offsets)
    spans = infer_slot_spans(text)
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
    add_after_marker(spans, text, lower, ["запиши заметку ", "создай заметку ", "заметка: ", "заметка ", "запиши "], "note_text")
    add_after_marker(spans, text, lower, ["создай напоминание ", "напоминание на вечер ", "напомни завтра ", "напомни через час ", "напомни "], "reminder_text")
    add_regex_span(spans, lower, r"\b\d+\s*(?:умножить на|\*|плюс|\+|минус|-)\s*\d+\b", "expression")
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

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    model = JointIntentSlotModel(args.base_model)

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
            intent_loss = F.cross_entropy(intent_logits, intent_labels)
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
    ordered_vocab = sorted(tokenizer.get_vocab().items(), key=lambda item: item[1])
    (output_dir / "vocab.txt").write_text(
        "".join(f"{token}\n" for token, _ in ordered_vocab),
        encoding="utf-8",
    )

    (output_dir / "intent_labels.txt").write_text("\n".join(INTENTS) + "\n", encoding="utf-8")
    (output_dir / "slot_labels.txt").write_text("\n".join(SLOT_LABELS) + "\n", encoding="utf-8")

    model.eval()
    wrapper = JointOnnxWrapper(model).eval()
    sample = tokenizer(
        "Поставь таймер на 5 минут",
        max_length=args.max_length,
        padding="max_length",
        truncation=True,
        return_tensors="pt",
    )
    onnx_path = output_dir / "rubert-tiny2-intent-slots.onnx"
    torch.onnx.export(
        wrapper,
        (
            sample["input_ids"],
            sample["attention_mask"],
            sample.get("token_type_ids", torch.zeros_like(sample["input_ids"])),
        ),
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
        opset_version=17,
    )
    onnx.checker.check_model(str(onnx_path))
    verify_onnx(onnx_path, sample, tokenizer, args.max_length)
    print(f"exported={output_dir}")


def verify_onnx(onnx_path, sample, tokenizer, max_length):
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    inputs = {
        "input_ids": sample["input_ids"].numpy().astype("int64"),
        "attention_mask": sample["attention_mask"].numpy().astype("int64"),
        "token_type_ids": sample.get("token_type_ids", torch.zeros_like(sample["input_ids"])).numpy().astype("int64"),
    }
    intent_logits, slot_logits = session.run(["intent_logits", "slot_logits"], inputs)
    if intent_logits.shape[-1] != len(INTENTS):
        raise RuntimeError(f"Unexpected ONNX intent logits shape: {intent_logits.shape}")
    if slot_logits.shape[-1] != len(SLOT_LABELS):
        raise RuntimeError(f"Unexpected ONNX slot logits shape: {slot_logits.shape}")
    for text, expected_intent, minimum_confidence in EXPORT_INTENT_CHECKS:
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
        if np.argmax(check_slot_logits[0], axis=-1).max() <= SLOT_LABELS.index("O"):
            raise RuntimeError(f"Export check failed for {text!r}: slot classifier returned only O labels")


def softmax(values):
    shifted = values - values.max()
    exps = np.exp(shifted)
    return exps / exps.sum()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-model", default="cointegrated/rubert-tiny2")
    parser.add_argument("--dataset", type=Path, default=Path("training/rubert/synthetic_intents.jsonl"))
    parser.add_argument("--output-dir", type=Path, default=Path("models/generated/rubert"))
    parser.add_argument("--epochs", type=int, default=4)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
    parser.add_argument("--max-length", type=int, default=64)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--slot-loss-weight", type=float, default=3.0)
    parser.add_argument("--outside-slot-weight", type=float, default=0.15)
    parser.add_argument("--named-slot-weight", type=float, default=6.0)
    return parser.parse_args()


if __name__ == "__main__":
    train(parse_args())
