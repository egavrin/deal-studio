# DEAL and Deal UI evolution for generated mini-applications

**Status:** design recommendations
**Date:** 2026-09-04
**Scope:** canonical `.deal` behavior, `.dealui` presentation, the embedded Studio runtime,
model-facing compiler protocol and the reusable host/component ABI

## Executive summary

The current system has already proved the important architectural boundary: a model can generate a
canonical `.deal` program and a pure `.dealui` view, the production compilers can reject invalid
regions, and the checked pair can run through a native Compose renderer. The main remaining problem
is not the absence of a compiler. It is the mismatch between the size of ordinary application logic
and the very small language/UI surface admitted by the generated-app profile.

A state update that changes one field currently has to reproduce every other state field. Updating an
array normally requires a hand-written copy loop. Deal UI cannot format values, call a pure selector,
inspect collection size or derive a presentation model, so every update must also keep duplicated
display strings, counters and chart arrays synchronized. This increases output tokens, generation
time and the number of ways a model can produce a semantically inconsistent result.

The recommended direction is:

1. Keep DEAL source as a real syntactic subset of TypeScript. Every accepted DEAL construct should
   also be valid TypeScript surface syntax after supplying ambient declarations for DEAL primitive
   names. Restricted or different runtime semantics are acceptable; proprietary grammar is not.
2. Do **not** add a new record-update form such as `state with { ... }`. Add target-typed object spread
   and use the familiar TypeScript form `return { ...state, field: value };`.
3. Move capabilities that already exist in DEAL v1.2 into the embedded generated-app profile before
   inventing parallel language features: inferred locals, nullable/optional data, function values,
   allowlisted imports, async effects and standard control flow already have compiler semantics.
4. Add a deliberately small high-value language layer that canonical DEAL does not yet have:
   object/array spread, `const`, ternary expressions, arrow syntax for pure callbacks, selected array
   transformations, string literal types and exhaustive `switch`.
5. Stop storing every presentation derivation in root state. Add compiler-checked pure selectors and
   generic formatting components/functions while preserving a pure Deal UI.
6. Bring the existing Deal UI effect model into Studio. OS data must enter through typed,
   permission-aware host capability modules and typed completion actions, never through arbitrary
   Android objects or application-specific Kotlin.
7. Expand the component pack by reusable concepts: richer input, lists/tables/timelines, calendars,
   charts, media, adaptive navigation and a batched graphics scene. Do not add medication, exam,
   weather or game-specific components.
8. Keep model output as compact source code. The compiler may use AST/HIR/graphs internally, but the
   model should not serialize a verbose JSON AST. Coarse compiler tool calls and targeted repair
   remain the right protocol.
9. Treat a TSX-shaped Deal UI surface as an experiment, not an immediate rewrite. Current `.dealui`
   is compact, but it is not a TypeScript syntactic subset. A TSX frontend should replace it only if an
   A/B evaluation shows materially better first-pass validity or lower total token cost.

The first implementation tranche should be object/array spread, `const` plus local type inference,
standard formatting intrinsics, pure selectors and several missing generic UI components. This is
the smallest set likely to reduce both source size and repair rate across utilities, trackers,
OS-data widgets and games.

## Product target

The target is not arbitrary native application generation. It is a safe runtime for small,
interactive, generated applications that can be created, inspected, saved and reopened inside DEAL
Studio:

- forms and calculators;
- task lists, trackers and lightweight personal workflows;
- dashboards over application or operating-system data;
- schedule, calendar and reminder widgets;
- image- and search-backed browsing surfaces;
- small multi-screen utilities;
- board games and bounded 2D games;
- interactive widgets that update from time, lifecycle, sensors or typed host data.

The generated artifact remains portable source:

```text
app.deal       authoritative state, behavior, effects and capability requests
app.dealui     pure declarative presentation and typed actions
metadata       prompt, versions, timings and provenance; never executable authority
```

The host owns permissions, lifecycle, rendering, persistence infrastructure and OS adapters. A
generated app cannot load arbitrary libraries, call arbitrary platform APIs, emit an APK, use
reflection or execute JavaScript/Kotlin/native code.

## Non-negotiable language invariant

### Definition

"TypeScript syntactic subset" should be made testable rather than informal:

1. DEAL may reserve fewer constructs and assign stricter types or different bounded semantics.
2. DEAL may use names such as `int`, `table` and `View`; these can be represented by ambient
   TypeScript declarations.
3. DEAL may recognize compiler directives in comments such as `// @ui-update`; the file remains
   valid TypeScript because TypeScript treats them as comments.
4. DEAL must not add keywords, delimiters, argument syntax or expression forms that a TypeScript
   parser rejects.
5. A CI syntax oracle should parse every accepted DEAL fixture with the pinned TypeScript parser
   after prepending ambient type declarations. This is a syntax check, not a claim of JavaScript or
   TypeScript runtime compatibility.

DEAL semantics can still be safer and smaller than JavaScript semantics:

- nominal data classes without constructors or methods;
- invariant types and no implicit conversions;
- bounded arrays and execution budgets;
- deterministic numeric and string operations;
- closed imports and host effects;
- no prototype chain, dynamic property lookup or ambient global environment.

