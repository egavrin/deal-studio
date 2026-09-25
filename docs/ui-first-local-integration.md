# Local UI-first integration

The portable route supplies no legal host capabilities. Renderer quality evidence is host-owned,
versioned as `renderer-quality-evidence-v1`, and bound to the exact v19 pack digest. Missing renderer
traits remain unsupported; this is not a claim that every component or origin row is complete.

Manifest v2 / draft wire v5 previews carry a checked hierarchy and typed unresolved bindings. They
contain no business-state samples. Partial or incomplete previews remain visible after a failed run;
the previous runnable application remains available. Raw DEAL admission checks frozen structural,
ABI and presentation digests before replacing the runnable application.

The experimental Jev route evaluates a pack-derived SELECT batch, publishes the first meaningful
compiler-checked inert UI, and then evaluates one LAYOUT batch before freezing the ABI. A single
scoped repair may follow a rejected batch within a three-HTTP-attempt budget, including transport
retries. Oversized or unrepresentable batches use the explicitly reported sequential compatibility
route. Debug `UiFirstMetrics` records first checked UI time, SELECT/LAYOUT/repair network time,
local UI compiler time, route and fallback reason, and time to runnable separately. The target
`p50 <= 2 s` includes fallbacks and requires paired measurements on a recorded device and network;
implementation and one successful preview do not close that gate.

## Explicit local credential bootstrap

Run `python3 scripts/bootstrap_debug_credentials.py` only when intentionally provisioning a local
debug build. It parses only `TYPESAFE_API_KEY` and `DEEPSEEK_API_KEY` from the user's `.env`, without
shell evaluation. It builds locally, uses `adb install -r --user 0`, and invokes the debug-only
bootstrap activity. Neither installation nor normal app initialization provisions keys.

The default fills absent credentials only. `--replace-bootstrap` refreshes bootstrap-owned keys;
neither mode overwrites user-owned keys, legacy keys, or intentionally cleared credentials. Values
are not printed or passed in command arguments. The local debug APK and build outputs contain the
embedded bootstrap values; keep these artifacts private and do not upload them.

Routine installation uses `adb install -r --user 0 app/build/outputs/apk/debug/app-debug.apk`.
Do not uninstall the application or clear its data to install an update.

## Pending evaluator and device work

The deterministic bridge smoke is `CanonicalUiFirstHostContractTest.java`. The Android JVM checks
are `CanonicalRendererQualityTest`, `FrozenUiPreviewContractTest`, and `CredentialProvenanceTest`.
Focus traversal, modal Back, accessibility labels, hierarchy screenshots, stateful interactions,
save/restore, and paired HTML5/Surprise comparisons still require connected acceptance. Live-run
counts and screenshot review must be measured independently; this integration does not close them.
