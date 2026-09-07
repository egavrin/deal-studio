# Pinned Deal Android frontend

`CanonicalDealToolchainBridge` is the narrow reflection boundary used to run the
production Deal lexer/parser/typechecker and Deal UI parser/checker from an Android
debug APK. The Java 25 bytecode is converted to DEX by Android build-tools; application
code does not implement a parallel grammar or type checker.

Build with:

```bash
DEAL_REPO=/path/to/arkts-dev/deal \
DEAL_UI_REPO=/path/to/arkts-dev/deal-ui \
scripts/build_deal_android_toolchain.sh
```

The checked-in debug asset is temporary integration evidence. Before release, the
toolchain source revisions must be remotely reachable and the build must run in CI with
the recorded SHA-256. Release builds must not consume an unverified developer asset.