### Explicitly rejected syntax

This proposal rejects a proprietary record-update operator:

```ts
// Do not add this. It is not TypeScript syntax.
return state with { draftName: action.name };
```

The correct surface form is object spread:

```ts
return { ...state, draftName: action.name };
```

The compiler can give this expression narrower semantics than JavaScript: `state` must have exactly
the target nominal class type, only declared fields may follow the spread, later fields override
earlier fields, and the result is validated as a complete value of the return type.

## Current baseline

There are three different surfaces that should not be conflated.

### Canonical DEAL v1.2

The sibling DEAL compiler already supports substantially more than Studio currently exposes:

- `let` with local type inference;
- C-style `for`, `for...of`, `while`, `break` and `continue`;
- nominal classes, optional fields and `T | null`;
- nested classes and arrays of classes;
- object and array literals;
- template strings;
- first-class function values and function expressions;
- `async`/`await`, `try`/`catch` and typed module imports;
- arrays, tables, JSON support and a closed standard library.

It intentionally does not currently provide object spread, array spread, `const`, general union
types, `switch`, arrow-function expressions or JavaScript array methods.

### Studio embedded canonical runtime

The Android bridge compiles with the canonical parser/type checker but executes a narrower bounded
subset. It already handles classes, object literals, field access and assignment, arrays, append at
`array.length`, `for`, `for...of`, `while`, templates, `has`, `delete`, direct pure function calls and
the standard arithmetic/boolean operators. It rejects async functions, imports, function
expressions, arbitrary methods and unsupported statements/expressions.

The model prompt narrows the surface further: it forbids nullable values, arrows, ternaries,
`switch`, collection methods, async/effects and ordinary `Math`/`String` APIs. It also requires a
complete presentation-ready root state.

### Deal UI in Studio

The v7 component pack currently has 51 typed components. It covers adaptive layout, text, icons,
basic inputs, cards, progress, two charts, navigation, overlays, image, clocks and a basic canvas.
Deal UI supports paths, literals, arithmetic/comparison/boolean expressions, `When`, keyed `ForEach`
and nominal action literals.

It does not support collection transformations, indexing, collection size, function calls,
formatting, ternaries, inline arrays/objects or pure selectors. This forces DEAL state to contain
denormalized row models and preformatted values.

Current `.dealui` syntax is TypeScript-inspired but **not** a TypeScript syntactic subset:

```text
export view ...
ui.Card(tone: "surface") { ... }
When(...) { ... } Else { ... }
ForEach(..., item: Type, key: item.id) { ... }
action app.ActionType { ... }
```

If the TypeScript-subset invariant applies to UI as well as behavior, this requires a future syntax
decision. It should not be hidden by calling the current grammar TypeScript.

## Evidence from real applications

The following numbers come from checked source already present in the local DEAL/Deal UI examples
and the live medication artifact. Counts of pass-through fields are lexical measurements of
`field: state.field`; they are useful indicators, not semantic proofs.

| Application | DEAL lines | DEAL bytes | Pass-through field copies | Object returns | Manual loops |
|---|---:|---:|---:|---:|---:|
| Checkout | 103 | 4,040 | 18 | 5 | 1 |
| Search mail | 165 | 8,639 | 33 | 17 | 1 |
| Kanban | 183 | 10,897 | 7 | 10 | 7 |
| Generated medication tracker | 488 | 15,786 | 76 | several typed result objects | 10 |

| Application | Deal UI lines | Deal UI bytes | Component calls | `When` | `ForEach` |
|---|---:|---:|---:|---:|---:|
| Checkout | 35 | 1,619 | 18 | 4 | 0 |
| Search mail | 37 | 1,502 | 20 | 3 | 1 |
| Kanban | 30 | 2,211 | 19 | 1 | 1 |
| Generated medication tracker | 70 | 3,793 | 38 | 0 | 1 |

The medication tracker is the clearest pressure test. Four input actions each change one field but
repeat the other thirteen fields. Collection updates manually copy arrays. Every action must keep
`nextDoseText`, `nextDoseCountdownText`, `todayScheduleText`, `sevenDayOverviewText` and status fields
consistent with the domain arrays.

Its recorded generation trace was:

| Metric | Value |
|---|---:|
| DEAL phase | 52.503 s |
| Deal UI phase | 11.353 s |
| End-to-end | 64.175 s |
| DEAL input / cached / output tokens | 12,914 / 3,968 / 11,430 |
| DEAL rounds | 4 |
| Accepted / rejected DEAL candidates | 16 / 21 |

Later coarse compiler transactions reduced this substantially, including a one-DEAL-call,
one-UI-call medication run at 38.885 seconds. The source pressure remains: protocol improvements can
avoid resending work, but they cannot eliminate tokens that the language requires the model to
write.

The paired latency experiment found that compiler-guided generation emitted 35.5% fewer output
tokens than direct generation at the median, while its total token advantage before coarse batching
was only 2.8%. This supports keeping compiler-guided repair, but it also shows that reducing required
source is the next larger lever.

