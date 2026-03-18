# Handoff docs

Updated: 2026-03-18

This directory stores phase-specific handoff notes for `pi-java`.

The original long-form handoff notes were split into phase files and then normalized on 2026-03-18 after an encoding issue corrupted the markdown. These files are now the authoritative continuation docs.

## Files

- `archive-2026-03-10.md`
  - Normalized archive snapshot for work completed before the handoff split.
- `phase-6-pi-tui.md`
  - Completed `pi-tui` bring-up summary.
- `phase-7-pi-cli-sdk.md`
  - Completed `pi-cli` / `pi-sdk` functional bring-up summary.
- `phase-8-behavior-parity.md`
  - Active parity backlog and latest completed slices.

## Reading order

1. `pi-java/docs/tasks.md`
2. `pi-java/docs/handoff/phase-8-behavior-parity.md`
3. `pi-java/docs/handoff/phase-7-pi-cli-sdk.md`
4. `pi-java/docs/handoff/phase-6-pi-tui.md`
5. `pi-java/docs/handoff/archive-2026-03-10.md`

## Maintenance rule

- Keep `pi-java/docs/handoff.md` as a short index only.
- Put active continuation detail into the phase-specific file.
- Prefer concise incremental summaries over large duplicated logs.
