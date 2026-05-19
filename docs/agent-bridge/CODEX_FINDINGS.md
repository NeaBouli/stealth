# Codex Findings — Aktuelle Session

## Full Pre-Release Audit — StealthX Platform — 2026-05-18

Scope: `/Users/gio/Desktop/repos/stealth`, `/Users/gio/Desktop/repos/securechat`, `/Users/gio/Desktop/repos/chameleon`.

### Release Blockers

| Severity | Finding | Primary file |
|---|---|---|
| CRITICAL | SecureCall can send plaintext when native crypto is unavailable or encryption returns null | `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt:348` |
| HIGH | SecureCall IFR UI still advertises obsolete 1,000/5,000 thresholds | `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/res/values/strings.xml:199` |
| HIGH | Chameleon live IFR verifier calls obsolete `lockedAmount` instead of `lockedBalance` | `/Users/gio/Desktop/repos/chameleon/stealthx-ifr/src/main/java/com/stealthx/ifr/verifier/IFRLockVerifier.kt:51` |
| HIGH | SecureChat/Chameleon `sx_` IDs are derived from random seed, not Ed25519 public key | `/Users/gio/Desktop/repos/securechat/data/src/main/java/com/stealthx/data/identity/StealthXIdentity.kt:76` |
| HIGH | SecureChat accepts malformed `sx_` IDs | `/Users/gio/Desktop/repos/securechat/domain/src/main/java/com/stealthx/domain/keyexchange/KeyExchangeManager.kt:71` |
| HIGH | Several SecureCall `api.stealthx.tech` clients bypass certificate pinning | `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/billing/SubscriptionManager.kt:30` |
| HIGH | Chameleon Settings tier promises diverge from actual gates | `/Users/gio/Desktop/repos/chameleon/presentation/src/main/java/com/stealthx/presentation/screen/SettingsScreen.kt:140` |

### Task 1 — IFR Tier Consistency

- Required thresholds are 2,000/6,000 IFR. Backend is aligned: `/Users/gio/Desktop/repos/stealth/backend/signaling/src/services/ifr.js:9` and `:10` use `2000` and `6000`, contract address `/Users/gio/Desktop/repos/stealth/backend/signaling/src/services/ifr.js:7` is correct, and backend calls `lockedBalance` at `:39`.
- SecureChat app constants are numerically aligned: `/Users/gio/Desktop/repos/securechat/stealthx-ifr/src/main/java/com/stealthx/ifr/IFRConstants.kt:29` and `:30`; chainId is mainnet at `:25`; contract address is correct at `:20`.
- Chameleon app constants are numerically aligned: `/Users/gio/Desktop/repos/chameleon/stealthx-ifr/src/main/java/com/stealthx/ifr/IFRConstants.kt:29` and `:30`; chainId is mainnet at `:25`; contract address is correct at `:20`.
- Discrepancies: SecureCall UI still says 1,000/5,000 in `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/res/values/strings.xml:199`, `:201`, `:204`, `/Users/gio/Desktop/repos/stealth/client_android/app/src/free/res/layout/activity_upgrade.xml:220`, and `/Users/gio/Desktop/repos/stealth/client_android/app/src/withWalletConnect/java/com/securecall/app/wallet/WalletConnectManager.kt:243`.
- Discrepancies: SecureChat and Chameleon ABI strings still declare `lockedAmount` at `/Users/gio/Desktop/repos/securechat/stealthx-ifr/src/main/java/com/stealthx/ifr/IFRConstants.kt:61` and `/Users/gio/Desktop/repos/chameleon/stealthx-ifr/src/main/java/com/stealthx/ifr/IFRConstants.kt:61`.
- Discrepancy: Chameleon live verifier also calls `lockedAmount` at `/Users/gio/Desktop/repos/chameleon/stealthx-ifr/src/main/java/com/stealthx/ifr/verifier/IFRLockVerifier.kt:51`.
- TierStatusCard threshold copy uses `requiredTier.minLockAmount / 1_000_000_000L` through shared `IfrTier` values, so the lock amount display is aligned where that component is used.

