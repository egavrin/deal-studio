# A2UI Dataset And Constrained Decoding Plan

**Status:** in progress
**Date:** 2026-08-25
**Depends on:**
`docs/superpowers/specs/2026-08-25-on-device-generated-app-studio.md`

## Implementation Progress (2026-08-25)

Completed for the UI-first host increment:

- declared and content-addressed the 18 pinned Basic Catalog components, 13
  Assistant Catalog components and 14 catalog functions;
- generated a closed Draft 2020-12 `UiBlueprintV1` schema and stable prompt
  signatures from one catalog source;
- added deterministic task scheduling across components, domains, locales,
  viewports, themes, product states, density, interaction and node bands;
- added a host-only `deepseek-v4-flash` Responses client using JSON Schema output,
  content-addressed caching, a bounded `none -> minimal -> none` correction loop,
  provider provenance, latency/usage accounting and task-hash-safe checkpoints;
- added graph, reference, binding, action, URL, accessibility and task-alignment
  validation plus deterministic A2UI Express and v1-style wire compilation;
- added recursive nested/empty collection binding validation, collection-scoped
  action contexts, data-bound design-token enums and a strictly checked
  task-specific required-component transport index;
- added identifier-insensitive structural clustering, exact deduplication,
  cluster-level train/validation/test assignment and quality reports;
- added eight immutable hand-authored fixtures covering all 31 components and 22
  mutation/contract tests, wired into the training CI job;
- separated the upstream A2UI source identity from the project-owned derived Basic
  Catalog runtime ID; no upstream wire-conformance claim is made.
- added an independent local / DeepSeek Flash / DeepSeek Pro selector for each role
  in the existing compact-DSL Studio. This is a validated inference comparison path,
  not completion of the broader A2UI Express runtime milestone.

Live DeepSeek calibration evidence:

| Run | Fresh/cache | First pass | Final | Notes |
| --- | --- | ---: | ---: | --- |
| component-index smoke | fresh, 1 candidate | 1/1 | 1/1 | provider accepts scalar task-specific index |
| component-index pilot | fresh first/second pass, resumed final correction | 13/20 | 20/20 | validates bounded recovery behavior |
| 50-record v3 | fresh, empty cache | 27/50 | 47/50 | before required-component index |
| 50-record v4 | fresh, empty cache | 23/50 | 45/50 | strict index enabled; p50 9.3 s, p95 40.6 s |

The v4 run used 1,097,869 reported tokens and accepted 45 unique blueprints. Its
five quarantined candidates failed graph/binding/depth semantics after all three
attempts. No failed or partially generated candidate enters a split.

Not yet complete:

- upstream A2UI wire conformance, production Express parser round trips, render and
  interaction harnesses, screenshot/accessibility gates and the 1,000-record pilot
  remain Milestones 1-2 work;
- the latest fresh 50-record run is below the Milestone 2 gate (`46%` first pass,
  `90%` final versus required `80%`/`95%`), so scaling generation now would create
  avoidable cost without production-quality evidence;
- 90,000-record generation, student training, llama.cpp syntax/semantic constrained
  decoding and Android progressive rendering remain later milestones.

## Objective

Replace the current demonstration corpus with a catalog-driven, automatically
validated dataset that trains the on-device UI model to compose new interfaces
instead of selecting one of a few memorized layouts. Add constrained decoding to
the Android `llama.cpp` runtime so every emitted token remains inside the selected
UI or behavior grammar.

The product objective must be stated precisely:

- every request must produce either a valid supported UI or a deterministic valid
  fallback UI;
- constrained decoding must make syntax failures exceptional, not merely repairable;
- catalog, binding, action, accessibility and resource semantics must still be
  checked after decoding;
- usefulness for an arbitrary request is an evaluation target, not something a
  grammar or a finite dataset can mathematically guarantee;
- unsupported capabilities must fail closed into a useful text/clarification UI,
  never arbitrary native code.

## Current Baseline And Why It Is Insufficient

The current training corpus is a smoke dataset:

| Dataset | Train rows | Unique train targets | Effective behavior |
| --- | ---: | ---: | --- |
| DEAL | 126 | 7 programs | reproduce seven game/toy programs |
| UI DSL | 180 | 3 trees | select three presentation layouts |

