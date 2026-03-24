# Phase 9 - package config selector

Updated: 2026-03-24
Status: active slice completed

## What landed

- Java CLI now has a top-level `config` command.
- `config` opens a package-resource selector instead of falling through to normal interactive startup.
- The selector lists package-provided:
  - extensions
  - skills
  - prompts
  - themes
- The selector works across both user and project package settings.
- Toggling an item updates the matching package entry in settings immediately.

## Resolver behavior

- Resolution is backed by the real Java package-source settings and install-path rules.
- Local package sources work.
- Already-installed `npm:` and `git:` package sources work through the same install roots used elsewhere in Java.
- Conventional directories are supported:
  - `extensions/`
  - `skills/`
  - `prompts/`
  - `themes/`
- `package.json` `pi.*` resource declarations are also supported.
- Manifest-only glob filters now affect enabled state correctly instead of being ignored.

## Selector behavior

- Search filters the flattened resource list without dropping section context.
- `Space` and `Enter` toggle the selected item.
- `Esc` closes the selector.
- The selector renders directly in the terminal and persists changes as they happen.

## Validation

- Added resolver coverage in `PiConfigResolverTest`.
- Added module-level command coverage in `PiCliModuleConfigCommandTest`.
- Added help-text coverage in `PiCliParserTest`.
- Re-ran:
  - `.\gradlew.bat :pi-cli:test --no-daemon`
  - `npm.cmd run check`

## Continuity point

- The package config surface now exists, so the next phase-9 slice should stop adding more selector polish and move to real distribution/output work or auth-backed package management flows.
