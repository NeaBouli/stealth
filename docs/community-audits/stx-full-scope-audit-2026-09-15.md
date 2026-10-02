# StealthX / SecureCall — Full-Scope Security Audit

- **Series:** Collateral Web3 Open Audits
- **Date:** 2026-09-15
- **Target:** NeaBouli/stealth @ `e06d018417bae5be16bf6b89d0a1887586a99d3b` + live `stealthx.tech` / `api.stealthx.tech`
- **Scope:** signaling/payment backend, deployment configs, Android client security mechanisms, live surfaces
- **Method:** 3 deep-recon agents + lead verification of every finding at exact file:line; live checks anonymous GET/HEAD only; no secret files read (repo policy honored); no fixes applied
- **Register:** STX-01 … STX-20 (this report) — **0 Critical / 3 High / 10 Medium / 6 Low / 1 Info**

---

## Executive summary

The 2026-05-03 master audit's structural items are mostly resolved: the monolith is partly modularized, `/ice-servers` is admin-gated (live 401), the license is consistently source-available, and no keystores/.env files remain in the tree. The payment stack (Stripe webhook, Google Play billing/RTDN, VLABS fulfillment) is genuinely fail-closed and well-tested.

The main exposure is the **identity layer**: WebSocket registration is unauthenticated, letting anyone claim a victim's SecureID, kick them offline, take over their push channel and intercept call invites (STX-01) — which chains directly into the unauthenticated key exchange documented in the crypto report (STX-21). Separately, a public status endpoint leaks the live user base's IP addresses (STX-02), and the TURN relay configuration is broken in a way that likely publishes the effective relay secret (STX-03, host verification pending).

## Severity table

| ID | Severity | Title |
|----|----------|-------|
| STX-01 | High | Unauthenticated WS REGISTER: identity hijack, FCM takeover, call-invite interception |
| STX-02 | High | `GET /status/live` publicly exposes connected clients' IP addresses |
| STX-03 | High* | coturn `static-auth-secret=$TURN_SECRET` literal — relay secret effectively public (*conditional on host config) |
| STX-04 | Medium | Phone-number enumeration oracle + unsalted SHA-256 contact hashes |
| STX-05 | Medium | Gift codes: 32-bit entropy, no per-code attempt limiting |
| STX-06 | Medium | Custom-ID password model weaknesses (timing, optional pepper, unlimited endpoints, silent transfer) |
| STX-07 | Medium | Rate-limit identity inconsistent (XFF trust) — limits either global-bucket DoS or spoofable |
| STX-08 | Medium | Unauthenticated `POST /key/register` grows unbounded in-memory map |
| STX-09 | Medium | Committed runtime data files (`data/*.json`) tracked in git and baked into Docker image |
| STX-10 | Medium | `deploy_signaling.sh` prints generated TURN_PASS and ADMIN_API_KEY to stdout |
| STX-11 | Medium | Legacy `backend/payments/` copy accepts unsigned webhooks and logs PII (dead but re-attachable) |
| STX-12 | Medium | SecurityEnforcer: Free "root warning" never fires; Pro "Blocks" only writes a log line |
| STX-13 | Medium | Hardcoded Open Relay TURN fallback with public credentials contradicts runtime-credential design |
| STX-14 | Low | Admin key comparison not timing-safe; two routes bypass `requireAdmin`; no admin throttling |
| STX-15 | Low | WebSocket Origin check disabled when `ALLOWED_ORIGINS` unset |
| STX-16 | Low | Caller-controlled metadata forwarded to callee and to Google FCM; lock-screen-visible caller name |
| STX-17 | Low | In-memory rate-limit maps never evict; sold-code store can lock sale recording permanently |
| STX-18 | Low | Data-directory fallback to world-readable `/tmp/stealthx-data` instead of failing startup |
| STX-19 | Low | Stub/dev artifacts in or near live paths (GHOST_PREPARE static relays, echo server, invalid nginx sample) |
| STX-20 | Info | Fingerprinting/hygiene: `x-powered-by: Express`, no HSTS on API, public `/licenses/status` price ladder |

---

## STX-01 — High — Unauthenticated WS REGISTER: identity hijack, FCM takeover, call-invite interception

