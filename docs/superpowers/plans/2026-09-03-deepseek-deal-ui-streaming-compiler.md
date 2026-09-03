# DeepSeek Compiler-Guided Deal / Deal UI Generation

**Status:** Graph-first vertical slice accepted on Oppo; performance and scenario matrix pending
**Date:** 2026-09-03

## Decision

Generate arbitrary small applications through a compiler-owned checked program graph. DeepSeek
uses coarse semantic compiler operations; it does not emit one tool call per AST constructor and it
does not serialize a complete program as JSON HIR. Canonical `.deal` is the deterministic projection
of the accepted graph. Canonical `.dealui` is generated only after DEAL is complete and checked.

This is a compiler-as-agent-API architecture similar to graph-first ZeroLang authoring:

```text
English request
      |
      v
create_deal_program
  nominal types + external actions + helper signatures + capabilities
      |
      v
compiler-owned checked graph with stable typed function holes
      |
      v
apply_deal_graph_patch (one or more hole bodies, guarded by graph hash)
      |
      +-- invalid body --> exact diagnostic; accepted sibling fills remain
      |
      v
production-checked canonical app.deal
      |
      v
extract exact AppInterfaceV1
      |
      v
DeepSeek canonical app.dealui generation
      |
      v
Deal UI checker + portable IR + reusable Compose renderer
      |
      v
cross-artifact and functional validation
```

The previous `p_program -> select_deal_choices -> s_*/e_*` protocol is rejected as a production
design. It required a remote model decision for nearly every AST node, repeatedly sent growing
context and made compiler feedback arrive only after the rest of a dependent batch was generated.
The JSON semantic-HIR experiment is also rejected because it expands compact DEAL into a verbose
transport representation without eliminating the need for compiler and functional validation.

## Graph Contract

`create_deal_program` is one checked declaration transaction containing:

- root state and nominal state/value types;
- external input action types;
- pure helper function signatures;
- reusable capability requirements.

These declarations are program structure, not a planner result, UI layout, runtime manifest or
application-family blueprint. The compiler creates stable holes for `initialState`, every helper and
every `@ui-update` handler. Unresolved holes project as type-correct compiler stubs so the partial
graph remains checkable; a graph containing stubs is never runnable or installable.

`apply_deal_graph_patch` contains an exact `base_hash` and one or more `{hole_id, body}` fills. Bodies
are compact DEAL statements without signatures or outer braces. Each body is projected into the
complete module and passed through the pinned production DEAL parser, type checker and exported
interface identity check before commit. Invalid fills stay unresolved; independently valid fills in
the same batch remain committed. Accepted fills cannot be rewritten during the generation run.

Graph operations and holes are generic. No compiler branch, helper, profile or prompt route may name
or recognize tic-tac-toe, trackers, medication, exam preparation, Pong, Arkanoid or another concrete
application. Concrete requests belong only in training, evaluation and acceptance data.

## Artifact Boundary

The installed and saved bundle is exactly:

```text
app.deal       authoritative state, rules, updates and effects
app.dealui     pure typed view and action bindings (shown as DUI in product UI)
metadata       compiler/pack/model digests and timings; never executable
```

The graph and `AppInterfaceV1` are ephemeral compiler state. They are not additional authored files.
`AppInterfaceV1` is extracted from the verified DEAL source and passed read-only to Deal UI
generation. UI and behavior are not guessed concurrently from an inferred planner record.

The DEAL root state is the presentation-ready boundary for the following Deal UI pass. Domain data
may remain structured, but every value that pure Deal UI cannot derive must also be exposed in an
appropriate checked field: `int[]` chart series, `string[]` labels/icons, formatted mixed text,
selected records, summaries and statuses. Update handlers keep those fields consistent with domain
state. Helper calls are implementation details and are never the only route to visible data.

## Deal UI Section Protocol

DeepSeek appends two to six cohesive top-level sections to a compiler-owned Root. Each cumulative
projection is checked before it becomes visible. Accepted section ids are immutable. If a section is
rejected, its id becomes the only schema-allowed target for the next compiler call; the model cannot
skip it or consume the retry budget by repeating an already accepted header. Partial projection uses
an ephemeral DEAL view containing only currently reachable UI update actions. Final projection is
always checked against the complete authoritative DEAL source.