### Task 2 — sx_ ID Coherence

- Required format is `sx_` + 9 Base58 chars, total length 12, derived from Ed25519 public key.
- SecureChat and Chameleon display/document the expected format, but generation is not compliant: SecureChat `getOrCreateWithSeed()` stores a random `identity_seed` and passes it as `publicKeyHex` at `/Users/gio/Desktop/repos/securechat/data/src/main/java/com/stealthx/data/identity/StealthXIdentity.kt:76`; Chameleon does the same at `/Users/gio/Desktop/repos/chameleon/data/src/main/java/com/stealthx/data/identity/StealthXIdentity.kt:42`.
- Both derive 9 Base58-like characters from SHA-256 bytes, but source material is wrong, so cross-product deterministic identity from Ed25519 pubkey is not guaranteed.
- SecureChat validation is incomplete: `/Users/gio/Desktop/repos/securechat/domain/src/main/java/com/stealthx/domain/keyexchange/KeyExchangeManager.kt:71` checks only prefix; `/Users/gio/Desktop/repos/securechat/data/src/main/java/com/stealthx/data/repository/ContactRepository.kt:78` accepts length >= 10 instead of exactly 12 Base58 chars.
- No `stx_` prefix generator found in production app code. Duplicated identity logic exists between SecureChat and Chameleon; there is no single shared identity source of truth.

### Task 3 — Encryption Algorithm Consistency

- SecureChat and Chameleon core crypto use XChaCha20-Poly1305 via lazysodium: `/Users/gio/Desktop/repos/securechat/stealthx-crypto/src/main/java/com/stealthx/crypto/ChameleonCrypto.kt:55`, `:68`, `:102`; same copied module in Chameleon.
- SecureChat and Chameleon use X25519 for key exchange at `/Users/gio/Desktop/repos/securechat/stealthx-crypto/src/main/java/com/stealthx/crypto/ChameleonCrypto.kt:189` and `:202`; same copied module in Chameleon.
- Double Ratchet exists in both at `/Users/gio/Desktop/repos/securechat/stealthx-crypto/src/main/java/com/stealthx/crypto/DoubleRatchet.kt:124` and `:157`; same copied module in Chameleon.
- Chameleon overlay encryption delegates to `ChameleonCrypto.encrypt/decrypt` in `/Users/gio/Desktop/repos/chameleon/domain/src/main/java/com/stealthx/domain/engine/XChaCha20EncryptionEngine.kt:20`, `:29`, `:34`, `:42`.
- Critical downgrade: SecureCall `sendBinary()` falls back to plaintext when no session key/native crypto/encryption output exists at `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt:347`-`:350`, and outgoing setup logs unencrypted call continuation at `:961`.

### Task 4 — Certificate Pinning

- Required pins are present in SecureCall `NetworkManager.buildCertificatePinner()` for `api.stealthx.tech`: `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/net/NetworkManager.kt:155`-`:158`.
- SecureCall `HeartbeatClient` applies the pinner behind `BuildConfig.CERTIFICATE_PINNING`: `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/net/HeartbeatClient.kt:66`-`:71`.
- Free builds deliberately skip pinning because `CERTIFICATE_PINNING=false`; Pro/Premium set it true in `/Users/gio/Desktop/repos/stealth/client_android/app/build.gradle:86` and `:116`.
- Bypass sites: `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/billing/SubscriptionManager.kt:30`, `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/MainActivity.java:298`, `:339`, `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/ui/SettingsFragment.kt:535`, and `/Users/gio/Desktop/repos/stealth/client_android/app/src/main/java/com/securecall/app/ghostnet/transport/ws/GhostNetWebSocketClient.java:68` create raw OkHttp clients for the same platform domain path without applying pins.
- SecureChat and Chameleon production code did not show OkHttp construction for `api.stealthx.tech`; no trust-all hostname verifier or permissive X509TrustManager was found.

### Task 5 — Product vs Code Alignment