**Evidence:** `backend/signaling/src/ws/handlers/register.js:14-29` accepts any `clientId` matching a regex with no proof of ownership; `:53-66` closes the existing connection for that ID (`"Superseded"`) while keeping its FCM token; `:152-164` lets `REGISTER_FCM_TOKEN` overwrite the token of the *claimed* ID; `call.js:50` then forwards caller invites (including the ECDH public key) to whoever holds the ID; `fcm.js:64-77` pushes caller name/phone to the registered token. App-signature check only applies when `ALLOWED_SIGNATURES` is set, and `FORK_PROTECTION_MODE` defaults to `"warn"` (register.js:31-51).

**Impact:** any anonymous WebSocket client can claim a victim's SecureID, kick the victim offline, receive their incoming-call invites (with the ECDH pubkey — enabling media MITM combined with STX-21), redirect push notifications containing caller metadata (name, phone number) to an attacker device, and repeat after every victim reconnect. Live state at probe time: 4 connected clients / 17 registered IDs — the user base is small, but the mechanism is fully exposed.

**Recommendation:** bind REGISTER to challenge-response (server nonce signed by a per-install keypair enrolled at first use), at minimum when re-claiming an ID with an active connection or stored FCM token; switch `FORK_PROTECTION_MODE` default to `enforce` in production; sign or out-of-band-verify the pubkey handoff.

## STX-02 — High — `GET /status/live` publicly exposes connected clients' IP addresses

**Evidence:** `backend/signaling/src/server.js:421-437` — no auth, no rate limit; `ipConnectionBuckets` with raw IPs at `:429`. **Live-verified 2026-09-15:** the endpoint returned real user IPs (three distinct addresses from German mobile and Greek residential ranges, masked in the public evidence file), plus `connectedClients`, `registeredIds`, `fcmTokens` and full `wsLimits` (evidence: `evidence/live/api-status-live.json`).

**Impact:** a privacy product publicly discloses its users' IP addresses (approximate locations, VPN usage patterns) and live connection counts to any scraper — a direct contradiction of the "zero metadata" positioning, and useful for DoS sizing.

**Recommendation:** gate behind `requireAdmin` or strip to aggregate counts; keep the minimal public `/health` for uptime checks.

## STX-03 — High (conditional) — coturn `static-auth-secret=$TURN_SECRET` literal

**Evidence:** `deploy/coturn/turnserver.conf:18-19` — `use-auth-secret` + `static-auth-secret=$TURN_SECRET`; `deploy/docker-compose.yml` mounts this file read-only and passes `TURN_SECRET` only as a container env var; **coturn does not expand environment variables inside turnserver.conf**, so the effective shared secret is likely the literal string `$TURN_SECRET` committed to a public repo. The RFC 8489 HMAC credential generator in `server.js:104-114` uses the real env value. Additionally `deployment/coturn_config/turnserver.conf:20-33` ships a `CHANGE_ME` placeholder, a stale `realm=turn.stealthx.app`, and a conflicting `lt-cred-mech` alongside `use-auth-secret` (the two mechanisms are mutually exclusive in coturn).

**Impact:** if the running coturn reads the literal string, signaling-minted credentials are invalid (relay broken) **and** anyone can mint valid TURN credentials against the public secret and relay unlimited traffic (cost/abuse amplification). Severity is conditional on the host's actual rendered config, which this audit could not inspect (no SSH per mandate).

**Recommendation:** verify the effective secret on the host now; render `turnserver.conf` at deploy time (envsubst from the secret store); remove `lt-cred-mech`; align `realm` with `TURN_HOST`. Positive note: when `TURN_SECRET` is correctly set, credentials are ephemeral HMAC-SHA1 with 24h TTL (`server.js:102-114`) — the design is right, the wiring is suspect.

## STX-04 — Medium — Phone-number enumeration oracle + unsalted SHA-256 contact hashes

**Evidence:** `ws/handlers/phone.js:7-60` returns `clientId` + `online` presence for any number or hash; limits are per-connection (10 single + 5×200 batch per minute) while `MAX_CONNS_PER_IP` defaults to 80 (`server.js:137`) → ~80k lookups/min/IP; `utils/phone.js:15-17` hashes numbers with bare SHA-256 — the E.164 space is trivially enumerable, so hashing provides no privacy.