## Application pressure-test matrix

The recommendations below were evaluated against application classes rather than named templates.
The concrete examples are intentionally diverse so that no proposed primitive exists only for one
demo.

| Application class | Behavior pressure | UI pressure | Host pressure |
|---|---|---|---|
| Medication/schedule tracker | recurring records, status windows, time derivation, immutable list updates | timeline, day/week schedule, forms, badges, countdown | clock, notifications, private storage |
| Exam planner | imported question list, planning, progress, overdue policy | multi-screen plan, question list, progress, camera result state | camera, OCR, notifications, optional focus control |
| Health/workout tracker | recurring rules, aggregation, goals | charts, metrics, schedule and completion controls | health data, sensors, notifications, storage |
| Todo/Kanban | add/edit/delete/reorder/filter, optimistic state | virtual list, drag/reorder, forms, overlays | storage, optional synchronization |
| Device-status dashboard | snapshot/stream updates and thresholds | gauges, charts, compact cards, adaptive grid | battery, storage, network, thermal and lifecycle data |
| Search/mail browser | loading/error/cancel, result paging and cached queries | search field, skeleton, result list, source/media preview | allowlisted network/search effect, storage |
| Expense or budget utility | decimal/money rules, grouping and totals | forms, table, category chart, filters | storage, optional file import |
| Tic-tac-toe/chess | board state, legal moves, selected cell and reset | responsive board, accessible pieces and status | pointer/keyboard only |
| Arkanoid/Pong | frame update, collision, score/lives, reset | batched scene, HUD, overlays and scaling | frame clock, pointer/keyboard, audio events |
| Photo triage widget | collection actions, selection and metadata | image grid, fullscreen preview, actions | photo picker/library capability |

The recurring cross-domain needs are clear: compact immutable updates, array transformations,
explicit state machines, derived views, typed effects, formatted values, richer collections and a
more capable graphics/media layer.

## Language recommendations

### P0: target-typed object spread

This should be the first language change.

Current generated update:

```ts
export function onUpdateDraftName(
  state: MedicationAppState,
  action: UpdateDraftNameAction
): MedicationAppState {
  return {
    draftName: action.name,
    draftDosage: state.draftDosage,
    draftTimeMinutes: state.draftTimeMinutes,
    draftDurationDays: state.draftDurationDays,
    currentTimeMinutes: state.currentTimeMinutes,
    currentDayIndex: state.currentDayIndex,
    medications: state.medications,
    weekDoses: state.weekDoses,
    nextDoseText: state.nextDoseText,
    nextDoseCountdownText: state.nextDoseCountdownText,
    todayScheduleText: state.todayScheduleText,
    sevenDayOverviewText: state.sevenDayOverviewText,
    notificationAvailable: state.notificationAvailable,
    notificationStatusText: state.notificationStatusText
  };
}
```

Target:

```ts
export function onUpdateDraftName(
  state: MedicationAppState,
  action: UpdateDraftNameAction
): MedicationAppState {
  return { ...state, draftName: action.name };
}
```

Recommended initial semantics:

- spread is accepted only in a context with one exact nominal class target;
- the spread operand must have that exact class type;
- only one spread operand is allowed initially;
- declared fields after the spread override the copied fields;
- unknown fields and duplicate explicit fields are errors;
- the operation is shallow; nested values retain normal DEAL value/reference rules;
- tables and dynamic objects are excluded from the first version;
- runtime budgets charge proportionally to copied field count.

This feature reduces code and prevents stale pass-through fields when a class evolves. It also makes
natural-language refinement safer: adding a field to `AppState` does not require patching every
unrelated update function.

### P0: array spread and immutable collection construction

Appending currently requires a mutable local and index assignment:

```ts
let result: Item[] = [];
for (let item: Item of state.items) {
  result[result.length] = item;
}
result[result.length] = newItem;
```

Add standard array spread:

```ts
const result: Item[] = [...state.items, newItem];
```

Recommended restrictions:

- spread operands must have the exact target element type;
- the result must satisfy the configured collection bound;
- no sparse-array or iterator protocol exists;
- spread copies array membership but follows DEAL value semantics for elements;
- nested or multiple spreads may be allowed once the simple form is stable.

Object and array spread should ship together because state updates often need both.

### P0: `const` and local type inference in the generated profile

Canonical DEAL already infers unambiguous `let` locals, but Studio instructs the model to annotate
every local and DEAL has no `const` declaration. Add `const` with ordinary TypeScript syntax and
immutable-binding semantics:

```ts
const next = state.count + 1;
const label = platformIntText(next);
```

The contained array/object may follow DEAL's existing value rules; `const` initially guarantees only
that the binding cannot be reassigned. A later `readonly` type feature is a separate decision.

Allow inference for literals, direct function calls, arithmetic and target-typed object/array
literals. Keep explicit annotations mandatory where an empty array, `null` or an ambiguous numeric
literal lacks enough context.

Benefits:

- fewer output tokens;
- fewer mismatched annotations during repair;
- clearer distinction between loop counters/mutable accumulators and stable intermediate values;
- syntax with very strong model priors.

