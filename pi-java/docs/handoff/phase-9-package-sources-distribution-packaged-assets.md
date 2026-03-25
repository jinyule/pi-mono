# Phase 9 - packaged docs and asset roots

Updated: 2026-03-25
Status: active slice completed

## What landed

- The staged distribution now includes:
  - `README.md`
  - `docs/`
  - `examples/`
- The native app image now includes the same packaged docs assets alongside the launcher.
- `PiPackagePaths` now resolves packaged `README`, `docs`, and `examples` roots in the same environment/workspace/code-source order already used for changelog lookup.

## Resolution behavior

- `PI_PACKAGE_DIR` still overrides packaged asset lookup first.
- If no override is set, Java now looks for packaged assets from:
  - the active workspace tree
  - then the packaged code-source tree
- Both direct packaged roots and repo-style `packages/coding-agent/` roots are supported.

## Validation

- Added/updated CLI tests for:
  - resolving packaged `README.md`
  - resolving packaged `docs/`
  - resolving packaged `examples/`
  - repo-root style package asset discovery
- Re-ran:
  - `.\gradlew.bat :pi-cli:test :pi-cli:piDistDir :pi-cli:piAppImage --no-daemon`
  - `npm.cmd run check`
- Smoke-tested:
  - `modules/pi-cli/build/dist/bin/pi-java.bat --version`
  - `modules/pi-cli/build/jpackage/image/pi-java/pi-java.exe --version`
- Verified the packaged assets exist in both output shapes:
  - `build/dist/README.md`, `build/dist/docs/`, `build/dist/examples/`
  - `build/jpackage/image/pi-java/README.md`, `build/jpackage/image/pi-java/docs/`, `build/jpackage/image/pi-java/examples/`

## Continuity point

- Java distribution outputs now carry runnable launchers plus the packaged docs payload they depend on.
- The next phase-9 slice should move into installer packaging unless auth-backed package management takes priority first.
