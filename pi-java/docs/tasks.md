# pi-java implementation tasks

Updated: 2026-03-18

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
- Phase 8 (behavior parity): in progress.
  - Session selector parity is substantially advanced.
  - Settings/theme parity is substantially advanced.
  - Footer parity is substantially advanced.
  - Interactive app key-hint parity continues to advance, including multi-binding rendering and shared key-hint styling in both startup header and queued-message hints.
  - Model selector parity is active and is now down to the last minor edge-case polish after compact-row truncation, multi-key scope-hint alignment, TypeScript-style tokenized fuzzy search alignment, selection-retention parity while searching, top-section width handling at narrow terminal sizes, and current-checkmark preservation in truncated rows.
- Phase 9 (package sources / distribution): not started.

## Current next slices

1. Finish the remaining `PiModelSelector` edge-case polish against the TypeScript behavior.
2. Add pending queue / compaction queue parity.
3. Finish settings/theme parity that still depends on wider CLI surface adoption and package-source discovery.
4. Finish the remaining footer parity edge cases.
5. Start phase 9 package-source and distribution work.

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
- The split handoff files under `pi-java/docs/handoff/` are now the authoritative continuation docs.
