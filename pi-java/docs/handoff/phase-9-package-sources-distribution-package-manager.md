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
- Wired the new backend into Java CLI package commands:
  - `pi install <source> [--local]`
  - `pi remove <source> [--local]`
  - `pi update [source]`
  - `pi list`
- Added focused CLI tests for:
  - install command calling the backend and saving settings
  - list output for user/project packages
  - invalid `--local` handling on unsupported commands
  - module-level interception so package commands do not create interactive sessions
- Updated `--help` text so these package commands now show up in Java CLI help too.

## Next smallest slice

Start phase-9 distribution/output work, or add auth-backed package management on top of the new command surface if package install flows need credentials before packaging can move forward.

## Validation

```bash
.\gradlew.bat :pi-session:test --no-daemon
.\\gradlew.bat :pi-session:test :pi-cli:test --no-daemon
npm.cmd run check
```