Each DEAL family repeats one source program 18 times. UI validation and test use
the same three target trees as training. The held-out runner candidate reproduced
the generated Arkanoid output, demonstrating that syntax compliance on known
families is not behavior generalization.

The current runtime also uses only greedy sampling in
`app/src/main/cpp/generated_app_llama.cpp`. The production parser rejects invalid
output after generation, but the sampler does not prevent invalid tokens while the
model is decoding.

## A2UI Research Decision

### Version policy

As of 2026-08-25, A2UI v0.9.1 is documented as current and v1.0 as candidate.
A2UI Express is a proposal targeting v1.0. This project will therefore:

- adopt A2UI concepts and A2UI Express as an internal, pinned profile;
- pin the upstream source commit and catalog digest used to generate each dataset;
- publish a project-owned catalog ID and schema version;
- keep a compiler adapter between the internal AST and upstream wire protocol;
- avoid claiming wire compatibility until the target A2UI version is stable and
  the conformance suite passes.

Research baseline:

| Item | Pinned value |
| --- | --- |
| Upstream repository | `a2ui-project/a2ui` |
| Upstream commit inspected | `7541f953050cd58b80f0bf5d85fe2d63192af305` |
| Protocol profile inspected | A2UI v1.0 candidate |
| Basic catalog SHA-256 | `29e01ac2cf69dc5860ad060f5a60c67fa5cdaa8a78ecab1018f531b178fa5c00` |
| Basic catalog size | 18 components, 14 functions |
| Compact syntax | A2UI Express proposal |

### Why A2UI Express is the target model syntax

A2UI's flat component list, stable IDs, separate data model and catalog negotiation
are better suited to streaming and updates than the current nested mini-DSL. A2UI
Express adds a compact line-oriented model syntax that compiles to the standard
wire representation. The official proposal reports a 55-70% token reduction over
wire JSON and explicitly targets small on-device models.

The model-facing target will be a pinned A2UI Express subset rather than verbose
A2UI JSON. The dataset source of truth will still be a typed JSON AST. The build
pipeline will deterministically derive both Express and wire JSON from that AST.

```text
typed UiBlueprint/A2UI AST
       |            |
       |            +-> canonical A2UI wire JSON -> schema validation
       |
       `-> canonical A2UI Express -> student SFT target