### P0: standard-looking deterministic intrinsics

Names such as `platformIntText` and `platformMinInt` are implementation-shaped and consume prompt
space. Prefer a small allowlisted standard surface that is still valid TypeScript syntax:

```ts
const low = Math.min(left, right);
const safe = Math.max(0, Math.min(value, maximum));
const label = String(value);
```

These are compiler intrinsics, not access to JavaScript globals. Their types and behavior are fixed by
DEAL. Add a pure `std/format` module for operations that do not have an honest TypeScript built-in:

```ts
import * as format from "std/format";

const time = format.timeOfDay(minutes);
const duration = format.durationMinutes(remaining);
const percent = format.percent(value, maximum);
```

The formatting module must be locale-explicit and deterministic. Do not silently inherit device
locale inside pure reducers; pass a supported locale identifier or select a host-formatted UI
component when localization belongs to presentation.

### P1: ternary expressions

Add `condition ? whenTrue : whenFalse` with strict branch type equality. It is useful for compact
state updates and pure callbacks:

```ts
const status = overdue ? "missed" : "scheduled";
```

Avoid JavaScript truthiness. The condition remains exactly `boolean`.

### P1: pure arrow functions and selected array methods

Manual loops dominate list updates and derivations. Canonical DEAL already has first-class functions
and closures, so arrow syntax can lower to existing function-expression semantics rather than
creating a second callback model.

Target example:

```ts
const doses = state.weekDoses.map(
  (dose: WeekDose): WeekDose =>
    dose.id === action.doseId
      ? { ...dose, statusText: "taken", takenButtonEnabled: false }
      : dose
);
```

Initial allowlist:

| Method | Result | Reason |
|---|---|---|
| `map` | `U[]` | immutable item updates and projections |
| `filter` | `T[]` | visible subsets and deletion |
| `find` | `T | null` | next item, selected item and lookups |
| `some` / `every` | `boolean` | validation and aggregate status |
| `slice` | `T[]` | paging and bounded windows |
| `concat` | `T[]` | familiar immutable combination |

Defer `reduce`, mutating `sort`, `splice`, `push`, arbitrary method dispatch and iterator protocols.
`reduce` is compact but is disproportionately difficult to type and generate correctly. A future
immutable `toSorted` can be added with explicit deterministic comparator rules.

Callbacks must be synchronous and pure in the generated-app profile. They may capture immutable
locals and action fields, but cannot dispatch, perform effects or mutate captured state. Collection
size and callback execution count remain budgeted.

### P1: literal types, discriminated unions and exhaustive `switch`

Generated applications repeatedly model statuses as unrestricted strings:

```ts
statusText: string = "scheduled";
```

This accepts misspellings and impossible states. Add string literal types and finite unions:

```ts
type DoseStatus = "scheduled" | "taken" | "delayed" | "missed";

class Dose {
  status: DoseStatus = "scheduled";
}
```

Then add `switch` with compile-time exhaustiveness for literal unions:

```ts
function statusTone(status: DoseStatus): string {
  switch (status) {
    case "scheduled": return "neutral";
    case "taken": return "positive";
    case "delayed": return "warning";
    case "missed": return "danger";
  }
}
```

This is a larger type-system change than spread, but it improves reliability for loading states,
routes, permissions, game phases and workflow statuses. General structural unions can remain out of
scope initially; literal unions deliver most of the value with a bounded implementation.

### P1: nullable and optional values in the Studio interface

Canonical DEAL already has `T | null`, optional fields and narrowing. Studio currently bans them,
forcing sentinel IDs, empty strings and booleans that can disagree.

Allow nullable fields in `AppInterfaceV2` and retain explicit narrowing:

```ts
nextDose: Dose | null = null;

if (state.nextDose !== null) {
  // narrowed to Dose
}
```

Add `??` only after nullable values are admitted:

```ts
const title = state.selectedTitle ?? "Nothing selected";
```

Optional chaining should come later. DEAL has no `undefined`, so its exact result type and difference
from TypeScript need careful specification. Do not add it only because models frequently emit it.

### P1: compiler-checked pure selectors

The root state should contain authoritative application data, not every possible formatted or
filtered view of that data. Add a selector boundary using ordinary functions plus a comment
directive:

```ts
// @ui-selector
export function todayDoses(state: MedicationAppState): WeekDose[] {
  return state.weekDoses.filter(
    (dose: WeekDose): boolean => dose.dayIndex === state.currentDayIndex
  );
}
```

Deal UI can bind to a selector result as a read-only path or call a selector through compiler-owned
binding syntax. The runtime may memoize a selector by committed state revision. Constraints:

- selector is pure, synchronous and bounded;
- selector parameters and result are fully typed;
- selector cannot access host capabilities;
- selector errors reject the candidate state/render rather than mutating state;
- selectors are represented in the extracted AppInterface;
- the compiler detects selector dependency cycles.

Selectors remove duplicated `visibleItems`, `count`, `todaySchedule`, `chartSeries` and similar
fields without moving business behavior into the UI language.

### P2: existing Deal UI effects in the embedded profile

The separate Deal UI framework already defines the correct model:

