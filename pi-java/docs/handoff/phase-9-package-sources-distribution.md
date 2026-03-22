# Phase 9 - package sources and distribution

Updated: 2026-03-22
Status: in progress

## Goal

Add the missing Java-side packaging surface that phase 8 deliberately deferred:

- package source settings and discovery
- saved model-scope settings
- auth-backed interactive login/logout
- eventual packaging and distribution outputs

## Current summary

- Phase 9 started from the settings/config layer instead of jumping straight into installers.
- `pi-session` now has a typed `PackageSource` model plus typed settings access for:
  - `packages`
  - `enabledModels`
- Global/project settings files can now round-trip package source entries in both supported shapes:
  - plain string source
  - object source with `extensions` / `skills` / `prompts` / `themes` filters
- This also lays the config foundation for later `/scoped-models` work because `enabledModels` is now a first-class setting instead of raw ad hoc JSON.

## Implemented slice

- Added `dev.pi.session.PackageSource`
- Added `Settings.getPackageSources(...)`
- Added `Settings.withPackageSources(...)`
- Added `Settings.withStringList(...)`
- Added typed `SettingsManager` methods:
  - `getPackages()`
  - `setPackages(...)`
  - `setProjectPackages(...)`
  - `getEnabledModels()`
  - `setEnabledModels(...)`
- Added tests for typed access and persisted JSON shape
- Added `dev.pi.session.PackageSourceDiscovery` for scope-aware local package-source resolution.
- Added `SettingsManager.getGlobalPackages()` and `SettingsManager.getProjectPackages()` so Java can discover both scopes instead of only the merged effective array.
- Wired local package-source themes into CLI startup and theme reload.
- Wired local package-source extensions into CLI startup extension discovery.
- Added focused tests for:
  - scope-relative package-source path resolution
  - project package-source themes showing up in `settings`
  - project package-source extensions showing up in startup resources
- Extended `PackageSourceDiscovery` so remote package sources now resolve already-installed package roots too:
  - project `npm:` packages under `<cwd>/.pi/npm/node_modules/...`
  - global `npm:` packages under the detected global `npm root -g`
  - project/global `git:` packages under the same `.../.pi/git/<host>/<path>` layout as the TypeScript app
- Added tests for installed project/global npm package roots and installed git package roots.
- Added CLI tests proving installed project `npm:` theme packages and installed project `git:` extension packages now affect startup behavior.
- Extended Java-side startup resources so discovered `skills` and `prompts` now flow through to the CLI resource surfaces instead of stopping at raw path discovery.
- Extended Java-side extension resource discovery wiring so extension-declared `skills`, `prompts`, and `themes` now affect startup resources and the available theme list.
- Added CLI-focused tests covering:
  - project package-source `skills` / `prompts` surfacing in startup resources
  - extension-declared `skills` / `prompts` surfacing in startup resources
  - extension-declared themes surfacing in the settings theme list
- Added session/application close wiring so extension runtimes are released after use instead of leaving JAR handles open.
- Added `dev.pi.session.AuthStorage` as a real `auth.json` store for Java:
  - persists `api_key` credentials
  - reads existing `oauth` credentials and uses their `access` token
  - uses the same `~/.pi/agent/auth.json` location by default
- Wired auth storage into Java session creation so saved credentials now flow into request auth automatically.
- Added Java interactive `/login` and `/logout` support:
  - `/login <provider> <token>` saves a provider token directly
  - `/logout <provider>` removes saved credentials directly
  - bare `/login` and `/logout` now open provider-selection overlays
  - bare `/login <provider>` now opens a hidden-input prompt for the token
- Added focused tests for:
  - auth storage persistence and OAuth-entry loading
  - session-level credential usage during requests
  - module wiring from auth storage into default sessions
  - interactive slash-command behavior for login/logout
- Added masked-input support to the shared TUI input so secrets are not echoed in plain text during the login prompt.

## Next smallest slice

Wire `enabledModels` into saved scoped-model selection and `/scoped-models`, so Java gets the same persisted model-scope surface that phase 8 deferred.

## Validation

```bash
.\gradlew.bat :pi-session:test :pi-tui:test :pi-cli:test --no-daemon
.\gradlew.bat :pi-cli:test --no-daemon
.\gradlew.bat :pi-session:test :pi-cli:test --no-daemon
npm.cmd run check
```
