# Phase 9 - Windows installer preflight

Updated: 2026-03-25
Status: active slice completed

## What landed

- Added `:pi-cli:piInstallerExe` in `modules/pi-cli/build.gradle.kts`.
- The task now sits on top of the existing `:pi-cli:piAppImage` output instead of inventing a second packaging path.
- When WiX is available on Windows, it runs `jpackage --type exe` and copies the generated installer to a stable artifact name under `modules/pi-cli/build/jpackage/installer/`.
- When WiX is missing, it now exits cleanly with an explicit skip message instead of failing the build.

## Guardrails

- Installer generation now tracks the native app-image as an input, so installer work reruns when the packaged app changes.
- WiX availability is tracked as task input state too, so adding or removing the toolchain changes the task result instead of reusing stale task state.
- Skip paths now delete any stale installer output before returning, so `piReleaseBundle` cannot accidentally ship an old installer from an earlier machine or environment.
- `piReleaseBundle` already picks up the installer only when the stable installer artifact actually exists, so the release directory stays clean on machines without WiX.

## Validation

- Re-ran:
  - `.\gradlew.bat :pi-cli:piInstallerExe --no-daemon --rerun-tasks`
  - `.\gradlew.bat :pi-cli:piReleaseBundle --no-daemon`
  - `npm.cmd run check`
- Verified the forced installer task prints the WiX-missing skip message instead of failing.
- Verified `modules/pi-cli/build/release/` still contains only the jar, dist zip, app-image zip, changelog, checksum file, and manifest when WiX is unavailable.

## Continuity point

- This slice does not produce a real Windows installer on the current machine because WiX is still not installed.
- The next distribution slice should be actual installer output and packaging verification on a machine with WiX available.
