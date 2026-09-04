# DEAL Studio Canonical Rework

## Decision

DEAL Studio has one production architecture:

```text
User request
  -> selected cloud provider DEAL generation
  -> pinned DEAL compiler and AppInterface extraction
  -> selected cloud provider Deal UI generation against that exact interface and pack
  -> pinned Deal UI compiler
  -> canonical app.deal + app.dealui + metadata
  -> Compose renderer and DEAL runtime
```

There is no production AppPlan, A2UI, compact layout plan, generated JSON AST, custom Kotlin DEAL
interpreter, local-model generation switch or parallel legacy runtime. Studio remains inside the
existing Android application module; it is not extracted into a shared library.

DeepSeek and Cerebras use one provider-neutral generation contract. Each sequential stage selects a
model independently, but all models receive the same compiler tools and must produce the same
canonical artifacts. Provider adapters own only endpoint authentication, request envelopes, SSE tool
assembly and usage metadata. API keys are separate Android-Keystore credentials. A transport retry
does not mutate compiler state or consume semantic-repair budget.

Core DEAL remains a mutable, TypeScript-shaped application language. Deal UI is intentionally a
separate compact declarative language. It uses `ForEach` for dynamic collections and does not gain
indexing, array literals, assignments or arbitrary calls. UI-handler state/action immutability is a
static Deal UI compiler rule over borrowed values; it does not alter core DEAL semantics or depend on
runtime readonly support.

## Preserved Checkpoints

| Repository | Reference | Commit |
|---|---|---|
| DEAL Studio before canonical rework | `bc34988` | `bc34988 checkpoint: preserve Studio before canonical rework` |
| DEAL Studio before latency protocol experiment | `pre-latency-protocol-experiment-2026-09-04` | `7a21529ea04c34ff028c38b2d2c456829df36d6c` |
| Deal UI portable checkpoint | `egavrin/deal-ui-portable-checkpoint` | `477d0c0` |
| Deal UI compiler rework | `egavrin/deal-ui-compiler-rework` | `be16c71654bbd9056cff1a2cbec8a48a523ec51c` |
| Core DEAL compiler | pinned input | `fc90c049b088fdf38217120aaf4e799af01ba5c3` |

Application work is performed on `egavrin/deal-studio-canonical-rework`.

## Compiler Boundary

The upstream Deal UI compiler owns language correctness. It now:

- rejects direct, indexed, nested and aliased mutation through borrowed handler state/action values;
- infers helper mutation/escape summaries and treats unknown callees conservatively;
- permits reads, comparisons, iteration, fresh-local mutation and structural sharing of unchanged
  borrowed references;
- validates nominal typed children through direct calls, `When` and `ForEach`;
- emits stable diagnostics and checked metadata for root state, reachable input actions, effect
  completions, used components/capabilities and component-pack version/digest;
- has a preview mode that permits temporarily unreachable updates while final compilation remains
  strict.

The Android host does not reproduce these semantics with regex validators. Progressive preview and
final completeness use compiler-produced metadata. Compiler graph updates use candidate validation
before committed source replaces the prior accepted graph.

## Component Pack v12

The authoritative pack is
`tooling/deal-ui-pack/deal-studio-v12.dealui-pack`. Gradle generates the Kotlin embedding and digest
from that tracked file. The same bytes are used for compiler checks, model prompts, capability
metadata and renderer conformance.

v12 uses nominal children for `NavigationBar`, `Tabs`, `Choice` and `Menu`. Whole-array props remain
only for genuinely batched visualization data such as chart series. v11 remains embedded only for
restoring records that explicitly name it; new generation always uses v12.

No pack component, prompt branch, validator or fallback is specific to medication, exam, health,
todo, weather, tic-tac-toe, Arkanoid, chess or any other acceptance scenario.

## Reproducible Toolchain

`tooling/deal-android-bridge/toolchain.lock` pins both compiler revisions, the exact pack version and
digest, Java, Android build tools, D8, minimum Android API and resulting DEX digest. The build script
requires explicit repository paths, rejects dirty or revision-mismatched compiler trees, verifies the
tracked pack, uses deterministic archive timestamps and rejects a mismatched DEX.

The current embedded DEX SHA-256 is
`e3aae302b3ef7dcdc19fca719abec45952fab60514fb84db7d2a85c87fdaa627`.

## Canonical Application State

The product state machine is exactly:

```text
Empty
Generating(phase, previousRunnable, acceptedPreview)
Runnable(bundle, runtimeSession)
Refining(previousRunnable)
Failed(previousRunnable?, userMessage, technicalTrace)
```

Generation failures retain the previous runnable application. Progressive previews contain only
compiler-accepted sections. One canonical runtime session powers Studio preview and fullscreen.
One natural-language refinement request is applied to checked DEAL first, then Deal UI is checked
against the newly extracted AppInterface; the pair is swapped atomically only after complete
validation. A failed edit preserves the prior runnable revision.

Compiler repair and product refinement use different revision protocols. Compiler repair replaces
one rejected typed function body or one rejected named UI surface while accepted siblings remain
immutable. It must not use a catch-all action dispatcher to reduce nominal type count: navigation,
collection mutation, confirmation, selection and host events are separate cohesive action families.
Body size and repair-token locality are optimization targets; minimum action count is not.

A natural-language refinement first attempts a compatible revision that replaces only responsible
existing DEAL bodies and Deal UI surfaces. If the request requires new fields, types, actions,
capabilities or routes, Studio creates a structural canonical graph revision, recompiles DEAL,
extracts a new AppInterface and regenerates only the Deal UI surfaces invalidated by that interface.
The candidate pair is committed atomically. The product never asks the user to choose logic versus UI.

## Persistence v2

Each saved record contains exactly:

```text
canonical-v2/<id>/app.deal
canonical-v2/<id>/app.dealui
canonical-v2/<id>/metadata.json
```

Metadata contains request/title, source digests, compiler/toolchain/pack provenance, model and prompt
identity, token counts, phase timings, graph rounds, repair counts and revision timestamps. It never
contains AppInterface, checked IR, graph state or runtime objects.

Restore verifies source and provenance, recompiles DEAL, re-extracts AppInterface, compiles Deal UI
with the recorded pack and creates a fresh runtime. Invalid or unavailable records are quarantined.
Legacy `apps-v1.json` code is never executed; only its natural-language request is exposed through a
Rebuild action, and the old record is removed only after the rebuilt app is reviewed and saved.

## Validation State

Automated JVM tests cover the canonical architecture guard, pack/renderer bidirectional component
coverage, typed collection children, compiler-owned coverage metadata, source-only persistence and
request-only legacy rebuild. Upstream compiler suites cover borrowed-value mutation and typed-child
checking.

The following remain release gates rather than implementation claims:

- connected device matrix for compact, regular and unfolded widths in light/dark/dynamic type;
- deterministic medication, exam, health, todo, weather, tic-tac-toe, Arkanoid and chess runs;
- held-out request and 50-100-run Surprise soak with retained failure artifacts;
- latency gates of median <=20 seconds and p95 <=45 seconds on a recorded network/model profile;
- final accessibility, clipping, touch, save/restore, refinement, shortcut and widget acceptance.

The reworked build remains internal until those gates are measured and pass.

In particular, none of these gates is closed by the current implementation alone. The iterative
development gate additionally requires a held-out complex application to survive several local and
structural natural-language revisions, one failed revision with rollback, save/restore and recorded
per-step changed units, tokens, diagnostics and latency. Generated visual quality must meet the same
polished native screenshot rubric as the mandatory matrix; successful compilation is insufficient.
