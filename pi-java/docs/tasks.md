# pi-java implementation tasks

Updated: 2026-03-22

## Working rules

1. Default to `TDD`, with each slice driven by `red -> green -> refactor`.
2. Every bug fix starts with a failing test.
3. New providers, tools, and session semantics need fixtures and contract tests first.
4. Code without tests can exist temporarily for exploration, but it does not count as complete.
5. A phase is not considered closed until its relevant tests are green.
6. Detailed test naming, fixture, and golden-file conventions follow `pi-java/docs/tdd.md`.

## Phase status

- Phase 0: complete.
- Phase 1 (`pi-ai`): complete.
  - Core model, events, `EventStream`, provider/model registries, credential resolution, `PiAiClient`.
  - `AssistantMessage` assembler, `SSE` parser, `WebSocket` adapter.
  - Providers: `openai-responses`, `openai-completions`, `anthropic-messages`, `google-generative-ai`, `bedrock-converse-stream`.
  - Shared provider compat/replay layer and cross-provider behavior matrix.
- Phase 2 (`pi-agent-runtime`): complete.
  - `AgentState`, `AgentEvent`, `AgentLoopConfig`, `AgentLoop`, sequential tool execution, argument validation.
  - Steering / follow-up queues, `Agent` facade, abort lifecycle, lifecycle tests.
- Phase 3 (`pi-session`): complete.
  - Session model, JSONL codec, `v1 -> v2 -> v3` migrations, replay, `SessionManager`.
  - Branching, fork extraction, persisted append behavior, settings layering, instruction resource loading.
- Phase 4 (`pi-tools`): complete.
  - Primitives: truncation, diff preview, shell execution, path policy, image resize.
  - Tools: `read`, `write`, `edit`, `bash`, `grep`, `find`, `ls`, plus golden tests.
- Phase 5 (`pi-extension-spi`): complete.
  - Core extension contracts, discovery, isolated loading, event bus, resource discovery, reload runtime, example plugin.
- Phase 6 (`pi-tui`): complete.
  - Core contracts, process terminal, diff renderer, overlays, text components, `Input`, `Editor`, `Markdown`, `Loader`, `SelectList`, `SettingsList`, `Image`, `VirtualTerminal`.
- Phase 7 (`pi-cli` / `pi-sdk`): functionally complete.
  - CLI modes: `interactive`, `print`, `json`, `rpc`.
  - SDK facade and session bootstrap.
  - Commands and overlays: `list-models`, `--resume`, `--export`, `copy`, `tree`, `fork`, `compact`, `reload`.
  - Module wiring, startup/session bootstrap sharing, instruction-aware prompt composition, help/version, exit semantics.
  - Remaining UI and behavior polish is tracked in phase 8.
