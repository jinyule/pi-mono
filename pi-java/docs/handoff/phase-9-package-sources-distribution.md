# Phase 9 - package sources and distribution

Updated: 2026-03-25
Status: in progress

This file is the phase-9 index. Detailed handoff is split to keep each file under 100 lines.

## Goal

Add the missing Java-side packaging surface that phase 8 deliberately deferred:

- package source settings and discovery
- saved model-scope settings
- auth-backed interactive login/logout
- package installation and refresh
- eventual packaging and distribution outputs

## Read order

1. `pi-java/docs/handoff/phase-9-package-sources-distribution-foundation.md`
2. `pi-java/docs/handoff/phase-9-package-sources-distribution-package-manager.md`
3. `pi-java/docs/handoff/phase-9-package-sources-distribution-paths.md`
4. `pi-java/docs/handoff/phase-9-package-sources-distribution-config.md`
5. `pi-java/docs/handoff/phase-9-package-sources-distribution-resolution.md`
6. `pi-java/docs/handoff/phase-9-package-sources-distribution-auth-packages.md`
7. `pi-java/docs/handoff/phase-9-package-sources-distribution-release-bundle.md`
8. `pi-java/docs/handoff/phase-9-package-sources-distribution-artifacts.md`
9. `pi-java/docs/handoff/phase-9-package-sources-distribution-native-image.md`
10. `pi-java/docs/handoff/phase-9-package-sources-distribution-packaged-assets.md`

## Current continuity point

- Saved model scope is now wired end-to-end in Java.
- Java now has both the package-management backend and the basic CLI package commands.
- Java now honors `PI_CODING_AGENT_DIR` across default agent-dir consumers and honors `PI_PACKAGE_DIR` / workspace-first changelog resolution for distribution-facing resources.
- Java now has a `config` command for toggling package-provided resources from configured package sources.
- Java now auto-installs missing remote package sources during startup resource resolution and `config`, and project package entries now override duplicate global entries.
- Java now has its first runnable distribution outputs too: `:pi-cli:fatJar`, `:pi-cli:piDistDir`, and `:pi-cli:piDistZip`.
- Java now also builds a native `jpackage` app-image and zip via `:pi-cli:piAppImage` and `:pi-cli:piAppImageZip`.
- Those distribution outputs now also carry the packaged `README.md`, `docs/`, and `examples/`, and Java now resolves those packaged asset roots explicitly via `PiPackagePaths`.
- Saved auth tokens now also back private GitHub and GitLab package installs and updates, and those same credentials now flow through startup discovery and `config`.
- Java login selectors now surface GitHub and GitLab so users can save package-host tokens without typing provider ids from memory.
- Java now also assembles a release bundle directory with versioned artifacts, checksums, a manifest, and smoke-tested artifact verification.
- The next slice should move back to installer packaging once WiX is available locally, or stay on auth UX if the package-host login flow needs more than token entry.
