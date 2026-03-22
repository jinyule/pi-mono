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

## Next smallest slice

Carry package-source discovery past local paths: detect already-installed `npm:` / `git:` package roots and start feeding discovered skill/prompt/theme resources into the Java CLI surfaces that already exist.

## Validation

```bash
.\gradlew.bat :pi-session:test :pi-cli:test --no-daemon
npm.cmd run check
```
