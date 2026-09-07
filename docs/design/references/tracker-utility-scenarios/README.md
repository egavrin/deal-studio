# Tracker utility scenario references

These four supplied product slides are visual and capability references for generated
mini-apps. They are not runtime templates and must never be selected by prompt keywords.

| File | Reference capabilities |
| --- | --- |
| `water-widget-landscape.jpg` | Daily target, quick amounts, ring/bar progress, cup count, periodic history |
| `todo-habit-landscape.jpg` | Grouped tasks, check-in, counters, weekly/monthly heatmaps, completion statistics |
| `water-scenario-kep.jpg` | Custom cup capacity, voice-originated updates, daily/weekly/monthly achievement views |
| `schedule-scenario-kep.jpg` | Call-derived tasks, topic grouping, due time, periodic routines, add/complete flows |

## Contract mapping

All four families map to the same generic generated-app contracts:

- DEAL `TRACKER` resources: named bounded `counter`, `list` and `series` state;
- A2UI data: read-only `/app/resources/<name>` snapshots;
- behavior: named DEAL events and bounded `state*` mutations;
- presentation: `ProgressRing`, `Stepper`, `ActionGroup`, `Checklist`, `Heatmap`, plus
  the existing `Metric`, `Progress`, `Chart`, form, navigation and overlay components.

This division keeps generated source compact. A target value or total is one counter;
a dynamic todo collection is one list rather than parallel fixed arrays; daily,
weekly or monthly history is one series. The model remains responsible for choosing
resources, composing presentation and implementing domain rules.

## Acceptance scenarios

The production compiler and cross-artifact validator cover four immutable fixtures in
`GeneratedTrackerReferenceScenariosTest`: water, todo, habit and schedule. Each fixture
must compile below 2,400 DEAL source characters, mutate through named events and reset
exactly. Device acceptance additionally requires live DeepSeek generation and visual
inspection on a phone.
