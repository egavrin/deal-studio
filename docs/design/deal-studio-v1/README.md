# DEAL Studio shell and generated-app design system

This package defines the first product-facing visual baseline for the standalone DEAL Studio app. It covers the Studio shell and the native Compose renderer used by generated DUI applications. It does not change generation semantics, the DEAL ABI, or scenario coverage.

## Product shape

- Studio is a creation workspace, not a diagnostics console.
- The primary flow is prompt, build, preview, refine, save, and run full screen.
- Model selection, keys, timings, compiler traces, and source details stay behind explicit settings or disclosure controls.
- Saved applications appear as live, reopenable previews.
- A generated application runs without Studio chrome in full-screen mode or its dedicated activity.
- A saved application can be added to the home screen as a direct app icon or an interactive widget.

## Visual system

- Material 3 supplies platform behavior, typography roles, and accessibility semantics. The accepted v1 Studio shell uses the light scheme; dark tokens remain available for a future explicit mode.
- The compact 80dp Studio header deliberately places its controls at the left and right edges. This keeps them clear of central camera cutouts without reserving an oversized blank safe-area band.
- The prompt is intentionally unbounded. Its counter reports the current character count without presenting a false maximum.
- The product palette uses blue for primary actions, jade for success and completion, amber for attention, and red only for destructive or failed states.
- Cards use a maximum corner radius of 8 dp. Sections remain unframed unless they are repeated items, an input surface, a dialog, or an actual tool frame.
- Interactive controls have a minimum 48 dp target.
- Layout spacing follows a 4/8/12/16/24/32 dp scale.
- Generated content is constrained to a readable maximum width while remaining fluid on compact, foldable, and tablet displays.
- Typography uses Material roles with zero letter spacing and respects system font scaling.

## Generated application rules

- Each generated application owns a single checked `ui.AppTheme`; it never inherits the Studio shell palette.
- The compact theme contains primary and secondary seed colours, visual emphasis, shape geometry, density and
  surface treatment. The native renderer derives accessible Material roles rather than asking the model to colour
  every component.
- Themes may be clean, soft or expressive; controls may be geometric, rounded or pill-shaped; layouts may be
  compact, comfortable or spacious; surfaces may be flat, tonal or elevated. Cards still use at most an 8dp radius.
- Positive, warning and error states retain stable semantic meaning across every app theme. Raw colour values are
  reserved for Canvas scenes rather than ordinary controls.
- Prefer an unframed page hierarchy: title, summary, primary content, supporting sections, then actions.
- Use one dominant primary action per state. Secondary actions remain outlined or tonal.
- Lists are native rows with dividers, not stacks of nested cards.
- Status is communicated with semantic color, icon, and text together.
- The renderer owns consistent pixels; DUI owns structure, bindings, tone, and actions.
- Full-screen applications must remain usable independently of Studio.
- An optional `ui.Widget` subtree describes glanceable content from the same state and actions. It is
  hidden in the full application and rendered only by the Android widget host. Old saved apps remain
  compatible through a generic compact projection; repeated list/grid/canvas actions are never
  guessed by that fallback.
- Widget layout responds to the host-provided dimensions: compact shows the highest-value status and
  one action, medium adds supporting content, and expanded may show up to nine content items and three
  actions. The breakpoints describe available space, not launcher brands or fixed cell counts.
- App icon, dedicated activity, Studio, fullscreen preview and widget all recompile the same canonical
  sources and share one state file bound to app id, source digest and revision.

## Home-screen lifecycle

```text
saved app.deal + app.dealui
          |
          +-- app icon --> GeneratedAppActivity --> Compose DUI renderer
          |
          +-- widget ----> RemoteViews projection --> private action receiver
                                                   --> DEAL update --> shared state
```

The launcher owns confirmation, placement and resizing. Removing an icon or widget never deletes the
saved canonical app. Deleting or revising an app invalidates incompatible persisted state rather than
executing it against changed source. Generated apps stay inside the Studio sandbox; this feature does
not build, sign or install arbitrary APKs.

The theme is emitted once by the compiler-owned boundary:

```typescript
ui.AppTheme(
  primary: "#087A61",
  secondary: "#CA8A04",
  style: "expressive",
  shape: "pill",
  density: "comfortable",
  surface: "elevated"
) {
  ui.Root(...) { /* generated sections */ }
}
```

This declaration is part of canonical `app.dealui`, so saved applications, live library previews and fullscreen
runtime all share the same appearance. It is a generic design primitive, not a preset tied to any scenario.

## Required states

The shell must remain coherent for empty, editing, keyboard-visible, generating, generated, refinement, recoverable error, saved-library, and full-screen runtime states in the accepted light v1 theme.

## References

- `studio-shell-concept.png`: product shell, composer, saved-app library, and starting ideas.
- `generated-app-design-system-concept.png`: representative native generated application using the shared design system.

These images are direction references. The Compose implementation is authoritative for behavior, accessibility, responsive sizing, and theme adaptation.
