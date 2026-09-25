# Deal experimental v20 integration status — 2026-09-23

## Pinned implementation

- Android branch: `integration/ui-first-foundation`, with existing user changes preserved in the working tree.
- Component pack: `deal-studio-dealui-pack-v20`, digest `c4b5d910e026d20167cd5673e8c01d0817c94a8268493c1c6ed2e0ed055c3cbc`. Its checked semantics descriptor enumerates all 100 components. The pack body is identical to v19 except for the version header.
- DEAL: `f75cbd2b58fa02bfa8eab4e1e255452977528ea1`.
- Deal UI: `dbe1ddc28e256e9db567dc29377c95a7bdf38913`.
- Android DEX input is the direct-executor streaming checkout at `2970f3077df15774abac1e8d9f5aea8ecf8c5001`. DEX SHA-256: `270205c1890cb931fb249d8fd436402d5220d95df26b29f5a52c319a4b6ea99d`.
- The v20 compact business protocol is also present in the separate streaming-compiler checkout at `502c18f`.

The compiler owns public DEAL declarations, UI source, scalar `onChange` assignments, and presentation binding. DeepSeek's `complete_business_v20` supplies static copy, initial/private values, helpers, and residual handlers. A second call, when available, is limited to a compiler-identified handler or the `initialState` values; the complete source is compiled and linked again. Android receives a compiler-checked inert preview and accepts the final DEAL/Deal UI pair atomically. Host components remain unavailable to this v20 path; the older sequential UI session remains an explicit capacity/representation fallback.

## Provider-free and device checks

- Both streaming checkouts passed `node --test test/manifest-ui-refinement.test.js` using the pinned DEAL, Deal UI and v20 pack. The direct-executor test covers missing-handler repair, parser-error cascade repair, handler type mismatch, and `initialState` repair.
- `:app:testDebugUnitTest`, `:deepseek-connector:testDebugUnitTest`, `:app:ktlintCheck`, `:app:detekt`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed on the final DEX.
- The selected ARM64 suite passed 12/12 on the final DEX: `CanonicalDealToolchainDeviceTest`, `CanonicalDealUiTouchDeviceTest`, and `ManifestUiPreviewDeviceTest`.

## Live observations on Pixel 10

The device is a Pixel 10 running Android 17 over Wi-Fi. There was no controlled network shaping. One ordinary prompt was used: “Build a simple todo list where I can add tasks and mark them complete.” The selected model credentials were restored through the repository's local debug bootstrap after the first device gate had removed the configured app data. Harness attempts made while the Build button was disabled are excluded; the harness now reports that condition directly.

| DEX revision | First checked UI callback | Jev route | Jev result | Full app |
| --- | ---: | --- | --- | --- |
| `939caba` | 4,048 ms | batch, no fallback | 2 HTTP requests; SELECT 2,694 ms, LAYOUT 680 ms | Rejected after 16,070 ms: `BUSINESS_V20_DEAL` parser errors, terminal `UIF3001` |
| `8b8e546` | none | batch, no fallback | SELECT rejected after probability-sum validation retries, terminal `UIF2104_PROBABILITY_SUM` | No app; terminal in 4,404 ms |
| `9553529` | 3,919 ms, SELECT | batch, no fallback | 3 HTTP attempts | Rejected after 15,435 ms: `BUSINESS_V20_DEAL` parser errors |
| `c0db235` | 2,568 ms, SELECT | batch, no fallback | 2 HTTP attempts | Rejected after 19,249 ms: parser failure reached repair, then `BUSINESS_V20_UPDATE_FIELD` |
| `da1821c` | 2,667 ms, SELECT | batch, no fallback | 2 HTTP attempts | Rejected after 19,705 ms: `BUSINESS_V20_DEAL` type/parser errors |

A separate complex workout tracker request on `2970f30` received its first checked partial UI after 3,879 ms. Jev used SELECT, one variant REPAIR, and LAYOUT in three HTTP attempts with no sequential fallback. Full generation was rejected after 33,350 ms with `BUSINESS_V20_LINK` (`UL2000`, `UI2050`). Local UI compiler time was 22,608 ms. This is a distinct failure mode from the todo parser errors.

Every observed checked preview exceeded the 2-second target. The 4,048 ms callback was created after SELECT but logged as phase LAYOUT by that DEX; the later DEX corrected its phase label without changing preview timing. The probability-sum failure has no preview and must count as a failure in any full-set latency and success calculation. These repeated and exploratory requests do not support a held-out p50/p95, fallback-rate, or quality conclusion. The v19 measurements in `ui-first-batch-device-benchmark-2026-09-23.md` were exploratory and are not a controlled paired comparison with v20.

## Open acceptance

Run paired v19/v20 held-out prompts with a fixed device, model, credentials, and network profile. Include ordinary and complex requests, fallback and failures. Record first checked callback p50/p95 over the whole set, full-app time, actual Jev HTTP attempts, fallback share, compiler acceptance, and behavioral/visual quality. Do not promote the latency or release gate until those results pass. The current live failures show separate reliability work in DeepSeek's residual DEAL syntax, DEAL/Deal UI linking, Jev response validation, and local compiler cost on complex screens.
