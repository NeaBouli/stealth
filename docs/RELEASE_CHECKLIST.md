# StealthX Release Checklist

Last updated: 2026-06-11

## Blocking Before Live

- [ ] Stripe key rotated in Stripe Dashboard.
- [ ] New `STRIPE_SECRET_KEY` set in `/opt/stealthx/.env.production` on Hetzner.
- [ ] `pm2 reload ecosystem.config.js --update-env` run after Stripe key update.
- [ ] Stripe Checkout smoke test returns a `checkout.stripe.com` session URL for:
  - [ ] SecureCall Pro Lifetime.
  - [ ] SecureCall Premium Lifetime.
  - [ ] SecureChat Pro Lifetime.
  - [ ] SecureChat Elite Lifetime.
  - [ ] Chameleon Pro Lifetime.
  - [ ] Chameleon Elite Lifetime.
  - [ ] StealthX Suite Lifetime.
- [ ] BUG-029 WireGuard tested with an active WireGuard profile.
- [ ] Dependabot high vulnerabilities resolved in GitHub alerts.

## App Verification

- [ ] SecureCall installed on test devices.
- [ ] SecureChat installed on test devices.
- [ ] Chameleon installed on test devices.
- [ ] SecureChat E2E message test passes on physical devices.
- [ ] Chameleon accessibility service opens the correct settings/app entry point.
- [ ] Activation-code redemption tested against live `api.stealthx.tech`.
- [ ] No active v1 UI button points to an unavailable or placeholder feature.

## Website Verification

- [ ] `https://stealthx.tech/` returns HTTP 200.
- [ ] `https://securechat.stealthx.tech/` returns HTTP 200.
- [ ] `https://chameleon.stealthx.tech/` returns HTTP 200.
- [ ] Website links all return HTTP 200 or intentional external redirects.
- [ ] SecureCall download APK links return HTTP 200.
- [ ] Landing hero frequency animation is present.
- [ ] Pricing and IFR values are consistent across product pages and docs.

## Backend Verification

- [ ] `https://api.stealthx.tech/health` returns HTTP 200.
- [ ] `https://api.stealthx.tech/licenses/status` returns all v1 product keys.
- [ ] CORS allows `https://stealthx.tech`.
- [ ] CORS allows `https://securechat.stealthx.tech`.
- [ ] CORS allows `https://chameleon.stealthx.tech`.
- [ ] Backend test suite passes locally.
- [ ] Backend test suite passes on Hetzner before/after deploy.
- [ ] PM2 `signaling` is online.
- [ ] Stale Docker `stealthx-signaling` container remains stopped unless intentionally restored.

## Store / Release

- [ ] Play Store AAB uploaded.
- [ ] Store listing and privacy policy links point to current production URLs.
- [ ] Version names/codes match release notes.
- [ ] Certificate pinning rotation reminder tracked for `api.stealthx.tech` before 2026-08-14.

## Post-Live Smoke

- [ ] Purchase test with live Stripe Checkout completes.
- [ ] Stripe webhook delivers activation code email.
- [ ] Activation code unlocks the expected product tier.
- [ ] No backend errors in PM2 logs after smoke.
- [ ] Bridge updated with final release result.