- `@ui-update` commits synchronous state;
- `@ui-effect` starts asynchronous or long-running work after commit;
- `@ui-effect-policy` controls start, cancellation, replacement and overlap;
- `@ui-effect-failure` maps errors to typed completion actions.

Studio should use this model instead of capability comments that do not have executable host
bindings. Example:

```ts
import * as battery from "host/battery";

export class RefreshBatteryAction {}
export class BatteryLoadedAction {
  level: int = 0;
  charging: boolean = false;
}

// @ui-update
export function beginRefresh(
  state: DeviceState,
  action: RefreshBatteryAction
): DeviceState {
  return { ...state, loading: true, error: null };
}

// @ui-effect
export async function loadBattery(
  state: DeviceState,
  action: RefreshBatteryAction
): BatteryLoadedAction {
  const snapshot = await battery.snapshot();
  return { level: snapshot.level, charging: snapshot.charging };
}

// @ui-update
export function finishRefresh(
  state: DeviceState,
  action: BatteryLoadedAction
): DeviceState {
  return {
    ...state,
    loading: false,
    batteryLevel: action.level,
    charging: action.charging
  };
}
```

The syntax is ordinary TypeScript-shaped DEAL. The host module is closed, versioned and
permission-aware. No Android object crosses the boundary.

### P2: bounded domain primitives without domain APIs

Some concepts recur too often to rebuild from integers and strings:

- `Instant`, `LocalDate`, `LocalTime`, `Duration` or a minimal equivalent;
- decimal/fixed-point values suitable for money and measurements;
- stable `Id` generation owned by state/runtime;
- result/loading/error carrier types;
- immutable paging/window helpers.

These should be general library types with explicit serialization and arithmetic. Do not create
`DoseTime`, `ExamDate`, `WorkoutPeriod` or other scenario-specific primitives.

## Deal UI recommendations

### Keep presentation declarative

Deal UI should remain free of mutation, arbitrary statements, network calls, renderer object access
and application logic. `When`, keyed repetition, component composition and typed action dispatch are
the right conceptual boundary.

However, "pure" should not mean "incapable of presenting typed values." The current restriction
pushes excessive work into state. Deal UI needs either selector bindings or generic formatting
components so presentation does not require duplicated strings.

### Add generic formatting components

High-value additions:

```text
DateText       epoch/local date -> localized date text
TimeText       minute/instant -> localized time text
DurationText   milliseconds/minutes -> countdown or duration text
DecimalText    fixed-point value + scale -> text
PercentText    value + maximum -> percent text
MoneyText      minor units + ISO currency -> localized money text
RelativeTime   instant -> "in 12 min" / "3 min ago"
```

Formatting components should own locale-sensitive presentation. DEAL should still own thresholds,
status transitions and which value is shown.

### Expand the semantic component pack

The pack should evolve by reusable visual behavior, not by application family.

| Priority | Missing generic components | Applications unlocked |
|---|---|---|
| P0 | `NumberField`, multiline text, date field, dropdown/menu, segmented control, radio group, form validation/error | forms, trackers, settings, calculators |
| P0 | virtual `List`, `DataTable`, `Timeline`, `Schedule`, `CalendarGrid` | tasks, medication, exam, logs, planning |
| P0 | `LineChart`, `AreaChart`, `Gauge`, `DonutChart`, `Heatmap`, chart legend/axis metadata | health, device data, finance, progress |
| P0 | skeleton/loading block, inline error, confirmation dialog | network and host-backed utilities |
| P1 | `ImageGallery`, local image handle, image preview, media placeholder | photo, search, catalog and OCR workflows |
| P1 | adaptive split pane, flow/wrap layout, pager and master-detail navigation | foldables, tablets and multi-screen utilities |
| P1 | menu/popover and richer modal/sheet controls | editing, contextual actions and secondary flows |
| P1 | drag/reorder surface and semantic drop target | kanban, lists and board editors |
| P2 | sprite, tile map, path, transform, clip, camera, animation and particle layers | richer 2D games and spatial visualizations |
| P2 | typed audio/haptic event components | games, timers and accessibility feedback |

`Timeline`, `Schedule`, `CalendarGrid` and chart components are not templates for medication or
health. They render generic typed data arrays. The generated application still defines records,
status rules and actions.

### Make adaptive layout compiler-visible

The renderer already treats `Grid.columns` as a maximum and supports `minimumCellWidth`. Extend this
into an explicit adaptive contract:

- min/max width constraints using tokens, not arbitrary device dimensions;
- compact/medium/expanded layout alternatives;
- safe-area ownership at the root;
- stable aspect ratio and fit modes for graphics;
- responsive typography roles;
- virtualized collections with stable keys;
- fold/hinge-aware adaptive split surfaces where available.

The model should specify intent such as `minimumCellWidth` or an adaptive variant. It should not
calculate screen pixels.

### Improve media and icons

Remote URLs alone are insufficient. Add typed media references:

```text
RemoteImageUrl       allowlisted HTTPS URL supplied by trusted input/state
PickedImageHandle    host-owned local image selected by the user
CapturedImageHandle  camera result
GeneratedAsset       packaged asset produced by a trusted asset pipeline
SemanticIcon         closed versioned icon name
```

