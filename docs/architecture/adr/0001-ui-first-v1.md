# ADR-0001: UI-first v1 is an experimental compiler protocol

## Status

Accepted for implementation behind the Studio **UI-first experimental** selector.

## Context

The direct Studio path accepts model-authored DEAL, extracts `AppInterface`, and
derives checked Deal UI from that interface.  It remains the production baseline.
It cannot express presentation intent without inferring it from field and action
names, which makes the auto-UI compiler both lossy and domain-shaped.

UI-first changes the order for one opt-in path only:

```text
request -> checked UI draft -> one DEAL business candidate -> AppInterface
        -> typed binding/link -> checked Deal UI -> atomic publish
```

The UI draft is not an application format and is not runnable.  It contains
stable node identifiers, typed ports, child constraints, style references and
source-span provenance.  It is owned by the compiler boundary and exists only
for an in-flight revision.

## Decision

1. `ui-first-v1` is negotiated explicitly by the streaming compiler and Studio.
   Old clients do not enter this path.  Direct DEAL-only generation remains
   available and is never a hidden fallback for a rejected UI-first candidate.
2. A UI-first candidate has one frozen structural digest before the business
   model call.  The model may create DEAL declarations, state, update handlers,
   allowed helpers and assignments for declared ports.  It may not rewrite the
   UI tree, component pack, style structure or host capability allowlist.
3. The canonical persisted result remains the existing DEAL source plus checked
   Deal UI/runtime artifacts.  Drafts, decision traces, preview fixtures,
   binding requirements and rejected candidates are ephemeral diagnostics, not
   a second executable or user-editable application representation.
4. Preview compilation substitutes typed inert fixtures only.  A preview cannot
   request permissions, persist data, issue host effects or be promoted without
   a checked DEAL candidate, real `AppInterface`, resolved bindings and full
   pair validation.
5. Binding resolution occurs only after DEAL validation and `AppInterface`
   extraction.  A draft obligation is not an `AppInterface`; it is rejected if
   a real state/action symbol, nominal type, event payload, collection key,
   scope capture or capability cannot satisfy it.
6. One targeted business-code repair and one evidence-backed structural Jev
   revision are the maximum recovery budget.  A repeated candidate without a
   newly accepted draft, source or binding progress terminates as failed or
   incomplete.  The last runnable revision stays intact.
7. Component semantics are versioned by one compatibility tuple: DEAL, Deal UI,
   streaming compiler, pack version/SHA, recipe/style manifest digest and
   renderer capability version.  A mismatch blocks loading rather than silently
   lowering to a different pack.
8. Studio stores the TypeSafe/Jev key through the existing encrypted credential
   store.  It must never appear in source, canonical metadata, captured
   generation artifacts, screenshots, benchmark reports or logs.

## Consequences

- The upstream Deal UI compiler needs a real draft/link API and preview path;
  Android must not implement a second parser or bind ports by text matching.
- The streaming compiler hosts Jev session state, budgets, replay and model
  completion orchestration; Studio hosts transport, encrypted settings,
  cancellation, preview UI, persistence and the native renderer.
- The complete Vercel-derived catalog is a release gate.  A component name in a
  pack is insufficient: its props, defaults, events, renderer behavior,
  accessibility and fixture evidence must be tracked in the coverage ledger.
- Existing saved direct applications remain unchanged.  UI-first metadata is
  provenance only, so restoring old records needs no source migration.

## Non-goals

- No HTML/React runtime, JSON executable application format, CSS interpreter,
  domain-specific product templates or name-based UI classifier is introduced.
- No component catalog claim is made from this ADR.  The tracked ledger is the
  authoritative implementation denominator.
