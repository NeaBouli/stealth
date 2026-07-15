# Stealth Agent Instructions

These rules extend `/Users/gio/AGENTS.md` for work inside this repository.

## Coordination

- Read `/Users/gio/BRIDGE.md`, this file, `BRIDGE.md`, and relevant files under `docs/agent-bridge/` before material work.
- Keep `BRIDGE.md` append-only and current after meaningful checks, fixes, releases, or decisions.
- Do not overwrite or clean unrelated dirty files, especially existing `docs/agent-bridge/*` changes.
- Never write secrets, full API keys, passwords, private keys, or unredacted production credentials to code, logs, commits, or bridge files.

## Agent Role Split

The main agent remains responsible for architecture, security, product decisions, release judgment, final verification, commits, pushes, and bridge updates.

Use project subagents when they reduce noise or isolate small work:

- `spark_worker`: small scoped patches, UI corrections, targeted searches, and focused local checks.
- `terra_analyst`: read-heavy repository exploration, log triage, larger code summaries, and test-output analysis.

Delegate only independent or clearly bounded work. Wait for subagent results, review their output yourself, and run the final integration checks in the main thread.

Do not delegate cross-repo release decisions, security-sensitive changes, pricing/tax logic, or unclear requirements without first narrowing the scope.
