# Stealth / SecureCall / StealthX — Master Audit

- **Datum:** 2026-05-03
- **Auditor:** Codex
- **Scope:** lokales Repo `/Users/gio/Desktop/repo/stealth`, GitHub `NeaBouli/stealth`, Website `https://stealthx.tech`, local docs/wiki/website, Android client, Rust crypto, Node signaling backend
- **Nicht gelesen:** `.env`, `.env.*`, `.gitignore`, keystores, keys, secret files
- **Keine Aktionen:** kein Commit, kein Push, kein Deployment, keine Serveränderung

## Executive Summary

Stealth/SecureCall ist ein sicherheitsorientiertes Android/Backend-Projekt mit starker Doku, Rust-Krypto, Android-Flavors, FCM, Stripe/IFR-Integrationen und einem Node-Signaling-Server. Viele frühere High-Risk-Punkte sind im Code bereits explizit als Bugfixes dokumentiert. Die Hauptprobleme im aktuellen Audit sind Kohärenzdrift in Lizenz/Distribution, persistierte lokale sensitive Artefakte, öffentliche ICE/TURN-Ausgabe, Feature-Flag-Unterschiede zwischen Free/Pro/Premium und ein sehr großer gewachsener Backend-Monolith.

## Geprüfte Quellen

- Lokal: `README.md`, `client_android`, `core_crypto`, `backend/signaling`, `docs`, `website`, `fdroid`, `fastlane`
- Öffentlich: `https://stealthx.tech`, GitHub `NeaBouli/stealth`
- Server: kein projektklarer Stealth-Server auf dem bekannten Hetzner-Host inventarisiert; README verweist auf Railway/Metered/FCM.

## Architektur

- Android: Kotlin/Java, Gradle, flavors `free`, `pro`, `premium`, `fdroid`.
- Crypto: Rust native via JNI; X25519, HKDF, XChaCha20-Poly1305 laut README.
- Backend: Express + ws signaling, Firebase Admin, Stripe, Brevo/Resend/Nodemailer, ethers.
- Website: static HTML/CSS/JS under `website/`.
- Distribution: Play Store beta, F-Droid metadata, GitHub releases, fastlane metadata.

## Critical Findings

### STX-CRIT-01 — Sensitive local artifacts are present in repo tree

**Evidence from filename-only scan:** `.env.local`, release keystores (`securecall-release-key.jks`, `.bak`), backend wallet data file path, build hash/key intermediates. Contents were not read.

**Risk:** Accidental prompt ingestion, backup leakage or commit leakage would compromise distribution or operational integrity.

**Recommendation:** Move keystores and real runtime data outside repo tree. Keep only `.env.example` and documented paths. Add a pre-commit denylist and CI secret scan.

### STX-HIGH-01 — License and branding claims conflict

**Evidence:** README badge says GPL-3.0, build/distribution text says GPL terms, German section says source-available and not compilable/distributable/commercially usable.

**Risk:** Legal ambiguity blocks contributors, F-Droid, security researchers and commercial evaluation.

**Recommendation:** Decide one canonical license model. If GPL-3.0, remove source-available restriction language. If source-available/trademark-limited, update badges, LICENSE and F-Droid claims.

**2026-05-03 Codex note:** User clarified that Play Store/APK/F-Droid rollout should remain stable and should not be reset. The light correction path is to keep GPL-3.0 for the client code while clarifying that SecureCall/StealthX branding, official backend services, store releases, and paid Pro/Premium licensing remain controlled by Vendetta Labs.

### STX-HIGH-02 — Public ICE server endpoint exposes relay config

**Evidence:** `backend/signaling/src/server.js` has public `/ice-servers`; comment says TODO move to WS-only delivery. Prior docs already flagged hardcoded/extractable TURN concerns.

**Risk:** TURN abuse/cost amplification and endpoint scraping. Even if credentials are ephemeral or low-privilege, public unauthenticated discovery is avoidable.

**Recommendation:** Require registered client/session auth for ICE retrieval. Use short-lived TURN credentials and per-client rate limits.

### STX-HIGH-03 — Backend monolith contains many admin/payment/FCM paths in one file

**Evidence:** `backend/signaling/src/server.js` includes admin routes, routing debug, public key directory, subscription status, FCM registration, broadcast, gift, payments and WebSocket logic.

**Risk:** Review complexity and regression risk. Auth mistakes in one area can affect unrelated features.

**Recommendation:** Split into route modules with explicit auth middleware, schema validation and targeted tests.

## Medium Findings

### STX-MED-01 — README download/status drift

**Evidence:** Top README links Play Store beta and latest release, but Download section says “Coming soon to Google Play” and website `neabouli.github.io/stealth`; top badge uses `stealthx.tech`.

**Risk:** User confusion and store-review inconsistency.

**Recommendation:** Update README distribution matrix to one current status and one canonical website.

### STX-MED-02 — Feature flags weaken Free flavor security posture

**Evidence:** Free flavor has certificate pinning/root/debugger/emulator/hardware-keystore requirements disabled, telemetry/third-party analytics enabled, call recording allowed.

**Risk:** Public beta/free users receive weaker security than marketing implies. Claims like “no compromises” may not hold for free flavor.

**Recommendation:** Document flavor security matrix prominently. For privacy brand consistency, consider enabling core protections in all flavors and reserving only UX/business limits for paid tiers.

### STX-MED-03 — FCM and third-party service privacy claims need precise metadata language

**Evidence:** README says no metadata/no logs; third-party table states FCM receives caller name/session ID.

**Risk:** Absolute privacy claims collide with operational push-notification metadata.

**Recommendation:** Replace “No metadata” with exact data-minimization statement: what metadata exists transiently, where, retention, and why.

### STX-MED-04 — Custom ID flow relies on password ownership model

**Evidence:** `custom_ids.js` stores password hashes/salts server-side and allows transfer/reclaim by password.

**Risk:** Password reuse/support/social engineering risks. Needs rate limiting, lockout and audit logs.

**Recommendation:** Ensure endpoint-level rate limits and timing-safe checks. Consider device-bound recovery or signed ownership proofs.

### STX-MED-05 — Static website likely needs link/status refresh

**Evidence:** `website/` has many pages and invite/payment flows; README and docs reference several domains and old GitHub Pages URLs.

**Risk:** Broken conversion/support flow.

**Recommendation:** Run link checker against `stealthx.tech` and static `website/`; align privacy, terms, audit, payment success and Play/F-Droid links.

## Strengths

- Admin query auth appears hardened in signaling server: `x-admin-key` header only.
- Many prior bugs are documented with comments and audit reports.
- F-Droid flavor explicitly neutralizes Firebase/google-services processing.
- Rust crypto boundary is clearly documented.

## Functional Test Recommendations

- Android: `./gradlew assembleFreeRelease assembleFdroidRelease` plus instrumentation smoke tests.
- Backend: add Jest/Supertest for admin auth, `/ice-servers`, custom ID activation, FCM token persistence, payment webhooks.
- Website: run link checker and Lighthouse against `https://stealthx.tech`.
- Privacy: verify F-Droid build has no Google/Firebase classes in final APK.

## Handover For Developers

Priority order:

1. Move sensitive local artifacts out of repo tree.
2. Resolve license/distribution contradiction.
3. Authenticate ICE/TURN discovery.
4. Split signaling backend into modules with tests.
5. Align privacy claims with actual FCM/TURN/signaling metadata.
