# Generated UI Dataset

This directory owns the host-side UI dataset pipeline for the isolated Generated
App Studio experiment. It replaces the old three-layout smoke corpus with a
catalog-driven source of truth.

The teacher never writes executable app code and its formatting is not trusted:

```text
coverage task seed
  -> DeepSeek Flash structured UiBlueprint JSON
  -> JSON Schema + semantic validation
  -> canonical typed blueprint
  -> deterministic A2UI Express and A2UI wire targets
  -> structural deduplication and group split
```

The behavior/DEAL dataset is intentionally out of scope for this increment.

## Catalog

`catalog.py` declares all 18 pinned A2UI Basic components and 13 Assistant Catalog
components. Run the artifact builder after a reviewed catalog change:

```bash
python3 training/generated_ui/build_artifacts.py
python3 training/generated_ui/build_artifacts.py --check
```

The generated schema is closed: component names and properties are discriminated,
unknown fields are rejected, and catalog signatures and digests are recorded in
`catalog-manifest.json`.

## Fixture Corpus

Build the checked-in compiler fixtures without network access:

```bash
python3 training/generated_ui/build_fixture_dataset.py
```

The eight hand-authored surfaces collectively exercise every catalog component,
bindings, relative list-template bindings, actions, functions, accessibility and
both Express/wire compilers. They are CI fixtures, not production training volume.

## Coverage Schedule

Inspect a deterministic schedule without making API calls:

```bash
python3 training/generated_ui/generate_dataset.py \
  --count 1000 \
  --tasks-only \
  --output build/generated-ui-pilot
```

Every component is rotated through the focal position. The scheduler also varies
domain, locale, viewport, theme, product state, density, interaction level and node
budget. Generation reports retain coverage counts instead of assuming prompt count
equals useful diversity.

## DeepSeek Flash Pilot

Keep the API key outside the repository:

```bash
export DEEPSEEK_API_KEY='...'
python3 training/generated_ui/generate_dataset.py \
  --count 1000 \
  --concurrency 8 \
  --max-attempts 3 \
  --reasoning-effort none \
  --retry-reasoning-effort minimal \
  --final-retry-reasoning-effort none \
  --output build/generated-ui-pilot
```

The host client uses `deepseek-v4-flash`, JSON Schema structured output, a stable
system prompt, non-thinking first pass, minimal-reasoning correction, non-thinking
final correction and content-addressed response caching. Corrections receive the
last schema-valid candidate plus exact validator diagnostics. Authorization headers
and API keys are never written. Accepted records are appended to a checkpoint
immediately; a record resumes only when its exact task-seed hash still matches.

The teacher transport adds checked `required_id_<Type>` scalar fields for the
task's required components. The adapter verifies that every ID exists and has the
declared type, then removes those transport-only fields before canonicalization.

Accepted records contain canonical blueprints, A2UI Express, A2UI wire, validation
status, exact/template hashes and provider provenance. Splits are assigned by
structural template hash, so changed visible text cannot move one layout family
across train, validation and test.

`calibration/2026-08-25-deepseek-v4-flash-50-v4.json` records the current live
pilot metrics and hashes its ignored raw artifacts. CI binds that evidence to the
exact catalog manifest and teacher prompt; it is evidence, not training data.

## Current Limits

- This is the static host pipeline, not the Android renderer or llama.cpp grammar.
- Checked fixtures prove catalog/compiler behavior, not arbitrary-request quality.
- The latest fresh 50-record calibration accepted 45/50 after bounded correction;
  this is below the 80% first-pass and 95% final pilot gate.
- The 90,000-record dataset requires a reviewed 1,000-record pilot first.
- Render, screenshot, accessibility-service and interaction gates remain future
  acceptance layers before a generated sample can become production training data.
