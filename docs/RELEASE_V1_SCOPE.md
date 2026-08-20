# StealthX v1 Release Scope

Last updated: 2026-07-16

This document defines what must be complete for the first public/live release and what is intentionally deferred to v1.1 or later. The goal is to avoid treating roadmap or "SOON" items as hidden release blockers.

## v1 Must Be Complete

### SecureCall

Status: Stage 3 candidate pending final operational checks.

Required for v1:
- Encrypted call setup through the production signaling backend.
- SecureID/no-phone-number identity flow.
- Pro/Premium tier handling.
- Activation-code redemption.
- Premium APK download links.
- Certificate pinning policy and rotation tracking.
- Website pricing, download, privacy, and legal pages.
- Backend health, WebSocket, routing, activation, and payment tests.

Known v1 blocker:
- External device-VPN compatibility remains part of network regression testing.
- SecureCall 1.0.45 Free incoming-call ad pause still needs a clean S10 -> Tab S4 accept retest.
- S7 remains an external call-matrix blocker until it has validated Internet or an approved mobile-data/roaming fallback.

### SecureChat Core

Status: complete for v1 core, pending full device/function QA and fresh final artifacts.

Required for v1:
- Local identity creation and persistence.
- QR/contact exchange.
- Contact list and core chat flow.
- E2E delivery between paired devices.
- Activation-code redemption for Pro/Elite.
- Website pricing and checkout buttons.
- Settings copy aligned with current tier model.

Deferred items must remain visibly marked as SOON or unavailable.

### Chameleon Core

Status: complete for v1 core, pending full device/function QA and fresh final artifacts.

Required for v1:
- Accessibility service entry point.
- Main settings/navigation flow.
- Overlay/decoy/private-zone core screens and routes.
- Activation-code redemption for Pro/Elite.
- Website pricing and checkout buttons.
- Settings copy aligned with current tier model.

Deferred items must remain visibly marked as SOON or unavailable.

### Payments

Status: code complete, externally blocked by Stripe key rotation and finance-provider launch gates.

Required for v1:
- Public product pages call `https://api.stealthx.tech/stripe/create-dynamic-checkout`.
- Backend catalog includes SecureCall, SecureChat, Chameleon, and Suite lifetime products.
- CORS allows `stealthx.tech`, `securechat.stealthx.tech`, and `chameleon.stealthx.tech`.
- Stripe Checkout session creation succeeds after a valid live `STRIPE_SECRET_KEY` is installed.
- Webhooks generate product-specific activation emails and activation codes.

Current blocker:
- The configured live Stripe secret key is expired. Rotate the key in Stripe and update Hetzner before going live.
- AADE/myDATA/e-timologio transfer must be verified in the private VLABS finance control center before production sales are treated as final.
- Public pages must not present IFR/wallet discount checkout as active while these gates remain open.

## v1.1 / SOON

The following items are explicitly not required for v1 and may remain marked as SOON, roadmap, or gated:

- Advanced Threat Detection.
- BuilderRegistry on-chain governance/registration.
- Multi-Decoy.
- Additional Chameleon high-end automation expansions beyond the v1 core.
- Any partner/on-chain governance action not controlled by the app binary or website.

## Release Rule

If a feature appears in production UI as active, it must work end to end before v1. If a feature is not complete, it must be clearly gated, hidden, or marked SOON so users do not mistake roadmap functionality for shipped functionality.