The renderer owns decoding, caching, size limits and lifecycle. DEAL stores opaque typed handles,
not filesystem paths or bitmaps. Every media component requires a description or explicitly marks
itself decorative.

### Upgrade graphics as a batched scene, not more scalar calls

The current Canvas has rectangles, rounded rectangles, circles, lines and text. This is enough for a
prototype board but not for attractive games. Add generic retained/batched scene data:

- sprite and sprite-sheet regions;
- image and tile-map layers;
- path/polyline/polygon;
- transform groups, rotation, scale and clipping;
- z-order and camera/viewport;
- pointer gestures, drag and multi-touch where supported;
- tween/animation descriptors for presentation-only motion;
- particles and audio/haptic events;
- semantic hit regions and accessibility labels.

Gameplay, collision, legal moves and progression remain in DEAL. Renderer interpolation and
particles must not silently mutate authoritative game state.

### Preserve A2UI's useful ideas without adopting JSON source

A2UI provides several relevant design lessons:

- the renderer advertises a trusted, versioned component catalog;
- UI structure is separate from application data;
- components have stable identities;
- dynamic lists use data templates;
- updates can be streamed and applied incrementally;
- native clients map abstract components to their own design system.

Those principles already fit Deal UI. They do not require model-generated A2UI JSON. Deal UI source
can compile to an internal flat adjacency graph with stable IDs, and the compiler can stream checked
graph updates to Compose. The source remains compact and reviewable.

Progressive rendering should follow these rules:

1. The compiler owns the root and stable node identities.
2. Every committed partial projection is type-correct and renderable.
3. A rejected section never replaces the last valid projection.
4. Components and state changes are diffed by identity/key.
5. Renderer commits are batched to a frame boundary rather than flashing on every token.
6. Interaction stays disabled only for nodes whose required behavior is not yet validated.

### Evaluate a TypeScript/TSX Deal UI frontend

If one syntax invariant is required for both files, current `.dealui` cannot remain unchanged. Two
TypeScript-valid alternatives are possible.

Ordinary function-call form:

```ts
export function App(state: app.AppState): View {
  return ui.Root({
    spacing: ui.spaceMd,
    children: [
      ui.Text({ value: state.title }),
      ui.Button({
        text: "Done",
        onClick: action(app.ToggleAction, { id: state.selectedId })
      })
    ]
  });
}
```

Restricted TSX form:

```tsx
export function App(state: app.AppState): View {
  return (
    <ui.Root spacing={ui.spaceMd}>
      <ui.Text value={state.title} />
      <ui.ForEach source={state.items} keyBy="id">
        {(item: app.Item) => (
          <ui.Button
            text={item.title}
            onClick={action(app.ToggleAction, { id: item.id })}
          />
        )}
      </ui.ForEach>
    </ui.Root>
  );
}
```

TSX has stronger model priors and existing editor/parser tooling. It also adds punctuation and a
larger parser/type-checking surface. The current grammar may remain more token-efficient. Therefore:

- compile both candidate syntaxes to the same current portable UI IR;
- generate a held-out corpus with the same model, prompt budget and component pack;
- compare first-pass parse/type validity, output tokens, wall time, repair count and visual fidelity;
- migrate only if the result is material, not because TSX is fashionable.

## Host capability recommendations

Generated mini-apps become useful when they can visualize real data. The capability model must be
typed and reusable.

### Capability shape

Every host capability should define:

- a versioned import path;
- typed input/output classes;
- availability and permission state;
- whether it is a snapshot, stream/event source or effect;
- cancellation and lifecycle behavior;
- bounded payload and rate limits;
- deterministic test fixtures;
- privacy classification and persistence rules.

Suggested modules:

```text
host/clock          minute, frame and calendar snapshots
host/storage        private load/save/remove
host/notifications schedule/cancel with explicit permission result
host/battery        level, charging and health snapshot/event
host/device         storage, memory and thermal summaries
host/network        connectivity snapshot, not arbitrary sockets
host/location       coarse/fine location with explicit permission
host/calendar       read/write through system confirmation where required
host/camera         capture or picker handles
host/vision         OCR over a supplied image handle
host/photos         user-selected media only unless broader permission is granted
host/health         allowlisted health records through platform APIs
host/share          system share sheet
host/haptics        bounded semantic feedback
host/audio          bounded playback events, not arbitrary file access
```

Periodic and lifecycle-driven data should use typed event-ingress components/capabilities. Do not
implement a sensor stream by recursively starting delayed async effects.

### Capability honesty

A declared capability is not proof that an operation succeeded. Generated state should represent:

```text
unavailable -> permission_required -> loading -> available
                                   \-> denied/error
```

Literal unions and exhaustive `switch` make this reliable. The UI pack should provide a generic
`CapabilityNotice` and permission/error states, while domain decisions remain in DEAL.

## Compiler and generation protocol

### Keep the compiler as the authority

The compiler must continue to own:

