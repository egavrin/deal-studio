# Jev coherent v20 r1: Pixel-verified checkpoint

This checkpoint captures the opt-in `-PjevCoherentV20=true` Android integration and
its exact compiler dependencies. It is a reproducible development baseline, not
a release-stability claim. Ordinary builds do not select this revision by default.

## Source and binary identity

| Component | Pinned identity |
| --- | --- |
| Android source | This checkpoint's Git commit and tag |
| DEAL | `b66f4488b0437345c460203061a9109404f1fc9f` |
| Deal UI | `e884a3ed74d4648e488dfb41b6e60013ee528edf` |
| Streaming compiler | `c4fb2452d467719c35abb4fea703cd2e65ca8d64` |
| Deal UI pack | `deal-studio-dealui-pack-v20`, SHA-256 `8a85d19d1ecf75cb52826d8e408f7fd6f3eb77c23112e77ad3e693e57ad4be75` |
| Embedded DEX | SHA-256 `ce324ebe921053d99d5536faf320ac47e0228df0101330f8fae6caf94b88bd23` |
| Tested debug APK | SHA-256 `5d7ef7fbabc1ee77ce682316768462765153884ebd1d44d6f101f92a99463464` |

The exact upstream revisions and DEX digest are also recorded in
`tooling/deal-android-bridge/toolchain.lock`. Rebuild the DEX using the pinned
checkouts and `scripts/build_deal_android_toolchain.sh`; build Android with
`-PjevCoherentV20=true`. API credentials are supplied at runtime or from local
build settings and are not part of this checkpoint.

## Verification already performed

- Android Gradle unit tests, ktlint, detekt, lint, debug APK and instrumentation
  APK assembly passed on this source and binary set.
- On a Pixel 10, `CanonicalDealToolchainDeviceTest` and
  `CanonicalDealUiTouchDeviceTest` passed: 10 tests.
- An accepted, model-produced personalized workout pair was recompiled on the
  Pixel, admitted by Android, saved and restored: 1 device test. Manual day
  selection and exercise checkoff changed state as expected.
- The complete request, safe token and timing summary, commands, logs, exact
  accepted source pair and screenshots are retained in the local evaluation
  artifact at
  `/Users/egavrin/Documents/deal-studio/artifacts/jev-coherent-v20-2026-09-24/contract-followup/workout-replay-2026-09-25/RESULT.md`.

The successful provider generation preceded the final Android admission patch;
the final APK was verified by replaying that exact accepted pair. A fresh
provider-to-UI run on the final APK, repeated reliability measurements, the full
scenario matrix, other widths, and accessibility review are still open gates.
The stopped 40-pair benchmark was not restarted.
