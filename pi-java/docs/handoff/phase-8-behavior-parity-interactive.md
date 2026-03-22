# Phase 8 - interactive parity

Updated: 2026-03-22
Status: in progress

## Interactive footer

- token, cost, and model summary
- ANSI hierarchy
- provider-aware model summary
- context-window indicator
- auto-compaction suffix
- stale and idle context handling
- cwd and session line
- middle truncation
- git-branch display and watcher
- parenthesized provider badges for multi-provider model summaries
- strict `>70` / `>90` context-usage color thresholds

## Interactive app keybindings and queue hints

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
- startup and queued-message hints join all configured bindings with `/`
- startup header hints use shared key-hint ANSI layering
- queued-message dequeue hints use shared `PiCliKeyHints.appHint` styling
- dequeue hint and restore status copy now matches the TypeScript wording
- queued steering/follow-up status lines use muted ANSI styling
- queued steering/follow-up lines truncate to the available status width instead of wrapping
- queued follow-up and steering submissions no longer add extra success banners; the queue display itself now carries that feedback, matching the TypeScript behavior more closely
- clipboard-image paste now silently does nothing when no image is available or clipboard access fails, matching the TypeScript no-op behavior on those paths
- successful clipboard-image paste now relies on the existing attached-image indicator instead of adding a separate success status line

## Interactive command and status copy

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
- `/new` slash-command wiring
- `/resume` slash-command wiring
- `/debug` slash-command wiring
- `/name` slash-command wiring
- empty-compaction warning wording
- compaction cancellation and failure wording
- manual compaction now relies on the compaction summary itself instead of a separate `Compacted context` success banner
- tool-details toggle now relies on the transcript expansion/collapse itself instead of adding separate `Tool details: ...` banners
- `/copy` empty-state now uses the plain `No agent messages to copy yet.` wording instead of adding an extra `Error:` prefix
- successful external-editor loads no longer add `Loaded text from external editor...` status lines; the editor content change itself now carries the feedback, matching the TypeScript behavior more closely

## Recent interactive slices

- `626df759` `feat(pi-java): show startup compaction status`
- `db882bae` `feat(pi-java): align reload status copy`
- `c5cc4a27` `feat(pi-java): align reload warning status`
- `4ce5c7c0` `feat(pi-java): align reload streaming warning`
- `90e3ab19` `feat(pi-java): align footer usage thresholds`
- `/reload` success now uses `Reloaded extensions, skills, prompts, themes`, matching the current TypeScript wording
- `/new` now starts a fresh session from the slash-command path too, matching the TypeScript command surface instead of only the app keybinding path
- `/resume` now reaches the existing resume path from the slash-command surface too, matching the TypeScript command surface instead of only the app keybinding path
- `/debug` now writes a debug snapshot to `~/.pi/agent/debug.log` from the slash-command surface, matching the TypeScript command surface closely enough to unblock troubleshooting
- `/name` now sets the session name from the slash-command surface, shows `Session name: ...` when called without an argument on a named session, shows `Usage: /name <name>` when unnamed, and refreshes the terminal title to include the session name
- after `/name` names a session, `/new` now resets the terminal title back to the plain cwd-based title instead of leaving the old session name behind
- queued follow-up and steering submissions now rely on the queued-message panel instead of separate `Queued ...` success status lines
- manual compaction success now relies on the compaction summary message instead of a separate `Compacted context` status line
- `/compact` failures now distinguish `Compaction cancelled` from `Compaction failed: ...`, instead of falling back to the generic `Error: ...` prefix
- expand-tools toggles now change the transcript without adding extra `Tool details: expanded/collapsed` status lines, matching the TypeScript behavior more closely
- `/copy` with no assistant output now shows `No agent messages to copy yet.` directly, matching the TypeScript wording
- clipboard-image paste now leaves the current status untouched when no image is available or clipboard access throws, matching the TypeScript no-op behavior for those edge cases
- external-editor success now leaves the status line untouched and just updates the editor content, matching the TypeScript behavior more closely
- clipboard-image paste success now leaves the status line untouched and relies on `Attached images: N` for feedback, matching the TypeScript "no extra banner" behavior more closely

## Next smallest slice

Move to pending queue / compaction queue parity unless another tiny footer or interactive copy drift is easier to close first.
