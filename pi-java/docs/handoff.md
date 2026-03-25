# pi-java handoff

Updated: 2026-03-24

This file is the entry point only. Detailed handoff is split under `pi-java/docs/handoff/`.

## Current state

- Phases 0 through 6 are complete.
- Phase 7 (`pi-cli` / `pi-sdk`) is functionally complete.
- Phase 8 (behavior parity) is complete.
- Phase 9 (package sources / distribution) is active.

The current implementation hotspot is phase 9 distribution follow-through now that package commands, package config, and path overrides exist.

## Read order

1. `pi-java/docs/tasks.md`
2. `pi-java/docs/handoff/phase-9-package-sources-distribution.md`
3. `pi-java/docs/handoff/phase-9-package-sources-distribution-config.md`
4. `pi-java/docs/handoff/phase-9-package-sources-distribution-resolution.md`
5. `pi-java/docs/handoff/phase-9-package-sources-distribution-artifacts.md`
6. `pi-java/docs/handoff/phase-9-package-sources-distribution-native-image.md`
7. `pi-java/docs/handoff/phase-9-package-sources-distribution-paths.md`
8. `pi-java/docs/handoff/phase-9-package-sources-distribution-package-manager.md`
9. `pi-java/docs/handoff/phase-9-package-sources-distribution-foundation.md`
10. `pi-java/docs/handoff/phase-8-behavior-parity.md`
11. `pi-java/docs/handoff/phase-7-pi-cli-sdk.md`
12. `pi-java/docs/handoff/phase-6-pi-tui.md`
13. `pi-java/docs/handoff/archive-2026-03-10.md`
14. `pi-java/docs/handoff/README.md`

## Verification

Most recently repeated validation commands:

```bash
.\gradlew.bat :pi-session:test :pi-tui:test :pi-cli:test --no-daemon
.\gradlew.bat :pi-session:test :pi-cli:test --no-daemon
.\gradlew.bat :pi-cli:test --no-daemon
.\gradlew.bat :pi-tui:test --no-daemon
.\gradlew.bat :pi-tui:test :pi-cli:test --no-daemon
npm.cmd run check
```

## Notes

- The handoff set was normalized on 2026-03-18 after an encoding issue made the previous markdown unreadable.
- The old slice-by-slice detail is still recoverable from `git log`, but the handoff docs are now maintained as clean summaries.
