# Phase 8 - closeout

Updated: 2026-03-22
Status: complete

## Final slice

- `PiInteractiveMode` now wires `/share` and reports the same share URL / gist URL shape as the TypeScript interactive mode.
- `PiShareCommand` now:
  - checks `gh auth status` first
  - exports the current session to a temporary HTML file
  - creates a secret gist with `gh gist create --public=false`
  - returns the share viewer URL plus the raw gist URL
- the Java test suite now covers both the share command itself and the `/share` interactive slash-command path.

## Why phase 8 closes here

The remaining TypeScript-only items are no longer small behavior-polish gaps on top of an already matching Java surface. They require new subsystems or broader product-surface additions:

1. `/login` and `/logout` need Java-side OAuth provider discovery, token storage, and interactive auth flows.
2. True `/scoped-models` needs saved enabled-model scope, multi-select UI, and model-scope persistence.
3. TypeScript-style compaction queueing assumes asynchronous compaction with message submission while compaction is in flight; the current Java compactor is synchronous by design.

Those items are better tracked as phase 9 follow-on work instead of keeping phase 8 open indefinitely for non-local parity work.

## Validation

```bash
.\gradlew.bat :pi-cli:test --no-daemon
npm.cmd run check
```

## Next phase

Start phase 9 with the first real subsystem gap, not more phase-8 polish. The cleanest entry points are:

1. Java-side auth storage and `/login` / `/logout`
2. saved scoped-model selection and `/scoped-models`
3. package-source discovery / distribution work