The versioned component pack v7 supplies semantic controls, typed text and time-of-day input, integer metrics, charts,
overlays, images, icons, Canvas
and responsive layout. `Grid(columns: N, minimumCellWidth: D)` treats `N` as a maximum and adapts to
compact and unfolded widths. Studio thumbnails use a stable logical viewport and scale the real
renderer; utility fullscreen roots receive host scrolling only when the generated app does not own
Scroll, PointerSurface or Canvas.

## Static And Functional Correctness

Compiler acceptance guarantees parseability, types, scope, declared effects/capabilities and stable
graph identity. It does not prove that generated behavior satisfies the natural-language request.
Functional fidelity is a separate gate consisting of deterministic scenario tests, invariants and
runtime smoke actions. Repair for a functional failure patches only the responsible accepted
semantic unit in a new revision; it is not part of the transport-level graph construction protocol.

## Performance Budget

A normal cloud generation should use:

1. one declaration call;
2. one batched DEAL body-fill call;
3. one Deal UI call;
4. zero repair calls.

Eight DEAL graph rounds are a hard failure ceiling, not an expected path. Metrics must record graph
rounds, accepted and rejected fills, typed-hole count, input/cached/output tokens, first compiler
patch latency, local compiler time, Deal UI TTFT and wall time. A run that repeatedly accepts one
hole per request is a protocol regression even if it eventually succeeds.

## Current Implementation

- `CanonicalDealProgramGraphCompiler` owns declarations, graph hashes, typed holes and checked fills.
- `CanonicalGeneratedAppCloudCompiler` generates DEAL first and Deal UI second.
- Every graph fill is checked with the embedded production toolchain before commit.
- The final DEAL hand-off repeats the production check and verifies that the exported interface is
  identical to the accepted declaration graph.
- Deal UI uses checked progressive sections with mandatory section-local repair.
- The former constructor-per-node and JSON-HIR implementations have been removed from production.

## Live Result

On 2026-09-03 a held-out water tracker generated, compiled, saved, restored and ran on an Oppo
CPH2765. It completed with no whole-source repair in about 30.4 seconds: DEAL 14.1 seconds, Deal UI
16.1 seconds, DEAL first patch 1.0 seconds, Deal UI first call 1.36 seconds, three DEAL graph rounds
and three Deal UI rounds. Buttons updated the checked DEAL state; the progress ring, chart and history
rerendered; the utility scrolled in fullscreen; the saved library thumbnail used the real renderer.
This proves the vertical slice but misses the product latency target and does not close visual review.

## Mandatory Scenario Matrix

The release-blocking scenarios are medication, exam, health, todo, weather, tic-tac-toe, Arkanoid and chess. They use
one generic compiler, Deal UI pack and runtime. Scenario names, rules and expected controls live only in acceptance
requests and assertions; they must never select a production compiler branch, blueprint or template fallback.

This matrix is a fixed regression floor, not a product taxonomy and not evidence of arbitrary-app generalization.
Static generation instructions are guarded against naming these scenarios. Compiler validation must operate on syntax,
types, effects, capabilities, resource bounds and cross-artifact bindings, never on domain field-name conventions.
Changes motivated by one failure are admitted only as domain-neutral language/UI/runtime primitives that cover at least
three unrelated application classes. A rotating held-out composition set remains required for every release candidate.

## Remaining Acceptance

- Run all eight mandatory scenarios on a physical device. Medication, tic-tac-toe and Arkanoid already have dedicated
  device tests; exam, health, todo, weather and chess are now executable device tests but still need live evidence.
- Confirm typical DEAL generation completes in two calls and never exceeds the eight-call ceiling.
- Record TTFT, graph calls, accepted/rejected holes, token counts, compiler latency and wall time.
- Reduce the current approximately 30 second sequential wall time without weakening compiler gates.
- Add screenshot gates for compact phone, ordinary phone and unfolded widths; reject empty charts,
  placeholder icons, clipped controls and technically valid but visually weak composition.
- Run held-out tracker, multi-screen utility and real-time game requests to detect task-specific
  assumptions.
- Add functional invariants per request family without adding application-specific compiler logic.

## References

- ZeroLang graph-first authoring: <https://github.com/vercel-labs/zerolang>
- ZeroLang compile path: <https://zerolang.ai/concepts/compile-path>
- Statically Contextualizing Large Language Models with Typed Holes:
  <https://arxiv.org/abs/2409.00921>
- Generative Compilation: <https://arxiv.org/abs/2607.13921>
- Type-Constrained Code Generation with Language Models: <https://doi.org/10.1145/3729274>
