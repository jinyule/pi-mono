# Phase 8 - behavior parity

Updated: 2026-03-22
Status: complete

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
- `phase-8-behavior-parity-closeout.md`
  - final `/share` slice
  - phase-close rationale
  - deferred follow-on items

## Final summary

- Session selector, settings/theme surface, model selector, interactive footer, queue hints, and interactive command/status copy are aligned for the current Java CLI/TUI surface.
- `/share` is now wired in interactive mode, closing the last local command-surface parity gap that still fit phase 8.
- The remaining TypeScript-only gaps need new auth, model-scope, or async-compaction subsystems, so they move forward as phase 9 work instead of blocking phase 8 closeout.

## Deferred to phase 9

1. Add Java-side auth storage and interactive `/login` / `/logout`.
2. Add saved scoped-model selection and `/scoped-models`.
3. Decide whether Java should gain true async compaction queueing or keep synchronous compaction as an intentional product difference.
4. Start package-source and distribution work.

## Recommended next slice

Start phase 9 from one subsystem gap, not more phase-8 polish. Auth storage or scoped-model persistence are the cleanest first cuts.

## Validation used during phase 8

```bash
.\gradlew.bat :pi-cli:test --no-daemon
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
npm.cmd run check
```
