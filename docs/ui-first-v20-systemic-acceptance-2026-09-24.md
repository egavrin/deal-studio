# UI-first v20 systemic acceptance ledger — 2026-09-24

## Fixed baseline

- Device: Pixel 10, Android 17, ARM64, Wi-Fi. The network was not traffic-shaped; these exploratory runs are not a latency gate.
- DEAL `751204bc540b4177e17c371780361118abc5f9eb`; Deal UI `e9c7dbdfb0e429f1bf4a01024b36e07796a08936`.
- Streaming compiler `295f7b1cc657fe9cbef38a262639a6fb07e24fd0`; v20 pack digest `c4b5d910e026d20167cd5673e8c01d0817c94a8268493c1c6ed2e0ed055c3cbc`; DEX SHA-256 `efc77c662db8016d3351496cc01739f13db77fd3f23faadccbf8f3959e74c5cc`.
- The separate typed-rescue experiment `4263d6c3ff1e27f93ca0bd89be7e3f3c3b4c617d` passed an offline test and was built as a DEX, but was not installed or measured on the phone. The Android lock was returned to the baseline above before the next device run.

## Exploratory live observations

| Host-free request | First checked callback | First drawn checked preview | Terminal | Business HTTP attempts | Evidence |
| --- | ---: | ---: | --- | ---: | --- |
| Status text with a button changing Ready to Done | 6,055 ms | Not instrumented | Compiler and Android accepted at 11,843 ms; action untested | 1 | Jev SELECT 5,144 ms; LAYOUT 334 ms; no residual |
| Counter with increment and decrement | 4,017 ms | Not instrumented | Rejected at 19,602 ms, `BUSINESS_V20_REPAIR_LIMIT` | 5 | Residual argument operation error, then `CC1004` missing handle and `FABI_RESIDUAL_LOCAL` |
| Same status request, repeated after draw instrumentation | 4,329 ms | 4,404 ms | Rejected at 9,838 ms, `BUSINESS_V20_ARGUMENTS` | 1 | `BUSINESS_V20_VALUE_DEPTH`; binding response was not repaired |

The first-drawn log is emitted once after the meaningful checked preview content is drawn by Compose. It is still an app-render event, not a display scanout measurement. The instrumentation test may pass on an expected terminal rejection (`requireRunnable=false`); that JUnit result is not application success. No row above proves functional correctness.

## Systemic v20 candidate, exploratory live observations

- DEAL `751204bc540b4177e17c371780361118abc5f9eb`; Deal UI `013b53921f98fc359fc723d1672509ea86e783ca`; streaming compiler `e94cd351d3c75a3ff7deb34d4969c5985ede7df5`; DEX SHA-256 `40b3acd1bb370ffa57ef34fee9f925b03e500714716d32d3ed5fc69b901b22e7`.
- Same Pixel 10 and unshaped Wi-Fi as the baseline. These are exploratory samples, not the paired latency gate.

| Host-free request | First checked callback | First drawn checked preview | Terminal | Business HTTP attempts | Evidence |
| --- | ---: | ---: | --- | ---: | --- |
| Status text with a button changing Ready to Done | 2,557 ms | 2,626 ms | Rejected at 13,884 ms, `BUSINESS_V20_RESIDUAL` | 4 | Model explicitly selected residual actions; one 27-node tree rejected `E3001` type mismatch, accepted sibling retained; correction rejected `FABI_RESIDUAL_REFERENCE`. Instrumentation `OK` is not functional success. Screenshot URI `content://media/external/images/media/5403`. |
| Counter with increment and decrement | 3,225 ms | 3,305 ms | Runnable at 14,310 ms; first button visibly changed screen | 3 | Jev 2 HTTP attempts, business bind 7,775 ms, residual 4,468 ms, no business repair. Screenshot URI `content://media/external/images/media/5404`. |

Focused protocol tests, Android Gradle unit/lint/build gates, and 12 selected ARM64 compiler/touch/preview device tests passed on these candidate commits. The full Deal UI script failed at generated gallery compilation (`long` to `int` and unchecked `-Werror` diagnostics); this gate remains open. One successful and one rejected live request cannot establish reliability or the release latency target.

