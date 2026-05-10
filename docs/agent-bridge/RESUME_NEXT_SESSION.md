# Resume Next Session — Stealth / SecureCall

Stand: 2026-05-10 — aktualisiert nach BUG-026 CC+Codex Review Session

---

## Sofort Lesen

1. `docs/agent-bridge/PROJECT_STATE.md` — kompletter aktueller Stand
2. `BRIDGE.md` — letzte Einträge (BUG-026 CC Review + Codex Gegenprüfung)
3. `docs/agent-bridge/BUGS.md` — Bug-Status
4. `docs/agent-bridge/TODO.md` — offene Punkte

---

## Git HEAD

- Branch: `main`
- HEAD: `e0875c3` — "docs: update BUG-026 status with architecture review decision"
- Remote: NICHT gepusht (lokal ahead — push wenn bereit)

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
- Health: OK — uptime 25078s

---

## Was in dieser Session erledigt wurde (2026-05-10)

| Was | Commit | Status |
|-----|--------|--------|
| BUG-026 Architektur-Review (CC) | `8c46491` | DONE |
| Codex Gegenprüfung BUG-026 | `bd9210f` | DONE |
| BUGS.md BUG-026 Status-Update | `e0875c3` | DONE |
| Backend Modularisierung Pre-Check | Codex läuft | IN PROGRESS |

---

## BUG-026 Architektur-Entscheidung (FINAL)

**Unified `StealthVpnService` mit Mode-Enum:**
- `WIREGUARD` — identisch zu `GhostVpnService.java` (kein Breaking Change)
- `ESIM_STEERING` — TUN-Packet-Router, braucht Userspace-IP/NAT-Stack (kein kleines Patch)
- `WIREGUARD_VIA_ESIM` — WireGuard-Underlay über eSIM (cleanste Premium-Lösung, keine zweite TUN)

**Implementierung:** v1.1.x. UI bleibt deaktiviert. WIREGUARD_VIA_ESIM zuerst.

---

## Offene Tasks für Gio (manuell)

1. **Play Console Upload:** `~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab`
2. **BUG-029 Retest:** eingehender Call bei aktivem StealthX-VPN → Audio prüfen
3. **Langzeittest:** 20-30 Min Lockscreen → eingehender Call
4. **Hetzner Migration:** 5 Entscheidungsfragen in `docs/agent-bridge/MIGRATION_PLAN.md`
5. **git push:** Commits `8c46491`, `bd9210f`, `e0875c3` noch nicht gepusht

---

## Codex-Task (läuft / nächster)

**Läuft:** Backend-Modularisierung Pre-Check (`BACKEND_MODULARIZATION.md` Review)
- Zirkuläre Imports prüfen
- Shared-State-Aufteilung bewerten
- WS-Handler-Dispatch-Strategie
- Konkrete Break-Points in `server.js`

**Nächster nach Codex-Output:** Entscheidung ob Backend-Modularisierung grünes Licht bekommt.

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
