# Phase 9 - native app image packaging

Updated: 2026-03-25
Status: active slice completed

## What landed

- `pi-cli` now builds a native app image through `jpackage` via `:pi-cli:piAppImage`.
- `pi-cli` now also zips that app image via `:pi-cli:piAppImageZip`.

## Output shape

- App image root:
  - `build/jpackage/image/pi-java/`
- Native launcher:
  - `build/jpackage/image/pi-java/pi-java.exe`
- Zipped app image:
  - `build/distributions/pi-java-app-image-<version>.zip`

The app image is built from the same self-contained jar used by the plain distribution tasks, so Java now has both a lightweight jar-based distribution and a native-image-shaped distribution baseline.

## Build wiring

- Added `prepareJpackageInput` to stage the fat jar in a dedicated `jpackage` input directory.
- Added `piAppImage` to invoke `jpackage --type app-image` against that staged input.
- Added `piAppImageZip` to archive the generated app image for handoff or release storage.
- Normalized the app version passed to `jpackage` so snapshot suffixes do not break native image generation.

## Validation

- Re-ran:
  - `.\gradlew.bat :pi-cli:piAppImage :pi-cli:piAppImageZip --no-daemon`
  - `.\gradlew.bat :pi-cli:test --no-daemon`
  - `npm.cmd run check`
- Smoke-tested the generated native launcher:
  - `modules/pi-cli/build/jpackage/image/pi-java/pi-java.exe --version`

## Continuity point

- Java now has a working native app image in addition to the plain jar/zip distribution.
- The next phase-9 slice should either:
  - move from app image to installer packaging, or
  - decide which extra packaged assets belong in the distributable bundle.