## Scoped diagnostic repair

- Deal UI `4160cafcabac619de99ee43f04e4a89fdb841c09`; streaming compiler `5491eec3ac02db83b3263ed4c58a2caf1945038b`; DEX SHA-256 `b326bf4ced9758499d32fe60c7efa15b8f2737b2b3295092ed5103aa49bf7954`.
- Reproduced a compiler feedback loss: a rejected residual action's `E3001` was replaced by `FABI_UNBOUND_ACTION` after another action was accepted. The new test failed before the repair and passed after it. Residual diagnostics now remain attached to each unresolved action, including argument diagnostics; an accepted sibling stays sealed.
- `node --test test/manifest-ui-refinement.test.js test/v20-systemic.test.js` passed with pinned dependencies. Android `:app:testDebugUnitTest`, `:deepseek-connector:testDebugUnitTest`, `:app:ktlintCheck`, `:app:detekt`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed. Selected ARM64 compiler/touch/preview instrumentation passed `12/12`.

| Host-free request | First checked callback | First drawn checked preview | Terminal | Business HTTP attempts | Evidence |
| --- | ---: | ---: | --- | ---: | --- |
| Same status/button request | 4,053 ms | 4,120 ms | Runnable at 15,065 ms; first button visibly changed screen | 4 | Jev 2 HTTP, business binding 5,162 ms, residual 4,942 ms, one correction 2,583 ms. Screenshot URI `content://media/external/images/media/5405`. |
| Toggle switching an On/Off label | 2,456 ms | 2,540 ms | Rejected at 18,549 ms, `BUSINESS_V20_RESIDUAL` | 5 | Jev selected multiple TextFields, Buttons and Snackbar obligations. Binding first needed a control correction, then residual bodies rejected with `E3007` and `E3001`; no accepted app. Screenshot URI `content://media/external/images/media/5406`. |

The repaired status result is consistent with better feedback but cannot establish causality from one stochastic repeat. The toggle failure shows the release criterion is still open; selected UI breadth increases the number of business bindings and residual actions. Its selection quality and model output need a broader, frozen evaluation before a protocol or prompt change. These exploratory prompts were not held out and are excluded from the 40-request gate.

## Compiler-owned controls and narrower Jev selection

- Current local pins: DEAL `751204bc540b4177e17c371780361118abc5f9eb`, Deal UI `1bb7fbcae38e543a528045347e63a52deca39f3d`, streaming compiler `5a1ec248376556ab73f657928dac0b76b067d449`, v20 pack `8a85d19d1ecf75cb52826d8e408f7fd6f3eb77c23112e77ad3e693e57ad4be75`, DEX `e9ee2f27dc6a6047e186097c47eaee7045814c2c154ac433583c38aaf2427f9a`.
- The v20 pack exposes all 14 scalar `assignPayload` controls as generic selectable `VALUE_CONTROL` surfaces. Jev SELECT has one editable-value obligation, including date and time; DETAIL needs one readable surface, and ACTION no longer forces FEEDBACK. Composite choice controls have a checked `CHOICE_CONTROL` role and required child actions.
- The binding session now supplies the typed root value slot, projection and onChange assignment from the checked pack default. The model may override only the typed initial value. Provider-free tests compile all 14 scalar control pairs without a model-authored onChange action. Optional secondary control events, such as TextField submit/focus/blur, are absent by default; the same SELECT request can explicitly choose each when needed. Selected controls no longer create unused business handlers. Item-scope interactions remain model-owned. Safe metrics identify compiler-owned slots/actions and model residuals.
- The first live toggle retry still selected Button and Snackbar and failed in residual repair. A general SELECT instruction now distinguishes a control change, a separate command and a transient message. This rule also applies to settings controls and parameter sliders; it does not inspect domain names.
- A non-control action may now request a residual body only with an explicit construct unavailable in the typed binding operations. Missing or invalid reasons receive one scoped action correction. The residual request carries the declared reason; the compiler still checks the complete residual tree and final DEAL/Deal UI pair. This gate is syntactic: the 40-request evaluation must measure whether the model uses truthful reasons and whether residual frequency actually falls.
- One exploratory toggle on the first residual-reason pin failed because the model redundantly wrote a compiler-owned control port (`BUSINESS_V20_CONTROL_RESERVED`). The binding session now normalizes redundant value and onChange decisions to the compiler-owned pair and preserves additional typed operations on the same event. Provider-free tests check both cases. A live toggle on the current pin reached a runnable pair and changed the rendered screen after touch.

