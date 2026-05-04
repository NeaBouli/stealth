# Codex Findings — Aktuelle Session

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
