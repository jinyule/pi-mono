# Phase 9 - startup and config package resolution

Updated: 2026-03-24
Status: active slice completed

## What landed

- Java now auto-installs missing remote package sources when startup resource discovery needs them.
- The same missing-package install behavior now applies to `pi config`, so config no longer silently hides remote package sources just because they are not already on disk.
- Project package entries now win over duplicate global entries for the same package identity during Java package resolution.

## Scope of behavior

- The new auto-install path covers remote package sources:
  - `npm:`
  - `git:`
- Local paths still stay non-destructive:
  - existing paths resolve normally
  - missing local paths are skipped instead of being force-created
- Startup wiring only auto-installs when Java is not in `--offline` mode.

## Resolver changes

- `PackageSourceManager` now exposes a shared package-root resolver that can optionally install missing remote sources before returning the resolved root.
- `PackageSourceDiscovery` now uses the manager instead of duplicating remote-path logic.
- Discovery now dedupes configured packages by identity with project scope first, matching the TypeScript behavior more closely.
- `PiConfigResolver` now uses the same package-root resolution path and the same project-over-global dedupe rule.

## Validation

- Added package-session tests for:
  - auto-installing missing project npm sources during discovery
  - preferring project packages over duplicate global packages
- Added CLI-side config resolver coverage for:
  - auto-installing missing remote packages during `config`
  - preferring the project package entry when both scopes point at the same package
- Re-ran:
  - `.\gradlew.bat :pi-session:test :pi-cli:test --no-daemon`
  - `npm.cmd run check`

## Continuity point

- Java package resolution is now much closer to the TypeScript runtime.
- The next phase-9 slice should stop circling discovery and move to actual distribution/output work unless auth-backed package installation must land first.
