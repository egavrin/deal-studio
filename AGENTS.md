# DEAL Studio Repository Guidance

## Product Boundary

This repository builds **DEAL Studio**, an Android application for generating, running, editing and
saving small interactive applications. The application id is `com.dealstudio.app`; debug builds use
`com.dealstudio.app.debug`.

The former offline voice assistant is not part of the product, manifest or APK. Its last complete
workspace snapshot is preserved on `egavrin/assistant-backup-2026-09-03`. Do not restore assistant
routing, speech recognition, speech synthesis, RuBERT, fixed assistant widgets, AppFunctions or
voice-service permissions on the DEAL Studio branch.

Keep Studio in the existing Android application module. Do **not** extract its shared code into a
new library module. Product separation is an application and source boundary, not a library-module
migration.

The user-facing application and generated examples use English. Generated applications must adapt
to compact phones, ordinary phones and unfolded displays without device-specific branches.

## Canonical Artifact

A generated application contains exactly:

```text
app.deal       authoritative state, data, rules, update functions and declared host effects
app.dealui     pure typed presentation and action bindings; labelled DUI in product UI
metadata       compiler, component-pack, model, token and timing provenance; not executable
```

There is no generated `AppPlan`, layout plan, profile, JSON AST, bytecode file or app-family record.
`AppInterfaceV1` is extracted from accepted DEAL and passed read-only to Deal UI generation. It is
ephemeral compiler state and is never independently generated or persisted.

State and data belong in DEAL/AppInterface. The DEAL root state is also the complete presentation
view model for the sequential Deal UI step: chart series, labels, formatted mixed text, status text
and selected/summarized values must be explicit fields and stay consistent through every update.
Deal UI cannot call helpers, index or transform arrays, compute lengths or coerce numbers to strings.
Deal UI does not own mutable business state, calculate domain transitions or duplicate rules.
Compose only renders checked portable Deal UI IR and sends typed events back to DEAL.

## Generation Protocol

Cloud generation is sequential and compiler-guided:

1. DeepSeek calls `create_deal_program` once to declare nominal types, external actions, reusable
   helper signatures and capability requirements.
2. The compiler creates stable typed holes for `initialState`, helpers and `@ui-update` functions.
3. DeepSeek calls `apply_deal_graph_patch` with one batched set of compact function bodies.
4. Every body is projected to canonical DEAL and accepted only after the pinned production parser,
   type checker and AppInterface identity check pass.
5. The exact accepted DEAL and extracted `AppInterfaceV1` are supplied to Deal UI generation.
6. DeepSeek authors canonical `app.dealui` through compiler tools. Only compiler-accepted UI commits
   may update the progressive preview. A rejected section becomes the only available repair target;
   accepted section ids cannot be repeated and new sections cannot bypass a pending repair.
7. The complete pair passes cross-artifact, capability, resource and runtime smoke validation before
   it becomes interactive.

DeepSeek Flash is the default cloud backend. UI and logic backend choices remain independent for
future local experiments, but a fully cloud run always finishes DEAL before starting Deal UI. Do
not reintroduce concurrent cloud generation coordinated by an inferred planner.

Compiler tools are a compact semantic API, not token-level constrained decoding and not one tool
call per AST constructor.

## Generalization Invariant

DEAL Studio implements one domain-independent application language, typed UI language, compiler and
runtime. It does not implement a finite catalog of recognized applications.

- The mandatory scenarios are a frozen external black-box regression set, not the implementation
  taxonomy and not generation profiles. Scenario prompts and assertions may exist only in test,
  evaluation, preset and dataset sources.
- Static production prompts, compiler tools, Kotlin/Compose runtime code and validators must not
  name or branch on a concrete app family, scenario, entity or action such as medication, chess,
  weather, dose or move. Do not add keyword routing, blueprints, canned reducers, template fallback,
  task-scoped profiles or hidden reference implementations.
- Compiler acceptance operates only on grammar, types, declared effects/capabilities, resource
  bounds, stable identities and cross-artifact bindings. Never infer domain semantics from field or
  action names and never make a scenario pass with a name-based validation or repair heuristic.
- Fix a scenario failure only at a reusable boundary: language construct, type/interface contract,
  generic component, model/compiler protocol, diagnostic, renderer, persistence or host capability.
  A new ABI primitive must use domain-neutral types and semantics and demonstrate utility in at least
  three unrelated application classes before it is accepted.
- Do not tune static production instructions after inspecting one scenario's output. Prompt changes
  must describe a general language rule and pass the prompt generalization guard.
- The fixed matrix proves regressions only. Every release candidate must also pass held-out
  compositional requests that were not used to design the current ABI. Success on any finite list
  must never be reported as arbitrary-application generalization.

A normal cloud run is budgeted for one declaration call, one batched DEAL fill call and one Deal UI
call. Diagnostic retries are a failure ceiling, not expected progress. Record TTFT, graph rounds,
accepted/rejected holes, input/cache/output tokens, local compiler time and wall time.

## Runtime And UI

