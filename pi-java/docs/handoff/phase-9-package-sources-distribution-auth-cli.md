# Phase 9 - CLI auth commands

Updated: 2026-03-25
Status: active slice completed

## What landed

- Java CLI now supports:
  - `pi login [provider] [token]`
  - `pi logout [provider]`
  - `pi auth list`
- These commands run before session startup, so they do not create an interactive session just to save or remove credentials.
- Package-host login now also reuses local host CLI sessions where possible, so `pi login github` and `pi login gitlab` no longer require pasting a token when `gh` or `glab` are already logged in.

## Behavior

- `pi login`:
  - accepts `provider` and `token` directly for scripting
  - imports from `gh auth token` for `github` and `glab auth token` for `gitlab` when the token is omitted
  - prompts for provider and token only when direct args and host-CLI import are both unavailable
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
- The host-CLI import path is also shared across:
  - `pi login github` / `pi login gitlab`
  - interactive `/login github`, `/login gitlab`, and provider selection from `/login`

## Validation

- Added CLI auth command tests for:
  - direct login and saved-credentials listing
  - host-CLI import for GitHub without a pasted token
  - prompted login when provider/token are omitted
  - prompted logout when multiple saved providers exist
  - module-level interception so auth commands do not create interactive sessions
- Added interactive auth coverage for `/login github` importing from host CLI without opening the token prompt.
- Added host-CLI importer coverage for both `gh auth token` and `glab auth token` command routing.
- Re-ran:
  - `.\gradlew.bat :pi-cli:test --no-daemon`
  - `npm.cmd run check`
- Smoke-tested the real CLI through the packaged fat jar:
  - `login github`
  - verified `auth.json` was created under a temporary `PI_CODING_AGENT_DIR`
  - verified the imported GitHub entry was saved as an API-key credential without echoing the real token

## Continuity point

- Java auth management now works both inside and outside interactive mode, and package-host login can now reuse existing `gh` / `glab` sessions instead of always forcing manual token entry.
- The remaining auth question is no longer basic command coverage; it is whether phase 9 should stop at CLI-import/token-entry auth or grow into fuller browser-driven login flows.
- If installer work stays blocked on WiX, that auth UX question is now the most useful next phase-9 slice.
