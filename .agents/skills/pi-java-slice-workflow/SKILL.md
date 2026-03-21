---
name: pi-java-slice-workflow
description: Continue ongoing pi-java implementation and parity work in this repository with the established step-by-step slice workflow. Use when asked to continue the next pi-java task, carry phase work forward without re-planning from scratch, or keep iterating in small validated increments on pi-java CLI, TUI, runtime, session, tools, extension, or AI modules. This workflow covers rebuilding context from pi-java/docs/tasks.md and pi-java/docs/handoff/, choosing one small slice, updating docs before commit, running npm.cmd run check plus the narrowest relevant pi-java Gradle tests, and committing only touched files when the user wants per-slice commits.
---

# Pi Java Slice Workflow

## Rebuild context

- Read `pi-java/docs/tasks.md`.
- Read the active continuation file under `pi-java/docs/handoff/`, usually the current phase file.
- Read every file you plan to modify in full before editing.
- If the Java change is parity work, read the matching TypeScript reference before changing behavior.
- Check `git status --short` before starting so you do not mix your slice with unrelated work.

## Choose one slice

- Take the smallest next slice from the active phase handoff.
- Prefer one behavior gap, one bug, or one small UI polish per slice.
- Start with a focused failing or missing test when practical.
- Keep the write set tight. Do not bundle unrelated cleanup into the same slice.
- If the current phase is nearly done, finish the last small gap before switching phases.

## Implement

- Keep Java changes aligned with existing repo style and current module boundaries.
- Preserve existing user-facing workflows unless the slice explicitly changes them.
- For selector or parity work, compare rendered copy, ordering, truncation, and key-hint behavior against the TypeScript source instead of guessing.
- When a shared helper can remove repeated drift, prefer that over one-off patches.

## Update project docs

- Update `pi-java/docs/tasks.md` after each completed slice.
- Append the slice result to the relevant phase file under `pi-java/docs/handoff/`.
- Keep the handoff incremental. If a handoff file becomes too long or noisy, continue in split phase files rather than growing a monolithic handoff.
- Record enough detail for the next slice: what changed, what was validated, and what the next smallest step is.

## Validate

- Always run `npm.cmd run check`.
- Run the narrowest relevant `pi-java` Gradle target for the slice:
  - CLI or interactive mode: `Set-Location 'pi-java'; .\gradlew.bat :pi-cli:test --no-daemon`
  - TUI widgets: `Set-Location 'pi-java'; .\gradlew.bat :pi-tui:test --no-daemon`
  - AI module: `Set-Location 'pi-java'; .\gradlew.bat :pi-ai:test --no-daemon`
  - Agent runtime: `Set-Location 'pi-java'; .\gradlew.bat :pi-agent-runtime:test --no-daemon`
  - Session: `Set-Location 'pi-java'; .\gradlew.bat :pi-session:test --no-daemon`
  - Tools: `Set-Location 'pi-java'; .\gradlew.bat :pi-tools:test --no-daemon`
  - Extension SPI: `Set-Location 'pi-java'; .\gradlew.bat :pi-extension-spi:test --no-daemon`
- If a slice crosses modules, run the smallest combined Gradle command that covers the touched area.

## Git discipline

- Do not commit unless the user explicitly asked for the established per-slice commit workflow.
- When commits are part of the workflow:
  - stage only the files touched in the slice
  - use one commit per slice
  - push only after validation is green
- Keep commit messages short and specific, usually `feat(pi-java): ...` or `fix(pi-java): ...`.

## Handoff response

- End each slice with:
  - what changed
  - validation commands that passed
  - doc files updated
  - commit hash if a commit was requested
  - worktree state
  - next smallest slice
