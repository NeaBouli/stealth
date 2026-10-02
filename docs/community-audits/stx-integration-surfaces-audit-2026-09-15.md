# StealthX / SecureCall — Integration & Surfaces Audit

- **Series:** Collateral Web3 Open Audits
- **Date:** 2026-09-15
- **Target:** NeaBouli/stealth @ `e06d018417bae5be16bf6b89d0a1887586a99d3b` + live `stealthx.tech` / `api.stealthx.tech`
- **Scope:** Web3/wallet/IFR surfaces, invite/return/payment flows, analytics wiring, third-party JS supply chain, invite mechanics, public API surfaces called by the site
- **Method:** full read of every public HTML/JS surface by a deep-recon agent + lead verification incl. live probes (anonymous GET/HEAD only — no POSTs against the API)
- **Register:** STX-29 … STX-36 (this report) — **0 Critical / 1 High / 4 Medium / 2 Low / 1 Info**

---

## Executive summary

The Web3 posture is genuinely conservative and correct: the in-app WalletConnect/SIWE flow is retired (static notice pages), the browser IFR discount checkout is hard-gated (`data-ifr-enabled="false"` with a fail-closed JS early-return), balance/eligibility checks are designed server-side, and no payment can be triggered from the public site. The serious finding is on the other side of the privacy promise: **Google Analytics loads without consent on 32 pages, including the invite pages that carry the callee's SecureID and the sender's name in the URL** — directly contradicting the wiki privacy policy's "No usage analytics … of any kind" claim on a page that itself loads GA.

## Severity table

| ID | Severity | Title |
|----|----------|-------|
| STX-29 | High | Google Analytics (GA4) without consent on 32 pages incl. invite/payment/custom-ID surfaces — contradicts own privacy policy |
| STX-30 | Medium | SECURITY.md "report privately" links to the public issue tracker; README/CONTRIBUTING contradict it; no private channel exists |
| STX-31 | Medium | Invite flow defects: malformed GitHub-referrer link, arbitrary `?link=` deep-link branch, SecureID persisted in localStorage |
| STX-32 | Medium | Third-party JS supply chain without SRI on payment/wallet paths (jsdelivr qrcode, esm.sh WalletConnect, external RPC) |
| STX-33 | Medium | Legacy SIWE return surface still live: `return/securecall` builds an app `intent://` forwarding address/signature/nonce from URL params |
| STX-34 | Low | Undisclosed dynamic price escalation in backend (`licenses.js`) vs static website prices |
| STX-35 | Low | `payment-success.html` renders custom-ID + token deep link client-side; success pages indexable-by-default (noindex present, but listed in robots-allowed space) |
| STX-36 | Info | Public-by-design items verified harmless: WC project ID, IFR contract address, `/licenses/status`, hosting on Hetzner (not Railway as docs claim — see STX-44) |

---

## STX-29 — High — Google Analytics without consent on 32 pages, incl. SecureID-carrying invite pages

**Evidence (all lead-verified):** GA4 property `G-V2L60E8E7R` loads unconditionally via gtag.js on 12 top-level pages (404, audit, disclaimer, faq, impressum, index, invite, payment-success, privacy, security, success, terms), all 19 `website/wiki/*.html` pages, and `invite/index.html` — **32 pages, no cookie-consent gate anywhere**. Privacy-critical cases:

- `invite.html` + `invite/index.html` process `?id=<SecureID>&name=<senderName>` (invite.html:86-152) — GA page_view transmits the full URL **including the SecureID and sender name** to Google.
- `payment-success.html` (checkout return) and `success.html` (post-purchase) load GA.
- `wiki/custom-id.html` (purchase form collecting billing country, email, company AFM/VAT) loads GA.
- `website/wiki/privacy-policy.html:210-212` states "**Analytics: No — No usage analytics, behavioral tracking, or telemetry of any kind**" on a page that itself loads GA (`:24-26`). `website/privacy.html` discloses AdMob and Crashlytics but **not Google Analytics on the website**.
- Notably absent (correctly): download.html, ifr.html, siwe.html, return*.html carry no GA.

