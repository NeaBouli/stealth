# SecureCall Fleet Plan

## Milestone: BREVO-SMTP-INACTIVITY-20260924 — COMPLETE

Goal: determine whether the Brevo SMTP credentials labelled `Master Password` and
`securecall-production` are still required, without exposing secrets or mutating production.

1. Claude maps repository email/SMTP ownership and runtime configuration names.
2. Codex inspects redacted Brevo provider metadata and reconciles service ownership.
3. Codex records one disposition per credential: retain-and-validate, replace through a separately
   approved change, or allow deactivation after proving it is unused.

Repository, CI and provider ownership are mapped. The Brevo dashboard reports both warned SMTP
credentials as active, non-expiring and never used. The separate SecureCall HTTP API credential is
active and was last used on 2026-09-15, matching the successful scheduled keepalive.

Disposition: allow Brevo's inactivity deactivation for the two unused SMTP credentials. Do not
delete them and do not add an SMTP keepalive. Continue monitoring only the HTTP API credential used
by SecureCall.

No test email, secret readout, rotation, deletion, deployment or production mutation is in scope.

## Milestone: SECURECALL-COMPLETION — READY TO EXECUTE

Authoritative programme: `.fleet/SECURECALL_COMPLETION_PLAN.md`.

The programme orders all remaining SecureCall work as M0 through M11 and assigns one writer per
surface. The first parallel batch is documentation/read-only only:

1. Kimi: independent PR #102 stack review and STX-01..62 finding sweep.
2. Grok: narrow Android CI setup correction for issue #87.
3. Claude: S10/Tab S4 UI geometry measurement for issue #86; proposal only.
4. Codex: PR metadata and integration control; no merge or closure until gates pass.

Hard gates remain: PR #102 needs an independent GitHub approval from a trusted account other than
`NeaBouli`; real tester grants, provider changes, deployments, Play uploads and sales activation
need their own exact authorization. PRODUCT_READY and FINANCE_READY are not granted.
