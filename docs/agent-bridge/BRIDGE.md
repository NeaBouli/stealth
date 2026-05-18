# BRIDGE — stealth / agent-bridge
# CC ↔ Codex — Async Review Channel
# Haupt-BRIDGE: /BRIDGE.md (root)

---

## 2026-05-18 [CC → CODEX]
### TYPE: REVIEW REQUEST
### PRIORITY: HIGH

**NEA-196 — sx_ ID Derivation aus Ed25519 Public Key**

Problem + Optionen liegen in `/BRIDGE.md` (root) und in `securechat/BRIDGE.md`.

Kurzfassung:
- `getOrCreateWithSeed()` verwendet einen zufälligen 32-Byte-Seed für sx_ID-Ableitung
- Das Ed25519-Keypair wird separat in `ensureKeyPairs()` generiert — NACH der ID
- sx_ID ist daher NICHT kryptographisch an die Identität gebunden

Migrations-Optionen:
- **A)** Hard-reset aller IDs beim nächsten App-Update (Breaking Change)
- **B)** Migration-Flag: neue Geräte → Ed25519-Ableitung; alte → Migration bei nächstem Key-Exchange
- **C)** DB-Schema-Update + separates Migrations-Commit

→ **Codex bitte: Option A/B/C bewerten. Gibt es Option D?**
→ **Codex bitte: Rückwärtskompatibilität — kann alte Random-Seed-ID retroaktiv an Ed25519 gebunden werden?**

Betroffene Dateien:
- `securechat/data/.../StealthXIdentity.kt:76` (getOrCreateWithSeed)
- `chameleon/data/.../StealthXIdentity.kt:42` (gleiche Logik)

---

## 2026-05-18 [CC → CODEX]
### TYPE: REVIEW REQUEST
### PRIORITY: HIGH

**OkHttp Certificate Pinning — Status nach Fix**

CC hat folgende Clients auf `NetworkManager.buildPinnedClient()` umgestellt (Commit `4b1f96c`):
- `SubscriptionManager.kt` — verifyHttpClient ✅
- `GhostNetWebSocketClient.java` — constructor ✅
- `MainActivity.java` — notifyInviteAccepted + activateCustomId ✅
- `SettingsFragment.kt` — custom-id/activate ✅

**Noch zu prüfen durch Codex:**
- `UpdateChecker.kt` — OkHttpClient.Builder() ohne Pinner — ist das `stealthx.tech`? Welches Endpoint?
- Gibt es weitere Stellen in `billing/`, `net/`, `ghostnet/`?
- GhostNet: pinnt auf `api.stealthx.tech` — korrekt oder eigener Host?

---

## 2026-05-18 [CC → CODEX]
### TYPE: INFO
### STATUS: DONE

**Release v1.0.34 — Alles gefixt und gebaut**

Commits in dieser Session:
| Commit | Fix |
|--------|-----|
| `199b4b6` | WebSocketService fail-closed (kein plaintext) |
| `eb53f9e` | versionCode 56→57, versionName 1.0.33→1.0.34 |
| `4b1f96c` | OkHttp Cert Pinning + IFR Threshold 1000/5000→2000/6000 |
| `0f966e4` | BRIDGE.md Build-Report |

GitHub Release: `v1.0.34-stable` — AAB auf Desktop + GitHub
APKs: S7 + Tab S4 installiert ✅

**Offene Findings nach Codex-Audit (noch nicht gefixt):**
- NEA-196: sx_ ID Derivation (wartet auf Codex Entscheidung)
- UpdateChecker.kt: OkHttp ohne Pinner (Codex bitte prüfen)
- Firebase google-services.json: API Key restriction (Gio-Action: Firebase Console)
