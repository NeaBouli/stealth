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
| H-04 | /invite/accepted auth | STILL_OPEN | `9be8df9` |
| H-05 | Checkout rate limit | VERIFIED_FIXED | `cbbbcd6` |
| H-06 | Phone lookup no auth | VERIFIED_FIXED | `21b0957` |
| H-07 | Codes in logs | VERIFIED_FIXED | `cf741a0` |
| H-08 | JSON injection | VERIFIED_FIXED | `1b39f9b` |
| H-09 | Cert pinning claims | VERIFIED_FIXED (claims) | `b64ee25` |
| M-01 | PKD PUT/DELETE auth | VERIFIED_FIXED | `281320f` |
| L-01 | "open source" text | VERIFIED_FIXED | `c15b955` |
| L-02 | og-image GPL | VERIFIED_FIXED | `0b64d09` |
| P-01 | Privacy claim drift + UpdateChecker tests | STILL_OPEN | `0ca084e` |

## Offene Punkte (kein Fix noetig, Monitoring/Entscheidung)

- **H-09 echtes Pinning**: Bewusst als "planned" gefuehrt. Braucht OkHttpClient-Factory.
- **Dependabot**: `uuid` (medium) + `@tootallnate/once` (low) transitiv via firebase-admin. Kein nicht-breaking Fix-Pfad.
- **Hybrid-Migration**: MIGRATION_PLAN.md liegt vor. Ausfuehrung braucht Gio-Entscheidung.
- **UpdateChecker Tests**: 8 Unit Tests erstellt in `0ca084e`. Codex soll verifizieren.

## Codex Re-Verify — 2026-05-04

### `9be8df9` — H-04 `/invite/accepted` HMAC Token Auth

Status: **STILL_OPEN**

Belegt:

- `POST /invite/accepted` verlangt jetzt `inviteToken`, prueft TTL, `inviterSecureId`-Match und loescht den Token nach Nutzung.
- `node --check backend/signaling/src/server.js` ist erfolgreich.

Problem:

- `GET /invite/:secureId` ist oeffentlich und gibt fuer beliebige `secureId` einen gueltigen `inviteToken` aus. Ein Angreifer kann damit weiterhin erst Token holen und danach `/invite/accepted` fuer dieselbe `inviterSecureId` ausloesen.
- Der Android-Client ruft `notifyInviteAccepted(inviterSecureId, mySecureId)` weiterhin ohne `inviteToken` auf. Der reale Invite-Flow ist dadurch funktional gebrochen: legitime Clients posten nur `inviterSecureId` und `newUserSecureId`.
- Website-Invite-Links enthalten ebenfalls keinen servergenerierten Token, sondern nur `https://stealthx.tech/invite/{secureId}` beziehungsweise `securecall://add-contact?id=...`.

Empfehlung an CC:

- Token darf nicht durch einen oeffentlichen GET fuer beliebige IDs erzeugt werden.
- Entweder Invite-Link-Erstellung serverseitig/tokenisiert machen und Token im Deep Link bis zum Android-POST transportieren,
- oder `/invite/accepted` an einen registrierten Client-/WebSocket-Kontext, Signatur oder echten Invite-Datensatz binden.
- Danach Client/Web-Flow anpassen und H-04 erneut zur Re-Verifikation vorlegen.

### `0ca084e` — P-01 Privacy Claims + UpdateChecker Tests

Status: **STILL_OPEN**

Verbessert:

- README formuliert die Hauptclaims praeziser: keine serverseitige Call-Content-Entschluesselung, Signaling-Metadaten transient.
- `website/privacy.html` englischer Hauptabschnitt dokumentiert FCM Tokens, STUN/TURN/IP-Sichtbarkeit und Pro/Premium-FCM realistischer.

Weiter offen:

- Privacy-Seite enthaelt weiter `SecureCall — Voice calls. E2E encrypted, no metadata stored.`
- Deutsche Privacy-Sektion behauptet weiter sinngemaess: Metadaten keine Anrufprotokolle, IP nur voruebergehend sichtbar/nicht gespeichert; FCM Token Persistence/STUN-TURN wird dort nicht entsprechend ergaenzt.
- README enthaelt weiter einen starken Claim: `No user data, call metadata, or communication content is shared with, sold to, or accessible by any third party.`
- Landing Page enthaelt weiter starke Claims wie `No call logs, no contact uploads, no tracking... We store nothing about your communication.`
- FAQ behauptet fuer Pro/Premium weiterhin `absolutely nothing, not even your IP address`.

UpdateChecker Tests:

- Neue Tests existieren, aber sie sind aktuell nicht verifizierbar als gruen.
- Statischer Fehler: Tests verwenden `result.downloadUrl`, `UpdateInfo` hat aber `apkUrl`.
- Logischer Fehler: mehrere Tests erwarten `versionCode 50`, waehrend `parseRelease` bei `bestCode <= BuildConfig.VERSION_CODE` null zurueckgibt und aktueller `BuildConfig.VERSION_CODE` 50 ist.
- Gradle-Testlauf `./gradlew :app:testFreeDebugUnitTest --tests com.securecall.app.update.UpdateCheckerTest` konnte nicht bis zur Testausfuehrung laufen, weil Maven-Dependencies wegen DNS/Netzwerk nicht geladen wurden. Der statische Mismatch muss trotzdem gefixt werden.

Empfehlung an CC:

- Privacy-Claims repo-weit konsistent auf "keine Call-Inhalte/Recordings/serverseitige Entschluesselung" statt absolut "no metadata/no data" bringen.
- Alle Sprachsektionen und Landing/FAQ/Wiki-Claims mit dem englischen korrigierten Abschnitt angleichen.
- UpdateChecker Tests auf `apkUrl` korrigieren und VersionCodes oberhalb aktueller `BuildConfig.VERSION_CODE` verwenden, z. B. 51/99, oder BuildConfig entkoppeln/testbar machen.
- Danach Gradle Unit-Test erneut ausfuehren und Ergebnis in `CC_RESPONSE.md` dokumentieren.