```

This avoids trusting teacher-generated punctuation and prevents format variation
from becoming accidental training signal.

## Component Catalog

### A2UI Basic Catalog coverage

The first renderer increment must support all 18 inspected Basic Catalog
components, even if some media components initially render a controlled unavailable
state while offline.

| Group | Components | Required dataset behavior |
| --- | --- | --- |
| Text and symbols | `Text`, `Icon` | literal and bound content, variants, accessibility |
| Media | `Image`, `Video`, `AudioPlayer` | URL/data binding, description, fit, unavailable state |
| Layout | `Row`, `Column`, `List`, `Card`, `Divider` | static and dynamic children, weights and alignment |
| Navigation/overlay | `Tabs`, `Modal` | valid child references and state-preserving interaction |
| Actions | `Button` | label child, allowlisted event or renderer function |
| Inputs | `TextField`, `CheckBox`, `ChoicePicker`, `Slider`, `DateTimeInput` | data binding, validation, keyboard/input semantics |

The inspected Basic Catalog functions are:

`and`, `email`, `formatCurrency`, `formatDate`, `formatNumber`, `formatString`,
`length`, `not`, `numeric`, `openUrl`, `or`, `pluralize`, `regex`, and `required`.

### Project Assistant Catalog v1

The Basic Catalog cannot express every assistant result well without awkward Row
and Column trees. Add a project-owned catalog, versioned independently and mixed
only with the same A2UI protocol version.

| Component | Purpose | Initial properties |
| --- | --- | --- |
| `Badge` | compact status/source indicator | text, tone, icon |
| `Progress` | determinate/indeterminate progress | value, max, label, state |
| `Metric` | prominent value with label/change | label, value, unit, trend |
| `KeyValue` | compact fact row | label, value, icon |
| `Grid` | responsive repeated layout | children/template, columns, gap |
| `DataTable` | dense comparable records | columns, rows, sort state |
| `Chart` | bounded line/bar/area visualization | series, axes, legend, variant |
| `Timeline` | ordered events and schedule | items, time, state, action |
| `ImageGallery` | attributed image results | items, layout, selected item |
| `SourceList` | citations and source previews | sources, selected source |
| `MapPreview` | noninteractive or delegated map result | markers, viewport, provider |
| `CodeBlock` | readable generated code | language, content, copy action |
| `InteractiveSurface` | bridge to validated DEAL state | module ID, aspect, input mode |

Arbitrary colors, dimensions, SVG, HTML, JavaScript and Android class names are not
catalog properties. Presentation uses design tokens and renderer-owned Material
components. Container additions are catalog-major changes because an older
renderer dropping a container also drops its subtree.

### Catalog compilation artifacts

Maintain these generated and reviewed artifacts:

```text
ui/catalogs/basic-pinned/catalog.json
ui/catalogs/assistant-v1/catalog.json
ui/catalogs/resolved/assistant-v1.resolved.schema.json
ui/catalogs/resolved/assistant-v1.signatures.txt
ui/grammar/a2ui-express-assistant-v1.gbnf
ui/grammar/deal-v3.gbnf
ui/catalogs/manifest.json
```

`manifest.json` records protocol version, catalog IDs, upstream commit, every
artifact digest, compiler version and grammar version. A model artifact is
compatible with exactly one catalog/grammar compatibility range.

## Canonical Dataset Record

JSONL chat messages are derived training views, not the source of truth. Store one
canonical record per accepted surface:

```json
{
  "schema_version": 1,
  "id": "sha256:...",
  "split": "train",
  "task": {
    "request": "Show my three most important morning events",
    "locale": "en-US",
    "domain": "calendar",
    "task_kind": "timeline",
    "viewport": "phone_compact",
    "theme": "light",
    "data_availability": "mock"
  },
  "catalog": {
    "protocol_version": "1.0-internal",
    "catalog_ids": ["...basic...", "...assistant-v1..."],
    "resolved_schema_sha256": "..."
  },
  "blueprint": {
    "data_schema": {},
    "events": [],
    "acceptance": []
  },
  "target": {
    "ast": {},
    "a2ui_express": "<a2ui>\n...\n</a2ui>",
    "a2ui_wire": {},
    "data_model": {}
  },
  "validation": {
    "catalog": "pass",
    "references": "pass",
    "bindings": "pass",
    "accessibility": "pass",
    "render": "pass",
    "interaction": "pass"
  },
  "provenance": {
    "generator": "deepseek-v4-flash",
    "reported_model": "...",
    "system_fingerprint": "...",
    "prompt_version": "...",
    "request_sha256": "...",
    "response_sha256": "...",
    "generated_at": "..."
  }
}
```

Do not store hidden reasoning. Do not store API keys, authorization headers,
personal content or device data. The mutable DeepSeek model alias is not treated as
reproducible identity; accepted records and raw final structured responses are
content-addressed and immutable.

## Shared App Blueprint

Independent UI and behavior generation cannot reliably agree on state paths and
action names. Introduce a small shared blueprint before parallel generation:

```text
request
  -> constrained AppBlueprint
       |- data schema and initial data
       |- actions and typed parameters
       |- required capabilities
       |- UI acceptance criteria
       |- behavior acceptance criteria
       |
       +-> UI model -> A2UI Express
       `-> behavior model -> DEAL
```

The blueprint is expected to be 80-200 output tokens. The UI and DEAL models still
run in parallel after it completes. The added planning latency is acceptable only
if measured end-to-end; it removes a much larger class of binding and action
mismatch failures.

## Coverage Model

### Task domains

Generate balanced tasks across these domains rather than only games:

