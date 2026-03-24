# Phase 9 - package sources and distribution

Updated: 2026-03-24
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

## Current continuity point

- Saved model scope is now wired end-to-end in Java.
- Java now has both the package-management backend and the basic CLI package commands.
- Java now honors `PI_CODING_AGENT_DIR` across default agent-dir consumers and honors `PI_PACKAGE_DIR` / workspace-first changelog resolution for distribution-facing resources.
- Java now has a `config` command for toggling package-provided resources from configured package sources.
- The next slice should move from config/path follow-through into actual distribution/output work or auth-backed package management.
