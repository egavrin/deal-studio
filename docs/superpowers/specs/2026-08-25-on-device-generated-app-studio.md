# Generated App Studio: Local And Cloud Backends

**Status:** Canonical DeepSeek `.deal` + `.dealui` vertical slice implemented; full acceptance pending
**Date:** 2026-08-25

## Objective

Given an English natural-language request, generate both the presentation and the
behavior of a small interactive app. Each role independently selects its local
model, DeepSeek V4 Flash or DeepSeek V4 Pro. The implementation must not select a
prebuilt app family, reducer or widget implementation at runtime.

## Architecture Correction (2026-09-03)

The target authored and persisted application consists only of canonical `.deal`
business logic and a pure `.dealui` view. The existing Compact UI, project-owned A2UI
wire profile and restricted DEAL interpreter described below are transitional prototype
mechanisms, not the final Deal UI architecture. New Studio capabilities must be added as
reusable Deal UI component-pack contracts, Android renderer support or typed host
capabilities, never as application-specific Kotlin or as additional A2UI-only semantics.

Canonical behavior uses `@ui-update`, `@ui-effect`, `@ui-effect-policy` and
`@ui-effect-failure`. Canonical presentation uses typed component props and tokens,
`When` and `ForEach`. Frame timing, pointer/keyboard input, lifecycle, storage and sensors
are reusable platform capabilities that dispatch typed actions through native event
ingress. The target streaming compiler may use partial typed HIR, but every final artifact
must emit and pass the pinned production `.deal` and `.dealui` compilers. See
`docs/superpowers/plans/2026-09-03-deepseek-deal-ui-streaming-compiler.md` for the ordered
migration and acceptance gates.

The final saved bundle contains `app.deal`, `app.dealui` and non-executable provenance
metadata. `DUI` is the product-facing name for `.dealui`, not a second file grammar.
Sequential cloud generation extracts an ephemeral `AppInterfaceV1` from accepted DEAL. It contains
only the root state type, exact typed fields, nominal actions and generic capability requirements.
It is discarded after compilation and must not prescribe layout, domain behavior, sample data or an
application family. `AppPlanV1` is not generated, persisted or part of the canonical path.

The Studio is isolated from the assistant pipeline. It cannot receive a RuBERT
action, consume an assistant answer or Exa data, execute an Android action or change
an assistant widget. Its own restricted DeepSeek client is available only after an
explicit Studio backend selection and reuses the Keystore-backed BYOK.

## Pipeline

```text
English request
  -> DeepSeek create_deal_program
  -> compiler-owned typed DEAL holes
  -> batched apply_deal_graph_patch + production checks
  -> canonical app.deal + extracted read-only AppInterfaceV1
  -> checked progressive Deal UI sections
  -> canonical app.dealui + portable IR
  -> Compose runtime, save/restore and fullscreen execution
```

The primary product path is sequential. DEAL is generated and production-checked first. Its root
state is both the behavior state and presentation-ready view model: Deal UI receives explicit chart
arrays, labels, formatted text, statuses and selected/summarized fields because pure Deal UI cannot
call helpers or transform data. There is no generated planner or `AppPlanV1`.

Deal UI arrives as two to six cohesive compiler tool calls. Every accepted cumulative projection is
renderable; rejected sections remain invisible and become the mandatory repair target before any new
section can be appended. Accepted section ids cannot be repeated. The complete pair passes the same
parser, type, ABI, capability and runtime gates before interaction. The previous local/A2UI path is a
compatibility implementation only and must not shape new product contracts.

Natural-language refinement is transactional. DEAL edits replace responsible checked function
bodies; Deal UI edits replace exact responsible fragments. Temporary candidates are recompiled and
swapped atomically only after all gates pass. A failed candidate leaves the prior runnable app and
saved revision unchanged.

The host owns `/app`; generated `dataModel` may not shadow it. Tracker counter progress
is an integer percent in 0..100. These are parser/compiler invariants, not prompt-only
guidance.

Regeneration is atomic from the user's perspective. If a validated app already
exists, it remains visible and interactive while the replacement UI and behavior
are generated. The runtime swaps only after both replacement artifacts pass every
gate; a failed or cancelled attempt preserves the previous app. A noninteractive
skeleton is shown only when no validated app exists yet and is explicitly labelled
as locked until behavior validation completes. The Studio labels a retained bundle
as the previous app, displays the exact pending or failed replacement request, and
automatically brings generation status, validation failures and a successfully
installed replacement into view. An old bundle must never appear to belong to the
newly edited prompt.

