# Phase 9 - package manager foundation

Updated: 2026-03-22
Status: in progress

## Implemented slice

- Added `dev.pi.session.PackageSourceManager` as Java-side package-management plumbing.
- The new manager now supports:
  - adding and removing package sources in global or project settings
  - resolving installed paths for local, `npm:`, and `git:` sources
  - installing project/global `npm:` sources
  - cloning, updating, and removing managed `git:` sources
  - skipping pinned `npm:` / `git:` sources during update
- Added focused tests for:
  - project `npm:` install plus settings persistence
  - managed `git:` update and removal
  - local-path normalization when saving settings
  - pinned-source update skips

## Next smallest slice

Wire `PackageSourceManager` into Java CLI package commands so Java can install, remove, update, and list configured packages from the command line instead of only providing the backend.

## Validation

```bash
.\gradlew.bat :pi-session:test --no-daemon
npm.cmd run check
```