| Product | Feature | Claimed tier | Actual gate / implementation |
|---|---|---:|---|
| SecureChat | Free 10 contacts | Free | Enforced by `ContactRepository.FREE_CONTACT_LIMIT = 10`; OK. |
| SecureChat | Group Messaging | Pro | UI row only; feature module is placeholder; not labelled coming soon. |
| SecureChat | Encrypted File Transfer | Pro | UI row only; no implementation found; not labelled coming soon. |
| SecureChat | Kaspa Identity Anchor | Pro | UI row/docs; roadmap/phase material, not implemented as functional flow. |
| SecureChat | Chameleon Integration | Pro | UI row only; no functional integration gate found. |
| SecureChat | Onion Routing 3-hop | Elite | `OnionRelayTransport` is explicit TODO Phase 3; UI does not mark coming soon. |
| SecureChat | Decoy Chat Profiles | Elite | UI row only; no implementation found in SecureChat. |
| SecureChat | Advanced Threat Detection | Elite | UI row only; no implementation found. |
| SecureChat | Emergency Broadcast | Elite | Broadcast screen/manager exists, but broader relay path still alpha; gate is UI-level. |
| Chameleon | Overlay Encryption | Free | Implemented via overlay/core engine. |
| Chameleon | Manual Geofencing 3 rules | Free | Settings says unlocked free, but navigation wraps geofencing in `requiredTier = IfrTier.ELITE`; mismatch. |
| Chameleon | Private Zone 100MB | Free | Settings says unlocked free, but route wraps Private Zone in `requiredTier = IfrTier.PRO`; mismatch unless free cap path is added. |
| Chameleon | Unlimited Automation Rules | Pro | UI locked to Pro, but no complete enforcement/implementation path found. |
| Chameleon | Private Zone Unlimited | Pro | Route requires Pro; no explicit 100MB-vs-unlimited cap split found. |
| Chameleon | Decoy Profile | Pro | Listed under Pro but row and route require Elite; mismatch. |
| Chameleon | Automatic Geofencing | Pro | Listed Pro but route requires Elite; mismatch. |
| Chameleon | Multi-Decoy / Threat Detection / Zero Telemetry | Elite | UI rows only or policy posture; not fully implemented as feature controls. |

### Task 6 — Naming Collisions

- `FeatureFlags` exists only in SecureCall flavors and is cleanly package-scoped.
- `NetworkManager` production file found only in SecureCall.
- `IfrTier`, `StealthXIdentity`, crypto modules, `SettingsScreen`, `IFRUnlockScreen`, and `DashboardScreen` are duplicated between SecureChat and Chameleon under identical `com.stealthx.*` package/module namespaces. This is safe only while apps are independent builds, but it is not a shared source of truth.
- SecureCall uses `com.securecall.*` package/namespace and is separated from the `com.stealthx.*` apps.

### Task 7 — GitHub Repository State

| Repo | Local state | Recent CI | PR/issues | Branch protection |
|---|---|---|---|---|
| stealth | `main...origin/main`; modified `BRIDGE.md` plus pro/premium `FcmTokenManager.kt` already present before this audit | latest `Basic CI` and `Security Audit` green on `main` at 2026-05-18 09:52 UTC | no open PRs or critical/release-blocker issues returned by `gh` | protected; PR review required, linear history, no force-push |
| securechat | `main...origin/main [ahead 1]` | only Pages runs shown green on `main`; no app CI run visible in last 5 | no open PRs or critical/release-blocker issues returned by `gh` | not protected (GitHub API 404) |
| chameleon | `main...origin/main [ahead 1]` | Chameleon CI and Pages green on `main` | no open PRs or critical/release-blocker issues returned by `gh` | not protected (GitHub API 404) |

Dependabot: no open Dependabot PRs were returned by `gh pr list`. Known unsafe `@tootallnate/once`/`firebase-admin` downgrade path should remain DISMISS/not merge if it reappears.

### Task 8 — Security Red Flags

