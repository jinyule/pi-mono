# Phase 9 - release bundle assembly

Updated: 2026-03-25
Status: active slice completed

## What landed

- `pi-cli` now assembles a release bundle directory through `:pi-cli:piReleaseBundle`.
- That bundle now contains:
  - a versioned runnable jar
  - the versioned staged distribution zip
  - the versioned native app-image zip
  - `CHANGELOG.md`
  - `SHA256SUMS.txt`
  - `release-manifest.json`

## Validation wiring

- Added `:pi-cli:piSmokeTestFatJar`
- Added `:pi-cli:piSmokeTestDistLauncher`
- Added `:pi-cli:piSmokeTestNativeLauncher`
- Added aggregate `:pi-cli:piSmokeTestArtifacts`

`piReleaseBundle` depends on those smoke tests, so the release directory is only assembled after the current runnable outputs have proven they can answer `--version`.

## Output shape

- Release directory:
  - `modules/pi-cli/build/release/`
- Verified artifact names:
  - `pi-java-cli-0.1.0-SNAPSHOT.jar`
  - `pi-java-cli-0.1.0-SNAPSHOT.zip`
  - `pi-java-app-image-0.1.0-SNAPSHOT.zip`

The checksum file uses standard `sha256  filename` lines, and the manifest records name, size, and hash for each shipped artifact.

## Validation

- Re-ran:
  - `.\gradlew.bat :pi-cli:piReleaseBundle --no-daemon`
  - `npm.cmd run check`
- Verified the generated release directory contains:
  - the three versioned artifacts
  - `CHANGELOG.md`
  - `SHA256SUMS.txt`
  - `release-manifest.json`

## Continuity point

- Java release outputs are now bundled into one handoff-friendly directory instead of being scattered across `libs/`, `dist/`, and `distributions/`.
- Windows installer packaging is still the main remaining distribution gap, but it remains blocked on this machine by the missing WiX toolchain.
- If installer work stays blocked, the next useful phase-9 slice should move to auth UX rather than slicing distribution more finely.
