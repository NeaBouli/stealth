# SC-SEC-RAILWAY-ROTATION-MAP-20260926 — Railway token ownership and rotation map

Worker: Claude Code
Mode: provider read-only security preparation

Read the current architecture/deployment handoffs, workflows and
`.fleet/reports/SC-SEC-GITLEAKS-TRIAGE-20260926.md`. Identify every current consumer and storage
location of the affected Railway token by metadata/name only, including local CLI, GitHub Actions,
VLABS and deployment tooling. Determine whether it is a personal/account token or project token,
the minimum replacement scope, revocation order, validation command and rollback boundary.

Inspect available Railway audit/deploy metadata read-only for misuse indicators. Never read, print,
copy or compare token values. Do not create/revoke tokens, change provider state, deploy or edit
code. State exactly which browser/manual step requires Gio and what Codex can validate afterward.

Write only `.fleet/reports/SC-SEC-RAILWAY-ROTATION-MAP-20260926.md` in standard format.