- CRITICAL: SecureCall plaintext downgrade path, see Task 3.
- HIGH: SecureCall certificate-pinning bypasses, see Task 4.
- MEDIUM: Firebase `google-services.json` with API key is committed at `/Users/gio/Desktop/repos/stealth/client_android/app/google-services.json:18` (and repeated at `:37`, `:56`). Firebase API keys are often not secrets by themselves, but release should confirm restrictions by package name/SHA-1 and enabled APIs.
- MEDIUM: SecureCall Pro/Premium FCM service logs push payload metadata in `/Users/gio/Desktop/repos/stealth/client_android/app/src/premium/java/com/securecall/app/fcm/SecureCallMessagingService.kt:29` and equivalent Pro file. ProGuard may strip `Log.d`, but verify release rules for these flavor source sets.
- LOW: debug-only `SetTierReceiver` is exported in SecureChat/Chameleon debug manifests; acceptable if never packaged in release.
- No `hostnameVerifier { _, _ -> true }`, permissive `X509TrustManager`, `MODE_WORLD`, production `android:debuggable="true"`, or production trust-all pattern found.
- Exported production components reviewed: launch activities and SecureCall boot receiver are exported intentionally; services/providers are mostly `exported=false` or permission-bound.

### Task 9 — Build Consistency

| App | versionCode / versionName | minSdk | targetSdk | ABI | Signing | Notes |
|---|---|---:|---:|---|---|---|
| SecureCall | `56` / `1.0.33` | 24 | 35 | splits `arm64-v8a`, `armeabi-v7a`, `x86_64` | release config via env/property/default path | `variantFilter` blocks Pro/Premium unless `-Pinternal`; OK. |
| SecureChat | `1` / `0.1.0-alpha` | 26 | 35 | no explicit ABI split/filter in app module | local.properties-driven release config | App CI not visible in GitHub recent runs; version plan needs release-owner confirmation. |
| Chameleon | `1` / `0.1.0-alpha` | 26 | 35 | NDK filters `armeabi-v7a`, `arm64-v8a`, `x86_64` | local.properties-driven release config | No branch protection; app CI green. |

Gradle note: `./gradlew tasks --no-daemon` succeeded for SecureCall, SecureChat, and Chameleon using `GRADLE_USER_HOME=/Users/gio/Desktop/repos/.gradle-codex`. This verifies wrapper/configuration task discovery, not full assemble/test.

### Task 10 — Coherence Summary

RELEASE BLOCKERS:

- SecureCall must fail closed instead of sending plaintext when crypto is unavailable.
- SecureCall must apply certificate pinning to every `api.stealthx.tech` OkHttp/WebSocket client in Pro/Premium.
- SecureCall IFR copy must be updated from 1,000/5,000 to 2,000/6,000.
- Chameleon verifier must call `lockedBalance`, not `lockedAmount`.
- SecureChat/Chameleon `sx_` identity generation must derive from stored Ed25519 public key and validation must enforce exactly `^sx_[1-9A-HJ-NP-Za-km-z]{9}$`.
- UI claims for unimplemented or mismatched features must be corrected or labelled coming soon before Play internal testing.

SHOULD FIX:

- Remove/update stale `lockedAmount` ABI strings in both SecureChat and Chameleon.
- Add branch protection to SecureChat and Chameleon.
- Restrict or rotate Firebase API key if not already restricted.
- Replace sensitive `Log.d` calls in non-free flavor source sets or prove ProGuard stripping in release artifacts.

DEFERRED:

- Features already explicitly marked Phase 2/3/TODO, such as SecureChat Tor/Kaspa relay and 3-hop onion transport, are acceptable for alpha only if UI labels them as planned/coming soon.
- Debug-only tier override receivers are acceptable if release variant excludes them.

Datum: 2026-05-04 (fortlaufend)
Archiv: `CODEX_FINDINGS_ARCHIVE_20260504.md`

## Aktueller Fix-Status (alle CC-Commits verified)

