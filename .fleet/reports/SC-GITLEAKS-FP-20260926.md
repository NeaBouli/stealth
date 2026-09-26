id: SC-GITLEAKS-FP-20260926
status: ok
worker: claude
branch: agent/claude/SC-GITLEAKS-FP-20260926
summary: Module: CI security pipeline; hop: security-audit.yml secret-scan -> gitleaks-action ->
 .gitleaks.toml global [allowlist] (regexTarget=line). No MAP.md; only this lane mapped.
 Added one fully anchored line regex for the exact code shape (TURN_SECRET const assigned from
 resolveTurnSecret(process.env), optional leading/trailing whitespace, nothing else on the line).
 No rule changed, no fingerprint/commit/path ignore, no .gitleaksignore, no history rewrite.
files: .gitleaks.toml (+1 regex line)
 tools/test_gitleaks_config.sh (new; fixtures generated at runtime in mktemp, random synthetic
 values via openssl rand, identifiers built by concatenation so the script itself scans clean)
tests: tools/test_gitleaks_config.sh with local gitleaks (~/go/bin/gitleaks) -> 8/8 PASS, exit 0:
 fp_exact clean, fp_indented clean; turn_literal, turn_fallback_literal (`|| "<syn>"`),
 turn_same_line_suffix (FP line + literal on same line), turn_env_file, generic_api_key -> leak;
 pr102_history (gitleaks git origin/main..origin/pr-102 @5f70b16) clean.
 Negative control with previous .gitleaks.toml: fp_exact/fp_indented/pr102_history FAIL (exit 1),
 all leak cases still leak -> test discriminates. Baseline range scan before fix: 1 finding
 turn-credentials server.js:92 @876b9c61; after fix: 0. Single-commit scan 6254b2a: 1 -> 0.
 PR #102 head server.js (dir scan): 1 -> 0. Self-scan of test script: 0 findings.
risks: Allowlist is line-scoped (regexTarget=line); anchors ^...$ prevent a secret appended on
 the same line from being masked (proven by turn_same_line_suffix). A future rename/reformat of
 that line (e.g. different variable or extra args) will be flagged again - intended.
 Full worktree dir scan still reports 5 turn-credentials hits, all in fleet prose that quotes the
 line (this brief :8, SC-SEC-GITLEAKS-TRIAGE report :8/:35 and its claude log :6/:13). None are on
 origin/main (e06d018); if those .fleet files are merged to main, push/schedule scans turn red.
 Deliberately not allowlisted (out of brief scope; would widen the entry).
 Test needs gitleaks >= 8.19 locally (dir/git subcommands); not wired into CI.
security: Change is suppression-only for one non-secret code shape (env read, no literal).
 Generic/TURN/Stripe rules and gitleaks defaults unchanged. No secret values read or written;
 all scans run with --redact. Historical credential findings B (BRIDGE.md 845a1a0e, TURN_SECRET +
 ADMIN_API_KEY) and C (tools/monitor-rollout.sh 196f6346, RAILWAY_TOKEN) stay RED by design -
 they still require rotation confirmation; this task does not resolve them.
next: Orchestrator: review + merge before/with PR #102. Decide separately how .fleet prose quoting
 the line is handled (rephrase quotes vs. path allowlist for .fleet/). Rotation of B/C remains open.
