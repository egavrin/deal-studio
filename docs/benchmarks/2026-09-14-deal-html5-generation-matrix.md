# DEAL vs HTML5 generation matrix — 2026-09-14

## Scope

This benchmark compares one direct `deepseek-v4-flash` call for a raw canonical
`app.deal` + `app.dealui` bundle against one direct HTML5 baseline call. Both use
temperature `0.0`, thinking disabled, and a `16,384` output-token cap. Each
scenario was sampled five times and canonical/HTML5 order alternated.

The versioned numeric source is
[`2026-09-14-deal-html5-generation-matrix.csv`](2026-09-14-deal-html5-generation-matrix.csv).
The live harness writes complete non-versioned local artifacts under
`app/build/live-generation-comparison/`.

## Validation boundary

`canonical_raw_framing_success_rate` means that a response supplied exactly one
valid raw bundle containing both `app.deal` and `app.dealui`. It does **not** mean
Android compiler or runtime validation; those are covered by separate device
tests. `html5_normalization_success_rate` means an answer was normalizable to a
standalone HTML document, not that every product behavior was browser-tested.

Travel planner is intentionally included as a broad product brief. Canonical
generation failed 5/5 times because `app.deal` consumed the full 16,384-token
budget before the required Deal UI delimiter. The harness records such failures,
their raw source and their model telemetry without aborting the remaining matrix.

## Pricing

Estimated USD/API-app cost uses the official off-peak DeepSeek V4 Flash rates at
the time of the run: `$0.007` per million cached-input tokens, `$0.22` per
million cache-miss input tokens, and `$0.66` per million output tokens. Cached
input is a subset of input, not an additional token count. Pricing is an estimate
for model usage only and excludes client/network/runtime infrastructure.

Source: [DeepSeek Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing/).

## High-level findings

- Canonical DEAL was faster and cheaper for the small calculator and capability-
  heavy Arkanoid cases, despite its approximately 5k-token fixed prompt contract.
- For medium and large manager apps, the typed state/action graph plus two-source
  bundle can produce more output than HTML5 and lose both wall time and cost.
- The full-bundle 16k ceiling is a demonstrated reliability boundary for broad
  one-call manager apps; it must be handled by a bounded product decomposition
  or a deliberately staged canonical protocol, not by silently accepting an
  incomplete first file.
