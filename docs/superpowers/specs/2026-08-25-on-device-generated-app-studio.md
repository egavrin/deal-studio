# Generated App Studio: Local And Cloud Backends

**Status:** ABI v2, cloud A2UI runtime slice and independent backend selection implemented; cloud behavior/device acceptance in progress
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
  |     compact UI DSL (local) | project-owned A2UI wire profile (cloud)
  |                               |
  |                    catalog + graph + binding validator
  |                               |
  |                      progressive safe preview
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
logic continues. Local generation permits one repair pass. Cloud generation permits
at most two diagnostic repair passes because one cloud replacement has proven
insufficient for semantic array/behavior failures. Every complete replacement must
pass the same parser, ABI, behavior and resource gates.

Regeneration is atomic from the user's perspective. If a validated app already
exists, it remains visible and interactive while the replacement UI and behavior
are generated. The runtime swaps only after both replacement artifacts pass every
gate; a failed or cancelled attempt preserves the previous app. A noninteractive
skeleton is shown only when no validated app exists yet and is explicitly labelled
as locked until behavior validation completes.

Model-facing instructions, ABI names, generated text and the complete product UI
are English. Russian remains supported as assistant input through T-one/RuBERT
normalization, but localization must never expand the executable language.

The executable model contract is versioned in
`app/src/main/java/com/offlineassistant/app/generatedapp/GeneratedAppLanguageContracts.kt`.
It is the single source used by local/cloud initial generation and local/cloud
repair. It includes the complete compatibility Compact UI syntax, the project-owned
A2UI wire profile and 31-component catalog, DEAL statements and expressions,
operators, builtins, resource limits, both ABIs, behavioral obligations and one
compiler-validated reference module per DEAL profile. Prompts may add the request
and compiler diagnostic, but may not replace these contracts with abbreviated token
lists or app-family examples.

## Generated contracts

Local Gemma currently uses a compatibility Compact UI DSL with recursive `column`,
`row`, `stack`, `grid2` and
`section` layouts; spacing, alignment, padding and section-tone tokens; bound
`text.heading`, `text.status` and `text.label`; `decor.divider`, `decor.spacer`,
`control.button`; and one generic `surface.app`. Every tree has one heading, one
status, one primary action and exactly one interaction surface. The surface renders
the validated `GRID` or `REALTIME_CANVAS` state produced by Qwen. Component
properties bind to generated state instead of embedding game behavior.

Cloud UI generation emits a project-owned A2UI v1-style `createSurface` document.
The runtime catalog contains 31 components: text/media, layout, navigation, controls,
assistant data display, collections, maps/code and one `InteractiveSurface`. The
validator pins trusted catalog IDs; enforces unique IDs, one parent, reachability,
acyclic depth, property shapes/enums, absolute and collection-template bindings,
allowlisted actions and HTTPS media hosts; and requires exactly one interaction
surface. This is an internal derived profile, not a claim of upstream A2UI wire
conformance. Local Gemma moves to this profile only after its new dataset/model pass
meets the same gates.

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

The runtime exposes up to 32 bounded scalar DEAL globals under
`/app/custom/<name>` for generated A2UI HUD bindings. Arrays and reserved ABI values
are never copied into this map. The semantic smoke activates the scene with a
pointer event before requiring a tick change, so valid tap-to-start and paused
applications do not enter repair merely because their initial frame is stationary.

The restricted interpreter supports declarations, functions, conditionals,
bounded `while`, assignment, arrays, integer arithmetic and `abs`, `min`, `max`,
and `clamp`. There is no reflection, I/O, network, Android API, dynamic import or
native-code generation. Scenes are limited to 48 validated `rect`, `circle`, `line`
or `text` primitives.

Generation is bounded independently from execution. Local Compact UI output is
capped at 512 tokens; cloud A2UI output is capped at 4,096 tokens. DEAL output and
each bounded repair pass are capped at 4,096 tokens, while
the accepted source remains capped at 10,000 characters by the model contract and
12,000 characters by the defensive parser boundary. The larger API context is for
complete grammar and app behavior, not permission to emit an unbounded program.

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
for Gemma and 4,096 tokens for the initial Qwen pass and repair. These are safety
ceilings rather than expected output lengths. A higher ceiling also requires a
larger native generation cap, context/source limits and new device latency, memory
and thermal evidence. ABI v1 hashes must not be promoted to ABI v2; new artifacts
require immutable host evaluation and Pixel interaction evidence.

## Cloud generator profiles

The Studio exposes `deepseek-v4-flash` and `deepseek-v4-pro` through the fixed
`https://api.deepseek.com/chat/completions` endpoint. Both requests disable thinking,
stream partial source into the model status cards, and use a 4,096-token ceiling for
cloud A2UI and logic/repair generation. The model IDs are a closed
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
- A2UI input controls currently retain renderer-local values; they do not yet write
  through to a generic generated state store. Named application events beyond
  `onPrimary`, `onItem`, pointer and tick remain the next ABI expansion.
- The 31 catalog components are renderable, but full semantic parity is not yet
  complete: modal presentation, mutable binding propagation and host client actions
  still require product hardening and screenshot/accessibility acceptance.
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
