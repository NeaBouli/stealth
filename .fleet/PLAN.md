# SecureCall Fleet Plan

## Binding execution board — 2026-09-26

Detailed scope remains `.fleet/SECURECALL_COMPLETION_PLAN.md`; current evidence is in the three
`SC-STATUS-*20260926` / `SC-SEC-*20260926` reports. No implementation starts before the security
owner decision below. Hosted GitHub CI is conserved until its 2026-10-01 reset.

### Gate S0 — owner security decision (blocks merge, deploy and sale)

- Public history credibly exposed historical TURN, admin API and Railway credentials; current
  `main` and PR #102 trees contain none of their values.
- Gio decides and separately authorizes rotation/revocation plus provider-log review. No history
  rewrite. After confirmed rotation, Claude owns the gitleaks false-positive/ignore correction and
  Kimi independently reviews it. Acceptance: all-ref local `gitleaks detect --redact` is clean.

### Wave A — local work through 2026-09-30 (no hosted CI or merge)

1. Kimi: PR #102 stack review, STX-01..62 sweep and entitlement review; read-only reports.
2. Claude: issue #86 S10/Tab S4 measurements, UI fix, visual-release evidence; then public truth
   and canonical-price diff only after Gio/VLABS confirm the price registry.
3. Grok: no standalone issue #87 patch; its fix already exists in #102/#89. Available only for a
   later narrow task under five files.
4. Codex: PR traceability metadata and integration only; run the local signaling/Rust/Android VPN
   policy matrix once candidate diffs are assembled. No duplicated worker review.

### Wave B — integration from 2026-10-01

1. Obtain a trusted non-`NeaBouli` GitHub approval; rerun exact-head CI once on PR #102; merge only
   when tree `0578362c` and all gates match, then verify `main`.
2. Close stacked/superseded implementation PRs only with the merge SHA. Preserve and separately
   integrate evidence PRs #83/#85/#88; then rebase Dependabot and disposition legacy docs PRs.
3. Verify runtime host truth, `/health`, public `/status/live` privacy and close all 62 audit items.

### Wave C — product, publication and sale

1. Tester entitlements and revocation; S10/S7/Tab S4 call/UI matrix; expired pin-set correction.
2. Build one new version: Play Free AAB only, direct Pro/Premium APKs only; inspect packages, VPN
   split, signing certificate and hashes; complete frontend visual-release gates.
3. Align site/Wiki/README/releases, publish artifacts, then test Stripe/VLABS fulfillment, IFR
   discount, Brevo delivery, restore/refund/dispute/revoke and Play RTDN in test mode.
4. Record `PRODUCT_READY=YES`; VLABS separately grants version-bound `FINANCE_READY=YES`; only then
   may Gio authorize checkout, Play production and direct sales. Until then both remain `NO`.

Exclusive ownership: Codex integration/release; Kimi large-context review; Claude implementation
and security/UI; Grok narrow tasks; Gio/VLABS consoles, credentials, finance and activation.

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
