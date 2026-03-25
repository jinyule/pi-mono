# Phase 9 - CLI auth commands

Updated: 2026-03-25
Status: active slice completed

## What landed

- Java CLI now supports:
  - `pi login [provider] [token]`
  - `pi logout [provider]`
  - `pi auth list`
- These commands run before session startup, so they do not create an interactive session just to save or remove credentials.

## Behavior

- `pi login`:
  - accepts `provider` and `token` directly for scripting
  - prompts for provider and token when either is omitted
  - still allows direct provider ids for custom providers
- `pi logout`:
  - removes a named provider directly
  - prompts when multiple saved providers exist and no provider was passed
  - shows a helpful empty-state message when nothing is saved
- `pi auth list`:
  - shows saved providers using friendly names
  - reports `No saved credentials.` when empty

## Shared provider surface

- The Java auth provider surface is now shared across:
  - interactive `/login` and `/logout`
  - command-line `login`, `logout`, and `auth list`
- GitHub and GitLab stay visible in that shared provider list so package-host auth is available even before model providers are configured.

## Validation

- Added CLI auth command tests for:
  - direct login and saved-credentials listing
  - prompted login when provider/token are omitted
  - prompted logout when multiple saved providers exist
  - module-level interception so auth commands do not create interactive sessions
- Re-ran:
  - `.\gradlew.bat :pi-cli:test --no-daemon`
  - `npm.cmd run check`
- Smoke-tested the real CLI through the packaged fat jar:
  - `login github ...`
  - `auth list`
  - `logout github`
  - `auth list`

## Continuity point

- Java auth management now works both inside and outside interactive mode.
- The remaining auth question is no longer basic command coverage; it is whether phase 9 should stop at token-entry auth or grow into fuller browser-driven login flows.
- If installer work stays blocked on WiX, that auth UX question is now the most useful next phase-9 slice.
