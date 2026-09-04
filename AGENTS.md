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

DEAL and Deal UI are independent platform-neutral transpilers and semantic checkers. The separate
streaming-compiler owns all LLM API calls, prompts, streaming tool transport, semantic generation,
repair/refinement rounds and their metrics. Studio is a product client of streaming-compiler and of
the transpilers' versioned stateless protocol. Production Android code
must not parse compiler ASTs, scan source with regexes, derive AppInterface, assign semantic ids,
interpret diagnostic text, construct model tools or repair context, implement repair policy, render
unchecked output or commit partial source pairs. It may transport canonical sources, compiler
operations, model requests and model responses between their owning boundaries. Transpiler behavior
belongs upstream; model orchestration belongs in streaming-compiler. The
embedded DEX bridge is only a transitional delivery adapter for deterministic transpiler APIs.

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

1. The selected cloud model calls `submit_deal_program` once with nominal types, external actions, reusable helper
   signatures, capabilities and every compact function body. The model does not select a separate
   root-state name; the compiler infers the unique root from nominal type references.
2. The compiler creates stable typed holes for `initialState`, helpers and `@ui-update` functions
   from that same call and checks every supplied body independently.
3. Every body is projected to canonical DEAL and accepted only after the pinned production parser,
   type checker and AppInterface identity check pass.
4. If holes remain, `repair_deal_batch` contains only their signatures and diagnostics; accepted
   declarations and bodies are immutable and omitted from repair context.
5. The exact accepted DEAL and extracted `AppInterfaceV1` are supplied to Deal UI generation.
6. The selected cloud model calls `submit_deal_ui_sections` once with two to six cohesive sections. The compiler
   accepts one compact, app-owned theme in that initial call, checks each section independently,
   commits valid sections and defers later siblings behind a rejected dependency without asking
   the model to regenerate them. The compiler emits the sole `ui.AppTheme` and `ui.Root` wrappers;
   generated section bodies may emit neither boundary.
7. A rejected UI section becomes the only schema-allowed repair target. A run gets one Flash repair
   before Pro escalation. If a model marks a valid section final before all declared actions or host
   capabilities are represented, the compiler preserves that section as partial progress and emits
   the exact missing bindings as obligations for the next round. A byte-identical rejected candidate
   ends the loop immediately.
8. The complete pair passes cross-artifact, capability, resource and runtime smoke validation before
   it becomes interactive.

Malformed, empty or truncated tool arguments are transport failures, not program repairs. The
The cloud connector validates the complete function-call argument object before invoking a compiler,
treats the final SSE item as authoritative over streamed deltas and may repeat the same transport
request once. A failed transport attempt must not mutate compiler state or consume a semantic repair
round. TTFC for tools means the first structurally valid compiler call.

Repair budgets are progress-aware. The fixed Flash and Pro budgets remain latency ceilings; one
additional round may be granted only when the compiler accepted new immutable graph content at the
budget boundary, and all generation is hard-capped. Rejections alone never buy another model call.

DeepSeek and Cerebras are production cloud providers behind the same canonical compiler API. A run
always finishes and checks DEAL before starting Deal UI, and the two stages may select providers
independently. Provider differences stop at authenticated transport, model identifiers, streaming
event parsing and usage metadata; they do not create different prompts, compiler semantics, repair
rules, artifacts or runtimes. Future local generators may return only behind the same canonical
compiler API and artifact contract. Do not reintroduce concurrent generation coordinated by an
inferred planner, AppPlan or a second runtime path.

Compiler tools are a compact semantic API, not token-level constrained decoding and not one tool
call per AST constructor.

### Repair Granularity Invariant

The independently checked and replaceable unit is one typed function body or one named Deal UI
surface. Compactness is measured by body size, repair input/output tokens and changed graph units,
not by minimizing the number of nominal action types.

- Never combine navigation, collection mutation, confirmation, selection and host events in a
  catch-all command action merely to reduce declaration count. Structurally identical operations may
  share a parameterized action only when every field has one stable meaning and the handler performs
  one cohesive state transition.
