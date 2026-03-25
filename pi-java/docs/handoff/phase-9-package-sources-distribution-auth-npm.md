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
- Java now also carries explicit `.npmrc` auth and npm config into the temporary install config instead of rebuilding a token-only file from scratch.

## Behavior

- When a package source is `npm:@scope/name`, Java now:
  - resolves the scoped registry from `.npmrc`
  - first carries through any explicit auth already present in `.npmrc`
  - otherwise falls back to a saved token in `auth.json`
  - writes a temporary npm user config that keeps the original `.npmrc` lines and only appends normalized registry/token lines when needed
  - runs `npm install` with that temporary config
- Saved tokens are matched by registry identity in this order:
  - full registry URL
  - `host/path`
  - bare host
- Known package-host logins are also bridged automatically:
  - `github` now backs `npm.pkg.github.com`
  - `gitlab` now backs GitLab npm registry URLs on `gitlab.com`
- Explicit `.npmrc` auth beats saved host tokens when both exist.
- Unscoped installs now also work with default-registry auth from `.npmrc` such as `registry=...` plus `_authToken=...`.
- Extra npm settings already present in `.npmrc` (for example `strict-ssl=false`) are preserved in the temporary config instead of being dropped.
- If no registry mapping, `.npmrc` auth, or saved token exists, Java falls back to normal npm behavior unchanged.

## Validation

- Added package-manager coverage for:
  - project `npm:` install with `.npmrc` scoped registry plus saved token
  - global `npm:` install using the same scoped registry mapping and saved token
  - project `npm:` install using a scoped-registry token already present in `.npmrc`
  - global `npm:` install using workspace `.npmrc` auth directly
  - precedence where project `.npmrc` token overrides saved GitHub token
  - unscoped `npm:` install using default-registry auth from `.npmrc`
- Re-ran:
  - `.\gradlew.bat :pi-session:test --no-daemon`
  - `npm.cmd run check`

## Continuity point

- Private git package auth and private npm package auth now both reuse `auth.json`, but Java no longer requires `auth.json` when the user already has working registry auth in `.npmrc`.
- The remaining npm question is whether phase 9 should stop at `.npmrc`-backed registry auth plus saved host tokens, or add fuller browser-driven registry-login/config flows beyond that.
- Installer packaging is still blocked by missing WiX on this machine, so auth/distribution follow-through remains the most useful phase-9 path until that toolchain is available.
