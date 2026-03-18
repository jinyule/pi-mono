# Phase 6 - `pi-tui`

Updated: 2026-03-18
Status: complete

## Scope

Phase 6 brought the Java terminal UI stack from zero to a usable baseline that the CLI can build on.

## Completed areas

### Core terminal stack

- `Terminal`, `Component`, `Focusable`, `Overlay`.
- `ProcessTerminal` and raw terminal lifecycle support.
- `TerminalInputBuffer`.
- `SynchronizedOutput`.

### Rendering

- `DiffRenderer`.
- Overlay composition and cursor placement.
- Differential rendering behavior.
- Resize-aware rendering path.

### Basic components

- `Container`
- `Text`
- `TruncatedText`
- `TerminalText` helpers

### Editing and input

- `Input`
- `Editor`
- `EditorKeybindings`
- `KeyMatcher`
- `KillRing`
- `UndoStack`

### Rich content

- `Markdown`
- `Loader`
- `SelectList`
- `SettingsList`
- `Image`
- `VirtualTerminal`

## Key test coverage

- Input and editor behavior.
- Markdown rendering.
- Loader rendering.
- Selector behavior.
- Image capability and fallback behavior.
- `VirtualTerminal` golden tests.

## Known limits

- `VirtualTerminal` is a lightweight ANSI emulator intended for current `pi-java` golden testing, not a full xterm-equivalent implementation.
- Further selector and overlay behavior polish is tracked in phase 8, not here.

## Validation used

```bash
.\gradlew.bat :pi-tui:test --no-daemon
npm.cmd run check
```

## Re-entry guidance

Only reopen phase 6 work if phase 8 parity exposes a missing TUI primitive or a rendering bug in shared components.