- Phase 8 (behavior parity): complete.
  - Session selector parity is substantially advanced.
  - Settings/theme parity is substantially advanced, including removal of the extra theme-submenu `(current)` marker, removal of the Java-only settings title/top-help block in favor of TypeScript-style border-only framing, and retention of `Thinking level` for non-reasoning models with an `off`-only submenu.
  - Footer parity is substantially advanced, including TypeScript-style parenthesized provider badges in multi-provider model summaries and strict `>70` / `>90` context-usage color thresholds.
  - Interactive queue/key-hint parity continues to advance, including shared key-hint styling, TypeScript-aligned dequeue copy in queued-message hints and restore statuses, muted queued-message status lines, narrow-width queued-line truncation instead of wrapping, removal of extra queued follow-up / steering success banners in favor of the queue display itself, silent clipboard-image paste no-ops when no image is available or clipboard access fails, and removal of the extra clipboard-image success banner in favor of the attached-image indicator itself.
  - Interactive command/status copy continues to converge, including TypeScript-aligned tree-navigation success wording, tree current-point wording, tree empty-state wording, fork-selector success wording, reload success wording, reload warning-state wording, reload streaming-warning wording, unsupported-thinking wording, startup compaction wording, new-session success wording, `/new`, `/resume`, and `/debug` slash-command wiring, empty-compaction warning wording, compaction cancellation/failure wording, removal of the extra manual-compaction success banner in favor of the compaction summary itself, removal of the extra tool-details toggle banner in favor of the transcript change itself, plain `/copy` empty-state wording without an extra `Error:` prefix, and silent external-editor success paths without extra status banners.
  - Interactive command/status copy now also includes `/name` slash-command wiring, current-name lookup, session-name updates, terminal-title refresh on name changes, terminal-title reset when `/new` starts a fresh unnamed session, and terminal-title refresh after `/fork` when the chosen branch drops the later session name.
  - Interactive slash-command parity now also includes `/session` session-info output with real message/token/cost totals, `/hotkeys` keyboard-shortcut output sourced from the configured editor/app bindings, `/changelog` changelog rendering from the nearest available coding-agent changelog file, `/export [path]` wiring onto the existing HTML exporter, and `/share` wiring onto GitHub gist sharing with TypeScript-style share/gist URLs.
  - Model selector parity was closed after compact-row truncation, multi-key scope-hint alignment, TypeScript-style tokenized fuzzy search alignment, selection-retention parity while searching, top-section width handling at narrow terminal sizes, current-checkmark preservation in truncated rows, TypeScript-style `Model: <id>` status copy after selection, TypeScript-style providerless model-cycle status copy with model-name preference, scoped-vs-global single-model fallback copy, `/model <term>` opening the selector with the initial search applied, and exact `/model <term>` hits selecting directly without opening the selector first.
  - Remaining TypeScript-only gaps (`/login`, `/logout`, true `/scoped-models`, and async compaction queueing) are now treated as phase 9 subsystem work rather than phase-8 polish.
- Phase 9 (package sources / distribution): in progress.
  - Typed settings support for `packages` and `enabledModels` is now in place in `pi-session`, including round-trip persistence for plain package-source strings and filtered package-source objects.
  - Local package sources now participate in Java-side startup discovery for themes and extensions, using the same scope-relative base directories as the TypeScript app (`~/.pi/agent` for global settings, `<cwd>/.pi` for project settings).
  - Theme package sources are also re-read on session reload, so settings-file changes can add or remove custom themes without restarting the CLI.
  - Already-installed `npm:` / `git:` package sources now participate in Java-side theme/extension discovery too, using the same installed-root layout as the TypeScript app for project packages and git packages.
  - Package installation and auth-backed package management are still later phase-9 work; Java currently discovers what is already on disk.

## Current next slices

1. Start consuming discovered package resources beyond raw paths: wire skill/prompt/theme resource surfaces into the Java CLI flows that already exist.
2. Add Java-side auth storage and interactive `/login` / `/logout`.
3. Wire `enabledModels` into saved scoped-model selection and `/scoped-models`.
4. Decide whether Java should keep synchronous compaction or grow TypeScript-style async compaction queueing.

## Milestones

### M1 - core execution usable

- `pi-ai`
- `pi-agent-runtime`
- `pi-session`
- `read`, `bash`, `edit`, `write`
- `print`, `json`

### M2 - interactive experience usable

- `pi-tui`
- `interactive`
- session tree
- settings
- model selection

### M3 - extensibility usable

- plugin SPI
- resource reload
- custom tools / commands / renderers

### M4 - packaging usable

- additional providers
- package sources
- installer / distribution

## Verification

Most recent routine validation commands:

```bash
.\gradlew.bat :pi-cli:test --no-daemon
.\gradlew.bat :pi-tui:test --no-daemon
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
npm.cmd run check
```

## Notes

- The handoff docs were normalized on 2026-03-18 after prior encoding corruption.
- The split handoff files under `pi-java/docs/handoff/` are now the authoritative continuation docs, and each phase handoff should stay split once a single markdown file would exceed 100 lines.
