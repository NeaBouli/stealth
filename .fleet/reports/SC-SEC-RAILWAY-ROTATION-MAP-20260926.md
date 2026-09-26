id: SC-SEC-RAILWAY-ROTATION-MAP-20260926
status: partial
worker: claude
branch: agent/claude/SC-SEC-RAILWAY-ROTATION-MAP-20260926
summary: Read-only map, no code/provider change, no token value read/printed/compared. No MAP.md; lane: ops tooling
 tools/monitor-rollout.sh -> backboard.railway.app/graphql/v2 (`Authorization: Bearer $RAILWAY_TOKEN`) -> Railway
 workspace "True Republic's Projects" (personal), project disciplined-flexibility/production (signaling).
 TYPE: Bearer header + worked on 2026-04-17 (rollout log shows deploy IDs) => account or workspace token, NOT a
 project token (those need Project-Access-Token). Account vs workspace scope is only visible in dashboard (Gio).
 CONSUMERS/STORAGE (names only): (1) tools/monitor-rollout.sh — env-only since 076c629, v1.0.22 monitor, obsolete;
 (2) ~/Desktop/repos/inferno/.env (600) has RAILWAY_TOKEN+PROJECT/SERVICE/ENVIRONMENT_ID = exact set the script
 expects -> likely the "private operator env"; Inferno context, sameness NOT checked by design; (3) copy of that
 .env in ~/agent-fleet/backup/20260921-235741/ (600). NOT consumers: GitHub Actions (stealth secrets: BREVO_API_KEY
 only; no workflow references Railway), VLABS (no RAILWAY_* names; spec.ts is prose), shell rc/LaunchAgents (none),
 local CLI (OAuth session accessToken/refreshToken, user.token empty), deploys (GitHub integration, not token).
files: .fleet/reports/SC-SEC-RAILWAY-ROTATION-MAP-20260926.md (only file written)
tests: No code change -> no suite. Checks: git grep RAILWAY_TOKEN/backboard at HEAD + 196f634 (header type only);
 gh secret list stealth/vlabs (names); railway 5.30.4 whoami/status; ~/.railway/config.json key structure only;
 `railway deployment list --limit 1000 --json` (2026-02-25..09-06): since 04-17 492 deploys, 491 repo
 NeaBouli/stealth@main author NeaBouli, all commitHashes exist in repo; last SUCCESS 09-06 = main e06d018.
risks: Misuse indicator (unconfirmed): 1 unattributed deploy 2026-07-31T22:19Z FAILED, no repo/commit meta
 (CLI upload or API) - correlate in audit log. CLI shows no audit log/variable reads/token list/other services;
 list covers linked service only. Token may be account-wide incl. Inferno project ifr-ai-copilot. Revoke is
 irreversible; if inferno tooling uses the same value it stops (monitor-rollout.sh is not in active use).
security: RAILWAY_TOKEN public since 2026-04-17 = credible compromise. Account/workspace token can read all
 service variables -> if audit log shows foreign token use, every var in disciplined-flexibility (ADMIN_API_KEY,
 TURN_SECRET, Stripe, FIREBASE SA, ID_HASH_PEPPER, Resend/Brevo) is in rotation scope. ~/.railway/config.json.bak
 is 0644 with an old OAuth refresh token (expired access) -> delete after `railway logout/login`.
next: GIO (browser): 1 Railway Account Settings -> Tokens (+ workspace Tokens): identify tokens created <=2026-04-17,
 note name/scope/last-used, REVOKE. Replacement: none (monitor obsolete); if needed later, a project token for
 disciplined-flexibility/production + script header change (separate brief). 2 Workspace Audit Logs 2026-04-17..now:
 token logins, variable reads, 07-31 22:19Z deploy, unknown IPs -> escalate var rotation if foreign. 3 Remove
 RAILWAY_* lines from inferno/.env + fleet backup copy (Inferno lane decides own token). 4 Optional private-env
 check: `curl -s -H "Authorization: Bearer $RAILWAY_TOKEN" -H 'Content-Type: application/json' -d '{"query":"{me{id}}"}'
 https://backboard.railway.com/graphql/v2 | jq -r '.errors[0].message // "STILL VALID"'` -> expect Not Authorized.
 5 BRIDGE confirmation (no values). CODEX after: `railway whoami` OK; next GitHub deploy appears in `railway
 deployment list --limit 3 --json` (deploy path unaffected); grep inferno/.env names -> 0; then gitleaks
 .gitleaksignore step from SC-SEC-GITLEAKS-TRIAGE. Rollback boundary: none needed for deploy/CLI/Actions; only
 recovery is issuing a NEW token (no un-revoke).
