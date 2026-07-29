# Intent Model Evaluation

**Status:** required before replacing RuBERT-tiny2
**Date:** 2026-07-29

## Decision

RuBERT-tiny2 remains the production model for the current 21 Russian labels because
it is small, already exported to ONNX and measured end to end in the app. It is not
assumed to be the best model for multilingual input or a future 200+ intent
taxonomy.

The next model must be selected by an immutable comparison, not by parameter count
or a few hand-written examples.

## Candidates

Evaluate at least:

1. the current RuBERT-tiny2 export as the latency and size baseline;
2. multilingual DistilBERT as the conservative multilingual ONNX candidate;
3. mmBERT-small as the broader multilingual candidate;
4. an XLM-R-class encoder as an optional quality ceiling when it fits the device
   budget.

Do not ship multiple intent models or route languages with keywords. The intended
production shape is one shared encoder with learned domain, intent and slot heads.
A learned hierarchical domain-to-intent mask may be evaluated for 200+ labels, but
it is adopted only if it beats a flat head on the same immutable set.

## Dataset

The current immutable Russian baseline is pinned by
`training/data/evaluation_manifest.json`. `scripts/check_nlu_eval_manifest.py`
verifies its digest, complete intent coverage, minimum support and slot offsets on
every PR. The manifest contains the pre-declared host metric floors; changing the
dataset or a floor is a reviewed benchmark-version change, not an incidental
training edit.

Maintain three non-overlapping groups:

- training and development utterances;
- immutable in-domain evaluation;
- immutable out-of-domain and near-miss evaluation.

The evaluation set must cover:

- every intent and slot type with macro-balanced support;
- clean typed Russian plus real and synthetic ASR transcripts;
- paraphrases, morphology, code switching and short ambiguous commands;
- supported product languages with native, independently reviewed utterances;
- confusable intent pairs and unknown requests;
- a projected 200+ intent taxonomy, without duplicating templates across splits.

The current v1 baseline has two or more examples for every production label. It is
a regression gate, not yet the final product corpus: real-ASR, accent, ambiguity,
calibration and multilingual partitions in the manifest remain promotion
requirements.

MASSIVE may seed multilingual intent representations, but product actions and slots
still require product-owned data. Generated data cannot be the only source of the
immutable set.

## Metrics And Gates

Record for each candidate:

- intent macro-F1 and per-intent recall;
- slot span F1 and normalized-command validity;
- unknown/OOD AUROC, false-action rate and false-cloud-route rate;
- expected calibration error and threshold curves;
- clean-text, noisy-ASR and per-language results;
- model bytes, APK/model-delivery bytes and cold-load time;
- warm p50/p95 inference latency, peak RSS and sustained thermal behavior;
- end-to-end action latency on the physical target device.

A production candidate must:

- regress no current safety-critical action below the approved per-intent floor;
- reduce the false-action rate, or keep it within the existing bound;
- meet the current interactive p95 latency budget on device;
- stay within the agreed package and resident-memory budgets;
- pass at least 50 alternating action/search/unknown calls without degradation.

The exact numeric floors must be frozen in the benchmark manifest before training,
not chosen after results are visible.

## Runtime Contract

Regardless of encoder, the app receives:

```text
intent
slots with offsets
calibrated confidence
out-of-domain score
model/version metadata for debug only
```

Low-confidence actions continue to clarify deterministically. Model unavailability
continues to fail closed. DeepSeek remains a natural-language answer model and
cannot repair NLU, emit an action or generate command JSON.

## Rollout

1. Freeze taxonomy, languages, device budgets and immutable sets.
2. Train all candidates with equivalent data and heads.
3. Export and validate ONNX parity against the training runtime.
4. Run host metrics and reject candidates that miss quality gates.
5. Run cold/warm device benchmarks and 50-call stability.
6. Shadow the candidate in debug builds without changing user-visible routing.
7. Promote one model only after parity and physical-device acceptance.
