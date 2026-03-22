# Phase 8 - selector and settings parity

Updated: 2026-03-22
Status: in progress

## Session selector

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
- empty-state hierarchy and hints
- metadata layout and truncation
- key-hint styling shared through `PiCliKeyHints`

## Shared selector hint styling

- tree selector key hints
- fork selector key hints
- settings selector key hints
- session picker key hints

## Settings and themes

- settings selector baseline
- hide-thinking parity
- quiet-startup parity
- `Theme` submenu and runtime preview
- `Thinking level` submenu
- hardware cursor and clear-on-shrink settings
- TypeScript-style descriptions for steering, follow-up, transport, and quiet-startup
- theme submenu now omits the extra `(current)` marker, matching the TypeScript selector more closely
- settings selector now drops the Java-only `Settings` title and top help sentence, and uses TypeScript-style border-only framing
- custom theme loader and hot reload
- startup resource listing
- broader custom theme core token support

## Model selector

- scoped/all views
- startup default-model restore
- transport setting support
- custom search and ranking
- detail pane introduction and simplification
- detail ANSI hierarchy
- scope and hint copy alignment
- current-model checkmark styling
- keybinding-aware hints
- empty-registry guard
- title-row removal
- search-scope narrowing
- source-order preservation within provider groups
- selected-row weight adjustment
- visible-row cap alignment
- shared row-prefix normalization
- compact metadata layout
- compact-row truncation that preserves the full model id before provider metadata
- current-model checkmark preservation in truncated rows
- tokenized fuzzy search over `modelId + provider`, including alphanumeric swap fallback
- selection retention while searching
- top-section width handling at narrow terminal sizes
- TypeScript-style `Model: <id>` selection status
- providerless model-cycle status copy with model-name preference
- scoped-vs-global single-model fallback copy
- `/model` slash command now opens the selector, and `/model <term>` now opens it with the initial search applied
- exact `/model <term>` matches now select directly instead of always opening the selector first

## Recent selector/settings slices

- `07cc3f16` `feat(pi-java): align compact selector truncation`
- `4fb9dedf` `feat(pi-java): join selector scope bindings`
- `489fbe0a` `feat(pi-java): align model selector fuzzy search`
- `7fb972ef` `feat(pi-java): preserve model selector search selection`
- `12116f86` `feat(pi-java): clamp model selector header width`
- `cde4d6c7` `feat(pi-java): preserve current model marker in compact rows`
- `/model` is now wired in the Java interactive mode, and `/model <term>` now pre-filters the selector the same way the TypeScript flow does before any exact-match shortcut handling
- exact `/model <term>` hits now short-circuit straight to selection, matching the TypeScript behavior more closely
- theme submenu options no longer add a Java-only `(current)` description, matching the TypeScript selector more closely
- settings selector now relies on the built-in settings list hint row plus top/bottom borders, instead of the Java-only title and extra top instruction sentence

## Next smallest slice

Finish the last visible `PiModelSelector` edge-case polish. If no clear selector drift remains after that, switch to pending queue / compaction queue parity.
