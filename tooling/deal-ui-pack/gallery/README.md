# Deal Studio UI Catalog

The catalog is a debug-only surface. Each allowlisted fixture is compiled by the embedded DEAL/Deal UI toolchain and rendered by `CanonicalDealUiRenderer`; it does not have a gallery-specific component implementation.

Run one case:

```bash
scripts/run-ui-catalog.sh --serial "$ANDROID_SERIAL" \
  --case shadcn.Button.default --style technical \
  --output build/ui-catalog/evidence/shadcn.Button.default-technical.png
```

Run the checked matrix:

```bash
scripts/verify-ui-catalog.sh --serial "$ANDROID_SERIAL" \
  --matrix tooling/deal-ui-pack/gallery/matrix.json \
  --output build/ui-catalog/evidence
```

`compileAndRender: PASS` means the fixture compiled, reached the renderer, and exposed its settled readiness semantic. It does not imply human visual approval; `visualReview` remains `PENDING` until a reviewer signs off. The debug activity accepts only case IDs from its packaged manifest and one of the six existing Studio style tokens. It never accepts executable source through an Intent.

Each fixture names its exact immutable pack version. The evidence report records that version and digest per screenshot, so a candidate-pack fixture is never reported as if it used the active pack from `toolchain.lock`.