- syntax and type validation;
- exact AppInterface extraction;
- stable graph identities and typed holes;
- capability admission;
- runtime budgets;
- cross-artifact action and binding validation;
- atomic installation of a valid source pair.

The model should receive exact diagnostics for the smallest responsible function or UI section. It
should not regenerate accepted code after a local failure.

### Keep tool calls coarse and source-oriented

The current direction of one complete DEAL transaction and one complete Deal UI section batch is
correct. Avoid:

- one tool call per AST node or token;
- serialized JSON AST/HIR as model output;
- task-specific blueprints;
- a separate layout planner;
- whole-file regeneration for a one-function diagnostic.

Structured tool arguments are acceptable as transport for signatures and source bodies. They must
not become the authored program representation.

### Make compiler diagnostics generative

Diagnostics should include:

- stable error code;
- exact source span;
- expected type and actual type;
- the nearest supported TypeScript-shaped alternative;
- allowed capability/component names where relevant;
- one compact declaration/interface context;
- a fingerprint so identical failed repairs stop immediately.

Example:

```text
E-GEN-ARRAY-METHOD app.deal:84:17
filter is not available in generated profile v1.
Supported alternatives: bounded for...of; target feature: array.filter in profile v2.
Expected result: WeekDose[].
```

Once a familiar form such as spread or `map` is implemented, remove prompt rules that tell the model
not to use it. Reliability should come from the language and compiler, not an ever-growing negative
prompt.

### Version the generated-app profile independently

Persist:

```text
dealLanguageVersion
generatedAppProfileVersion
dealUiLanguageVersion
componentPackVersion
hostCapabilityVersions
```

Saved applications are recompiled under their pinned compatible profile. A migration tool can
produce a new source revision; the runtime should not silently reinterpret old source.

## Recommended implementation order

### Phase 0: freeze and measure

- Keep the existing pre-experiment checkpoint.
- Create a held-out corpus covering at least the ten application classes in this document.
- Record current source size, model input/cache/output tokens, first-pass validity, repairs, TTFR,
  TTFUI and functional acceptance.
- Keep scenario prompts out of production instructions and compiler branches.

### Phase 1: compact immutable updates

1. Add object spread to the canonical lexer/parser/AST/type checker/backends.
2. Add array spread with bounded runtime semantics.
3. Add `const` declarations.
4. Permit unambiguous local inference in the Studio profile.
5. Add the TypeScript syntax-oracle CI gate.
6. Add the features to the Android embedded runtime and compiler prelude.
7. Regenerate the same corpus without changing application prompts.

Gate: lower median DEAL output tokens by at least 20% on state-heavy held-out applications, with no
decrease in compile@1 or functional@1.

### Phase 2: collection and state-machine reliability

1. Add ternary expressions.
2. Add pure arrow syntax.
3. Add the initial array-method allowlist.
4. Add literal types and exhaustive `switch`.
5. Admit nullable/optional fields into `AppInterfaceV2`.
6. Add pure selectors and selector caching.

Gate: medication-like, kanban-like and search-like applications compile on the first transaction in
at least 90% of held-out runs and no longer store avoidable duplicate display collections.

### Phase 3: product UI surface

1. Add formatting components.
2. Add form, collection, schedule/calendar and chart components.
3. Add loading/error/confirmation components.
4. Add typed local media handles and image gallery.
5. Add adaptive split/master-detail behavior.
6. Add screenshot and accessibility gates at compact, normal and unfolded sizes.

Gate: every held-out utility uses semantic native components, no critical control clips, and no
application-specific renderer code is introduced.

### Phase 4: typed host data and effects

1. Port `@ui-effect`, policy and failure semantics into the Studio runtime.
2. Enable allowlisted host imports.
3. Implement storage, notifications, camera/OCR and a small OS-data set first.
4. Add permission, cancellation, lifecycle and process-restoration tests.
5. Expose capability fixtures to deterministic generated-app tests.

Gate: a generated device dashboard and one camera/OCR workflow run without application-specific
native callbacks, and denial/offline/error paths remain usable.

### Phase 5: graphics and UI syntax experiment

1. Add batched sprite/tile/path/transform/clip primitives and renderer tests.
2. Measure frame pacing and input latency on phones and foldables.
3. Prototype TypeScript-call and TSX Deal UI frontends over the same IR.
4. A/B generation against the existing `.dealui` grammar.
5. Keep the current syntax unless the replacement wins on measurable generation and maintenance
   criteria.

## Evaluation design

### Corpus

Use at least 40 held-out requests, with multiple phrasing variants and compositions across:

- state-heavy utilities;
- forms and validation;
- collection editing;
- async loading/error/cancel;
- OS-data dashboards;
- image/media workflows;
- schedules and reminders;
- multi-screen applications;
- board games;
- real-time 2D games.

The mandatory medication, exam, health, todo, weather, tic-tac-toe, Arkanoid and chess scenarios are
a regression floor, not the corpus taxonomy. At least half of the evaluation prompts should be
rotated and unavailable while language/component changes are designed.

### Metrics

