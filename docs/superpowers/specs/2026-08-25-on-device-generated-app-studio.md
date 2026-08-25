# Generated App Studio: Local And Cloud Backends

**Status:** ABI v2, compositional UI DSL and independent backend selection implemented; live cloud/device acceptance pending
**Date:** 2026-08-25

## Objective

Given an English natural-language request, generate both the presentation and the
behavior of a small interactive app. Each role independently selects its local
model, DeepSeek V4 Flash or DeepSeek V4 Pro. The implementation must not select a
prebuilt app family, reducer or widget implementation at runtime.

The Studio is isolated from the assistant pipeline. It cannot receive a RuBERT
action, consume an assistant answer or Exa data, execute an Android action or change
an assistant widget. Its own restricted DeepSeek client is available only after an
explicit Studio backend selection and reuses the Keystore-backed BYOK.

## Pipeline

```text
request
  |
  +-> UI generator
  |     Gemma 3 270M Q4_K_M | DeepSeek V4 Flash | DeepSeek V4 Pro
  |                               |
  |                    compact UI DSL -> parser + binding validator
  |                                            |
  |                                   progressive safe preview
  |
  `-> Logic generator
        Qwen2.5-Coder 0.5B Q4_K_M | DeepSeek V4 Flash | DeepSeek V4 Pro
                                      |
                           DEAL generated-app profile
                                      |
                           parser + ABI + smoke actions
                                      |
                           generic Compose renderer/interpreter
```

The selected UI and logic generators start concurrently. Local roles use separate
llama.cpp sessions; cloud roles use separate cancellable SSE requests so mixed and
fully cloud runs remain parallel. The choices are persisted independently. Selecting
cloud releases that role's local session, and a missing key or network failure does
not silently fall back. The first valid UI artifact can produce a safe skeleton while
logic continues. A single repair pass on the selected logic backend is allowed after
a compile or ABI failure and its complete replacement must pass the same parser,
ABI, behavior and resource gates.

Model-facing instructions, ABI names, generated text and the complete product UI
are English. Russian remains supported as assistant input through T-one/RuBERT
normalization, but localization must never expand the executable language.

## Generated contracts

The compact UI DSL supports recursive `column`, `row`, `stack`, `grid2` and
`section` layouts; spacing, alignment, padding and section-tone tokens; bound
`text.heading`, `text.status` and `text.label`; `decor.divider`, `decor.spacer`,
`control.button`; and one generic `surface.app`. Every tree has one heading, one
status, one primary action and exactly one interaction surface. The surface renders
the validated `GRID` or `REALTIME_CANVAS` state produced by Qwen. Component
properties bind to generated state instead of embedding game behavior.

The executable contract is the **DEAL generated-app profile**, not canonical DEAL
v1.2. Every module exports `title`, `status`, and `primaryLabel`, then exactly one of
the following profiles.

`GRID`:

```text
items:string[]
columns:int
```

Actions: `onItem(index:int):null`, `onPrimary():null`.

`REALTIME_CANVAS`:

```text
canvasWidth:int
canvasHeight:int
canvasBackground:string
shapeKinds:string[]
shapeX:int[]
shapeY:int[]
shapeW:int[]
shapeH:int[]
shapeColors:string[]
shapeLabels:string[]
```

Actions: `onTick(deltaMs:int):null`, `onPointer(x:int,y:int,phase:int):null`,
`onPrimary():null`. Pointer phases are down `0`, move `1`, and up `2`. Compose sends
bounded scene coordinates and a capped frame delta. The generated module owns
movement, collisions, score, lives, win/lose state and reset behavior.

The restricted interpreter supports declarations, functions, conditionals,
bounded `while`, assignment, arrays, integer arithmetic and `abs`, `min`, `max`,
and `clamp`. There is no reflection, I/O, network, Android API, dynamic import or
native-code generation. Scenes are limited to 48 validated `rect`, `circle`, `line`
or `text` primitives.

## No-prebuilt rule

- Production code contains no `appKind`, `tic_tac_toe`, `pong`, `arkanoid`,
  `tank_duel` or equivalent
  runtime family switch.
- Compose renders generic DSL primitives only.
- The interpreter invokes only generated functions and reads only generated state.
- There is no canned app fallback. Invalid output leaves the preview noninteractive.
- Training examples may teach the profile, as with any fine-tuned model; their
  source must not be copied into runtime code or selected by request keywords.

## Safety gates

1. Extract one bounded artifact and reject unsupported characters or syntax.
2. Validate all UI components, properties, bindings, nesting and node counts.
3. Parse DEAL into an AST; never evaluate it as Kotlin or JavaScript.
4. Enforce the exact state/action ABI and value types.
5. Instantiate with statement and loop execution budgets.
6. Run deterministic smoke actions and require observable state changes.
7. Execute only after both artifacts pass. Partial and cancelled output never runs.
8. A repair pass remains model output and must pass every gate again.
9. `onTick` runs with a bounded delta and statement budget; malformed or expensive
   generated code fails closed instead of blocking the UI thread indefinitely.

## Training and generalization

`training/generated_app/build_demo_dataset.py` deterministically emits separate
chat-format datasets for UI DSL and DEAL. Grid and real-time families are mixed in
training. `pixel_canvas` is held out for validation and `runner` is held out for
test, so success cannot be claimed by reproducing only training prompts.

The checked-in JSONL is training input only. It is never packaged in the APK,
loaded by production code, selected by keywords, or used as a runtime fallback.
`GeneratedAppTrainingDatasetTest` compiles every unique target with the production
parsers and verifies split isolation.

The current corpus is intentionally only a smoke dataset and is not evidence of
arbitrary-app generalization. Its replacement, the A2UI-aligned catalog, DeepSeek
Flash teacher pipeline, leakage-resistant split strategy and llama.cpp constrained
decoding rollout are specified in
`docs/superpowers/plans/2026-08-25-a2ui-dataset-and-constrained-decoding.md`.

## Selected internal models

| Role | Artifact | Quantization | Storage |
|---|---|---:|---:|
| UI DSL | `gemma-ui-q4-k-m.gguf` | Q4_K_M | 253,114,976 bytes |
| Behavior | `qwen-deal-app-0.5b-q4-k-m.gguf` | Q4_K_M | 397,807,968 bytes |

Neither model is packaged in the APK. The production output ceilings are 512 tokens
for Gemma and 1,536 tokens for the initial Qwen pass and repair. These are safety
ceilings rather than expected output lengths. A higher ceiling also requires a
larger native generation cap, context/source limits and new device latency, memory
and thermal evidence. ABI v1 hashes must not be promoted to ABI v2; new artifacts
require immutable host evaluation and Pixel interaction evidence.

## Cloud generator profiles

The Studio exposes `deepseek-v4-flash` and `deepseek-v4-pro` through the fixed
`https://api.deepseek.com/chat/completions` endpoint. Both requests disable thinking,
stream partial source into the model status cards, and use the same 512-token UI and
1,536-token logic/repair ceilings as local generation. The model IDs are a closed
enum, not user-provided strings. DeepSeek Chat Completions supports both aliases;
the Responses API is not used because its Pro support is not yet equivalent.

