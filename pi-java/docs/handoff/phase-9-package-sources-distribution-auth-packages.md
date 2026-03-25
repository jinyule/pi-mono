# Phase 9 - auth-backed package installs

Updated: 2026-03-25
Status: active slice completed

## What landed

- Java package management now uses saved auth tokens for private git package hosts during install and update.
- The current host mapping is intentionally narrow:
  - `github.com` -> saved `github` login
  - `gitlab.com` -> saved `gitlab` login
  - exact host-name matches in `auth.json` also work
- When a saved token is present, Java now:
  - clones through an HTTPS remote even if the configured package source used SSH shorthand
  - injects per-command Git auth so the token is available during clone and fetch
  - rewrites existing repo `origin` URLs to the authenticated HTTPS remote before update when needed

## Where it now applies

- `pi install git:...`
- `pi update`
- startup package-source discovery when Java auto-installs missing remote packages
- `pi config` when it resolves package-provided extensions, skills, prompts, and themes

## CLI and session surface

- `PiCliModule` now passes the shared `AuthStorage` into package commands, startup package discovery, and `config`, so the same saved login state is reused everywhere.
- Java interactive auth selection now always includes `GitHub` and `GitLab` alongside model providers, so users can save package-host tokens from `/login` without remembering hidden provider ids.

## Validation

- Added session tests for:
  - private GitHub package install using saved auth
  - private GitHub package update using saved auth
  - startup discovery auto-install using saved auth
- Added CLI tests for:
  - `config` resolving missing private git packages with saved auth
  - auth selection surfacing `github` and `gitlab`
- Re-ran:
  - `.\gradlew.bat :pi-session:test :pi-cli:test --no-daemon`
  - `npm.cmd run check`

## Continuity point

- Private git package auth is now wired through the main Java package flows.
- Installer packaging is still blocked on this machine by a missing WiX toolchain for `jpackage --type exe`.
- The next phase-9 slice should either:
  - resume installer packaging on a machine with WiX available, or
  - improve auth UX beyond token-entry login if phase 9 needs a fuller login flow.