| Domain group | Example surfaces |
| --- | --- |
| Assistant answers | definition, summary, comparison, clarification, error |
| Search/research | cited answer, source list, image results, related questions |
| Personal productivity | notes, tasks, reminders, calendar, routines |
| Communication | contact, call/message confirmation, email draft |
| Device control | permission, settings handoff, media control, app launch |
| Information | weather, finance, travel, navigation, package/status tracking |
| Data display | dashboard, metrics, table, chart, timeline, key-value summary |
| Forms | single field, multi-step form, survey, filters, date/time selection |
| Media | gallery, audio result, video result, now playing |
| Education | quiz, flashcards, calculator, step-by-step explanation |
| Interactive tools | board, counter, canvas, timer, score tracker |
| Generated mini-apps | grid games and bounded 2D DEAL surfaces |
| Product states | empty, loading, partial, stale, offline, denied, retry |
| Unsupported/OOD | safe explanation, clarification or text fallback |

### Structural coverage

Use a covering-array generator instead of random component sampling.

| Dimension | Required distribution |
| --- | --- |
| Components | every component focal in at least 2,000 accepted records |
| Functions | every function used in at least 500 accepted records |
| Component pairs | 100% valid pairwise coverage |
| Critical triples | 100% for form, collection, action and media combinations |
| Node count | 30% 4-8, 45% 9-20, 20% 21-48, 5% 49-64 |
| Depth | balanced across 2-8, no production tree deeper than 8 |
| Data | at least 60% use typed data bindings |
| Interaction | at least 50% contain actions or inputs |
| Dynamic lists | at least 15% use list/grid templates |
| Updates | at least 10% are incremental surface/data updates |
| Accessibility | 100% media/input/action components have valid semantics |
| Themes | light, dark, high contrast and renderer-controlled dynamic color |
| Viewports | compact phone, foldable, tablet and large-font constraints |

### Language coverage

The first broad model remains English-first but must not encode English text into
the grammar:

| Locale class | Share |
| --- | ---: |
| English | 60% |
| Russian | 30% |
| Mixed/locale stress | 10% |

Identifiers remain stable UAX #31-compatible names. Visible literals follow the
request locale. Locale split tests hold out paraphrases, not syntax.

## Target Dataset Size

The first production-oriented corpus target is 90,000 accepted records:

| Partition | Records | Unique target requirement |
| --- | ---: | --- |
| Core composition | 30,000 | at least 27,000 AST hashes |
| Forms and validation | 12,000 | at least 10,000 AST hashes |
| Collections and dynamic data | 10,000 | at least 9,000 AST hashes |
| Dashboards and custom components | 10,000 | at least 9,000 AST hashes |
| Media and search results | 7,000 | at least 6,000 AST hashes |
| Incremental update/follow-up | 7,000 | at least 6,000 update programs |
| Repair and mutation | 7,000 | every input invalid for a known reason |
| Unsupported/OOD fallback | 7,000 | all targets valid safe fallback surfaces |

No exact target may occur more than three times in train. Prompt uniqueness is not
counted as target diversity. Near-duplicate ASTs are clustered before splitting.

## DeepSeek Flash Teacher Pipeline

Use `deepseek-v4-flash` through the DeepSeek Responses API for dataset generation.
The API currently supports JSON Schema structured output. This is preferable to
free-form JSON mode because the teacher output can be constrained to the canonical
`UiBlueprint` schema.

```text
coverage scheduler
  -> structured task seed
  -> DeepSeek Flash: strict UiBlueprint JSON
  -> deterministic AST/A2UI compiler
  -> validators and render harness
  -> accepted content-addressed record
  -> paraphrase/update/repair derivations
```

Teacher request policy:

- use a stable system prefix so provider prompt caching can apply;
- use structured output with `text.format.type=json_schema`;
- disable hidden thinking for routine generation;
- use Flash low reasoning only for complex composition candidates when supported;
- use low temperature for blueprint generation and higher temperature only for
  request paraphrases;
- request one candidate first, then use at most two schema-constrained corrections
  with the rejected candidate and exact validator diagnostics;
- cap concurrency initially at 64 and tune from measured errors/rate limits;
- cache every successful final response by prompt and schema digest;
- quarantine API empty output, truncation and model-version drift;
- never automatically accept a teacher self-score as validation evidence.