| Layer | Required metrics |
|---|---|
| Model | input, cached input and output tokens; TTFT; model calls; stop reason |
| Compiler | parse@1, typecheck@1, compile@1, diagnostics, rejected candidates, validation time |
| Product | TTFR, TTFUI, time to interactive, repair count, cancellation behavior |
| Correctness | functional@1, invariant coverage, effect/capability behavior, save/restore |
| Source | DEAL/Deal UI bytes, declarations, field pass-through copies, manual loops |
| UI | screenshot diff/review, clipping, adaptive layout, accessibility, interaction reachability |
| Runtime | frame time, pointer latency, memory, battery/thermal behavior, crash-free repetitions |

Report p50 and p95, not only successful examples. A language feature is accepted only if it improves
held-out results without hiding application-specific behavior in the compiler, runtime or pack.

### Required comparisons

For each language tranche compare:

1. current source + compiler-guided repair;
2. new source + compiler-guided repair;
3. direct full source + compact patch repair;
4. current Deal UI grammar versus any TS/TSX candidate.

Use alternating request order to reduce API cache and network-order bias.

## Concrete target examples

These are proposed target-source examples, not claims that every construct is implemented today.

### Tracker update

```ts
export function markDone(state: TrackerState, action: MarkDoneAction): TrackerState {
  const items = state.items.map(
    (item: TrackerItem): TrackerItem =>
      item.id === action.id ? { ...item, done: true } : item
  );
  return { ...state, items };
}
```

### Derived schedule

```ts
// @ui-selector
export function todaySchedule(state: ScheduleState): ScheduleItem[] {
  return state.items.filter(
    (item: ScheduleItem): boolean => item.dayIndex === state.currentDayIndex
  );
}
```

### OS-data widget

```ts
import * as device from "host/device";

// @ui-effect
export async function refresh(
  state: DeviceDashboardState,
  action: RefreshAction
): DeviceSnapshotAction {
  const snapshot = await device.snapshot();
  return {
    batteryPercent: snapshot.batteryPercent,
    freeStorageBytes: snapshot.freeStorageBytes,
    thermalStatus: snapshot.thermalStatus
  };
}
```

### Real-time game update

```ts
export function onFrame(state: GameState, action: FrameAction): GameState {
  const nextX = state.ball.x + state.ball.velocityX * action.deltaMillis / 1000;
  const nextBall = { ...state.ball, x: nextX };
  return { ...state, ball: nextBall };
}
```

### Semantic utility UI

```text
ui.Schedule(items: select app.todaySchedule(state), timeField: "timeMinutes", statusField: "status")
ui.DurationText(minutes: state.minutesUntilNext, style: ui.textMetric)
ui.LineChart(series: state.weekSeries, xLabel: "Day", yLabel: "Completed")
```

The exact selector-binding syntax needs design work. The important boundary is that Schedule and
charts remain generic, and that DEAL owns the records and rules.

## Decisions to make now

1. Adopt the formal TypeScript syntax-subset definition and add the syntax oracle.
2. Choose object/array spread as the first language extension.
3. Reuse canonical DEAL v1.2 features in the embedded profile instead of creating substitutes.
4. Approve pure selectors as the solution for derived presentation state.
5. Approve the typed host effect model for OS data.
6. Prioritize semantic UI components and formatting before rewriting Deal UI syntax.
7. Run a TSX frontend experiment only after the language/component improvements establish a new
   baseline.

## Explicit non-goals

- no `state with` or other proprietary record syntax;
- no JSON AST/HIR authored by the model;
- no hidden application templates, blueprints or keyword routing;
- no scenario-specific validators or renderer components;
- no general JavaScript runtime, DOM, npm modules or arbitrary network sockets;
- no direct Android/Compose objects in generated source;
- no effects inside pure Deal UI;
- no semantic correctness claims based only on successful compilation;
- no migration to TSX without measured benefit.

## References

Project evidence and design:

- [Generated App Studio specification](../superpowers/specs/2026-08-25-on-device-generated-app-studio.md)
- [DeepSeek DEAL/Deal UI streaming compiler plan](../superpowers/plans/2026-09-03-deepseek-deal-ui-streaming-compiler.md)
- [DEAL generation latency study](2026-09-04-deal-generation-latency-study.md)
- [DEAL v1.2 compiler](https://github.com/arkts-dev/deal)
- [Deal UI framework](https://github.com/arkts-dev/deal-ui)

Relevant TypeScript surface references:

- [TypeScript variable declarations and spread](https://www.typescriptlang.org/docs/handbook/variable-declarations)
- [TypeScript object types](https://www.typescriptlang.org/docs/handbook/2/objects)
- [TypeScript narrowing and discriminated unions](https://www.typescriptlang.org/docs/handbook/2/narrowing)
- [TypeScript JSX](https://www.typescriptlang.org/docs/handbook/jsx.html)

A2UI design references:

- [A2UI components and structure](https://a2ui.org/concepts/components/)
- [A2UI data binding](https://a2ui.org/concepts/data-binding/)
- [A2UI catalogs](https://a2ui.org/concepts/catalogs/)
- [A2UI data flow and progressive rendering](https://a2ui.org/concepts/data-flow/)
