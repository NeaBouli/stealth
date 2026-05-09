# Resume Next Session — Stealth / SecureCall

Stand: 2026-05-09 (Abend) — aktualisiert nach CC Autonomous Session

---

## Sofort Lesen

1. `docs/agent-bridge/PROJECT_STATE.md` — kompletter aktueller Stand
2. `BRIDGE.md` — CC/Codex Kommunikation dieser Session
3. `docs/agent-bridge/TODO.md` — offene Punkte
4. `docs/BUGS.md` — Bug-Status

---

## Git HEAD

- Branch: `main`
- HEAD: `fe8bd63`
- Remote: in sync mit origin/main

---

## Aktuelle Version

- **versionCode:** 55 | **versionName:** 1.0.33
- **AAB (Play Console):** `~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab` — noch NICHT hochgeladen
- **GitHub Release:** https://github.com/NeaBouli/stealth/releases/tag/v1.0.33
- **APK auf Geräten:** S7 (SM-G930F) + Tab S4 (SM-T835) — v1.0.33-free, vC55001

---

## Backend (Railway)

- HEAD live: `c6965e8`
- URL: `protective-healing-production.up.railway.app`
- FORK_PROTECTION_MODE: `warn`
- Health: OK

---

## Was diese Session erledigt wurde (2026-05-09)

| Was | Commit | Status |
|-----|--------|--------|
| BUG-029: VPN+VPN kein Audio | `30c87fd` | DONE — Codex |
| BUG-031: Kontakt-Verifikation | `5239f71` | DONE — CC |
| EACCES Railway Volume | `c7e17d3` | DONE — CC |
| fast-xml-builder HIGH | `ef28d46` | DONE — CC |
| H-01: ICE Endpoint Auth | bereits | DONE — dokumentiert |
| H-09: Certificate Pinning | `5949617` | DONE — CC |
| Privacy Claims cleanup | `5949617` | DONE — CC |
| nodemailer 8.0.4→8.0.7 | `c6965e8` | DONE — CC |
| Rust deps patch update | `ce60b67` | DONE — CC |
| v1.0.33 Release | `5171ea6` | DONE — CC |

---

## Offene Tasks für Gio (manuell)

1. **Play Console Upload:** `~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab`
2. **BUG-029 Retest:** eingehender Call bei aktivem StealthX-VPN → Audio prüfen
3. **Langzeittest:** 20-30 Min Lockscreen → eingehender Call
4. **Hetzner Migration:** 5 Entscheidungsfragen in `docs/agent-bridge/MIGRATION_PLAN.md`

---

## Nächster Codex-Task (bereit)

**BUG-026 VpnService-Architektur** — in BRIDGE.md als Task hinterlegt.

Codex verfügbar ab: ~06:00-07:00 Uhr (14h nach 16:30 Uhr)

Task-Beschreibung:
- Datei: `client_android/app/src/main/java/com/securecall/app/net/NetworkManager.kt`
- Aktueller Stand: `bindProcessToNetwork()` reicht nicht für paralleles WiFi+Cellular
- Analyse-Auftrag: konkrete Implementierungsstrategie für VpnService-basiertes Traffic Steering
  ohne Konflikt mit bestehendem WireGuard `GhostVpnService.java`
- Vorarbeit steht in `docs/agent-bridge/BUGS.md` (BUG-026 Abschnitt)

---

## Wichtige Dateipfade

| Was | Pfad |
|-----|------|
| Keystore | `~/Desktop/repos/stealth/securecall-release-key.jks` |
| Keystore-PW | in `client_android/gradle.properties` (lokal, nicht in git) |
| JDK 17 | `/tmp/jdk17/Contents/Home` |
| Android SDK | `~/android-sdk` |
| AAB | `~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab` |

---

## Cert-Pin Rotation Reminder

- **Fälligkeit:** 2027-03-12 (Let's Encrypt E7 Intermediate Ablauf)
- **Was:** `client_android/app/src/main/res/xml/network_security_config.xml` updaten
- **Wie:** neuen SPKI SHA-256 via `openssl s_client` + `openssl x509` ermitteln
- **Dann:** Version bump + APK/AAB bauen + Play Console Upload

---

## Grenzen

- Keine Secrets aus Dateien lesen oder ausgeben
- Kein `npm install` — immer `npm ci`
- Kein `git push --force`
- Backend-Modularisierung nur nach Codex-Review und mit Testplan
- firebase-admin NICHT upgraden/downgraden ohne explizite Anweisung