DeepSeek Flash is an economical primary teacher. At the pricing inspected on
2026-08-25, 90,000 examples at roughly 2,500 uncached input and 1,000 output tokens
would cost about USD 57 before retries. A realistic pilot budget is USD 75-150
including rejected candidates and evaluation calls. Actual usage and pricing must
be measured from a 1,000-record pilot before authorizing the full run.

## Deterministic Generation And Validation

An example enters train only after every required gate passes.

### Static gates

1. Validate teacher `UiBlueprint` against its closed JSON Schema.
2. Compile blueprint to typed project AST.
3. Require exactly one root and globally unique component IDs.
4. Require every child reference to resolve.
5. Reject cycles, unreachable components and disallowed depth/node counts.
6. Resolve every component and function against the pinned catalogs.
7. Type-check every property, binding and function argument.
8. Require referenced data paths to exist in the declared data schema.
9. Restrict events to the blueprint action list and renderer allowlist.
10. Require accessibility labels/descriptions for media and icon-only actions.
11. Compile to canonical A2UI Express and canonical wire JSON.
12. Validate wire JSON against the resolved protocol/catalog schema.
13. Reject arbitrary URLs, code, HTML, platform classes and untrusted catalog IDs.

### Runtime/render gates

1. Parse the Express target with the production compiler.
2. Render through the production Compose registry in a host/device harness.
3. Exercise every declared action and input with deterministic fixtures.
4. Verify data mutations against the blueprint postconditions.
5. Capture light/dark screenshots for representative strata.
6. Check clipping, overlap, blank output, contrast, touch-target and font-scale rules.
7. Verify TalkBack semantics for all interactive and media components.
8. Enforce frame, allocation and tree-size budgets.

Visual judgment can rank already valid candidates, but cannot override a failed
schema, semantic, accessibility or interaction gate.

## Dataset Splits Without Leakage

Split after canonicalization and clustering, never by random JSONL row.

| Split | Share | Isolation rule |
| --- | ---: | --- |
| Train | 80% | no target/template cluster shared with immutable test |
| Validation | 10% | held-out AST clusters and paraphrases |
| Immutable test | 10% | frozen before training candidate selection |

The immutable test is additionally labeled into independent slices:

- unseen wording for seen structures;
- unseen component combinations using seen primitives;
- held-out task domains;
- held-out custom components;
- long and ambiguous requests;
- Russian and mixed-locale requests;
- incremental updates to existing surfaces;
- adversarial IDs, strings, prompt injection and malformed user content;
- unsupported requests requiring fallback;
- large 49-64-node surfaces;
- behavior/UI action-contract consistency.

Deduplicate by canonical AST hash, normalized blueprint hash, MinHash of requests
and structural tree-edit similarity. The test manifest contains hashes only and is
never consumed by teacher generation or training.

## Constrained Decoding In llama.cpp

### Runtime design

Add a trusted constraint identifier to `LocalLlamaBridge`:

```text
NONE
A2UI_EXPRESS_ASSISTANT_V1
APP_BLUEPRINT_V1
DEAL_V3
```

The application selects a bundled grammar by enum. User input, model output and
downloaded data can never supply arbitrary GBNF.

JNI generation changes:

1. Pass the constraint ID through Kotlin/JNI.
2. Load and digest-check the bundled grammar for the active model/catalog manifest.
3. Initialize a `llama_sampler_chain` per generation.
4. Add `llama_sampler_init_grammar(vocab, grammar, "root")` before the terminal
   greedy/distribution sampler.
5. Sample and accept through the chain for every token.
6. Fail before prompt decoding when grammar initialization fails.
7. Require a grammar-complete end state before accepting EOS or max-token output.
8. Apply the same grammar to repair generation.
9. On failure or cancellation, render the deterministic fallback surface.

The initial implementation uses core GBNF support already linked through `llama`;
it does not enable the full `llama.cpp` common/server library. JSON Schema-to-GBNF
conversion happens at build time. LLGuidance is a later benchmark candidate if the
full catalog grammar becomes too slow or difficult to maintain.

### Semantic-aware sampler

The vendored llama.cpp exposes the public `llama_sampler_i` interface. A project
sampler can therefore participate in the same chain without patching upstream
llama.cpp:

```text
catalog/request GBNF
  -> project semantic sampler
  -> greedy/distribution terminal sampler
```