The validated runtime can move between the Studio preview and an immersive
execution surface without being reinstantiated. System and Studio chrome are hidden.
Canvas content is measured and uniformly scaled inside safe edge insets without changing pointer
hit testing. Utility applications use semantic layout, an adaptive Grid whose column count follows
`minimumCellWidth`, and host scrolling when they do not declare Scroll, PointerSurface or Canvas.
Saved thumbnails render the real runtime at a 360 dp logical width before scaling. The same artifact
therefore adapts to a cover screen, ordinary phone and unfolded display without device-specific
generation. Only one renderer owns the frame
loop at a time, so expanding an animated app cannot double its simulation speed.
Back or the collapse command returns to Studio with the same generated state.

Users may save a validated bundle to the private Studio library. A record includes
the original request, complete UI DSL and DEAL sources, selected backends, timings and
creation time. The library shows a static clipped viewport rendered by the real generic
runtime rather than a placeholder. Opening a record creates a fresh interactive state.
Every process-level restore reparses UI, recompiles DEAL and reruns the cross-artifact
validator; an invalid or obsolete record is not loaded. Studio does not create or install
arbitrary APKs.

The previous validated bundle remains available during regeneration. If the first
generation fails before a valid bundle exists, the last streamed UI/DEAL output remains
available behind an `Inspect generated output` disclosure, labelled incomplete and
non-runnable. Transport inactivity is bounded and retried once; an active SSE stream may
run longer than that inactivity interval.

For cloud-generated bundles, Studio accepts a natural-language refinement such as
"make the paddle wider" or "use a darker background". Independent UI and DEAL edit
agents receive the current valid sources and return only one to eight exact
SEARCH/REPLACE operations. An agent may return `NO_CHANGES` when its artifact is
unaffected, but the pair must change at least one artifact. Failed application or
validation feeds a diagnostic into the next pass, and only unresolved artifacts are
retried. A dedicated general Repair command gives each agent bounded read-only
counterpart source so it can audit original-request fidelity, bindings, visual
consistency, actions and reset behavior. Studio applies the
patches to temporary sources, reruns the full validators and creates a temporary
runtime. Only then does it atomically replace the active bundle; invalid, cancelled,
ambiguous or no-op edits leave the previous app untouched. Local in-place refinement
is intentionally disabled until local models are trained on the edit protocol.

Model-facing instructions, ABI names, generated text and the complete product UI are English.

The executable model contract is versioned in
`app/src/main/java/com/offlineassistant/app/generatedapp/GeneratedAppLanguageContracts.kt`.
It is the single source used by local/cloud initial generation and local/cloud
repair. It includes the complete compatibility Compact UI syntax, the project-owned
A2UI wire profile and 41-component catalog, DEAL statements and expressions,
operators, builtins, resource limits, all ABIs, behavioral obligations and one
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

