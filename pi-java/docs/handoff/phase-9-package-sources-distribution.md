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

## Next smallest slice

Wire package sources into actual Java-side discovery so settings-based package entries start affecting extension/skill/prompt/theme loading instead of being config-only.

## Validation

```bash
.\gradlew.bat :pi-session:test --no-daemon
npm.cmd run check
```