The project sampler owns an incremental parser and semantic state. Its `apply`
callback masks candidate-token logits that cannot extend the current valid state;
its `accept` callback commits the selected token to the parser and symbol table.
`reset`, `clone` and `free` must preserve normal llama.cpp sampler lifecycle and
cancellation behavior.

Semantic state may enforce during decoding:

- catalog component and function membership;
- component/property compatibility beyond the generic lexical grammar;
- uniqueness of completed component identifiers;
- definition and type of data paths declared by `AppBlueprint`;
- action names and typed parameters declared by `AppBlueprint`;
- component/node/depth/resource budgets;
- topologically valid child references when using the project ordered profile;
- required accessibility fields on interactive or media components.

Do not implement semantic sampling by reparsing the full prefix for every token in
the vocabulary. Precompute token byte pieces and prefix tries, ask the incremental
parser for allowed terminals/literals, mask from the resulting token-ID sets, and
fall back to a bounded candidate simulation only for ambiguous tokens. Benchmark
against the 150K-class Qwen vocabulary; a naive parser clone per vocabulary token
would erase the benefit of the small model.

For A2UI Express, prefer statement-boundary semantic decoding:

1. Decode one grammar-valid declaration through its newline.
2. Validate and commit that declaration to the symbol table.
3. Rebuild the small request-specific constraint for the next declaration while
   retaining the model KV cache.
4. Stage valid components until `root` and every reference are complete.
5. Publish only semantically closed incremental updates.

The project A2UI Express profile should use topological declaration order where a
component may reference only previously declared children, with `root` emitted
last. This trades a small amount of syntax freedom for immediate referential
validation. Components may be staged before root and rendered as one coherent
surface when root closes.

The semantic sampler remains in this repository's JNI library and uses only the
public llama.cpp API. Do not fork or edit upstream sampler internals. A llama.cpp
submodule update must be testable by rebuilding the adapter against the public
interface.

The following properties still require final validation and cannot be guaranteed
by token masking alone:

- every required feature from the natural-language request is represented;
- the finished graph is useful, visually coherent and accessible as a whole;
- behavior implements the intended game or application rules;
- dynamic data received after generation matches runtime expectations;
- privileged actions have current permission and user confirmation;
- the complete output reaches a valid terminal state before cancellation or token
  limit.

### Grammar strategy

Maintain separate bounded grammars rather than one universal language:

- `AppBlueprint` uses constrained JSON and closed enums;
- A2UI Express grammar enforces sentinels, statements, constructor names, literal
  syntax, binding paths, enum values and bounded lists;
- DEAL grammar enforces declarations, statements, operators and function syntax;
- semantic types, symbol tables, ID uniqueness, references, ABI and action matching
  remain compiler responsibilities.

Do not encode huge optional repetitions as chains of `?`; the llama.cpp grammar
documentation warns this can make sampling extremely slow. Generate bounded
`{min,max}` repetitions and benchmark grammar-first overhead.

### Constrained-decoding acceptance

| Gate | Requirement |
| --- | --- |
| Grammar corpus | accepts 100% of canonical train/valid/test targets |
| Mutation corpus | rejects 100% of single-fault syntax mutations |
| Semantic mutation corpus | rejects duplicate IDs, bad paths/actions/references before acceptance |
| Base model fuzz | 10,000 generations parse syntactically or end in fallback |
| Tuned model fuzz | 10,000 generations parse syntactically or end in fallback |
| Semantic validity | at least 99.9% without repair on immutable in-domain test |
| Completion | no accepted truncated output |
| Performance | p50 decode regression <=10%, p95 <=15% on reference phone |
| Stability | 100 repeated constrained generations without crash/leak |

Constrained decoding guarantees token-level grammar compliance. It does not prove
that a UI is useful, that IDs resolve, that an action is safe, or that generated
behavior implements the request. Those remain mandatory post-generation gates.

## Training Curriculum

Train in stages so the small model learns the language before broad composition:

