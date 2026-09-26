# SC-GITLEAKS-FP-20260926 — Narrow TURN code false-positive correction

Worker: Grok
Mode: security configuration implementation

Use architecture-map first. Modify only `.gitleaks.toml` and focused tests/fixtures if required.
Add the narrowest stable allowlist for the literal code shape
`const TURN_SECRET = resolveTurnSecret(process.env);` across PR #102 history/head.

Do not add ignores for historical credential fingerprints, weaken generic rules, read secret
values, rewrite history or touch runtime configuration. Prove the code false positive is suppressed
while synthetic generic API-key and TURN-secret leaks still fail. Write the standard report
`.fleet/reports/SC-GITLEAKS-FP-20260926.md`; mark `security` explicitly.
