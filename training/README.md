# RuBERT Training

This directory owns the single trainable local model in the current product:
RuBERT-tiny2 joint intent and BIO slot classification.

The production contract contains exactly ten labels:

```text
get_current_time
get_weather
set_timer
set_alarm
create_reminder
create_note
calculate
open_app
help
unknown
```

## Dataset

Generate the deterministic balanced corpus:

```bash
python3 training/rubert/generate_core_dataset.py
```

Each JSONL row contains text, intent and optional character-aligned slots. The
generator creates at least 80 examples per intent and is tested for label, balance and
offset integrity. `training/data/eval_set.jsonl` is the small immutable regression set.

## Train And Export

Use the pinned packages from `training/rubert/requirements.txt`, then run:

```bash
python3 training/rubert/train_export.py \
  --dataset training/rubert/synthetic_intents.jsonl \
  --output-dir models/generated/rubert
```

The output is one ONNX joint intent/slot model plus tokenizer, label files and a
hash-bound training manifest. The default base model revision is pinned and remote
model code is disabled.

## Evaluate

```bash
python3 training/rubert/evaluate_export.py \
  --model-dir models/generated/rubert \
  --output build/rubert-host-eval.jsonl
```

Required metrics are intent accuracy, macro F1, slot F1, per-intent confusion and
validation pass rate.

CI also runs a randomly initialized tiny BERT through the same opset-18 export path:

```bash
python3 training/rubert/ci_export_smoke.py --output-dir build/rubert-ci
```

No LLM, widget planner or synthetic runtime fallback is part of this pipeline.