Cloud UI generation emits a project-owned A2UI v3 `createSurface` document derived
from the upstream flat adjacency-list and bidirectional data-model architecture.
The runtime catalog contains 41 components: text/media, layout, navigation, controls,
assistant data display, collections, maps/code and an optional `InteractiveSurface`.
The generic tracker presentation additions are `ProgressRing`, `Stepper`, `ActionGroup`,
`Checklist` and `Heatmap`; none is a domain-specific app template. The
validator pins trusted catalog IDs; enforces unique IDs, one parent, root or detached
bottom-sheet overlay reachability, acyclic depth, property shapes/enums, absolute and collection-template bindings,
allowlisted actions and HTTPS media hosts; and permits at most one interaction
surface. Forms, lists and multi-screen utilities instead use bidirectional JSON
Pointer bindings, template `@index`, named DEAL events with exact parameter matching,
and bounded local functions for state, collection, navigation and overlay updates.
The host may deterministically normalize unambiguous duplicate IDs and synthesize
missing initial values for writable controls only. It never invents content, media,
domain data or behavior. Fidelity rules require direct catalog semantics rather than
decorative icon legends: real routes use `Navigation`, collections use collection
components and every overlay has a matching `showOverlay` opener.
This is an internal derived profile, not a claim of upstream A2UI wire
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
```

Actions: `onTick(deltaMs:int):null`, `onPointer(x:int,y:int,phase:int):null`,
`onPrimary():null`. Pointer phases are down `0`, move `1`, and up `2`. Compose sends
bounded scene coordinates and a capped frame delta. The generated module owns
movement, collisions, score, lives, win/lose state and reset behavior.

`TRACKER`:

```text
stateCounter(name,value,target,min,max) -> handle
stateList(name,capacity) -> handle
stateSeries(name,labels,values) -> handle
```

The generated module exports `onPrimary():null` plus at least one named UI action.
The closed `stateCounter*`, `stateList*` and `stateSeries*` operations mutate bounded
host-managed resources. `stateReset()` restores their post-initialization state.
Snapshots are exposed read-only below `/app/resources/<name>` and cross-artifact
validation rejects any A2UI resource path that does not exist in DEAL. The ABI has no
water, todo, habit, fitness or schedule operation: those applications are compositions
of counters, lists and series chosen by the model.

Realtime rendering uses **Scene ABI v3**, a sandbox-owned retained scene graph.
Generated code creates up to 96 bounded nodes with generic `sceneRect`,
`sceneRoundRect`, `sceneCircle`, `sceneEllipse`, `sceneLine` and `sceneText` calls.
Creators return deterministic integer handles and assign a string group. Code can
mutate position, size, color, stroke, label, visibility, layer, rotation and corner
radius; repeated nodes are discovered through `sceneCount(group)` and
`sceneAt(group,index)`. Generic AABB/point/circle helpers support collision and hit
testing. `sceneClear()` resets both nodes and handle allocation, enabling exact reset
validation. Pointer targets are generated declarations, not validator guesses:
DEAL marks visible hit regions with `sceneSetInteractive(handle,true)`, and semantic
smoke invokes `onPointer` at the center of those regions. Parallel renderer arrays
are forbidden.

The runtime accepts up to 64 global declarations and exposes at most 32 bounded
scalar DEAL globals under `/app/custom/<name>` for generated A2UI HUD bindings.
Arrays and reserved ABI values
are never copied into this map. The semantic smoke activates the scene with a
pointer event before requiring a tick change, so valid tap-to-start and paused
applications do not enter repair merely because their initial frame is stationary.

The restricted interpreter supports declarations, functions, conditionals,
bounded `while`, assignment, arrays, integer arithmetic, `abs`, `min`, `max`,
`clamp`, bounded `arrayFilled` and independent `arrayCopy`, plus the closed Scene ABI v3 and Tracker ABI v4
builtin sets. GRID semantic smoke retries each bounded cell from reset state until it
finds an observable interaction; it does not assume index zero is actionable. There is no reflection, I/O,
network, platform API, dynamic import or native-code generation. Scenes are limited
to 96 validated retained nodes.

Generation is bounded independently from execution. Local Compact UI output is
capped at 512 tokens; cloud A2UI output is capped at 8,192 tokens. DEAL output and
each local repair pass are capped at 1,536 tokens by the native llama.cpp bridge;
cloud DEAL initial generation is
capped at 8,192 tokens and cloud repair patches at 2,048 tokens. DEAL targets compact
10,000-character modules but the compiler accepts up to 48,000 characters, while the
cloud transport has a 64 KiB defensive response boundary. The 6,000-token lexer,
statement, global, execution and scene limits remain unchanged. Cloud
refinement uses the same 2,048-token patch ceiling as validation repair. The larger API context is for
complete grammar and app behavior, not permission to emit an unbounded program.

Canonical Grid treats `columns` as the maximum. With `minimumCellWidth: 0` it remains exact, which
preserves fixed game boards; with a positive minimum it reduces the count on compact viewports and
provides adaptive dashboard layout. Deal UI uses a closed documented semantic icon catalog. The
renderer currently falls back safely for an unknown literal, but screenshot acceptance treats a
placeholder icon as a failure; compile-time literal icon validation remains a product gate.

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
| Behavior, legacy ABI v2 only | `qwen-deal-app-0.5b-q4-k-m.gguf` | Q4_K_M | 397,807,968 bytes |
| Behavior, Scene ABI v3 | `qwen-deal-app-v3-0.5b-q4-k-m.gguf` | pending | pending |

Neither model is packaged in the APK. When the v3 Qwen artifact is absent, the legacy
artifact may be selected only by its legacy filename under an explicit GRID compatibility
policy. Its prompt is pinned to GRID and the compiled profile is checked again before UI
generation; canvas and TRACKER fail closed. Output ceilings are 512 tokens for Gemma and
1,536 tokens for the local Qwen initial pass and repair. These are safety
ceilings rather than expected output lengths. A higher ceiling also requires a
larger native generation cap, context/source limits and new device latency, memory
and thermal evidence. The legacy artifact must not be renamed or represented as v3;
new artifacts require immutable host evaluation and Pixel interaction evidence.

The legacy compact UI path uses the exact fine-tuning prompt contract and a llama.cpp
GBNF sampler supplied by the host. The grammar admits general compact composition
families and closed style values while making malformed nodes, bindings and unfinished
trees unsampleable. It is not a runtime template or fallback: the model emits the UI
source, the parser validates that source, and failure remains closed. Qwen uses greedy
decoding and emits all GRID state and behavior. Because the current Gemma dataset has
only three presentation families, passing this path proves the local compiler/runtime
smoke scenario only; it does not prove arbitrary UI generation.

## Cloud generator profiles

The Studio exposes `deepseek-v4-flash` and `deepseek-v4-pro` through the fixed
`https://api.deepseek.com/chat/completions` endpoint. Both requests disable thinking,
stream partial source into the model status cards, and use an 8,192-token ceiling for
cloud A2UI, an 8,192-token ceiling for initial cloud logic, and a 2,048-token ceiling
for repair/refinement patches. The model IDs are a closed
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
| ABI v2 tic-tac-toe hot | Gemma UI v7 Q4_K_M + GBNF | 309 tok | 101 tok | 719 ms | 12,338 ms | 8.6 tok/s |
| ABI v2 tic-tac-toe hot | Qwen DEAL v4 Q4_K_M | 199 tok | 459 tok | 1,136 ms | 28,218 ms | 16.9 tok/s |