**Impact:** at-scale enumeration of which phone numbers are SecureCall users, number→SecureID mapping, and online-presence tracking — for a product whose FAQ claims "no phone number required" and "zero metadata". The BUG-077 invite enumeration was fixed (`server.js:556-560`); this oracle remains.

**Recommendation:** require an established-contact or signed capability for lookups; per-IP (not per-connection) budgets plus a global daily cap; HMAC with server pepper for stored hashes; document that phone discovery is inherently non-private.

## STX-05 — Medium — Gift codes: 32-bit entropy, no per-code attempt limiting

**Evidence:** `server.js:520` — `"GIFT-" + crypto.randomBytes(4).toString("hex")` = 8 hex chars (~4.3×10⁹ space); redemption via WS `ACTIVATE_CODE` (`ws/handlers/subscription.js:168-249`) has no failed-attempt counter or lockout — only the global 40 msgs/10s per-connection limit, and connections are cheap (80/IP). A redeemed gift code grants PRO/PREMIUM tier.

**Impact:** ~320 attempts/s/IP → the gift-code keyspace falls in months from one IP, days from a botnet. (Sold `PREM-XXXX-XXXX-XXXX` codes at ~62 bits are not practically brute-forceable.)

**Recommendation:** ≥64-bit entropy (`randomBytes(8)`); per-IP and per-code failed-attempt tracking with exponential backoff.

## STX-06 — Medium — Custom-ID password model weaknesses

**Evidence (`backend/signaling/src/custom_ids.js`):** PBKDF2-SHA512/100k/per-record salt present (`:169-173`), but the verify compare is plain `===` (`:175-178`); `ID_HASH_PEPPER` is optional with cleartext-ID storage and only a startup warning (`:37-41,140-145`); `POST /custom-id/activate-token` has **no** rate limit (`:364`); `GET /custom-id/check` unlimited (`:251`, live-verified: returns `{"available":true,"price":500}` — a taken-ID enumeration oracle); transfer-by-password rebinds the `deviceId` with no notification to the previous device (`:202-218`). The `activate`/`purchase` rate limiter keys on `req.ip`, which is broken by STX-07.

**Impact:** password-guess protection exists but is undermined by the broken limiter identity and unlimited adjacent endpoints; a phished/reused password silently hands the ID to a new device; without the pepper, committed/store files expose cleartext vanity IDs. Positive: token replay is prevented (token deleted, `:428`) and Stripe session binding is checked (`:402-405`).

**Recommendation:** `crypto.timingSafeEqual`; fail startup when `ID_HASH_PEPPER` is unset in production; rate-limit `activate-token` and `check`; FCM/WS-notify the old device on transfer.

## STX-07 — Medium — Rate-limit identity inconsistent (XFF trust)

**Evidence:** `middleware/ip.js:4-13` trusts the *first* XFF hop when `TRUST_PROXY=true` **or** `RAILWAY_ENVIRONMENT` is set; `deploy/docker-compose.yml` does **not** set `TRUST_PROXY` while nginx proxies everything → all users share one bucket (invite 3/10min, billing 12/10min, checkout 5/10min become *global* limits one actor can exhaust); `custom_ids.js:238` and `stripe_handler.js:507` key on `req.ip` with no `app.set('trust proxy')` anywhere; `reportRoute.js:129` trusts XFF **unconditionally** → trivially spoofable 3/hr limit.

**Impact:** depending on deployment, per-IP limits either collapse into global limits (availability) or are bypassable by header spoofing.

**Recommendation:** `app.set('trust proxy', 1)` + `TRUST_PROXY=true` consistently (docker-compose included); key every limiter on `getClientIp`; fix `reportRoute` accordingly.

## STX-08 — Medium — Unauthenticated `POST /key/register` grows an unbounded in-memory map

**Evidence:** `server.js:239-253`, `pkd.js:21-34` — public key directory accepts 256-char keys with no auth, no rate limit, no eviction; non-persistent (restart wipes it).

**Impact:** slow memory-exhaustion DoS of the single-process signaling server (~430k entries/day behind the reference nginx limit, more on the current host where none exists).

**Recommendation:** per-IP limit + total-entry cap with LRU eviction, or require a REGISTERed WS association.