The API key is shared with Settings but decrypted from Android Keystore only when a
request starts. It is never embedded in the APK, copied into `BuildConfig`, logged
or persisted with Studio preferences. Studio preferences contain only the two
backend enum values.

## Pixel 10 measurements

Native runtime: llama.cpp, four CPU threads, greedy decoding, ARMv8.6 dot-product,
FP16 and I8MM build with KleidiAI. Numbers below are live wall-clock measurements;
generation throughput excludes prompt prefill and uses `(tokens - 1) / (total - TTFT)`.

| State | Model | Prompt | Output | TTFT | Total | Decode rate |
|---|---|---:|---:|---:|---:|---:|
| ABI v1 cold | Gemma 270M | 129 tok | 54 tok | 570 ms | 3,385 ms | 18.8 tok/s |
| ABI v1 cold | Qwen Coder 0.5B | 170 tok | 471 tok | 566 ms | 47,660 ms | 10.0 tok/s |
| ABI v1 hot | Gemma 270M | 129 tok | 54 tok | 472 ms | 4,157 ms | 14.4 tok/s |
| ABI v1 hot | Qwen Coder 0.5B | 170 tok | 471 tok | 494 ms | 33,106 ms | 14.4 tok/s |
| ABI v2 Pong cold | Gemma UI v7 Q4_K_M | 301 tok | 97 tok | 1,514 ms | 6,988 ms | 17.5 tok/s |
| ABI v2 Pong cold | Qwen DEAL v4 Q4_K_M | 443 tok | 560 tok | 4,381 ms | 70,537 ms | 8.4 tok/s |

These figures are retained as the grid-only baseline. They do not constitute ABI
v2 acceptance. The ABI v2 Pong rows are a live Pixel 10 generation with a valid
composition and DEAL module; drag input changed the generated status and paddle,
and frame ticks moved the ball. Arkanoid and tank duel still require device evidence,
and held-out runner must be evaluated without claiming unsupported generalization.

## Rejected candidate

The loop-optimized adapter dated 2026-08-25 reduced its target to about 405 tokens,
but live Q4_0 output repeated declarations, failed validation and also failed its
repair pass. It is not a production candidate. A training loss near zero on the
small demo corpus is insufficient without immutable generation and device gates.

Qwen DEAL v4 quantized as Q4_0 was also rejected for ABI v2: its 513-token Pong
completed in 57,658 ms but failed the production compiler and entered repair. The
selected Q4_K_M artifact produced a valid 560-token Pong without repair. The quality
gain currently costs both storage and latency and must be revisited before release.

## Current limits

- The ABI represents grid and bounded 2D canvas mini-apps, not arbitrary native
  applications.
- CPU generation is interactive for a demo but not yet product latency.
- Cloud quality and latency depend on connectivity and have not yet passed the live
  device matrix for every local/cloud role combination.
- The ARMv8.6 native build is a Pixel-class internal profile, not a universal ARM64
  release. Product delivery requires compatible baseline and optimized variants.
- A broader app vocabulary requires versioned DSL/ABI evolution and evaluation,
  not request-specific Kotlin branches.

## Acceptance

```bash
./gradlew :app:testDebugUnitTest --tests 'com.offlineassistant.app.generatedapp.*'
./gradlew :app:assembleDebug
python3 scripts/check_core_scope.py
```

Device acceptance requires cold and hot parallel runs, valid source tabs, generated
Pong, Arkanoid and tank interaction, held-out runner evaluation, reset,
cancellation, one invalid-output rejection, all nine UI/logic backend combinations,
missing-key and offline failures, plus captured native/cloud timing evidence.
