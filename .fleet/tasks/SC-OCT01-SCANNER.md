id: SC-OCT01-SCANNER
goal: Resolve the known non-secret function-call false positive without weakening real secret detection.
scope: .gitleaks.toml and a narrowly scoped regression test/evidence only.
instructions:
  - Read AGENTS.md, architecture map, and the sanitized SC-SEC-GITLEAKS-TRIAGE-20260926 report if present. Do not expose raw historical findings.
  - Integration report identifies resolveTurnSecret(process.env) in backend/signaling/src/server.js as known finding A, a function-call false positive in full-history/scheduled scans.
  - Verify the current scanner config and that precise non-secret expression. Implement the narrowest expression-level exception, not a whole-file/rule/history bypass.
  - Prove the benign expression is ignored while synthetic representative real-secret matches still fail. Use only synthetic data, redact scanner output, never print historic secret matches.
  - Preserve all real findings and owner-deferred credential-rotation gates; do not allowlist credentials, access runtime or rotate anything.
  - Commit local worker branch and report exact regression results, config diff and remaining scan blockers. No push/main merge/deployment/ADB/other agents.
report: .fleet/reports/SC-OCT01-SCANNER.md
