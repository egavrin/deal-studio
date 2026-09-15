# Repair v2 Studio integration

Date: 2026-09-07. Status: internal default, not a completed general-release gate.

## Pinned inputs

- DEAL: `df8395e145c36cbe0590219a47d3a39f69a5ea6a`
- Deal UI: `18150c97dd519b771fc2e1b2f1ee25c344e64f52`
- Streaming compiler: `f6dc95e0d8890bc333876e70a01af56ccfa96df0`
- DEX SHA-256: `8c955ed12709b641724abfe5ce8344dab3ccdfe2304ccd9b669758ce70d2202d`
- Pack: v13, unchanged; see `tooling/deal-android-bridge/toolchain.lock`.
- All compiler repositories were clean and revision-matched for both DEX builds.
- The second build without `--update-lock` reproduced the recorded DEX digest.

## Default behavior

Both portable bridge factories, generation and refinement, select source-free constructors and
negotiate `repair-workspace-v2`. The reasoning-configured generation factory uses the same path.
Unsupported capabilities fail explicitly. No Kotlin repair logic or silent compatibility fallback
was added.

The new UI expansion allocates a compiler-owned empty insertion slot at a permitted untouched
container. The model obtains that permission separately, then fills the slot using constructor
operations. An invalid inserted node preserves the published source; the prior staged candidate
operation remains available. A stale permission cannot allocate another slot. Empty insertions
are invalid candidates, not successful no-op edits.

## Checks performed

| Check | Result |
| --- | --- |
| Core RepairWorkspaceV2Test | Passed |
| Core CompilerWorkspaceTest | Passed |
| UiCompilerWorkspaceTest | Passed, including new child slot, rollback and stale permission |
| UiRuntimeInvariantTest | 429 checks passed |
| Portable refinement agent suite | Passed, through emitted source-free tools |
| Diagnostic code inventory guard | Passed |
| Android bridge factory test | Default v2 generation/refinement, reasoning and unsupported-version rejection passed |
| App and DeepSeek connector debug unit tests | Passed |
| App ktlint, detekt and debug lint | Passed |
| Debug APK and instrumentation APK | Built |
| Pixel 10 instrumentation | 18/18 passed, final run 7.078 seconds |

The device was a connected Pixel 10, not an Oppo. The application and test APKs were installed.
Instrumentation included CanonicalDealToolchainDeviceTest and CanonicalDealUiTouchDeviceTest.
These were deterministic tests without provider requests.

During integration, old device fixtures were updated to satisfy the existing v13 required Canvas
children contract. The old raw-source test was moved to constructor tools. A test that requested
whole-view insertion now verifies rejection, matching the existing subtree-only refinement
surface; whole-view insertion was not enabled to make that test pass. Two lint failures were fixed
by using the actual window container dimensions for renderer fallbacks instead of Configuration.

## Not proven or implemented here

- Compiler-issued UI-to-DEAL schema/handler repair transactions remain unsupported.
- New child-slot coverage is action-reachability/host-capability insertion, not every structural
  diagnostic family or every cross-artifact obligation.
- Complete mutation/property-based and held-out repair-family coverage remains pending.
- No live Flash benchmark, 30-run soak, token reduction or LLM success-rate claim.
- No Oppo or multi-width visual acceptance claim.
- The full core test runner still needs its separate JUnit/Hamcrest environment; targeted tests
  above must not be represented as that full runner passing.

Business-logic fidelity is not inferred from successful compilation or these protocol tests.
