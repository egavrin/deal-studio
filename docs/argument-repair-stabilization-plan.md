# Argument repair and device acceptance

## Scope

Preserve the source-free compiler construction API. No scenario-specific production rules, new
language escape hatches, or full-batch regeneration for a local schema mismatch.

1. Add an upstream, ephemeral argument workspace for parsed tool calls. Locate schema failures
   structurally, retain the original candidate, and issue one fixed-location replacement tool.
2. Revalidate the whole candidate after each replacement. Preserve all values outside that location.
   Reject stale patches and repeated no-progress patches; bound argument-repair rounds separately
   from semantic repair. Broken JSON and unknown tools remain transport failures.
3. Integrate the same workspace with greenfield generation and refinement. Android only forwards
   engine-issued tools. Connector JSON parsing remains strict; schema repair belongs to the engine.
4. Test nested failures, multiple failures, unchanged candidates, unknown tools, rollback, sibling
   preservation, and unchanged semantic-repair counts. Replay real failed model arguments.
5. Pin/build/install the toolchain. Generate medication tracking and Arkanoid using DeepSeek Flash
   without reasoning. Test manual entry/confirmation and game movement/collisions, plus save/restore.
6. Run additional held-out requests and report success, repairs, tokens, and latency. Do not infer
   stability or visual quality from compilation alone.

## Gates

- Invalid candidates never reach the runtime.
- Argument patches cannot change unrelated fields or select a different location.
- Both requested applications must be exercised on the device; record screenshots and sources.
- Any unsupported host integration or unverified behavior remains explicitly open.

## Implementation And Findings (2026-09-07)

- Implemented an engine-owned pending argument workspace. Envelope fields use fixed-location
  patches; malformed constructor operands use up to eight fixed constructor slots in one repair
  call plus at most eight new dependencies. Existing sibling operations cannot be changed. Identical dependency echoes are
  harmless, but conflicting ids are rejected before mutation.
- Empty constructor envelopes remain transport failures. Patch tickets expire with the candidate.
  Precise patch diagnostics retain the rejected location, actual value and expected schema.
- Added separate argument-repair counters and an eight-round bound. Original batches pass their
  whole schema and compiler checks after repair; no invalid candidate is published as runnable.
- The engine now issues output ceilings: 32768 for a complete DEAL constructor batch, 16384 for a
  complete UI change, and 8192 for narrow operations. This removes the inherited 8192-token cutoff
  that truncated an Arkanoid response three times in one run.
- Fixed an upstream Deal UI parser bug: quoted operators/keywords such as "-", "true" and "action"
  were mistaken for syntax. Added canonical compilation regressions for eighteen such labels.
- Portable engine tests, the upstream Deal UI test suite and Android unit/build checks pass.

Device experiments exposed additional open work. The full medication/Arkanoid/packing-list matrix
has not passed. Traces contain empty tool responses, malformed JSON, constructor dependency errors,
and semantic repair with no progress. A local argument patch is not a substitute for a complete
typed construction/repair strategy. Smaller baseline requests are a separate diagnostic experiment,
not acceptance of the full requested product.

Status: implementation and device investigation continue. Stability, functional, visual, refinement
and stochastic release gates remain OPEN. Do not publish this build as stable.

## Latest Device Result

Pinned engine `25e11ab67de4f636830a1c2afbe976908e8c1727`, Deal UI
`59dcd664d6f729ec54a5129ddffab85e2d87d249`; Oppo CPH2765; DeepSeek Flash,
reasoning none. Results: `artifacts/argument-repair-oppo-2026-09-07-grouped/results.json`.

| Request | Result | Instrumentation elapsed | Failure |
| --- | --- | --- | --- |
| Medication | Failed | 5.491 s | Empty constructor envelopes on three attempts |
| Arkanoid | Failed | 163.920 s | Output truncation, then repeated invalid empty-array operands in repair |
| Packing list | Failed | 110.279 s | Invalid empty-array operand in a repair dependency |

These durations include device instrumentation, not just provider generation. All three failed
before a runnable candidate was published. The higher output ceiling did not establish a latency
improvement: the first Arkanoid response took 124.507 s and still reached its output limit.

Earlier, on engine `d813a7920f911f84f93be78388a7d132b6c4bf94`, one of three Surprise
requests produced a working Tap Counter in 18.474 s generation wall time, using 36,019 input and
1,980 output tokens. Five taps persisted count=5/best=5; reset persisted count=0/best=5.
That is narrow interaction evidence, not acceptance of medication, Arkanoid, or the latest pin.

Next work must address the constructor operand contract and rejected-patch feedback end to end.
Replay these exact failures in deterministic tests before another stochastic run. Parsed invalid
repair patches currently return through the Android transport-retry branch: repair classification
and progress handling are still incomplete. Do not fix this with scenario defaults, permissive
JSON rewriting, or more retries. Retain compiler validation and immutable accepted siblings.

## Follow-Up Implementation

- Engine `a8f9f55`: parsed invalid argument-repair responses stay in the engine workspace. The next
  request includes the rejected patch and exact diagnostic; the candidate remains unchanged.
  Stale tickets remain rejected. Repeated identical invalid patches stop with an explicit
  no-progress error. Tests cover subsequent successful repair and unchanged semantic counters.
- DEAL `e616e54`: construction VALUE operands accept `{"emptyArray":true}`, projected to the
  existing `[]` expression. This does not change DEAL syntax or allow arrays in Deal UI.
  Contextual int/string/boolean array typing and invalid literal markers have regression tests.
- Engine `32daf3c`: the DEAL generation contract describes native form draft state, scalar input
  actions, and payload-free button events before the UI artifact is generated.
- CompilerWorkspaceTest, portable refinement tests, the Deal UI suite (129 checks and 429 runtime
  invariants), Android unit tests and APK builds pass. This is not product acceptance.

On engine `b47b1b5`, medication reached UI checking but failed because its DEAL interface lacked
usable form setters. Packing-list compiled, launched and restored, but its initial generated DEAL
already contained no-op add/toggle/delete handlers; it is a FUNCTIONAL FAILURE. Its 153.680 s wall
time includes a truncated transport attempt; reported successful-response token counts were 65,108
input and 4,338 output and do not account for that incomplete attempt. Do not report these as the
complete billed token usage. Screenshot and sources are in
`artifacts/argument-repair-oppo-2026-09-07-array-literal/3-packing-list/`.

Full requests on engine `32daf3c` still failed: medication 25.254 s, Arkanoid 48.486 s,
packing-list 15.950 s (instrumentation elapsed). Missing declaration dependencies can leave repair
with only an existing declaration replacement slot; that is an open repair-scope gap. The accepted
source-free constructor API also does not establish that transitions satisfy a user request.
Compilation/launch and functional acceptance must remain distinct results.

Current installed compiler pin: DEAL `e616e5439c1c904c2af080ca1d379a01d7122637`, Deal UI
`0dcfbb643c1c73737bcff95a1263464be542c8a5`, engine `32daf3cac4c1916d4a3b752eb235e6323e1a2195`.
The Deal UI merge changed repository guidance only. Release gates remain OPEN.
