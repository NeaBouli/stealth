id: SC-TESTER-CODES-READINESS-01
status: partial
worker: codex (solo mode; Fleet fallback produced no valid report)
branch: agent/codex/SC-UI-086-PR102
summary: Direct Premium tester-license lane from private staging through device-bound entitlement. Source and synthetic protocol are ready; private recipient reconciliation, exact signed artifact, runtime provisioning and two-device acceptance remain external gates.
files: read-only analysis; no real recipient or production file accessed
tests: backend tester protocol 3/3 PASS; private staging/export baseline 25/25 PASS; Android focused unit build PASS; API 35 hardware-key instrumentation 1/1 PASS
risks: Server verifies P-256 possession but not Android remote key attestation. Reinstall/key loss and device replacement intentionally require owner recovery. A lease lasts 30 days with a seven-day renewal grace; revocation cannot invalidate an offline proof immediately.
security: Registry stores only code hashes and public device keys. Runtime and Android feature are default-disabled and Direct-Premium-only. No code, address, device identifier, signer or private data was created or exposed.
next: Finish controlled private promotion tooling; on the coordinator machine reconcile the complete inventory and owner-approved recipients, then stage inactive codes. Later provide an exact configured signed APK, isolated signer/runtime pairing and two physical devices before import, activation or delivery.