**Impact:** (1) GDPR/ePrivacy: analytics without consent on EU-facing pages of an EU company (Vendetta Labs, Kalamata). (2) Direct, demonstrable self-contradiction of the published privacy policy. (3) A privacy-branded product leaks the social graph of invitations (who invites whom, when, from which IP) to Google.

**Recommendation:** remove GA from all pages (the brand promise) or gate it behind real consent and strip query strings before collection; at minimum remove it from invite/payment/custom-ID/privacy pages immediately and correct the policy text.

## STX-30 — Medium — SECURITY.md "report privately" links to the public issue tracker

**Evidence:** `SECURITY.md:5-10` — "please **do not open a public GitHub issue**. Instead, report it privately to: **[Open an issue](https://github.com/NeaBouli/stealth/issues)**" — the designated "private" channel is the public tracker. `README.md:69,192` and `CONTRIBUTING.md:11` actively instruct filing vulnerabilities in **public** issues, contradicting SECURITY.md's prohibition. No private channel exists anywhere (no security@ address, no PGP, GitHub private vulnerability reporting not referenced). Contact surface fragmentation compounds it: impressum.html:295 `kaspartisan@proton.me`, LICENSE:19 `contact@stealthx.tech`, success.html:194 `support@stealthx.tech`, llms.txt:79 "GitHub Issues only".

**Impact:** researchers either disclose publicly or don't report; response-timeline promises (48h ack / 30d high fix) attach to a channel that doesn't exist.

**Recommendation:** one disclosure address (e.g. security@stealthx.tech) + enable/reference GitHub private vulnerability reporting; align README/CONTRIBUTING/SECURITY.

## STX-31 — Medium — Invite flow defects

**Evidence (lead-verified):**
- `invite.html:68` sets `playLink` to `https://github.com/NeaBouli/stealth/releases/latest` (no query string); `:152` then appends `'&referrer=' + …` → produces `releases/latest&referrer=invite_<id>` — a **malformed URL**; the primary download button on the invite page breaks whenever an invite ID is present.
- `invite.html:86-117`: the SecureChat/Chameleon branch accepts an arbitrary `?link=` parameter, `decodeURIComponent`s it and navigates to it as a `stealthx://add/<sxId>?x=…` deep link — app-side validation is required (web side performs none beyond the app-name check).
- `invite/index.html` persists `localStorage.pendingInviteSecureId`; GA on both invite variants (STX-29).
- `404.html` doubles as the SPA invite router — invite links are served through the 404 page (works, but fragile for SEO/status-code semantics).

**Recommendation:** build the referrer URL with proper `?`/`&` handling (or drop referrer tracking on the GitHub target); validate the deep-link target app-side with an allowlist; consider sessionStorage.

## STX-32 — Medium — Third-party JS supply chain without SRI on payment/wallet paths

**Evidence (lead-verified):** `payment-success.html:171` loads `https://cdn.jsdelivr.net/npm/qrcode@1.5.3/build/qrcode.min.js` **without `integrity`/SRI** and renders a custom-ID deep link + QR from URL params; when the IFR checkout is enabled, `js/ifr-checkout.js:9` imports the WalletConnect provider from `https://esm.sh/@walletconnect/ethereum-provider@2.17.3` (no SRI possible for ESM imports — the trust anchor is esm.sh) and uses `https://eth.llamarpc.com` as RPC (line 10). Embedded `WC_PROJECT_ID` (line 8) and the IFR contract address in index.html are public-by-design (verified harmless).

**Impact:** compromise of jsdelivr/esm.sh (or DNS) would let an attacker alter QR/deep-link content on a payment-adjacent page, or inject into the wallet flow when enabled. Today the payment page grants nothing client-side (fail-closed copy verified), which caps the impact.

**Recommendation:** self-host the qrcode bundle; pin the WalletConnect provider via import-map to a self-hosted build when enabling checkout; document the RPC trust assumption.

## STX-33 — Medium — Legacy SIWE return surface still live