## STX-09 — Medium — Committed runtime data files tracked in git and baked into the Docker image

**Evidence (filenames/sizes only — contents not read, per repo policy):** `git ls-files` tracks `backend/signaling/data/activation_codes.json` (286 bytes) and `backend/signaling/data/wallets.json` (20 bytes); `backend/signaling/Dockerfile:18` runs `COPY data/ ./data/`; commit `c20765a` previously removed these and gitignored them, but they are present again at `e06d018`. Remnant of master-audit STX-CRIT-01.

**Impact:** whatever activation codes / wallet mappings these files hold ship inside image layers and git history; contradicts the "no personal data" posture and invites exactly the leakage class the master audit flagged.

**Recommendation:** untrack both files (keep `.gitignore`), purge from history if they ever held live data, seed codes only via the existing `SEED_ACTIVATION_CODES` env mechanism (`activation_store.js:57-72`), add a CI check that `data/` stays empty.

## STX-10 — Medium — `deploy_signaling.sh` prints generated secrets to stdout

**Evidence:** `deployment/deploy_signaling.sh:108-110` echoes `TURN_PASS=…` and `ADMIN_API_KEY=…` into the deploy log ("SAVE THESE CREDENTIALS SECURELY!"); the same values are correctly written to `/opt/securecall/signaling/.env` (`:36-56`).

**Impact:** credentials land in shell scrollback, CI logs and PM2 transcripts — a classic leak vector.

**Recommendation:** print only the file path; rotate any credentials ever deployed with this script version.

## STX-11 — Medium — Legacy `backend/payments/` accepts unsigned webhooks and logs PII (dead but re-attachable)

**Evidence:** `backend/payments/stripe_handler.js:196-199` — when `STRIPE_WEBHOOK_SECRET` is unset the handler parses the raw body **without signature verification** (free activation codes); `:208,132` logs full activation code and customer email; `:225-227` logs API-key prefixes; `backend/payments/email_handler.js:108-140` logs full email + code; its own `STRIPE_SETUP.md:59` instructs wiring this file. Verified **not** required by the live server (`server.js:765` loads the hardened `backend/signaling/src/payments/` copy, which fails closed at `:534-536` and masks identifiers).

**Impact:** none while unwired; anyone following the legacy setup doc reintroduces a critical payment-forgery hole.

**Recommendation:** delete `backend/payments/` or replace it with a README pointer to `backend/signaling/src/payments/`.

## STX-12 — Medium — SecurityEnforcer: Free "root warning" never fires; Pro "Blocks" only logs

**Evidence:** `client_android/app/src/main/java/com/securecall/app/security/SecurityEnforcer.kt:60-101` — `Action.ALLOW` is a no-op, `Action.WARN` writes `Log.w`, `Action.BLOCK` writes `Log.e` and nothing else; only Premium's `TERMINATE` kills the process. ROOT detection is gated on `fp.rootDetectionBlocks` (`:85`), false for Free → the README's "Root Detection — Free: Warning only" (README.md:135) is actually **silent no-op**; Pro's "Blocks" is a log line.

**Impact:** the published tier-security matrix overstates device-protection behavior that users may rely on.

**Recommendation:** either implement the advertised WARN UX (dialog/banner) and a real Pro-level block, or correct the matrix.

## STX-13 — Medium — Hardcoded Open Relay TURN fallback with public credentials

**Evidence:** `client_android/app/src/main/java/com/securecall/app/webrtc/WebRtcManager.kt:147-155` — `turn:openrelay.metered.ca` with `openrelayproject`/`openrelayproject` (public Open Relay Project credentials, public by design) is used whenever the dynamic ICE fetch has no cache (first-call race); contradicts the build comment "TURN credentials are returned by signaling at runtime; only public URLs remain" (`app/build.gradle:188-189`). The primary path (authenticated WS delivery, `IceServerFetcher.kt`) is correct.

**Impact:** calls can silently fall back to a free public relay anyone can use — different availability and trust properties than the documented Metered.ca provider; a privacy-sensitive user has no indication which relay carried the call.

**Recommendation:** fail closed to "no TURN" (or show a visible degraded-mode indicator) instead of the public fallback; fix the stale build comment.