The verified hot tic-tac-toe pipeline completed in 40,602 ms in the pre-parallel
Pixel baseline. The generated runtime passed occupied-cell guarding, alternating
turns, win detection, reset and full-screen state-preserving interaction on Pixel 10.
GBNF currently trades Gemma throughput for deterministic structural validity.

### OPPO CPH2765 parallel local acceptance

Both sessions were already loaded. Native prompt start timestamps differed by 13 ms.

| Model | Prompt | Output | TTFT | Total | Decode rate |
|---|---:|---:|---:|---:|---:|
| Gemma UI v7 Q4_K_M + GBNF | 309 tok | 101 tok | 873 ms | 7,846 ms | 14.3 tok/s |
| Qwen DEAL v4 Q4_K_M | 199 tok | 459 tok | 1,971 ms | 11,517 ms | 48.0 tok/s |
| Qwen DEAL v4 Q4_K_M + lexer-style syntax GBNF | 199 tok | 459 tok | 2,258 ms | 123,589 ms | 3.8 tok/s |

Pipeline wall time was 11,541 ms rather than the 19,363 ms sum of the two model runs.
The generated app passed occupied-cell guarding, alternating turns, win detection,
reset and full-screen interaction at the unfolded 2248 x 2480 viewport.

The constrained Qwen row is a same-device, same-prompt run against a general
syntax-only DEAL grammar: it encoded declarations, functions, control flow, arrays,
calls and expression precedence, but no app family, game rule, identifier or literal.
Each lexical token owned its trailing whitespace exactly once; top-level and function
body statements were separated to avoid nullable-whitespace and statement ambiguity.
It produced the same valid 459-token tic-tac-toe module without repair, and the live
runtime accepted a tap, placed `X` and advanced the status to `Turn: O`.

Native sampler instrumentation isolated the remaining regression:

| Qwen mode | Sampling | Neural decode | Total |
|---|---:|---:|---:|
| Greedy smoke after restoring production | 108 ms | 11,579 ms | 14,350 ms |
| Lexer-style syntax GBNF | 107,125 ms | 14,165 ms | 123,589 ms |

The optimized grammar therefore spends 86.7% of total wall time in eager grammar
sampling. Sampling itself is about 992x more expensive than greedy while neural decode
remains in the same range. A first grammar with shared nullable whitespace completed
in 140,349 ms and was discarded as ambiguous; fixing that defect helped but did not
solve eager masking against Qwen's 150K-class vocabulary. This GBNF configuration is
rejected for production. Local Qwen remains greedy behind the compiler and ABI
validators until an incremental/profile-aware constraint implementation meets the
latency gate; constrained decoding remains enabled only for the compact Gemma UI DSL.

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

- The ABI represents grid, bounded 2D canvas and stateful tracker mini-apps, not
  arbitrary native applications.
- The 41 A2UI catalog components now share one ViewModel-owned mutable data model;
  navigation and overlay state survive preview/full-screen transitions. Process-death
  restoration of a complete generated bundle remains outside the current Studio.
- Modal, detached bottom-sheet, menu, image fallback and named-event paths are
  implemented. A live OPPO run rendered an allowlisted hero photograph, semantic
  icons, writable field, slider and progress in immersive mode. The broader
  screenshot, TalkBack and dynamic-type matrix is still pending.
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

Device acceptance requires cold and hot runs, valid source tabs, generated
Pong, Arkanoid and tank interaction, held-out runner evaluation, reset,
cancellation, one invalid-output rejection, all nine UI/logic backend combinations,
missing-key and offline failures, plus captured native/cloud timing evidence.