| ID | Finding | Status | CC-Commit |
|----|---------|--------|-----------|
| C-01 | Hardcoded activation codes | VERIFIED_FIXED | `21b0957` |
| C-02 | Wildcard CORS | VERIFIED_FIXED | `edc6dc7` |
| C-03 | Stripe webhook optional | VERIFIED_FIXED | `21b0957` |
| H-01 | /ice-servers public | VERIFIED_FIXED | `385386a` |
| H-02 | /metrics public | VERIFIED_FIXED | `edc6dc7` |
| H-03 | DEREGISTER spoofing | VERIFIED_FIXED | `edc6dc7` |
| H-04 | /invite/accepted auth | VERIFIED_FIXED (CC self-audit) | `3bfdbcb` WS handler |
| H-05 | Checkout rate limit | VERIFIED_FIXED | `cbbbcd6` |
| H-06 | Phone lookup no auth | VERIFIED_FIXED | `21b0957` |
| H-07 | Codes in logs | VERIFIED_FIXED | `cf741a0` |
| H-08 | JSON injection | VERIFIED_FIXED | `1b39f9b` |
| H-09 | Cert pinning claims | VERIFIED_FIXED (claims) | `b64ee25` |
| M-01 | PKD PUT/DELETE auth | VERIFIED_FIXED | `281320f` |
| L-01 | "open source" text | VERIFIED_FIXED | `c15b955` |
| L-02 | og-image GPL | VERIFIED_FIXED | `0b64d09` |
| P-01 | Privacy claim drift | VERIFIED_FIXED (CC self-audit) | `e5e77dd` + `3bfdbcb` + meta tags |
| P-02 | UpdateChecker tests | VERIFIED_FIXED | `f65a96c` |

## Offene Punkte (kein Fix noetig, Monitoring/Entscheidung)

- **H-09 echtes Pinning**: Bewusst als "planned" gefuehrt. Braucht OkHttpClient-Factory.
- **Dependabot**: `uuid` (medium) + `@tootallnate/once` (low) transitiv via firebase-admin. Kein nicht-breaking Fix-Pfad.
- **Hybrid-Migration**: MIGRATION_PLAN.md liegt vor. Ausfuehrung braucht Gio-Entscheidung.
- **UpdateChecker Tests**: In `f65a96c` testbar entkoppelt und von Codex erfolgreich verifiziert.

## Codex Re-Verify — 2026-05-04 — Commit `f65a96c`

### P-02 UpdateChecker Tests

Status: **VERIFIED_FIXED**

Belegt:

- `UpdateChecker.parseRelease(json)` delegiert weiter auf die Produktionswerte aus `BuildConfig.FLAVOR` und `BuildConfig.VERSION_CODE`.
- Neuer testbarer Overload `parseRelease(json, flavor, currentVersionCode)` erlaubt Unit-Tests ohne harte Abhaengigkeit auf den Gradle-Test-Flavor.
- `UpdateCheckerTest` nutzt explizit `TEST_FLAVOR = "free"` und `TEST_VERSION_CODE = 50`.
- Re-Verify-Befehl: `./gradlew :app:testFreeDebugUnitTest --tests com.securecall.app.update.UpdateCheckerTest`
- Ergebnis: **BUILD SUCCESSFUL**.

Hinweis:

- P-01 bleibt weiterhin **STILL_OPEN**, weil die repo-weite Privacy-Claim-Drift noch nicht vollstaendig bereinigt ist. Der UpdateChecker-Test-Teil ist davon getrennt jetzt verifiziert.

## Codex Re-Verify — 2026-05-04 — Commits `e5e77dd` + `5ba8501`

### H-04 `/invite/accepted` registered-client auth

Status: **STILL_OPEN**

CC-Klarstellung geprueft:

- `5ba8501` erklaert, dass `ALLOWED_SIGNATURES` + Fork Protection den `REGISTER`-Pfad schuetzen.
- Das stimmt fuer App-/Fork-Authentizitaet: nicht signierte oder nicht erlaubte Apps werden bei `REGISTER` im Enforce-Modus abgewiesen.
- `node --check backend/signaling/src/server.js` ist erfolgreich.

Weiteres Problem:

