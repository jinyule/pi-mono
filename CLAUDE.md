# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Pi is a minimal terminal coding harness - an AI coding agent that runs in the terminal. This monorepo contains the core packages for building AI agents and managing LLM deployments.

## Key Commands

```bash
npm install          # Install all dependencies
npm run build        # Build all packages (required before check)
npm run check        # Lint, format, and type check (must pass before committing)
./test.sh            # Run tests (skips LLM-dependent tests without API keys)
./pi-test.sh         # Run pi from sources (must be run from repo root)
```

**Running specific tests** (from the package root, not repo root):
```bash
npx tsx ../../node_modules/vitest/dist/cli.js --run test/specific.test.ts
```

## Architecture

```
packages/
  ai/           # LLM provider abstraction (OpenAI, Anthropic, Google, etc.)
  agent/        # Agent loop, tool execution, message types, event streaming
  tui/          # Terminal UI with differential rendering
  coding-agent/ # CLI, interactive mode, sessions, extensions, skills
  mom/          # Slack bot wrapper for the coding agent
  web-ui/       # Web components for AI chat interfaces
  pods/         # vLLM deployment management on GPU pods
```

### Package Dependencies

The packages form a dependency hierarchy:
- `ai` is the foundation (LLM abstraction, no internal deps)
- `agent` depends on `ai` (agent loop, tools, events)
- `tui` is standalone (terminal UI components)
- `coding-agent` depends on `ai`, `agent`, `tui` (the main CLI product)
- `mom` and `web-ui` build on `coding-agent`/`agent`

## Development Rules

### Code Quality
- No `any` types unless absolutely necessary
- **NEVER use inline imports** - no `await import("./foo.js")`, no dynamic imports for types
- All keybindings must be configurable via `DEFAULT_EDITOR_KEYBINDINGS` or `DEFAULT_APP_KEYBINDINGS`
- Never hardcode key checks like `matchesKey(keyData, "ctrl+x")`

### Workflow
- After code changes: run `npm run check` and fix all errors, warnings, and infos
- Note: `npm run check` does not run tests
- NEVER run: `npm run dev`, `npm run build`, `npm test` (only run specific tests when instructed)
- Run tests from the package root, not the repo root
- NEVER commit unless user asks

### Git Rules for Parallel Agents
- **ONLY commit files YOU changed in THIS session**
- NEVER use `git add -A` or `git add .` - always use `git add <specific-file-paths>`
- Before committing, run `git status` to verify you're only staging your files
- Include `fixes #<number>` or `closes #<number>` in commit messages when applicable
- Forbidden operations: `git reset --hard`, `git checkout .`, `git clean -fd`, `git stash`, `git commit --no-verify`

### Style
- No emojis in commits, issues, PR comments, or code
- Keep answers short and concise
- Technical prose only - no fluff or cheerful filler text

## Adding a New LLM Provider

Requires changes across multiple files in `packages/ai`:
1. `src/types.ts` - Add API identifier to `Api` type union, create options interface, add to `ApiOptionsMap`
2. `src/providers/` - Create provider file with `stream<Provider>()` function
3. `src/stream.ts` - Add credential detection, case in `mapOptionsForApi()`, add to `streamFunctions` map
4. `scripts/generate-models.ts` - Add model fetching logic
5. `test/` - Add provider to all test files (stream.test.ts, tokens.test.ts, abort.test.ts, etc.)

For `packages/coding-agent`:
- `src/core/model-resolver.ts`: Add default model ID to `DEFAULT_MODELS`
- `src/cli/args.ts`: Add env var documentation
- `README.md`: Add provider setup instructions

## Releasing

**Lockstep versioning**: All packages always share the same version number.

```bash
npm run release:patch    # Bug fixes and new features
npm run release:minor    # API breaking changes
```

The script handles: version bump, CHANGELOG finalization, commit, tag, publish.

## Changelog Format

Location: `packages/*/CHANGELOG.md` (each package has its own)

Sections under `## [Unreleased]`:
- `### Breaking Changes` - API changes requiring migration
- `### Added` - New features
- `### Changed` - Changes to existing functionality
- `### Fixed` - Bug fixes
- `### Removed` - Removed features

**Attribution format**:
- Internal: `Fixed foo bar ([#123](link))`
- External: `Added feature X ([#456](link) by [@username](link))`

## Context Files

Pi loads `AGENTS.md` (or `CLAUDE.md`) at startup from:
- `~/.pi/agent/AGENTS.md` (global)
- Parent directories walking up from cwd
- Current directory

All matching files are concatenated. Use for project instructions and conventions.