# Phase 9 - distribution path overrides

Updated: 2026-03-23
Status: in progress

## Implemented slice

- Added a shared Java-side path helper for distribution defaults:
  - agent-dir resolution now respects `PI_CODING_AGENT_DIR`
  - `~` expansion is handled consistently for override paths
  - session and debug-log defaults now come from the same source instead of repeated hardcoded paths
- Rewired Java-side default agent-dir consumers to use that shared resolution:
  - settings storage
  - auth storage
  - instruction loading
  - session storage defaults
  - package-source discovery and package management
  - theme loading
  - keybinding loading
  - session resolution
  - interactive debug-log writes
- Added a Java-side package-path helper for distribution-facing assets:
  - packaged changelog lookup now respects `PI_PACKAGE_DIR`
  - changelog lookup now prefers the active workspace tree before falling back to the installed package tree
  - code-source fallback still works when running outside a source checkout
- Updated CLI help so the new override environment variables are discoverable from `--help`.
- Added focused tests for:
  - `PI_CODING_AGENT_DIR` path expansion and session/debug-log path derivation
  - `PI_PACKAGE_DIR` changelog override
  - code-source fallback
  - workspace-first changelog resolution when both workspace and installed package trees exist
- Fixed a regression caught by validation where `/changelog` started reading the installed repository changelog even when the active workspace had its own coding-agent changelog.

## Why this slice mattered

- Phase 9 had started package and distribution work, but Java still had several hidden hardcoded `~/.pi/agent` assumptions.
- That would have broken forked installs, custom agent directories, and distribution packaging that relocates bundled assets.
- This slice closes the path-resolution gap before moving deeper into installers or packaged outputs.

## Next smallest slice

Move from path/discovery correctness into actual distribution outputs, unless auth-backed package installation needs to land first.

## Validation

```bash
.\gradlew.bat :pi-session:test :pi-cli:test --no-daemon
npm.cmd run check
```
