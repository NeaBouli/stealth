# SecureCall Fleet Plan

## Milestone: BREVO-SMTP-INACTIVITY-20260924 — PARTIAL

Goal: determine whether the Brevo SMTP credentials labelled `Master Password` and
`securecall-production` are still required, without exposing secrets or mutating production.

1. Claude maps repository email/SMTP ownership and runtime configuration names.
2. Codex inspects redacted Brevo provider metadata and reconciles service ownership.
3. Codex records one disposition per credential: retain-and-validate, replace through a separately
   approved change, or allow deactivation after proving it is unused.

Repository and CI ownership are mapped. Provider metadata remains open because the explicitly
requested in-app browser was unavailable in this session.

No test email, secret readout, rotation, deletion, deployment or production mutation is in scope.