| Stage | Data | Promotion gate |
| --- | --- | --- |
| 0 | component/function microexamples | >=99.9% syntax and property accuracy |
| 1 | 4-8 node static surfaces | >=98% catalog/graph validity |
| 2 | bound data, actions and forms | >=97% binding/action validity |
| 3 | lists, tabs, modals and custom components | >=95% task rubric score |
| 4 | 21-64 node compositions | stable latency and no structural regression |
| 5 | updates, repairs, OOD fallback | >=99.9% valid fallback/update behavior |

Compare at least:

- current Gemma 3 270M adapter;
- a larger local ceiling model that still fits the target memory budget;
- untuned base models with constrained decoding;
- tuned models with and without constrained decoding.

Do not promote based on train/validation loss. Candidate selection uses immutable
generation, semantic validation, visual review and physical-device latency/PSS.

## Evaluation Metrics

Report separate metrics; do not collapse them into a single “valid” number.

| Layer | Metrics |
| --- | --- |
| Syntax | grammar completion, parser pass, truncation rate |
| Catalog | component/property/function validity |
| Graph | root, references, cycles, unreachable nodes, depth |
| Data | binding existence/type, template context, update validity |
| Interaction | action resolution, postconditions, confirmation boundary |
| Accessibility | labels, semantics, contrast, touch targets, font scale |
| Visual | clipping, overlap, blank area, hierarchy, screenshot rubric |
| Task | required content/actions present, unsupported request handling |
| Diversity | unique AST ratio, component entropy, cluster distribution |
| Runtime | TTFT, tokens/s, total, repair rate, fallback rate, PSS, thermal |

Production claims require:

- 100% crash-free rendering for accepted and fallback surfaces;
- 100% syntactic validity or deterministic fallback;
- >=99.9% catalog/graph/binding validity without repair;
- >=95% task satisfaction on in-domain immutable tests;
- >=90% useful fallback/clarification on held-out domains;
- zero unconfirmed privileged actions from generated UI.

## Implementation Milestones

### Milestone 0: Freeze contracts

Deliver:

- project catalog IDs and version policy;
- pinned A2UI source manifest;
- canonical `UiBlueprint` JSON Schema;
- accepted/fallback semantics;
- initial Express and DEAL grammar profiles.

Exit gate: one hand-authored example for every component compiles, validates and
renders through production code.

### Milestone 1: Compiler and validator

Deliver:

- schema-to-signature compiler;
- typed AST and Express parser/compiler;
- AST-to-A2UI wire compiler;
- graph, binding, action and accessibility validators;
- deterministic canonicalizer and AST hashing;
- mutation/fuzz test suite.

Exit gate: 100% of catalog fixtures pass and every seeded invalid mutation is
rejected for the expected diagnostic.

### Milestone 2: DeepSeek Flash pilot

Deliver:

- host-only Responses API generator using `DEEPSEEK_API_KEY` from the environment;
- JSON Schema structured output;
- prompt/version/provenance manifests;
- caching, retry, truncation and cost accounting;
- 1,000 accepted records across all component groups.

Exit gate: >=80% first-pass acceptance, >=95% after bounded correction, >=80%
unique ASTs and a measured full-run cost forecast.

### Milestone 3: Dataset v1

Deliver:

- coverage scheduler and domain taxonomy;
- 90,000 accepted canonical records;
- derived SFT views for blueprint and Express generation;
- immutable split manifests and leakage report;
- dataset card with exact distribution and known limits.

Exit gate: all coverage requirements are met and no immutable-test AST/template
cluster appears in train.

### Milestone 4: Constrained Android runtime

Deliver:

- trusted grammar IDs through Kotlin/JNI;
- grammar sampler chain in `generated_app_llama.cpp`;
- project `llama_sampler_i` semantic sampler with incremental symbol table;
- request-specific action and data-path constraints compiled from `AppBlueprint`;
- grammar completeness/truncation handling;
- deterministic valid fallback UI;
- host and device grammar benchmarks.

Exit gate: 10,000 constrained host generations and 100 mixed device generations
complete without an accepted syntax error, crash or leaked sampler.

### Milestone 5: Model training and selection

Deliver:

- staged curriculum runs;
- base/tuned and constrained/unconstrained ablations;
- immutable test reports;
- Pixel and OPPO latency/PSS/thermal evidence;
- selected catalog-compatible GGUF artifacts and manifest.

