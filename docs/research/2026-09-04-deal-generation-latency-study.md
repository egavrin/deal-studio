# DEAL generation latency study

Date: 2026-09-04

## Question

Does compiler-guided DEAL generation provide a measurable advantage over generating a complete DEAL source file and repairing it after validation?

The comparison is specifically about DEAL behavior generation. Deal UI generation is a separate sequential phase and is discussed where a full application trace is available.

## Compared strategies

### Compiler-guided graph generation

1. DeepSeek declares the program graph and typed holes.
2. DeepSeek submits one or more hole implementations through compiler tools.
3. Each candidate is parsed and type-checked immediately.
4. Valid independent holes are retained. Only rejected holes remain pending for the next round.
5. The final assembled source is validated again before it can enter the runtime.

This is semantic compiler feedback over tool calls. It is not token-level constrained decoding.

### Direct source plus repair

1. DeepSeek generates one complete DEAL source file.
2. The production DEAL parser and type checker validate the file.
3. If validation fails, DeepSeek receives the diagnostic and returns compact exact `SEARCH/REPLACE` patches.
4. The patched source is validated again. The benchmark allows at most two repair calls.

A separate one-run baseline used complete source regeneration instead of patches.

## Environment

- Device: Pixel 10
- Model: DeepSeek V4 Flash
- Temperature: 0
- Request: held-out score keeper app, not named in production prompts or compiler rules
- Runs: six paired runs for compiler-guided generation versus direct source plus patch repair
- Validation: production DEAL parser, type checker, and AppInterface extraction
- Ordering: direct generation ran first in three pairs; compiler-guided generation ran first in three pairs

DeepSeek's best-effort prefix cache and network variance remain confounders. The balanced ordering removes the simplest ordering bias, but the results still represent the current end-to-end implementation rather than isolated decoder throughput. A release decision requires at least ten samples per strategy across multiple held-out requests.

## Paired results

| Run | First | Compiler valid | Compiler wall | Rounds / rejected | Compiler input / cached / output | Direct valid | Direct wall | Attempts | Direct input / cached / output |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | direct | yes | 11.131 s | 2 / 0 | 3,181 / 2,048 / 1,310 | yes | 12.338 s | 2 | 2,612 / 384 / 2,258 |
| 2 | direct | yes | 16.983 s | 3 / 6 | 5,281 / 2,944 / 2,715 | yes | 10.173 s | 2 | 2,497 / 384 / 1,924 |
| 3 | direct | yes | 7.008 s | 2 / 0 | 3,105 / 2,048 / 1,369 | yes | 13.370 s | 2 | 2,525 / 768 / 2,167 |
| 4 | compiler | yes | 15.405 s | 3 / 6 | 5,651 / 2,944 / 2,306 | yes | 9.698 s | 2 | 2,416 / 896 / 2,048 |
| 5 | compiler | yes | 10.054 s | 2 / 0 | 3,197 / 2,048 / 1,424 | **no** | 19.062 s | 3 | 4,222 / 1,152 / 3,072 |
| 6 | compiler | yes | 13.804 s | 2 / 0 | 3,181 / 3,072 / 1,260 | yes | 9.991 s | 2 | 2,521 / 896 / 2,163 |
| Median | balanced | **100%** | **12.468 s** | **2 / 0** | **3,189 / 2,496 / 1,397** | **83.3%** | **11.256 s** | **2** | **2,523 / 832 / 2,165** |

`cached` is a subset of input tokens, not an additional token category.

These are the API's aggregate usage counters. Input includes the model instructions, user request, compiler tool schemas, and any graph or diagnostic context resent for another round. Output includes generated source and serialized tool-call arguments; rejected hole bodies still consume output tokens. DeepSeek does not report a finer per-section split, so exact prompt-versus-schema attribution requires local tokenization with the deployed model tokenizer.

Median derived values:

- Compiler-guided time to first accepted compiler patch: 3.420 s.
- Direct time to first output token: 2.712 s.
- Compiler-guided total tokens per paired run: 4,556 median.
- Direct total tokens per paired run: 4,688 median.
- Compiler-guided uncached input: 1,141 tokens median.
- Direct uncached input: 1,935 tokens median.
- Compiler-guided output: 1,397 tokens median.
- Direct output: 2,165 tokens median.
- Local compiler validation: 509 ms median versus 225 ms for direct generation.

Across all six elapsed durations, direct generation was 1.212 s, or 9.7%, faster at the median. It was faster in three of the five pairs where both strategies completed; compiler-guided generation was faster in two. The direct strategy failed to produce a valid program in the remaining pair after three attempts, so its shorter median is not equivalent to better time-to-valid-app reliability.

Compiler-guided generation emitted 35.5% fewer output tokens at the median and used 41.0% fewer uncached input tokens, but its repeated graph context raised gross input. Its median total token advantage was only 2.8%. In the two runs with six rejected candidates, a third round increased compiler wall time and token use sharply.

The dominant cost is therefore model/network generation and repeated context, not local compilation. Local validation was hundreds of milliseconds, while generation took seconds.

## What the final success state hides

All six direct initial sources were invalid. Five became valid after one patch repair. One remained invalid after two patch repairs and 19.062 s. A representative failure used unsupported TypeScript-style `new RootState()` expressions; in a successful run the direct patch repaired both locations and the second validation passed.

Compiler-guided generation also produced invalid candidates:

- Four score-keeper runs had no rejected holes.
- Two score-keeper runs rejected six hole candidates and needed a third model round.
- The full medication trace rejected 21 hole candidates before producing a valid program.

The user sees only the final valid application because the compiler does not commit rejected candidates. Therefore, "I have not seen an invalid program" is evidence that the validity gate works, not evidence that the model generated valid code on its first attempt.

## Complex medication trace