Exploratory repeats on the same Pixel 10 and unshaped Wi-Fi, excluded from the frozen held-out gate:

| Request and revision | First checked / drawn UI | Terminal and functional check | Jev / business HTTP | Residual / repair |
| --- | ---: | --- | ---: | --- |
| Status/button, initial control candidate | 7,459 / 7,532 ms | Runnable in 16,452 ms; button visibly changed screen | 3 / 4 | 2,800 / 1,858 ms |
| Toggle, before the general SELECT clarification | 4,996 / 5,060 ms | Rejected in 14,000 ms; extra Button and Snackbar selected | 2 / 4 | `E3001`, no accepted pair |
| Toggle, after SELECT clarification | 3,772 / 3,833 ms | Runnable in 7,841 ms; switch changed rendered screen | 2 / 1 | none |
| Text input/readout, before optional event omission | 3,157 / 3,235 ms | Rejected in 10,742 ms; unused submit/focus/blur handlers entered residual | 2 / 5 | `FABI_RESIDUAL_REFERENCE` |
| Text input/readout, after optional event omission | 4,198 / 4,276 ms | Runnable in 8,798 ms; entering `Ping` showed it in field and readout | 3 / 1 | none |
| Same text input/readout, final pin with optional event choices | 5,014 / 5,084 ms | Runnable in 10,422 ms; entering `Ping` showed it in field and readout | 2 / 1 | none |
| Status/button, residual-reason pin | 3,316 / 3,377 ms | Runnable in 8,182 ms; button visibly changed screen | 2 / 1 | none |
| Toggle, normalized-control pin | 4,602 / 4,661 ms | Runnable in 8,914 ms; switch changed rendered screen | 2 / 1 | none |
| Text input/readout, exact final pin | 3,427 / 3,500 ms | Runnable in 8,218 ms; entering `Ping` showed it in field and readout | 2 / 1 | none |

The provider sometimes used a third Jev HTTP attempt while the batch SELECT/LAYOUT decisions remained two phases. These samples show the failure mechanism and a working correction, but are too few and too familiar to establish the p50, p95 or 90% reliability gates. On the final pin, `node --test test/manifest-ui-refinement.test.js test/v20-systemic.test.js` and the Android unit, ktlint, detekt, lint, debug APK and AndroidTest APK gates passed. The selected ARM64 compiler/touch/preview instrumentation passed 12/12, and the exact-final-pin live text/readout interaction passed. The broad streaming `npm test` suite has eight failing legacy/raw or smoke tests on the last full run, including `FABI_COPY_TYPE` and outdated preview-envelope expectations.

Terminal screenshots copied from the device to `/Users/egavrin/Documents/deal-studio/artifacts/v20-systemic/`: `counter-terminal.png`, `status-rejected.png`, `status-repaired-terminal.png`, and `toggle-rejected.png`. The runnable screenshot is captured before the harness interaction assertion; a visible difference after the tap is checked separately by the harness.

## Acceptance to complete

- Compare the pinned baseline and the systemic v20 candidate in alternating order on at least 40 frozen host-free requests, with repeat runs and a recorded model/device/network profile. Failures and timeouts remain in the denominator.
- Promotion requires at least 90% functional success: checked pair, launch, and request-relevant visible interaction. Compilation alone does not count. Inspect visual and accessibility quality and run the required ARM64 device gates.
- Record p50/p95 for first checked callback, first drawn preview and time to working application, including failed attempts; report HTTP attempts, tokens, typed versus residual actions, repair origins, compiler time and discarded prefetch separately. First UI must not regress from the pinned baseline. The existing full-app latency gate is median at most 20 seconds and p95 at most 45 seconds.
- Leave v20 internal until these measurements pass. Host capabilities are outside this first v20 reliability gate and must not be simulated with fake success state.
