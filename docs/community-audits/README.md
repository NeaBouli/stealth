# Community Audits — Collateral Web3 Open Audits

External audit series of the SecureCall/StealthX project, commissioned by the project
owner (Gio) and published here with his explicit authorization ("öffentlich wie
IFR/Ekklesia"), overriding the private-disclosure wording of `SECURITY.md` for these
reports themselves. **Report-only**: no audited code was changed; live checks were
anonymous GET/HEAD requests only; no secret files were read (repo `AUDIT_MUST_READ`
policy honored).

- **Audit date:** 2026-09-15
- **Baseline:** `main` @ `e06d018417bae5be16bf6b89d0a1887586a99d3b`
- **Register:** STX-01 … STX-62 — **0 Critical / 8 High / 28 Medium / 20 Low / 6 Informational**
- **Finding tracker:** umbrella issue (see PR description / issue list) with one checkbox per finding
- **2026-05-03 master audit re-baselined:** STX-CRIT-01 PARTIAL, STX-HIGH-01 FIXED, STX-HIGH-02 FIXED, STX-HIGH-03 PARTIAL, STX-MED-01 FIXED, STX-MED-02 PARTIAL, STX-MED-03 OPEN, STX-MED-04/05 PARTIAL (details in the full-scope report)

## Reports

| # | Report | Register | Severity (C/H/M/L/I) | SHA-256 |
|---|--------|----------|----------------------|---------|
| 1 | [Full-scope security](stx-full-scope-audit-2026-09-15.md) | STX-01…20 | 0/3/10/6/1 | `083a92135403b6487bc1397c8f0085885fc0060115f349259b3d6b0c6d49e74d` |
| 2 | [Cryptography deep audit](stx-crypto-deep-audit-2026-09-15.md) | STX-21…28 | 0/2/4/1/1 | `aa8f26abc80b2658ec829f317674fed9c5cae9da92c081ff6a48b7e76a073d8b` |
| 3 | [Integration & surfaces](stx-integration-surfaces-audit-2026-09-15.md) | STX-29…36 | 0/1/4/2/1 | `d1364414a46db1f750bff9e2202f911a49872f8087eb2fcb8fe47a653fe3b791` |
| 4 | [Content & coherence](stx-content-coherence-audit-2026-09-15.md) | STX-37…55 | 0/2/8/7/2 | `c576c58dec89d5ef9e752c796cc6bbe4b115d035a4d542eba8f63410a5165d62` |
| 5 | [AI-readiness & SEO anchors](stx-ai-readiness-audit-2026-09-15.md) | STX-56…62 | 0/0/2/4/1 | `547d1604b9bdba14d2d41b49609fbbb3c5427a651da72d35cc97ebf3f33b85dc` |

## Headline findings

- **STX-01 (High):** unauthenticated WebSocket REGISTER — anyone can claim a victim's
  SecureID, supersede their connection, take over their FCM push channel and intercept
  call invites (`ws/handlers/register.js:14-29,53-66,152-164`).
- **STX-02 (High):** `GET /status/live` publicly exposes connected clients' IP
  addresses (live-verified 2026-09-15; `server.js:421-437`).
- **STX-03 (High, conditional):** coturn `static-auth-secret=$TURN_SECRET` is likely
  the literal string — relay secret effectively public
  (`deploy/coturn/turnserver.conf:19`; host verification pending).
- **STX-21 (High):** unauthenticated X25519 key exchange with `HKDF salt=None` and no
  fingerprint/SAS verification — the "zero-knowledge server" guarantee holds only
  against a passive server (`core_crypto/src/ffi/mod.rs:265-269`).
- **STX-22 (High):** "Double Ratchet" advertised in README/website/Play copy is not
  implemented — one static key per call (`core_crypto/src/session/mod.rs:29`).
- **STX-29 (High):** Google Analytics without consent on 32 pages incl. invite pages
  carrying SecureID+sender name in the URL — contradicts the wiki privacy policy's
  "No analytics of any kind" on a page that itself loads GA.
- **STX-37/38 (High):** live GitHub wiki still sells removed GhostNet IP masking;
  shipped app UI charges €49 where the website says €25.

## Verified strengths (selection)

Payment stack fail-closed (Stripe webhook idempotency, Play RTDN OIDC, VLABS HMAC);
IFR/Web3 surfaces genuinely launch-gated; primitive crypto layer correct with current
patched RustCrypto deps and low-order-DH check; APK supply chain hash-pinned (all
three v1.0.48 APKs match published SHA256SUMS — independently re-hashed); website
deployment byte-fresh against baseline; distribution VPN split compliant at source
level with Gradle guards; live media path fail-closed.
