# RuBERT-tiny2 Training Pipeline

This directory is the stable training interface described by the Android Offline Assistant PoC spec.

The current implementation delegates the heavy work to `training/rubert/`, which contains the maintained fine-tuning and ONNX export code. The `training/scripts/` files are thin entry points with spec-friendly names.

## Data

- `data/synthetic_intents.jsonl` contains synthetic Russian command examples.
- `data/eval_set.jsonl` contains fixed validation cases with intent and slot spans.

Each record uses this shape:

```json
{"text":"Поставь таймер на 5 минут","intent":"set_timer","slots":[{"name":"duration","value":"5 минут","start":18,"end":25}]}
```

## Commands

Generate or refresh the synthetic dataset:

```bash
python3 training/scripts/generate_synthetic_dataset.py --output training/data/synthetic_intents.jsonl
```

Train and export RuBERT-tiny2:

```bash
python3 training/scripts/train_rubert_tiny2.py --dataset training/data/synthetic_intents.jsonl --output-dir models/generated/rubert
```

Export-only entry point:

```bash
python3 training/scripts/export_onnx.py --dataset training/data/synthetic_intents.jsonl --output-dir models/generated/rubert
```

Evaluate an exported model:

```bash
python3 training/scripts/evaluate_nlu.py --model-dir models/generated/rubert --output build/rubert-host-eval.jsonl
```

The evaluator writes two artifacts:

- `build/rubert-host-eval.jsonl` with one normalized result per case;
- `build/rubert-host-eval-metrics.json` with aggregate metrics and the strict regression-gate summary.

## Required Metrics

The evaluation report must include:

- intent accuracy;
- macro F1;
- slot F1;
- per-intent confusion;
- validation pass rate after normalization.

`training/rubert/evaluate_export.py` is the current host gate for the exported ONNX bundle. It evaluates `training/data/eval_set.jsonl`, writes per-case JSONL, and writes aggregate JSON containing intent accuracy, macro F1, slot precision/recall/F1, per-intent metrics, confusion matrix, validation pass rate, exact matches and the separate strict regression subset. The wrapper `training/scripts/evaluate_nlu.py` delegates to it and is the stable command name.

The 2026-07-10 generated bundle reports 12 cases, intent accuracy `1.000`, macro F1 `1.000`, slot F1 `0.873`, validation pass rate `0.917`, exact matches `11/12`, and strict regression gate `9/9`. The non-exact note case remains visible in the report and should be improved through data/model work rather than hidden by a phrase-specific fallback.
