# Deal Studio component pack v16

v16 is the active pack in the Studio toolchain tuple and the UI-first experimental path. It is
not yet a promoted claim of complete json-render mobile parity: its full catalog, renderer,
device, visual-evidence, and accessibility gates remain independently PENDING.

v16 intentionally has no `legacyRestore` gate. v15 compatibility, restoration, and migration are
out of scope for this delivery. A new artifact still needs all listed v16 contract, renderer,
device, visual-evidence, and accessibility evidence before the product can claim full coverage.

The first implemented family adds typed `disabled`, `loading`, `loadingLabel`, and size properties to Button/IconButton and introduces Pressable with separate press and long-press events. Compiler-backed Gallery fixtures and focused device interaction tests exist, but the release gates remain `PENDING` until the complete component denominator, visual matrix, accessibility review, and current-pack contract suite pass.

Do not mark full-catalog promotion PASS from the existence of screenshots alone.