Exit gate: the selected local model meets syntax, semantic, visual, task and device
gates simultaneously. A larger model wins only if its product latency and memory
remain acceptable.

### Milestone 6: Progressive rendering and updates

Deliver:

- complete-line streaming parser;
- staged unresolved references;
- incremental A2UI component/data updates;
- stable Compose keys and no full-surface flashing;
- cancellation and late-token suppression.

Exit gate: the user sees a coherent partial surface before full completion, and
updates never expose an invalid or interactive partial action.

## CI And Repository Gates

Add these required checks:

```text
catalog-schema-check
catalog-signature-drift-check
a2ui-express-parser-tests
a2ui-wire-schema-validation
grammar-acceptance-and-mutation-tests
dataset-provenance-and-secret-scan
dataset-dedup-and-split-leakage-check
renderer-component-fixtures
generated-ui-accessibility-check
model-immutable-eval
android-constrained-runtime-smoke
```

Dataset generation is never a normal PR check. CI validates checked-in manifests
and a small fixture set. Full generation runs are explicit, resumable jobs whose
outputs are reviewed before promotion.

## Risks And Mitigations

| Risk | Mitigation |
| --- | --- |
| A2UI v1/Express changes | pin commit and catalog; isolate compiler adapter |
| Teacher produces repetitive templates | covering arrays, AST uniqueness quotas, entropy report |
| Teacher self-confirms bad semantics | deterministic validators and interaction tests are authoritative |
| Grammar slows decoding | bounded profile grammars, benchmark, LLGuidance comparison |
| Grammar accepts semantically invalid UI | mandatory graph/binding/action validators |
| Small model memorizes domains | held-out combinations/domains and AST-cluster split |
| UI and DEAL disagree | shared constrained `AppBlueprint` |
| Very broad catalog hurts 270M model | curriculum, catalog subsets, larger-model ceiling comparison |
| API alias changes during generation | record returned model/fingerprint, batch windows, immutable outputs |
| Generated actions become unsafe | catalog allowlist, typed events, confirmation boundary, no native calls |
| “Any request” becomes an unsafe claim | define valid fallback separately from task satisfaction |

## Definition Of Done

This plan is complete when:

- the renderer supports the pinned Basic Catalog plus Assistant Catalog v1;
- teacher generation produces a typed canonical AST, not unchecked free-form DSL;
- at least 90,000 accepted records meet coverage and uniqueness quotas;
- immutable splits show no AST/template leakage;
- the local UI model composes unseen component combinations rather than selecting
  a fixed layout set;
- A2UI Express and DEAL generation use trusted llama.cpp grammar samplers;
- every request produces a valid accepted surface or deterministic valid fallback;
- semantic, accessibility and action safety checks remain mandatory after grammar;
- device results meet the stated validity, latency, memory and stability gates;
- catalog, dataset, grammar, model and runtime compatibility are content-addressed
  in one release manifest.

## References

- [A2UI components and structure](https://a2ui.org/concepts/components/)
- [A2UI catalogs and versioning](https://a2ui.org/concepts/catalogs/)
- [A2UI v1.0 candidate protocol](https://github.com/a2ui-project/a2ui/blob/main/specification/v1_0/docs/a2ui_protocol.md)
- [A2UI v1.0 Basic Catalog](https://github.com/a2ui-project/a2ui/blob/main/specification/v1_0/catalogs/basic/catalog.json)
- [A2UI Express proposal](https://a2ui.org/specification/proposals/express/a2ui_express/)
- [llama.cpp GBNF and JSON Schema grammars](https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md)
- [llama.cpp grammar sampler API](https://github.com/ggml-org/llama.cpp/blob/master/include/llama.h)
- [llama.cpp LLGuidance notes](https://github.com/ggml-org/llama.cpp/blob/master/docs/llguidance.md)
- [DeepSeek models and pricing](https://api-docs.deepseek.com/quick_start/pricing/)
- [DeepSeek Responses API structured output](https://api-docs.deepseek.com/api/create-response/)
- [DeepSeek JSON Output guidance](https://api-docs.deepseek.com/guides/json_mode)
