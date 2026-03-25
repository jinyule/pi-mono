# pi-java handoff

Updated: 2026-03-25

This file is the entry point only. Detailed handoff is split under `pi-java/docs/handoff/`.

## Current state

- Phases 0 through 6 are complete.
- Phase 7 (`pi-cli` / `pi-sdk`) is functionally complete.
- Phase 8 (behavior parity) is complete.
- Phase 9 (package sources / distribution) is active.

The current implementation hotspot is phase 9 release follow-through now that package auth, auth commands, host-CLI auth import, private npm auth, package commands, package config, release bundles, path overrides, and basic distribution outputs all exist.

## Read order

1. `pi-java/docs/tasks.md`
2. `pi-java/docs/handoff/phase-9-package-sources-distribution.md`
3. `pi-java/docs/handoff/phase-9-package-sources-distribution-config.md`
4. `pi-java/docs/handoff/phase-9-package-sources-distribution-resolution.md`
5. `pi-java/docs/handoff/phase-9-package-sources-distribution-auth-packages.md`
6. `pi-java/docs/handoff/phase-9-package-sources-distribution-auth-cli.md`
7. `pi-java/docs/handoff/phase-9-package-sources-distribution-auth-npm.md`
8. `pi-java/docs/handoff/phase-9-package-sources-distribution-release-bundle.md`
9. `pi-java/docs/handoff/phase-9-package-sources-distribution-artifacts.md`
10. `pi-java/docs/handoff/phase-9-package-sources-distribution-native-image.md`
11. `pi-java/docs/handoff/phase-9-package-sources-distribution-packaged-assets.md`
12. `pi-java/docs/handoff/phase-9-package-sources-distribution-paths.md`
13. `pi-java/docs/handoff/phase-9-package-sources-distribution-package-manager.md`
14. `pi-java/docs/handoff/phase-9-package-sources-distribution-foundation.md`
15. `pi-java/docs/handoff/phase-8-behavior-parity.md`
16. `pi-java/docs/handoff/phase-7-pi-cli-sdk.md`
17. `pi-java/docs/handoff/phase-6-pi-tui.md`
18. `pi-java/docs/handoff/archive-2026-03-10.md`
19. `pi-java/docs/handoff/README.md`

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
