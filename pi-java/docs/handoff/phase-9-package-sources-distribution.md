# Phase 9 - package sources and distribution

Updated: 2026-03-22
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

## Current continuity point

- Saved model scope is now wired end-to-end in Java.
- Java now has both the package-management backend and the basic CLI package commands.
- The next slice should move from package basics into distribution/output work or auth-backed package management.