- Initial declarations must expose enough small actions and pure helper holes for a rejected body to
  be corrected without replacing unrelated accepted behavior. Accepted sibling bodies remain
  immutable during compiler repair.
- A compiler repair request contains only unresolved or rejected graph units, their exact signatures,
  stable diagnostics and the minimum declaration context required to type-check them. Repeating an
  unchanged rejected body is no progress and terminates that repair path.
- Natural-language product refinement is distinct from compiler repair. A compatible refinement may
  replace existing function bodies or named UI surfaces. A structural refinement may add or change
  declarations only through a new canonical graph revision, followed by AppInterface extraction and
  dependent Deal UI regeneration. Both paths compile completely and swap atomically; failure retains
  the previous runnable revision.
- Do not expose artifact or scope selection to the user. One ordinary text request determines the
  smallest valid revision internally.

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
- A failure observed in an acceptance scenario is evidence, not a specification. Before editing
  production code, restate it as a domain-neutral invariant and give at least two unrelated examples
  that require the same rule. If that cannot be done, keep the change in the test/evaluation layer and
  do not add it to the language, component pack, prompt, compiler, repair loop or runtime.
- Production validation must never inspect section ids, visible copy, field names, action names or
  prompt keywords to infer application meaning. Structural rules may distinguish only declared
  language/framework concepts such as route, widget, overlay, collection, capability and host effect.
- Do not repair a generated artifact in production by injecting scenario-authored source. The same
  candidate must pass or fail through the generic compiler API used by unknown held-out requests.
- Never add a production component, compiler rule, prompt branch, repair strategy, renderer path,
  persistence shape or host integration specifically for an acceptance scenario or a currently
  observed PRD. Generation and repair must not select implementation paths by scenario identity,
  domain vocabulary or recognizable prompt shape, even when that would improve the fixed matrix.
- Do not tune static production instructions after inspecting one scenario's output. Prompt changes
  must describe a general language rule and pass the prompt generalization guard.
- The fixed matrix proves regressions only. Every release candidate must also pass held-out
  compositional requests that were not used to design the current ABI. Success on any finite list
  must never be reported as arbitrary-application generalization.

No release gate is considered closed by implementation, a single successful generation or a
hand-picked screenshot. A gate closes only after its recorded matrix or soak measurement passes on
the pinned compiler, pack, model and device/network profile. Until then the build remains internal.

A normal cloud run is budgeted for one complete DEAL call and one complete Deal UI batch call.
Diagnostic retries are a failure ceiling, not expected progress. Record TTFT, graph rounds,
accepted/rejected holes, input/cache/output tokens, local compiler time and wall time.

## Runtime And UI

Deal UI is pure and declarative. Repetition uses typed `ForEach`; filtering, sorting, derived strings
and domain transformations stay in DEAL. Pointer phase is `0=down`, `1=move`, `2=up`; an ordinary
tap produces down and up without requiring move. Integer event payloads must enter DEAL as `Int`,
not JVM `Long`.

Core DEAL remains a mutable TypeScript-shaped language and may use indexed reads where its type
system permits them. Deal UI remains a separate restricted declaration language: no indexing, array
literals, assignments or arbitrary calls. The Deal UI compiler treats state/action values borrowed by
UI handlers as immutable, tracks aliases and helper mutation summaries, and rejects writes or escape.
This is a framework static guarantee, not a change to core DEAL mutation semantics or an unavailable
readonly runtime feature. `ForEach` is the only dynamic collection-rendering construct in Deal UI.

Presentation-owned selection and navigation are compositional: use `NavigationBar`, `Tabs`, `Choice`
and `Menu` with nominal `NavigationItem`, `TabItem`, `ChoiceItem` and `MenuItem` children. Each item
binds its own checked action. Dynamic children are produced with `ForEach`. Array-prop navigation is
v11 restore syntax only and must never be generated for v12.

The component pack is generic and versioned. It must cover:

- adaptive Root, Column, Row, Stack, Grid, Scroll, Section, Card and Spacer layout;
- Text, IntText, Icon, Badge, Stat, ListItem, progress and empty/error states;
- Button, IconButton, TextField, Toggle, Choice and Slider controls;
- HTTPS Image and a closed semantic icon catalog;
- Route, Dialog/Modal, BottomSheet, Menu and Snackbar presentation;
- FrameClock, MinuteClock, PointerSurface and Canvas host ingress;
- declared, permission-aware reusable host effects rather than application-specific callbacks.

Every generated application owns one compact checked theme, independent of the Studio shell. The
theme carries two seed colours plus style, shape, density and surface treatment. Compose derives
accessible Material roles and fixed semantic success, warning and error roles from that declaration;
the model must not repeat raw colours across component props. Themes are selected through the generic
compiler tool schema, never through named app-family palettes or scenario routing. Cards remain at
most 8 dp even when controls use pill geometry. Raw colours remain available only for explicit Canvas
graphics where semantic Material roles cannot represent the scene.

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

Saved canonical apps may be projected onto the Android home screen in two forms. A pinned app icon
opens `GeneratedAppActivity`, a dedicated host for the saved app; it is not a generated APK. An
interactive app widget renders either the app's optional `ui.Widget` subtree or a generic compact
projection of the checked app UI. Both surfaces load the same saved sources, revalidate them with the
pinned toolchain, execute the same nominal DEAL update handlers and share one source-bound durable
state. Widget acceptance may depend only on Android `RemoteViews` capabilities and resource bounds,
never on app names, domains or regression scenarios. Widget layout is selected from the actual host
dimensions (compact, medium or expanded); do not hard-code launcher cell counts or OEM branches.

## Save And Refine

Saving persists canonical `app.deal`, `app.dealui` and provenance. Never persist checked IR as the
source of truth. The app-owned `ui.AppTheme` is part of `app.dealui`, so saved previews and fullscreen
restores reproduce the same visual identity. Restore selects the recorded pack, verifies recorded
compiler/toolchain provenance, then reparses and recompiles both files before creating a new runtime.
Unavailable provenance quarantines the record rather than executing stale code. Library cards render
noninteractive live previews; opening a card creates an interactive fullscreen session.

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

Iterative-development acceptance uses at least one held-out complex application that was not used to
shape production prompts or ABI. Starting from a runnable base, apply multiple ordinary text requests
that independently add behavior, add or change a screen, refine visual hierarchy and exercise
save/restore. At every step record selected revision mode, changed DEAL holes and Deal UI surfaces,
input/output tokens, compiler diagnostics, time to runnable and whether state was preserved. Include
one intentionally invalid change and verify atomic rollback. The test passes only when local edits do
not regenerate unrelated accepted units and structural edits create a new checked AppInterface.

## Validation

Before a change is complete, run the narrowest relevant checks and then the product gates:

```bash
./gradlew :app:testDebugUnitTest :deepseek-connector:testDebugUnitTest
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Connected acceptance uses an ARM64 Android device because the embedded production DEAL toolchain is
a device artifact. At minimum run `CanonicalDealToolchainDeviceTest`,
`CanonicalDealUiTouchDeviceTest` and the cloud scenario tests selected for the change.

Do not claim visual or device acceptance when the phone was unavailable. Preserve the exact command
and mark that gate pending.

## Repository Map

```text
app/src/main/java/com/offlineassistant/app/generatedapp/  Studio, compilers, runtime and renderer
deepseek-connector/                                      streaming/tool-call cloud client
app/src/debug/assets/deal-android-toolchain.dex           pinned production DEAL toolchain
tooling/deal-ui-pack/                                     tracked versioned Deal UI component pack
tooling/deal-android-bridge/                              portable compiler bridge and toolchain lock
docs/superpowers/plans/                                   implementation decisions and status
```

The package namespace remains `com.offlineassistant.app` temporarily to avoid a low-value mechanical
rewrite. It is not evidence that assistant functionality remains in the product.
