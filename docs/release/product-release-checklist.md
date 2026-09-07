# Product Release Checklist

## Automated On Every PR

- core architecture and secret boundary checks;
- immutable NLU evaluation manifest validation;
- app/core/connector unit tests;
- AndroidTest compilation, ktlint, detekt and lint;
- debug ARM64 APK;
- minified unsigned release-like AAB;
- conditional full RuBERT export contract when training inputs change.

An unsigned release-like artifact proves shrinker and packaging behavior. It is not
distributable and must never be relabelled as a production build.

## Protected Release Configuration

CI supplies these Gradle properties from protected environment secrets:

```text
OFFLINE_ASSISTANT_RELEASE_STORE_FILE
OFFLINE_ASSISTANT_RELEASE_STORE_PASSWORD
OFFLINE_ASSISTANT_RELEASE_KEY_ALIAS
OFFLINE_ASSISTANT_RELEASE_KEY_PASSWORD
```

The repository contains no keystore or signing secret. When the complete property
set is absent, `release` remains unsigned; it never falls back to debug signing.

## Required Before External Rollout

- increment `versionCode` and set a reviewed semantic `versionName`;
- test update from every externally shipped version;
- verify chat, settings, Keystore BYOK and local command data survive update;
- run baseline profile generation and startup/frame benchmarks;
- run 100 mixed sessions with local actions, voice, DeepSeek, Exa and interruption;
- record PSS, disk, battery and thermal results on Pixel/AOSP and OPPO/ColorOS;
- pass TalkBack, 200% font, IME, cutout, foldable and reduced-motion checks;
- inspect the signed AAB/APK for keys, debug symbols and unexpected model copies;
- stage rollout with crash/ANR monitoring and a documented rollback owner.

## Model Rollout

Remote model delivery stays disabled until the catalog is signed and every artifact
has a stable version, byte count, digest, compatibility range and rollback target.
An interrupted or failed verification must preserve the last-known-good bundle.