The existing live medication run is not a paired A/B measurement, but it shows the current compiler path under a larger request:

| Phase | Time / count |
|---|---:|
| DEAL behavior generation | 52.503 s |
| Deal UI generation | 11.353 s |
| End-to-end wall time | 64.175 s |
| First accepted DEAL patch | 2.852 s |
| Local validation | 2.706 s |
| DEAL model rounds | 4 |
| Deal UI model rounds | 2 |
| Accepted / rejected DEAL hole candidates | 16 / 21 |
| DEAL input / cached / output tokens | 12,914 / 3,968 / 11,430 |

The compiler rejected unsupported `new`, `++`, and `--` constructs and retained valid independent helpers. The final program was valid, but repeated failed hole bodies made generation expensive. About 95.8% of wall time remained outside local validation.

The first patch at 2.852 s was not a runnable or visible app. Deal UI generation only started after the 52.503 s DEAL phase. The current system streams compiler progress, not a progressively usable UI.

## Full-regeneration repair baseline

In one separate score-keeper run, direct generation regenerated the complete source after every diagnostic:

- Compiler-guided: valid in 8.941 s, 1,454 output tokens.
- Direct full regeneration: still invalid after three attempts and 19.808 s, 4,360 output tokens.

This is enough to reject whole-file regeneration as the default repair strategy. It is not enough to prove that the current hole protocol is optimal; compact direct patches performed much better.

## Coarse transaction experiment

The recommended protocol was implemented after the baseline was frozen at tag
`pre-latency-protocol-experiment-2026-09-04`:

- `submit_deal_program` now carries declarations and every deterministic DEAL body in one tool call;
- the compiler infers the root state from nominal type references instead of asking the model to repeat its name;
- empty actions accept a compact name-only signature;
- `repair_deal_batch` exposes only unresolved holes and compact immutable declaration context;
- `submit_deal_ui_sections` carries two to six UI sections in one tool call;
- valid UI siblings behind a rejected section are deferred and revalidated locally after repair;
- Flash has one focused repair and one Pro escalation; Pro has one repair;
- a byte-identical rejected candidate stops the loop.

Two early paired score-keeper runs on the same Pixel 10 are not a release benchmark, but they show
the intended mechanical effect:

| Run | Compiler wall / rounds | Compiler input / output | Direct + patch wall / attempts | Direct input / output |
|---|---:|---:|---:|---:|
| 1 | 6.399 s / 1 | 1,781 / 1,064 | 8.956 s / 2 | 2,334 / 1,944 |
| 2 | 5.787 s / 1 | 1,790 / 1,112 | 12.029 s / 2 | 2,399 / 2,039 |
| Median | **6.093 s / 1** | **1,786 / 1,088** | **10.493 s / 2** | **2,367 / 1,992** |

Relative to the six-run pre-change compiler median, early compiler wall time fell from 12.468 s to
6.093 s and gross input fell from 3,189 to 1,786 tokens. Both new compiler runs were valid in the
initial transaction; both direct runs required a repair. This result must still be repeated over the
full held-out matrix before treating the percentages as stable.

The larger live paths provide two additional observations:

- Medication completed in 32.354 s after the DEAL transaction change, versus the previous 64.175 s
  trace. DEAL took 21.949 s in two rounds and Deal UI took 10.097 s in two rounds.
- Arkanoid completed production compilation plus pointer/frame runtime acceptance in 23.736 s after
  both coarse transactions. DEAL took 19.084 s in two rounds; the complete Deal UI batch took one
  4.301 s round.

A later medication sample correctly stopped after Flash and Pro repeated an identical UI repair: the
generated UI omitted the required `MinuteClock`, leaving `TickAction` unreachable. This is evidence
that the retry ceiling works, not that generation reliability is complete. Action/capability
reachability is now an explicit general UI-generation rule and remains part of the full matrix gate.
After that rule was added, the next medication run passed in one DEAL transaction and one Deal UI
transaction: 28.323 s DEAL, 10.288 s Deal UI and 38.885 s wall time.

## Conclusion

The compiler is useful and should remain the authority for syntax, types, AppInterface compatibility, and runtime admission. It demonstrably prevents invalid candidates from becoming runnable applications and can preserve valid independent work.

The current multi-round generation protocol is not a demonstrated latency optimization. Direct generation plus a compact patch was usually faster when it succeeded. Compiler-guided generation reduced output tokens and completed more reliably. On a larger request, or when several holes fail together, repeated tool rounds can dominate latency.

The useful product is the compiler and its typed repair boundary. The phrase "streaming compiler" should not be treated as proof of faster user-visible output until time-to-first-runnable-UI is measured.

## Recommended generation protocol

1. Keep compiler validation and runtime admission mandatory.
2. Generate declarations and AppInterface first.
3. Submit all independent DEAL holes in one batched model response.
4. Return one compact structured diagnostic containing only rejected hole IDs, signatures, and errors.
5. Repair all rejected holes in one targeted model call; do not resend the complete accepted program.
6. Detect repeated identical invalid candidates and stop instead of spending another blind round.
7. Start a compiler-owned Deal UI skeleton from the validated AppInterface while behavior holes are being filled, without inventing behavior or adding a planner.
8. Measure `TTFR` (first runnable application) and `TTFUI` (first meaningful visible UI), not only first token or first accepted patch.
9. Run an alternating-order benchmark with at least ten samples per strategy over multiple held-out requests, including both compact and state-heavy applications.
10. Compare success rate, p50/p95 wall time, API calls, input/cache/output tokens, rejected candidates, validation time, and final source size.

## Reproduction

The device benchmark is `CanonicalGenerationLatencyBenchmarkDeviceTest`. Raw local captures are written under `build/latency-study/` and are intentionally not product fixtures.
