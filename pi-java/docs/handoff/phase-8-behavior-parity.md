# Phase 8 - behavior parity

Updated: 2026-03-22
Status: in progress

## Goal

Bring the Java CLI/TUI behavior closer to the TypeScript reference after the main feature set became usable in phase 7.

## Index

- `phase-8-behavior-parity-selector-settings.md`
  - session selector
  - shared selector hint styling
  - settings and themes
  - model selector
- `phase-8-behavior-parity-interactive.md`
  - interactive footer
  - app keybindings
  - interactive command/status copy
  - queue and compaction related parity

## Current summary

- Session selector, settings/theme surface, and model selector are down to minor edge-case polish.
- Interactive footer, queue hints, and command/status copy are substantially aligned.
- Pending queue / compaction queue parity is still the next broader behavior gap once the remaining small copy and selector drifts are closed.

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