## STX-14 — Low — Admin key comparison not timing-safe; two bypass routes; no admin throttling

**Evidence:** `server.js:205` and `middleware/admin.js:10` use `provided !== ADMIN_API_KEY`; `/api/subscription/:clientId` (`server.js:293-297`) and `/stripe/test-email` (`stripe_handler.js:557-560`) re-implement the check inline with divergent status codes; no rate limit on any admin route.

**Impact:** theoretical remote timing oracle for the admin key; unlimited online guesses if the key were weak; divergent code paths invite future mistakes. Positive: admin-by-query-param was already removed (BUG-076 comment, `server.js:203`), and all probed admin endpoints correctly return 401/403.

**Recommendation:** centralize on `makeRequireAdmin` with `crypto.timingSafeEqual` over digests; delete the inline checks; add a per-IP 401 counter.

## STX-15 — Low — WebSocket Origin check disabled when `ALLOWED_ORIGINS` unset

**Evidence:** `server.js:358-363` enforces Origin only when the allowlist is non-empty; the `DEFAULT_ALLOWED_ORIGINS` fallback (`:153-158`) is applied only to HTTP CORS (`:160`).

**Impact:** with default env, any website can open WS connections from a victim's browser (no cookie auth, so impact is limited to resource/registration abuse — but combined with STX-01 a web page could hijack IDs).

**Recommendation:** apply the same default allowlist in `verifyClient` (reject when an Origin header is present and not allowlisted).

## STX-16 — Low — Caller-controlled metadata forwarded to callee and to Google FCM

**Evidence:** `call.js:50` forwards an unvalidated `callerPhone` string to the callee; `fcm.js:64-77` includes `callerName`/`callerPhone`/`sessionId` in the FCM data payload (Google-visible metadata — master-audit STX-MED-03, still present); `ws/handlers/subscription.js:298-311` lets any registered client push arbitrary "joined" notifications to any ID; client-side, the incoming-call notification is `VISIBILITY_PUBLIC`, showing the caller name on the lock screen (`SecureCallMessagingService.kt:148,175`).

**Impact:** push-spoofing/spam surface and metadata exposure to Google and lock screens; clients must treat `callerPhone` as untrusted display data.

**Recommendation:** validate/cap `callerPhone` server-side; minimize the FCM payload to a wake-up ping; default the notification visibility to private; per-target notification counter.

## STX-17 — Low — In-memory limiter maps never evict; sold-code store can lock permanently

**Evidence:** `inviteRateLimits`/`checkoutRateLimits` never evict IPs (`server.js:564-575,849-860`; contrast the capped `billingVerificationAttempts` at `:623-629`); `sold_codes.js:22-34` file lock is never auto-reclaimed — a crash at the wrong moment disables all Stripe-sale recording until manual recovery (documented intent, but an availability trap).

**Recommendation:** periodic eviction sweeps like the existing 60s janitor (`server.js:707-722`); document the lock-recovery runbook.

## STX-18 — Low — Data-directory fallback to `/tmp/stealthx-data`

**Evidence:** `server.js:13-26` — if the preferred data dir is unwritable, all stores (activation codes, FCM tokens, wallets, subscriptions) land in `/tmp/stealthx-data` with default umask instead of failing startup.

**Impact:** on shared hosts other local users could read entitlement data; on ephemeral containers state silently becomes non-persistent.

**Recommendation:** fail startup in `NODE_ENV=production`, or `chmod 0700` the fallback.

## STX-19 — Low — Stub/dev artifacts in or near live paths

**Evidence:** live WS handler `GHOST_PREPARE` returns hardcoded `*.securecall.local` relay hints (`ws/handlers/webrtc.js:68-71`); `backend/ghostnet/ghostnet_router.js:12-15` same static list (unwired); `ghostnet_server_stub.js` health-only stub; `backend/ghostnet_echo_server.js` unauthenticated TCP echo on `0.0.0.0:8080` (dev tool port-colliding with signaling); `broadcast.js:12` requires a non-existent `./safeSend` (would crash if loaded); `deployment/nginx_config/signal.stealthx.app.conf` places `limit_req_zone` inside a `server{}` block (invalid nginx context; the `deploy/nginx/nginx.conf:41-42` variant is correct).

