# Phase 9 - runnable distribution artifacts

Updated: 2026-03-25
Status: active slice completed

## What landed

- `pi-cli` now builds a self-contained runnable jar via `:pi-cli:fatJar`.
- `pi-cli` now stages a runnable distribution directory via `:pi-cli:piDistDir`.
- `pi-cli` now builds a shareable zip of that directory via `:pi-cli:piDistZip`.

## Distribution contents

- `lib/pi-java-cli-all.jar`
- `bin/pi-java`
- `bin/pi-java.bat`
- `CHANGELOG.md`

The launch scripts run the packaged fat jar directly, so the staged distribution can be invoked without reconstructing a Gradle classpath.

## Build wiring

- The fat jar now carries:
  - `Main-Class: dev.pi.cli.PiCliMain`
  - `Implementation-Version: <project version>`
- The distribution zip is assembled from the staged directory instead of re-copying loose outputs.
- The packaged changelog is copied from the upstream coding-agent changelog so Java keeps the existing `/changelog` behavior when run from the staged distribution.

## Validation

- Re-ran:
  - `.\gradlew.bat :pi-cli:fatJar :pi-cli:piDistDir :pi-cli:piDistZip --no-daemon`
  - `.\gradlew.bat :pi-cli:test --no-daemon`
  - `npm.cmd run check`
- Smoke-tested the produced artifacts:
  - `java -jar modules/pi-cli/build/libs/pi-java-cli-all.jar --version`
  - `modules/pi-cli/build/dist/bin/pi-java.bat --version`

## Continuity point

- Java now has a concrete runnable jar and zip-shaped distribution baseline.
- The next phase-9 slice should either:
  - add native installer/image packaging on top of this baseline, or
  - extend the distribution bundle with more packaged assets once the intended payload is clear.
