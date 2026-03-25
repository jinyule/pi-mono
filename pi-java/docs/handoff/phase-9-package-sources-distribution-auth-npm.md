# Phase 9 - private npm auth

Updated: 2026-03-25
Status: active slice completed

## What landed

- Java package installs now support auth-backed private npm registries when the package registry is discoverable from `.npmrc`.
- This works for both:
  - project `npm:` installs under `.pi/npm`
  - global `npm:` installs
- The package manager now reads registry mapping from:
  - `~/.npmrc`
  - `<cwd>/.npmrc`

## Behavior

- When a package source is `npm:@scope/name`, Java now:
  - resolves the scoped registry from `.npmrc`
  - looks for a saved token in `auth.json`
  - writes a temporary npm user config with the registry line plus `_authToken`
  - runs `npm install` with that temporary config
- Saved tokens are matched by registry identity in this order:
  - full registry URL
  - `host/path`
  - bare host
- If no registry mapping or no saved token exists, Java falls back to normal npm behavior unchanged.

## Validation

- Added package-manager coverage for:
  - project `npm:` install with `.npmrc` scoped registry plus saved token
  - global `npm:` install using the same scoped registry mapping and saved token
- Re-ran:
  - `.\gradlew.bat :pi-session:test --no-daemon`
  - `npm.cmd run check`

## Continuity point

- Private git package auth and private npm package auth now both reuse `auth.json`.
- The remaining npm question is whether phase 9 should stop at `.npmrc`-mapped registry auth, or add fuller registry-login/config flows beyond saved host tokens.
- Installer packaging is still blocked by missing WiX on this machine, so auth/distribution follow-through remains the most useful phase-9 path until that toolchain is available.
