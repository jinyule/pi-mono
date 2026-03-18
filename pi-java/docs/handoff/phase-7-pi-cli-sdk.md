# Phase 7 - `pi-cli` and `pi-sdk`

Updated: 2026-03-18
Status: functionally complete

## Scope

Phase 7 brought the Java CLI and SDK from skeleton state to usable end-to-end feature coverage.

## Completed CLI modes

- `interactive`
- `print`
- `json`
- `rpc`

## Completed SDK surface

- `PiSdk`
- `PiSdkSession`
- shared session bootstrap helpers
- instruction-aware prompt composition

## Completed commands and flows

- `list-models`
- session resolution for new and resume flows
- `--resume` picker baseline
- `--export`
- `copy`
- `tree`
- `fork`
- `compact`
- `reload`

## Completed integration work

- shared startup/session bootstrap between CLI and SDK
- real `main()` and module wiring
- instruction-resource-aware system prompt composition
- `@file` / initial prompt wiring
- `help` / `version` output
- interactive exit semantics

## Important implementation notes

- Phase 7 is considered feature-complete enough to move on.
- Remaining work is mostly behavior and UI parity rather than missing top-level commands.
- Session selector, model selector, settings selector, footer behavior, and custom theme polish are tracked in phase 8.

## Validation used

```bash
.\gradlew.bat :pi-sdk:test :pi-cli:test --no-daemon
.\gradlew.bat :pi-cli:test --no-daemon
npm.cmd run check
```

## Re-entry guidance

Reopen phase 7 only for:

- a missing functional CLI/SDK path,
- a broken command contract,
- or a bootstrap issue that is not just phase 8 parity polish.