**Recommendation:** clearly mark stubs, exclude echo/stub from deploy artifacts, fix or delete the invalid nginx sample.

## STX-20 — Info — Fingerprinting/hygiene items

- `x-powered-by: Express` exposed on all API responses (live-verified); no `Strict-Transport-Security` header on `api.stealthx.tech` (live-verified) — the app also does not use helmet.
- `GET /licenses/status` publicly discloses the dynamic price ladder (`pro_lifetime` €15→escalating, `premium_lifetime` €25→escalating, 100 units each; live-verified). Presumably intentional transparency, but see STX-38 for the disclosure gap against the website.
- Uptime at probe time ≈ 45 days; 4 connected clients; `wsLimits` disclosed via STX-02's endpoint.

---

## Master-audit (2026-05-03) re-baseline

| ID | Verdict | Evidence |
|----|---------|----------|
| STX-CRIT-01 (sensitive artifacts in tree) | **PARTIAL** | keystores/.env gone (filename scan clean except public ISRG root cert); but `data/*.json` tracked again + baked into image → STX-09 |
| STX-HIGH-01 (license conflict) | **FIXED** | LICENSE now "StealthX Source-Available License"; README badge "Source--Available"; consistent — residual tension: LICENSE forbids build/run while docs invite verification (STX-55, content report) |
| STX-HIGH-02 (public /ice-servers) | **FIXED** | `requireAdmin` at `server.js:221`; live probe 401; unset key → 403 `admin_api_disabled` |
| STX-HIGH-03 (backend monolith) | **PARTIAL** | routes/middleware/services/ws extracted; `server.js` still 1014 lines with all HTTP endpoints inline; `src/routes/*`, `rateLimit.js`, `broadcast.js`, `presence.js`, `sessions*.js`, `validator.js` are **dead code — verified unwired** (`wireRoutes` in `context.js:161` never called) |
| STX-MED-01 (README download drift) | **FIXED** | README consistently stealthx.tech + GitHub releases; legacy neabouli.github.io/stealth 301-redirects to stealthx.tech (live-verified) |
| STX-MED-02 (Free flavor weakened) | **PARTIAL→regressed** | matrix documented, but Free "warning" is silent and XML pins expired → STX-12, STX-23 |
| STX-MED-03 (FCM metadata claims) | **OPEN** | README third-party table now discloses FCM metadata, but payload still contains caller name/phone/session → STX-16 |
| STX-MED-04 (Custom-ID password model) | **PARTIAL** | PBKDF2 + some rate limits added; timing/pepper/enumeration issues remain → STX-06 |
| STX-MED-05 (website link refresh) | **PARTIAL** | core links fixed; new defects found → STX-31, STX-48…STX-54 |

## Verified strengths (evidence-checked)

- Stripe webhook: raw body before JSON parser (`server.js:144-146`), `constructEvent` verification, fail-closed without secret, persisted 14-day idempotency, refund/dispute revocation incl. activation-code tombstoning.
- Google Play RTDN: Google OIDC signature/audience/email checks, package allowlist, Pub/Sub message-id idempotency; subscription revalidation server-side.
- VLABS fulfillment: HMAC-SHA256 + ±300s timestamp window, timing-safe compare.
- Admin surface: all probed admin endpoints (`/routing/list`, `/clients/list`, `/metrics`) return 401; admin-by-query-param removed.
- Retired surfaces: `/siwe/*` and `/verify-ifr` return 404/410 (live-verified); IFR checkout is 410-gated behind a launch flag.
- 18 backend test files wired into `npm test` incl. payment, RTDN, entitlement and IFR-holder suites; weekly `npm audit --audit-level=high` in CI (`.github/workflows/security-audit.yml`).
- Live media path fail-closed: frames dropped when no session key or native crypto missing (`WebSocketService.kt:457-470`).
- APK supply chain: all three release APKs (Free/Pro/Premium v1.0.48) match the published `SecureCall-v1.0.48-SHA256SUMS.txt` (Free `e825608d…a52a`, Pro `6b8e68f3…2568`, Premium `f9389652…1554` — lead-verified by download + hash).
- Website deployment byte-fresh: 6/6 probed pages identical between live stealthx.tech and repo @ e06d018.