**Evidence:** `website/return/securecall/index.html:36-46` builds `intent://stealthx.tech/return/securecall?…` forwarding `address`/`signature`/`nonce`/`deviceId`/`returnPackage` (default `com.securecall.app.premium`) from URL parameters into an Android intent. The sibling pages (return.html, return/securechat, return/chameleon) are correctly static "flow disabled" notices, and siwe.html is a clean static notice. Per CODEX_AUDIT records the Android SIWE handlers were removed — the web side of the retired flow survived. Any `?returnPackage=` value is forwarded into the intent URI.

**Impact:** limited (no exported handler should remain in current apps), but the page is live, unparameterized-validation-free, and could resurrect a signing-flow attack surface if an old APK or a fork still exports the intent handler.

**Recommendation:** replace with the same static "disabled" notice as the sibling pages.

## STX-34 — Low — Undisclosed dynamic price escalation in the backend

**Evidence:** `backend/signaling/src/licenses.js:18-19` implements escalating lifetime prices (pro_lifetime €15 → +€0.35/sale up to €50; premium_lifetime €25 → up to €100 per code comments) and `GET /licenses/status` publishes the ladder (live-verified: `currentPrice 1500`, `nextPrice 1535`, 100 units each). The website shows static "candidate" prices (index.html:516-518) and llms.txt:41 defers to "VLABS checkout"; marketing/play_store/en discloses the escalation ("Price increases automatically") while the German copy and the site do not.

**Impact:** consumer-transparency gap on a flagship one-time price; cross-referenced with the €49-vs-€25 conflict (STX-38).

**Recommendation:** disclose the escalation mechanic wherever the price is shown, or freeze it.

## STX-35 — Low — `payment-success.html` client-side deep link; transactional pages indexable-by-default

**Evidence:** `payment-success.html:173-190` renders a `securecall://custom-id?id=…&token=…` deep link + QR purely from URL parameters — the copy is correctly fail-closed ("Returning to this page alone does not confirm a payment"; activation requires the signed Stripe webhook, verified server-side at `custom_ids.js:402-405` token binding), so entitlement cannot be forged from the page; the residual risk is phishing-style link crafting. `success.html:168` mixes German ("Settings → Konto") into the English steps and points to `support@stealthx.tech` (contradicts llms.txt:79). All transactional pages carry `noindex` (verified) — but invite.html sits **in the sitemap** (see STX-58).

**Recommendation:** keep the fail-closed copy; drop the deep-link rendering unless the token is single-use (it is — `:428`) **and** expired sessions render inert; align the support channel text.

## STX-36 — Info — Verified-harmless public items

- WalletConnect project ID and IFR token contract (`0x77e99917Eca8539c62F509ED1193ac36580A6e7B`) are public-by-design client config.
- `GET /licenses/status` public price ladder — presumably intentional (see STX-34 for the disclosure gap).
- `api.stealthx.tech` resolves to `135.181.254.229` (Hetzner) — live hosting reality matches the legal pages, not the engineering docs (STX-44).
- Server-side IFR design is correct in principle: balance/eligibility enforced server-side on mainnet; the client grants nothing; 410-gated until launch approval.

---

## Verified strengths (evidence-checked)

- IFR checkout hard-gated end-to-end: `index.html:481 data-ifr-enabled="false"` → `ifr-checkout.js:20-27` fail-closed early return; `ifr.html` all buttons disabled + `noindex`; backend endpoints 410 unless the launch flag is set; `checkout_moved_to_vlabs` error mapping exists.
- Retired surfaces retired properly: siwe.html/return.html static notices, no orphaned JS; `/siwe/*` + `/verify-ifr` live-probe 404.
- payment-success/success pages grant nothing client-side; entitlement enforced server-side via signed webhooks + token binding.
- Public API attack surface from the site is small and mostly rate-limited (`/health`, `/api/report` honeypot-capped, `/custom-id/check|purchase`, gated `/stripe/*`).
- invite.html domain-guard redirect to the canonical host; no `http://` resources anywhere on the site (agent swept all pages).