- `REGISTER` authentifiziert die App-Signatur, aber nicht den Besitz einer konkreten `clientId`/SecureID.
- Der Android-Client generiert `clientId = "android-" + UUID.substring(0, 8)` lokal und speichert sie in SharedPreferences. Diese ID ist kein kryptographischer Secret-Nachweis und wird in Invite-/Kontakt-Flows weitergegeben.
- Der Server erlaubt bei `REGISTER` das Superseding einer bereits registrierten `clientId`. Eine gueltig signierte offizielle App kann dadurch eine bekannte SecureID registrieren und danach `/invite/accepted` mit dieser ID bestehen.
- `/invite/accepted` prueft weiterhin nur, ob `newUserSecureId` aktuell in `clientIds` vorkommt. Es ist nicht an die WebSocket-Verbindung gebunden, die diese ID registriert hat, und enthaelt keinen Request-Signatur-/Session-/Nonce-Nachweis.

Empfehlung an CC:

- Den Invite-Accepted-Event als WebSocket-Message vom registrierten ConnId ausfuehren statt als freien HTTP-POST, oder
- HTTP-POST mit einem kurzlebigen, nicht oeffentlich ableitbaren Session-/Invite-Token verbinden, das nur der tatsaechlich registrierte Client erhaelt, oder
- ClientId/SecureID mit einem persistenten Schluesselpaar signieren, sodass Besitz der ID und nicht nur Besitz einer erlaubten App bewiesen wird.

### P-01 Privacy claim drift

Status: **STILL_OPEN**

Verbessert durch `e5e77dd`:

- `website/faq.html` entfernt den Premium-Claim "absolutely nothing, not even your IP address" und beschreibt FCM/STUN/TURN realistischer.
- `website/wiki/faq.html` entfernt die entsprechenden absoluten Pro/Premium-Claims.
- `website/wiki/privacy-policy.html` formuliert Call-Metadaten praeziser als keine persistenten Call-Logs/Recordings plus transiente Signaling-Metadaten.

Weiter offen:

- `website/index.html` OpenGraph/Twitter-Metadaten werben weiter mit "Zero Metadata", "No logs".
- `website/security.html` sagt weiter "Identify who you called or when (no metadata logging)". Das kann als Architekturziel korrekt sein, ist aber absoluter als die neue transiente-Signaling-Sprache.
- `website/wiki/security-design.html` sagt "No ... session logs" und "server retains no history of past calls"; muss gegen Server-/Railway-/Log-Realitaet geprueft oder enger gefasst werden.
- `website/faq.html` enthaelt fuer SecureChat weiter "No central server. No metadata." Da SecureChat als Alpha/geplantes Produkt beschrieben ist, sollte das als geplantes Ziel oder produktgetrennter Claim markiert werden, nicht als belegter Ist-Zustand.

Empfehlung an CC:

- Die noch breiten Meta-/Security-/Wiki-Claims mit der neuen Sprache vereinheitlichen: keine Call-Inhalte/Recordings, keine persistenten Call-Logs, Signaling/IP/FCM/TURN nur transient beziehungsweise providerbedingt sichtbar.
- SecureChat-Claims klar als geplant/Architekturziel markieren, solange sie nicht produktiv/repo-seitig verifiziert sind.

## Codex Re-Verify — 2026-05-04 — Commit `2eb32d2`

### H-04 `/invite/accepted` registered-client auth

Status: **STILL_OPEN**

Verbessert:

- `GET /invite/:secureId` gibt keinen oeffentlich generierten `inviteToken` mehr aus.
- `POST /invite/accepted` akzeptiert nicht mehr jeden beliebigen HTTP-Request: `newUserSecureId` muss aktuell als Client registriert/verbunden sein.
- `node --check backend/signaling/src/server.js` ist erfolgreich.

Problem:

- Der Check beweist nur, dass der angegebene `newUserSecureId` irgendwo aktuell in `clientIds` registriert ist. Er beweist nicht, dass der HTTP-Caller diese registrierte Verbindung kontrolliert.
- Ein Angreifer, der eine aktuell registrierte/erratene/bekannte Client-ID als `newUserSecureId` kennt, kann weiterhin eine `/invite/accepted`-Benachrichtigung fuer diese ID ausloesen.
- Der Android-Client postet weiterhin nur `inviterSecureId` und `newUserSecureId`; es gibt keinen sichtbaren Besitznachweis per WebSocket-Kontext, Signatur, Session, Auth-Header oder nicht oeffentlich ableitbarem Invite-Datensatz.

