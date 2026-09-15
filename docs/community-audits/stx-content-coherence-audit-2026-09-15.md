# StealthX / SecureCall — Content & Coherence Audit

- **Series:** Collateral Web3 Open Audits
- **Date:** 2026-09-15
- **Target:** NeaBouli/stealth @ `e06d018417bae5be16bf6b89d0a1887586a99d3b` + live surfaces + GitHub wiki clone
- **Scope:** README, docs/ (~40 files), website/*.html, website/wiki/*, GitHub wiki, marketing/, store_assets/, fastlane/, changelogs, pricing, versions, claims vs code
- **Method:** deep-recon agent sweep + lead verification of every finding; live probes anonymous GET/HEAD only (Play listing state verified with a fake-package control)
- **Register:** STX-37 … STX-55 (this report) — **0 Critical / 2 High / 8 Medium / 7 Low / 2 Info**

---

## Executive summary

The codebase tells one product story; the public texts tell at least four. The **live GitHub wiki still sells GhostNet IP masking** — a feature whose flavor and code were removed in May 2026 — to Premium buyers. The shipped app UI charges **€49** where the website says **€25** for the same lifetime/activation product. The "Security: Audited / independently audited" posture rests on two internal self-reviews. And four changelogs disagree about what the current version even is. None of these are code bugs; all of them are the kind of drift that destroys a security product's credibility and creates consumer-protection exposure.

## Severity table

| ID | Severity | Title |
|----|----------|-------|
| STX-37 | High | Live GitHub wiki is a stale pre-pivot product: GhostNet IP masking, "zero metadata", wrong distribution model (13/13 pages drift) |
| STX-38 | High | Pricing chaos: shipped app UI says €49 vs website €25; USD scarcity copy; undisclosed backend escalation (cross-ref STX-34) |
| STX-39 | Medium | "Security: Audited" badge + "independently audited" claim rest on internal self-reviews; public 94/100 page contradicts newest internal audit |
| STX-40 | Medium | Four changelogs, four heads; wiki changelog lists v1.0.12 four times with four versionCodes; anachronisms |
| STX-41 | Medium | fastlane metadata (what automation uploads) contradicts license, ads, features and pricing |
| STX-42 | Medium | Google Play status contradictions: listing is LIVE (lead-verified) while homepage and llms.txt say "in review" |
| STX-43 | Medium | Ghost issue references #101–#306 do not exist; Known-Issues #101 contradicts shipping pinning reality |
| STX-44 | Medium | Hosting identity drift: docs/tooling say Railway, legal pages say Hetzner — DNS confirms Hetzner |
| STX-45 | Medium | F-Droid/GPL remnants everywhere although the flavor was removed and the license is source-available |
| STX-46 | Low | Absolute marketing claims contradicted by the product's own privacy policy |
| STX-47 | Low | Contact/disclosure addresses fragmented across five channels |
| STX-48 | Low | Version-identifier drift (marketing stuck at v0.2-beta/v1.0.13; undocumented versionCode jump; missing 78015 changelog) |
| STX-49 | Low | store_assets includes a screenshot of the removed WalletConnect/IFR feature in Play upload slot #4 |
| STX-50 | Low | Stale docs: AUDIT_REPORT claims ifrunit.tech dead (it is live); user-manual donation/IFR leftovers; broken `docs/tech/` reference |
| STX-51 | Low | Structural triple-wiki drift: 9 pages HTML-only, 3 pages MD-only; publishing is manual copy-paste |
| STX-52 | Low | Two divergent privacy policies; neither discloses GA (STX-29); Google Fonts third-party load undisclosed |
| STX-53 | Low | audit.html self-awarded 94/100 "PRODUCTION-READY" with factual errors (36 vs 27 sitemap URLs) and never updated |
| STX-54 | Info | Cosmetic: German "Konto" in English success steps; stale `stealthx.app` comment; hardcoded "4 bpm" heartbeat |
| STX-55 | Info | License vs auditability tension: source-available LICENSE forbids build/run while docs invite independent verification |

---

## STX-37 — High — Live GitHub wiki is a stale pre-pivot product

**Evidence (all lead-verified in the wiki clone, fetched 2026-09-15):** the **public GitHub wiki** (13 pages) still advertises removed features: `Security-Design.md:69` "IP address visible … **Masked (GhostNet)**" (Premium); `FAQ.md:25` "Premium: Absolutely nothing — not even your IP address (masked via GhostNet)"; `FAQ.md:69` "GhostNet IP Masking — Yes"; `Installation-Guide.md:11` "Premium = Everything + GhostNet IP masking, **In-App Upgrade**" (wrong distribution: Pro/Premium are direct APKs); `Architecture.md:130` "premium … Everything + GhostNet"; `Roadmap.md:42` "GhostNet multi-hop relay network"; `Home.md` "zero metadata collection"; FAQ describes Free as "optional anonymous crash reports only" with **no AdMob mention**. All 13 pages differ from the newer `docs/WIKI/` versions (14–64 changed lines each). The GhostNet flavor/source-set was removed 2026-05-04 (`docs/agent-bridge/ACTION_LOG.md:795-816`); the only GhostNet code left in the client is the mock layer of STX-26. Root cause: wiki publishing is manual copy-paste (`docs/ENABLE_WIKI.md`).

**Impact:** the most-linked public documentation (README "Documentation → Wiki") describes IP-masking protection that does not exist, to exactly the at-risk user groups the product targets. A Premium buyer's threat model may depend on it.

**Recommendation:** single-source the wiki (CI-publish `docs/WIKI/`), or stamp every stale page with a dated deprecation banner until then.

## STX-38 — High — Pricing chaos

**Evidence (lead-verified):** the **shipped app UI** (`client_android/app/src/free/res/layout/activity_upgrade.xml:250,267`) sells "Activation Code — **€49**"; `marketing/play_store_de.txt:44` and `backend/payments/STRIPE_SETUP.md:12,38` agree at €49. The website (`index.html:516-518`), `docs/PRICING.md:7-8`, `docs/WIKI/FAQ.md:94-95` and llms.txt:42 say **€25** (Premium lifetime / activation code). `marketing/play_store/en/full_description.txt` quotes **"$15/$25 (100 available)"** in USD with scarcity pressure. The backend additionally escalates lifetime prices per sale (STX-34). Custom-ID prices (€1/€2/€5) are consistent — but the website hardcodes Stripe price IDs `price_1TJIT…` (wiki/custom-id.html:347) that differ from the backend's `price_1TLU3…` (`custom_ids.js:271-273`); the client-side IDs are display-only dead config (verified), still drift.

**Impact:** the flagship one-time price differs by ~2× depending on which official surface a buyer reads; combined with the scarcity copy this is a consumer-protection problem, not just a doc bug.

**Recommendation:** one canonical price source (VLABS catalog) rendered everywhere; scrub €49 from the client UI and German store copy or update the website; disclose the escalation mechanic.

## STX-39 — Medium — "Audited" claims rest on internal self-reviews

**Evidence (lead-verified):** README.md:15 badge "Security: Audited" → `docs/SECURITY_AUDIT_REPORT.md`, an **internal self-review** (2026-02-15, "Auditor: Internal Security Review"). `website/audit.html` presents "AUDITUS ROMANA CALIGULA … **94/100 PRODUCTION-READY** … Audit conducted by Claude Code — automated + manually verified" (`:7,73,176`) and links `docs/AUDIT_REPORT.md` (same nature). `marketing/play_store/en/full_description.txt` claims "**Independently audited**" — false for both. The newest internal audit (`CODEX_AUDIT.md`, 2026-08-27) records **1 FAIL / 6 WARN / 12 PASS** with payments FAIL/blocking — audit.html was never updated with it. (`website/wiki/faq.html:322-328` at least says "internal".)

**Recommendation:** label every audit reference as internal/automated; drop "independently audited" from store copy; update or unpublish audit.html; link this external series once published.

## STX-40 — Medium — Four changelogs, four heads

**Evidence:** `CHANGELOG.md:10` latest = **1.0.49** (2026-08-26, vC78016); `website/wiki/changelog.html:154-192` latest = **v1.0.12**, listed four times with four different versionCodes (24, 28, 29, 30), "last updated April 3, 2026", plus anachronisms: "v1.0.0 (2026-03-24)" claims WireGuard VPN that `CHANGELOG.md:25` dates to 1.0.48, and a "Security Audit — Feb 15, 2026, 48 findings" entry predating the "First Internal Test Release"; `docs/WIKI/Changelog.md:14` head = **[Unreleased] v0.2-beta**; fastlane has changelogs for 78014 and 78016 but **none for 78015** (the published 1.0.48 line) and nothing for candidate 78017.

**Recommendation:** one generated changelog (single source), backfill 78015, fix or unpublish the wiki changelog.

## STX-41 — Medium — fastlane metadata contradicts the product

**Evidence:** `fastlane/metadata/android/en-US/short_description.txt` = "…**Zero metadata.**" (contradicted by the own privacy policy: FCM tokens stored, phonebook hashes, transient signaling metadata); `full_description.txt:11-17` still sells "WalletConnect v2 for IFR token verification", "Unlock with activation code or IFR token lock", "**No ads, no tracking, no telemetry**" (Free has AdMob), and the abandoned "After 30 days, unlock outgoing calls" model; `changelogs/42.txt` "v1.0.21 — Initial F-Droid release … GPL-3.0-only license"; 48.txt ≡ 49.txt duplicates.

**Impact:** this tree is what release automation pushes to the Play listing — every claim in it is currently wrong against the product.

**Recommendation:** regenerate fastlane metadata from the same canonical source as the website; add a CI diff-check.

## STX-42 — Medium — Google Play status contradictions

**Evidence (lead live-verified 2026-09-15):** `play.google.com/store/apps/details?id=com.securecall.app.free` returns **200** (control: fake package → 404) — the listing is **LIVE**. Yet `index.html:395` says "Google Play distribution is **in review**" and `llms.txt:73` "remains under review" — while the same homepage (`:329,544`) and README badges say "Free now"/live, and llms.txt:68 says "public listing live" (llms.txt contradicts itself between lines 68 and 73).

**Recommendation:** one status line everywhere; the "in review" texts are stale.

## STX-43 — Medium — Ghost issue references #101–#306

**Evidence:** `docs/SECURITY_AUDIT_REPORT.md:157+` and `docs/WIKI/Known-Issues.md` (and the live wiki) reference audit findings as GitHub issues #101–#306 — **lead-verified non-existent** (`gh issue view 101` / `306` → not found; the repo's latest PR is #81). Known-Issues "#101 No TLS certificate pinning on WebSocket connection (High, OPEN)" directly contradicts README.md:137 and index.html:431 (pinning enabled) — reality: OkHttp pinning exists in all flavors; only the XML pin-set expired (STX-23). So the public "known issues" list is simultaneously unverifiable and partly false.

**Recommendation:** re-file real issues (this series' register can seed them) or annotate the IDs as external-tracker references; close the stale #101 entry.

## STX-44 — Medium — Hosting identity drift

**Evidence:** README.md:115 third-party table lists **Railway.app** as signaling host; `docs/RAILWAY_DEPLOYMENT.md`, `RAILWAY_ENV_VARS.md`, `tools/monitor-rollout.sh` target Railway; but `disclaimer.html:151,231` and `wiki/privacy-policy.html:361` name **Hetzner-hosted api.stealthx.tech** — and DNS confirms it: `api.stealthx.tech → 135.181.254.229` (Hetzner, lead-resolved 2026-09-15).

**Impact:** GDPR processor accuracy — the legal pages are (accidentally) right while every engineering doc is stale; any incident response runbook pointing at Railway is wrong.

**Recommendation:** update docs to Hetzner (or re-deploy to Railway) and record the processor correctly in the privacy docs.

## STX-45 — Medium — F-Droid/GPL remnants

**Evidence:** `fastlane/.../changelogs/42.txt` "Initial F-Droid release … GPL-3.0-only"; `CHANGELOG.md:148,175`; `docs/AUDIT_REPORT.md:107` "F-Droid MR !36557: Await review". The fdroid flavor was fully removed 2026-05-04 (`docs/agent-bridge/ACTION_LOG.md:795-816`), and the current LICENSE is source-available (F-Droid-incompatible). Dead code remains: `WindowSecurityHelper.kt:14,25` branches on a `"FDROID"` tier that `TierManager.TIER_RANK` can never produce.

**Recommendation:** scrub F-Droid/GPL from changelogs and docs; delete the dead tier branch.

## STX-46 — Low — Absolute marketing claims contradicted by the own privacy policy

**Evidence (lead-verified samples):** "zero metadata" (fastlane short_description, marketing, wiki Home/FAQ) vs `privacy.html` (FCM tokens retained, phonebook-hash uploads, transient signaling metadata — privacy.html itself admits "hashing alone does not guarantee anonymity"); `index.html:159` (FAQ JSON-LD) "does not require … **any personal information**" vs optional phone-number processing; "military-grade cryptography" (README.md:26) and marketing/en "…that even intelligence agencies cannot break"; "best alternative to Signal, Wickr, and Silent Phone" (llms.txt:11); "38 devices tested" (index.html:424) vs physical QA on 3 devices (CODEX_AUDIT, llms.txt:65).

**Recommendation:** replace absolutes with the (actually good) precise statements the privacy policy already uses.

## STX-47 — Low — Contact/disclosure fragmentation

`impressum.html:295` kaspartisan@proton.me · `LICENSE:19` contact@stealthx.tech · `success.html:194` support@stealthx.tech · `llms.txt:79` "GitHub Issues only" · `play_store_de.txt` "@secureslot on X" for license purchases. One canonical contact + one security channel (see STX-30).

## STX-48 — Low — Version-identifier drift

`marketing/play_store/en/release_notes.txt` stuck at **v0.2-beta**, `play_store_de.txt` at **v1.0.13**; versionCode jumps 50 → 78015 without documented rationale (CHANGELOG.md:39 vs :21); README correctly distinguishes published v1.0.48 / candidate v1.0.50 — keep that, fix the rest.

## STX-49 — Low — store_assets screenshot of a removed feature

`store_assets/playstore/phone/04_ifr_wallet.png` shows the removed in-app WalletConnect/IFR UI and sits in slot #4 of the Play upload order (`store_assets/playstore/README.md`); `store_assets/README.md` still lists "WalletConnect/IFR screen screenshot" as needed. Remove/replace before the next store upload.

## STX-50 — Low — Stale docs cluster

`docs/AUDIT_REPORT.md:45-47` declares "ifrunit.tech Domain Down (no DNS, no HTTP) … fixed immediately" — the domain is **live** (lead-probed 200) and the claimed fix is itself stale documentation; `docs/user-manual.md:227,282` keeps donation-address and IFR-purchase instructions for the retired in-app flow (its :184 is correct); `native/README.md` references `docs/tech/` — directory does not exist; `docs/PRIVACY_POLICY.md:142` + both MD wikis' Home pages still cite `neabouli.github.io/stealth` as the official site (it 301-redirects — works, but is not canonical).

## STX-51 — Low — Structural triple-wiki drift

9 pages exist only as `website/wiki/*.html` (announcements, beta-testing, bug-report, custom-id, getting-started, ifr-unlock, matrix-integration, privacy-policy, troubleshooting); 3 only as MD (`Build-Instructions`, `Encryption-Architecture`, `Known-Issues`); publishing to the GitHub wiki is manual copy-paste (`docs/ENABLE_WIKI.md`). Single-source the pipeline.

## STX-52 — Low — Two divergent privacy policies

`website/privacy.html` (app-focused) vs `website/wiki/privacy-policy.html` (different structure, names Hetzner, claims "Analytics: No") — overlapping but not identical content, two canonical candidates; neither discloses GA (STX-29) or the Google Fonts third-party load present on every page (`privacy.html:18-20`).

## STX-53 — Low — audit.html self-awarded score with factual errors

`website/audit.html` (`index,follow`) publishes "94/100 PRODUCTION-READY", "sitemap.xml current (**36 URLs** total)" (`:159` — actual: 27), "40 HTML pages", and never received the 2026-08-27 CODEX_AUDIT results (1 FAIL/6 WARN). The "AUDITUS ROMANA CALIGULA" branding reads unprofessionally for a security product. Update with current data or unpublish.

## STX-54 — Info — Cosmetic cluster

`success.html:168` German "Settings → Konto" inside English steps; `js/main.js` header comment says "stealthx.app"; index.html:579-596 hardcodes the "live heartbeat" to a constant 4 bpm while pinging /health every 15 s (cosmetic deception, not a metric).

## STX-55 — Info — License vs auditability tension

LICENSE forbids "build, run, host" while README invites "verify the cryptographic implementation yourself" and `docs/WIKI/Build-Instructions.md` exists — auditors literally cannot compile to verify. A one-line "verification build permitted for audit purposes" exception would resolve it.

---

## Verified strengths (evidence-checked)

- README is the most accurate single document: correct published/candidate version split, accurate VPN-split explanation, honest third-party table incl. FCM metadata, tier matrix mostly matching code (deviations reported as STX-12, and two matrix rows corrected in the crypto recon: Pro screen-capture over-delivers; hardware-keystore/aggressive-rotation rows are flag-only).
- Current IFR messaging is consistent across index.html, ifr.html, siwe.html, terms.html, wiki/ifr-unlock.html and llms.txt (browser-only, launch-gated) — the stale copies are confined to fastlane/marketing (STX-41).
- Website↔repo deployment is byte-fresh (6/6 probed pages identical to baseline).
- `docs/WIKI/` (the newer MD wiki) is largely accurate — it is simply not what the public wiki shows (STX-37).
