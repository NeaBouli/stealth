id: SC-BREVO-001
status: partial
worker: Codex (solo mode after Claude/Kimi/Grok fallback)
branch: agent/codex/SC-BREVO-001
summary: |
  Module: signaling payment fulfillment. Hop: Stripe/VLABS fulfillment ->
  sendActivationCode -> Brevo HTTP API `/v3/smtp/email` using `BREVO_API_KEY`.
  Separate CI hop: scheduled workflow -> `tools/brevo_keepalive.sh` -> HTTP API
  `/v3/account`. No active SMTP transport was found.
files:
  - .fleet/PLAN.md
  - .fleet/tasks/SC-BREVO-001.md
  - .fleet/reports/SC-BREVO-001.md
tests: |
  `bash -n tools/brevo_keepalive.sh` -> PASS
  `node backend/signaling/src/__tests__/email_handler.test.js` -> PASS
  workflow YAML safe parse -> PASS
  GitHub scheduled keepalive run 34943227532 (2026-09-15) -> PASS
  tracked cross-repo SMTP/Brevo search -> no SMTP transport outside Stealth legacy/debug references
risks: |
  Provider-side last-use and external-integration metadata for the two warned SMTP credentials
  remains unverified. Do not renew, delete, rotate or intentionally use either credential yet.
security: stale unused SMTP credentials increase account exposure; no credential value was read
next: |
  Open Brevo Settings -> SMTP & API -> SMTP in the in-app browser, inspect only redacted status
  and last-used metadata, then allow expiry only if no external owner is identified.