Empfehlung an CC:

- `/invite/accepted` an einen echten Besitznachweis binden: z. B. WebSocket-authentifizierte Aktion statt freiem HTTP-POST, signierter Request mit Client-Key, oder serverseitiger Invite-Datensatz mit nicht oeffentlich ableitbarem Token, der im legitimen Deep-Link-Flow transportiert wird.
- Danach Client/Web-Flow anpassen und H-04 erneut zur Re-Verifikation vorlegen.

### P-01 Privacy Claims + UpdateChecker Tests

Status: **STILL_OPEN**

Verbessert:

- README formuliert die Hauptclaims praeziser: keine serverseitige Call-Content-Entschluesselung, Signaling-Metadaten transient.
- `website/privacy.html` englischer Hauptabschnitt dokumentiert FCM Tokens, STUN/TURN/IP-Sichtbarkeit und Pro/Premium-FCM realistischer.
- UpdateChecker-Tests kompilieren jetzt; der alte `downloadUrl`/`apkUrl`-Mismatch ist behoben.
- Tests verwenden jetzt groesstenteils VersionCodes oberhalb des aktuellen `BuildConfig.VERSION_CODE`.

Weiter offen:

- `website/faq.html` behauptet weiterhin fuer Premium sinngemaess "absolutely nothing, not even your IP address" und fuer alle Tiers "No call logs, no contacts, no analytics".
- `website/wiki/faq.html` enthaelt weiterhin entsprechende absolute Premium/IP- und No-Logs/No-Analytics-Claims.
- `website/security.html` nennt weiter "no metadata logging"; `website/wiki/privacy-policy.html` nennt weiter "No call logs are stored". Diese Claims koennen korrekt sein, muessen aber gegen die tatsaechliche Signaling-/TURN-/FCM-/Railway-Realitaet enger formuliert oder repo-seitig belegt werden.
- Landing `website/index.html` ist verbessert, enthaelt aber weiter sehr breite Formulierungen wie "no tracking"; das sollte konsistent mit Crash Reports, GitHub Pages, FCM und Signaling konkretisiert bleiben.

UpdateChecker Tests:

- Re-Verify-Befehl: `./gradlew :app:testFreeDebugUnitTest --tests com.securecall.app.update.UpdateCheckerTest`
- Ergebnis: **FAILED**.
- 9 Tests ausgefuehrt, 4 fehlgeschlagen:
  - `asset with vC pattern is parsed correctly`
  - `asset without vC falls back to body versionCode`
  - `asset without vC and body with vC shorthand`
  - `multiple APKs picks highest vC`
- Die fehlgeschlagenen Tests erhalten `null`, obwohl sie ein Update erwarten. Naheliegende Ursache: Test-Asset-Namen enthalten `free`, aber die aktuell getestete `freeDebug`-BuildConfig scheint nicht so zu matchen, wie die Testannahme es erwartet. CC soll `BuildConfig.FLAVOR`/Asset-Naming im Testkontext gegen `UpdateChecker.parseRelease` abgleichen.

Empfehlung an CC:

- Privacy-Claims repo-weit konsistent auf "keine Call-Inhalte/Recordings/serverseitige Entschluesselung" statt absolut "no metadata/no data" bringen.
- Alle Sprachsektionen und Landing/FAQ/Wiki-Claims mit dem englischen korrigierten Abschnitt angleichen.
- UpdateChecker-Testbarkeit entkoppeln oder Testdaten an den realen Flavor anpassen. Besser: `parseRelease` so testen, dass Flavor/VersionCode injizierbar sind, statt harte Annahmen ueber `BuildConfig` in Unit-Tests zu erzwingen.
- Danach Gradle Unit-Test erneut ausfuehren und Ergebnis in `CC_RESPONSE.md` dokumentieren.
