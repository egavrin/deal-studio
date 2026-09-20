# Deal Studio component pack v16 candidate

v16 is an unpromoted candidate. The active toolchain remains pinned to v15 only while v16's
release gates are incomplete; this is rollout sequencing, not a v15 compatibility promise.

v16 intentionally has no `legacyRestore` gate. Existing v15 saved applications do not need to
restore or migrate when v16 is promoted. A compatible new artifact must still pass every listed
v16 contract, renderer, device, visual-evidence, and accessibility gate before promotion.

The first implemented family adds typed `disabled`, `loading`, `loadingLabel`, and size properties to Button/IconButton and introduces Pressable with separate press and long-press events. Compiler-backed Gallery fixtures and focused device interaction tests exist, but the release gates remain `PENDING` until the complete component denominator, visual matrix, accessibility review, and current-pack contract suite pass.

Do not update `toolchain.lock` or mark promotion `PASS` from the existence of screenshots alone.
