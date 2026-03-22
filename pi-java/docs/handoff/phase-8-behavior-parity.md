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
- parenthesized provider badges for multi-provider model summaries
- strict `>70` / `>90` context-usage color thresholds

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
- startup and queued-message hints now join all configured bindings with `/`, matching the selector hint formatter
- startup header hints now use shared key-hint ANSI layering instead of dimming the whole line as one block
- queued-message dequeue hints now use the shared `PiCliKeyHints.appHint` ANSI layering instead of plain text concatenation
- dequeue-related copy now matches TypeScript more closely: `edit all queued messages`, `No queued messages to restore`, and `Restored ... to editor`
- queued steering/follow-up status lines now use muted ANSI styling, matching the TypeScript pending-message display hierarchy more closely
- queued steering/follow-up lines now truncate to the available status width instead of wrapping, matching the TypeScript `TruncatedText` behavior more closely

### Interactive command/status copy

- tree navigation success wording
- tree current-point wording
- tree empty-state wording
- fork-selector success wording
- reload success wording
- reload warning-state wording
- reload streaming-warning wording
- unsupported-thinking wording
- startup compaction wording
- new-session success wording
- empty-compaction warning wording

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
- current-model checkmark now stays visible when compact row metadata must truncate at narrow widths
- selector completion status now matches the TypeScript `Model: <id>` copy instead of the older Java-specific `Selected provider/model` wording
- model-cycle status now drops the Java-specific provider prefix and prefers `model.name` over `model.id`, matching the TypeScript `Switched to <name|id>` wording more closely
- model-cycle fallback copy now distinguishes `Only one model in scope` from `Only one model available`, matching the TypeScript scoped/global behavior

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
- working tree slice: compact model-selector rows now preserve the trailing current-model checkmark instead of chopping it off with provider metadata at narrow widths
- working tree slice: interactive header hints and queued-message dequeue hints now render all configured app bindings instead of only the first one
- working tree slice: queued-message dequeue hints now use shared key-hint styling, matching the selector hint hierarchy more closely
- working tree slice: startup header hints now use shared key-hint styling, matching the queued-message and selector hint hierarchy more closely
- working tree slice: dequeue hint and restore status copy now match the TypeScript wording for queued-message editing and restore feedback
- working tree slice: queued steering/follow-up status lines now use muted ANSI styling, matching the TypeScript pending-message display hierarchy more closely
- working tree slice: queued steering/follow-up lines now truncate to the available status width instead of wrapping, matching the TypeScript `TruncatedText` behavior more closely
- working tree slice: model-selector completion status now uses the TypeScript `Model: <id>` copy instead of the older Java-specific selection wording
- working tree slice: model-cycle status now drops the provider prefix and prefers `model.name`, matching the TypeScript `Switched to <name|id>` copy more closely
- working tree slice: model-cycle fallback copy now distinguishes scoped-vs-global single-model states, matching the TypeScript behavior more closely
- working tree slice: footer model summaries now use TypeScript-style parenthesized provider badges instead of `provider/model`
- working tree slice: footer context-usage coloring now stays muted at exactly `70.0%` and warning-colored at exactly `90.0%`, matching the TypeScript `>70` / `>90` threshold logic
- working tree slice: tree navigation success now uses the TypeScript `Navigated to selected point` wording
- working tree slice: selecting the current tree node now uses the TypeScript `Already at this point` wording instead of reporting a navigation success
- working tree slice: empty `/tree` now uses the TypeScript `No entries in session` wording
- working tree slice: fork selection success now uses the TypeScript `Branched to new session` wording
- working tree slice: reload success now uses user-facing `Reloaded extensions, prompts, themes, and settings` wording instead of the older Java-specific resource phrasing
- working tree slice: reload now keeps the same success status even when warnings are present, matching the TypeScript pattern of surfacing warning details separately via diagnostics
- working tree slice: `/reload` while streaming now uses the TypeScript-style warning copy directly instead of surfacing the lower-level reload exception as an `Error: ...` status
- working tree slice: cycling thinking on a non-reasoning model now uses the TypeScript `Current model does not support thinking` status instead of surfacing the lower-level unsupported-operation error
- working tree slice: startup now surfaces `Session compacted 1 time` / `N times` when the current session already contains compaction entries, matching the TypeScript startup status behavior
- working tree slice: new-session success now uses the TypeScript-style `New session started` wording instead of the older `Started new session`
- working tree slice: `/compact` with fewer than two messages now short-circuits with the TypeScript `Nothing to compact (no messages yet)` warning instead of entering compaction

## Remaining gaps

1. Finish the last `PiModelSelector` edge-case polish against TypeScript.
2. Add pending queue / compaction queue parity.
3. Finish remaining settings/theme parity that still depends on wider CLI surface adoption.
4. Finish footer edge cases and any remaining provider/git/cwd display drift.
5. Close out the phase and hand off to phase 9.

## Recommended next slice

Continue with the remaining `PiModelSelector` edge-case polish first if another selector-specific drift is visible; otherwise move to pending queue / compaction queue parity.

## Validation used during phase 8

```bash
.\gradlew.bat :pi-cli:test --no-daemon
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
npm.cmd run check
```