Deal UI is pure and declarative. Repetition uses typed `ForEach`; filtering, sorting, derived strings
and domain transformations stay in DEAL. Pointer phase is `0=down`, `1=move`, `2=up`; an ordinary
tap produces down and up without requiring move. Integer event payloads must enter DEAL as `Int`,
not JVM `Long`.

The component pack is generic and versioned. It must cover:

- adaptive Root, Column, Row, Stack, Grid, Scroll, Section, Card and Spacer layout;
- Text, IntText, Icon, Badge, Stat, ListItem, progress and empty/error states;
- Button, IconButton, TextField, Toggle, Choice and Slider controls;
- HTTPS Image and a closed semantic icon catalog;
- Route, Modal, BottomSheet and Snackbar presentation;
- FrameClock, MinuteClock, PointerSurface and Canvas host ingress;
- declared, permission-aware reusable host effects rather than application-specific callbacks.

Utility applications use native semantic components. Canvas is reserved for games and genuinely
spatial visualizations. Do not put cards inside cards. Cards use at most an 8 dp radius. Use stable
responsive constraints, 48 dp touch targets, accessible labels, dynamic type, Material colour roles
and concise hierarchy. Never hard-code a fixed device size outside the logical coordinate system of
a Canvas. Remote media is HTTPS-only and must come from an authoritative URL supplied in input or
state; the model may not invent image URLs.

`Grid.columns` is the maximum column count. Supplying `minimumCellWidth` makes it an adaptive grid:
the renderer reduces the count when a compact viewport cannot fit that minimum. Container width is
owned by the parent; nested Row/Column nodes must not force `fillMaxWidth` and destroy composition.
Library thumbnails render through the real runtime at a stable 360 dp logical width and are scaled
noninteractively. Fullscreen utility apps receive host scrolling when they do not declare their own
Scroll, PointerSurface or Canvas; inline Studio previews never create nested host scroll containers.

Fullscreen is the real runtime, not a screenshot or second instance. Expanding and collapsing keeps
the same DEAL session and state. Saved applications remain inside the sandboxed Studio runtime; the
product does not emit arbitrary APKs.

## Save And Refine

Saving persists canonical `app.deal`, `app.dealui` and provenance. Never persist checked IR as the
source of truth. Restore reparses and recompiles both files with the current pinned toolchain before
creating a new runtime. Library cards render noninteractive live previews; opening a card creates an
interactive fullscreen session.

The user edits an application with one natural-language request. Do not expose manual UI/logic
scope controls. Two narrow edit agents may inspect the same request: the DEAL agent replaces only
responsible function bodies or returns unchanged; the Deal UI agent replaces only responsible view
units or returns unchanged. The host validates both candidates and swaps them atomically. A failed
edit preserves the previous runnable application. Changing the public AppInterface requires a
controlled full revision, not an unvalidated in-place mutation.

Repair handles compiler or functional diagnostics for the responsible artifact. Transport-level
compiler checks and semantic request fidelity are separate stages. Do not hide incorrect behaviour
with scenario-specific host heuristics.

## Acceptance

The mandatory scenario matrix is a regression and product-quality floor, not an exhaustive list of
applications the architecture recognizes:

1. medication;
2. exam preparation;
3. health/workout tracking;
4. todo;
5. weather;
6. tic-tac-toe;
7. Arkanoid;
8. chess.

Passing means more than compiling. Each scenario must:

- generate canonical DEAL and Deal UI without a template fallback;
- launch, accept touch/input and preserve state through Studio/fullscreen transitions;
- save, recompile, restore and show a live library preview;
- accept one natural-language refinement without full regeneration when the interface is unchanged;
- render without clipping on compact, phone and unfolded widths;
- look like a polished native application, not a student demo;
- pass screenshot review, dark/light theme, dynamic type, TalkBack labels and minimum touch targets.

Raw blue button grids, inaccessible canvas hit zones, monochrome screens, nested cards, placeholder
icons, technical diagnostics in the app surface and fixed-size phone layouts are acceptance failures.

## Validation

Before a change is complete, run the narrowest relevant checks and then the product gates:

```bash
./gradlew :app:testDebugUnitTest :deepseek-connector:testDebugUnitTest
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Connected acceptance uses an ARM64 Android device because the embedded production DEAL toolchain and
local llama runtime are device artifacts. At minimum run `CanonicalDealToolchainDeviceTest`,
`CanonicalDealUiTouchDeviceTest` and the cloud scenario tests selected for the change.

Do not claim visual or device acceptance when the phone was unavailable. Preserve the exact command
and mark that gate pending.

## Repository Map

```text
app/src/main/java/com/offlineassistant/app/generatedapp/  Studio, compilers, runtime and renderer
deepseek-connector/                                      streaming/tool-call cloud client
app/src/main/cpp/                                        local llama.cpp compatibility runtime
app/src/debug/assets/deal-android-toolchain.dex           pinned production DEAL toolchain
training/generated_app/                                  local-model datasets and evaluation
docs/superpowers/plans/                                   implementation decisions and status
```

The package namespace remains `com.offlineassistant.app` temporarily to avoid a low-value mechanical
rewrite. It is not evidence that assistant functionality remains in the product.
