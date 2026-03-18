# Phase 8 - behavior parity

Updated: 2026-03-18
Status: in progress

## Goal

Bring the Java CLI/TUI behavior closer to the TypeScript reference after the main feature set became usable in phase 7.

## Completed parity areas

### Session selector

- current/all scope toggle
- sort toggle
- named-only filter
- path show/hide toggle
- threaded sort
- fuzzy parser and fuzzy search
- `keybindings.json` loading
- loading/progress header
- search scope alignment
- copy and header cleanup
- ANSI layering
- regex error handling
- empty-state hierarchy
- empty-state hints
- metadata layout and truncation
- key-hint styling shared through `PiCliKeyHints`

### Shared selector hint styling

- tree selector key hints
- fork selector key hints
- settings selector key hints
- session picker key hints

### Interactive footer

- token / cost / model summary
- ANSI hierarchy
- provider-aware model summary
- context-window indicator
- auto-compaction suffix
- stale / idle context handling
- cwd / session line
- middle truncation
- git-branch display and watcher

### Interactive app keybindings

- thinking-level cycle
- model cycle forward/backward
- new session
- toggle thinking
- clear
- exit
- expand tools
- paste image
- suspend
- external editor
- startup keybinding hints

### Settings and themes

- settings selector baseline
- hide-thinking parity
- quiet-startup parity
- `Theme` submenu and runtime preview
- `Thinking level` submenu
- hardware cursor and clear-on-shrink settings
- TypeScript-style descriptions for steering / follow-up / transport / quiet-startup
- custom theme loader
- custom theme hot reload
- startup resource listing
- broader custom theme core token support

### Model selector

- scoped/all views
- startup default-model restore
- transport setting support
- custom search and ranking
- detail pane introduction
- detail ANSI hierarchy
- outer border and panel structure
- scope and hint copy alignment
- scope hint now joins multiple configured keybindings with `/`, matching the TypeScript key-hint formatter
- current-model checkmark styling
- keybinding-aware hints
- empty-registry guard
- title-row removal
- search-scope narrowing
- source-order preservation within provider groups
- selected-detail content narrowing
- selected-detail panel simplification
- selected-row weight adjustment
- visible-row cap alignment
- shared row-prefix normalization
- compact metadata layout
- compact-row truncation now preserves the full model id before provider metadata at narrow widths
- current-model checkmark reordered after provider badge
- tokenized fuzzy search now matches against the combined `modelId + provider` text, aligning with the TypeScript `fuzzyFilter` behavior
- alphanumeric swap fallback is now mirrored for model search queries like `5gpt -> gpt-5`
- search selection now stays on the first filtered result instead of snapping back to the current model after each query change
- top-section scope summary and hint now truncate cleanly at narrow widths instead of overflowing the terminal

## Latest completed slices

- `1ba17d3d` `feat(pi-java): style session picker hints`
- `beb62dfa` `feat(pi-java): refine model selector detail header`
- `8888feae` `feat(pi-java): handle empty model selector state`
- `ed85e6b2` `feat(pi-java): remove model selector title row`
- `92164167` `feat(pi-java): narrow model selector search scope`
- `4f297f1c` `feat(pi-java): align model selector provider ordering`
- `a1ab824f` `feat(pi-java): narrow model selector detail content`
- `04700f25` `feat(pi-java): simplify model selector detail layout`
- `16205627` `feat(pi-java): lighten model selector selected row`
- `69a90d68` `feat(pi-java): cap model selector visible rows`
- `831ca535` `feat(pi-java): normalize selector row prefix`
- `cb8a342c` `feat(pi-java): compact model selector row metadata`
- `cb44def3` `feat(pi-java): reorder model selector checkmark`
- working tree slice: compact selector rows now give full width to the model label before truncating provider metadata, matching the TypeScript row-composition strategy more closely
- working tree slice: model selector scope hints now render all configured bindings instead of only the first one, matching the TypeScript key-hint formatter more closely
- working tree slice: model selector search now uses tokenized fuzzy matching over `modelId + provider`, including alphanumeric swap fallback, matching the TypeScript fuzzy filter more closely
- working tree slice: model selector now preserves the active filtered-row selection across search updates instead of re-selecting the current model each time
- working tree slice: model selector top-section lines now truncate to terminal width, avoiding scope-summary and key-hint overflow in narrow layouts

## Remaining gaps

1. Finish the last `PiModelSelector` minor row polish against TypeScript.
2. Finish remaining settings/theme parity that still depends on wider CLI surface adoption.
3. Add pending queue / compaction queue parity.
4. Finish footer edge cases and any remaining provider/git/cwd display drift.
5. Close out the phase and hand off to phase 9.

## Recommended next slice

Continue with the remaining `PiModelSelector` list-row/layout polish first. That is the smallest active gap and the current continuity point.

## Validation used during phase 8

```bash
.\gradlew.bat :pi-cli:test --no-daemon
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
npm.cmd run check
```
