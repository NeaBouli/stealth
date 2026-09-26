# SC-SEC-RUNTIME-ROTATION-MAP-20260926 — TURN/Admin runtime and log map

Worker: Kimi
Mode: production read-only security preparation

Read the current architecture map, SecureCall signaling/TURN configuration, deployment handoffs,
and `.fleet/reports/SC-SEC-GITLEAKS-TRIAGE-20260926.md`. Using only the restricted Fleet SSH access,
identify the authoritative production host/services consuming `TURN_SECRET` and `ADMIN_API_KEY`,
their reload/restart requirements, health checks, rollback order and relevant redacted logs.

Inspect logs read-only for evidence of misuse since the exposure dates. Never read, print, copy or
compare secret values. Do not write to servers, providers, GitHub or code. State exactly which
mutation steps Codex can perform with current permissions and which require Gio/operator access.

Write only `.fleet/reports/SC-SEC-RUNTIME-ROTATION-MAP-20260926.md` in standard format.
