id: SC-SEC-GITLEAKS-TRIAGE-20260926
status: partial
worker: claude
branch: agent/claude/SC-SEC-GITLEAKS-TRIAGE-20260926
summary: Read-only triage; no code/config touched. Module: CI security pipeline; hop: security-audit.yml
 secret-scan -> gitleaks detect (default --all refs, fetch-depth 0) -> .gitleaks.toml. No MAP.md.
 Scheduled run 35579087031 (main e06d018) has 7 findings, not 5; schedule red since >=2026-08-17.
 A turn-credentials x2 876b9c61/6254b2a2 server.js:92 -> FALSE POSITIVE: `const TURN_SECRET =
   resolveTurnSecret(process.env);` (entropy 2.9, env read). Not in main history; 876b9c61 IS in
   #102 history, and the same shape is at #102 head server.js:94 (dcc5bca), not allowlisted.
 B generic-api-key x4 845a1a0e BRIDGE.md:1295/1296/1372/1373 (2026-05-16, in main) -> CREDIBLE
   EXPOSURE: 2 distinct 64-hex values labelled TURN_SECRET and ADMIN_API_KEY "generated/already
   set" on hetzner; public repo. Current BRIDGE shows `[REDACTED - rotate if still active]`.
 C generic-api-key x1 196f6346 tools/monitor-rollout.sh:8 (2026-04-17, in main) -> CREDIBLE
   EXPOSURE: RAILWAY_TOKEN, UUID-shaped non-placeholder literal. Removed by 076c629 (#40); BRIDGE
   :5449 still lists rotation as open external blocker; no rotation confirmation found.
 Heads: main e06d018 and #102 5f70b16 contain none of the 3 flagged values (0 files). main
 server.js:94 is allowlisted (process.env.TURN_SECRET); #102 head server.js:94 is not.
files: .fleet/reports/SC-SEC-GITLEAKS-TRIAGE-20260926.md (only file written)
tests: No code change -> no suite run. Verification: gh run view --log-failed filtered to
 RuleID/File/Line/Commit/Fingerprint only (redacted run, 1379 commits, leaks found: 7);
 git merge-base --is-ancestor per commit vs main/#102; in-process value comparison against
 main/#102 trees printing counts only (0/0); rule+allowlist regex replay on server.js at 4 refs;
 PR run 35512897724 scans --first-parent range only (green PR vs red schedule); no local gitleaks.
risks: Without secret/provider access unverifiable: whether B/C values are still live, were
 rotated, or were used by third parties (Railway audit log, coturn/signaling admin logs).
 Kimi report under-counted (BRIDGE x4 not x3; missed monitor-rollout.sh). Merging #102 lands
 876b9c61 + unallowlisted line on main -> push/schedule scans stay red until A is fixed.
security: DISPOSITION: A = false positive (code). B = credible compromise of TURN_SECRET +
 ADMIN_API_KEY (public history since 2026-05-16). C = credible compromise of RAILWAY_TOKEN (public
 history since 2026-04-17). Current trees redacted/clean of values; history is not. Escalate B+C
 to Gio (owner) for rotation/revocation; no history rewrite (public, already cached/forked).
next: 1 Gio: rotate TURN_SECRET + ADMIN_API_KEY (hetzner signaling/coturn) and revoke/reissue the
   Railway token; review provider logs; confirm in BRIDGE (no values).
 2 On #102 branch before merge, add to .gitleaks.toml allowlist: '''const TURN_SECRET = resolveTurnSecret\(process\.env\);'''
   (history-independent; covers 876b9c61, 6254b2a2 and #102 head).
 3 After step 1 confirmed only: add .gitleaksignore with the 5 B/C fingerprints
   (845a1a0e:BRIDGE.md:generic-api-key:{1295,1296,1372,1373},
   196f6346:tools/monitor-rollout.sh:generic-api-key:8) - non-secret, stable.
 4 Acceptance: local `gitleaks detect --redact` (all refs) on #102 head -> no leaks; then schedule.
