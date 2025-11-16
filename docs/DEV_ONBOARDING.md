# SecureCall Ecosystem – Developer Onboarding

Willkommen im **SecureCall / Stealth**-Projekt.

Dieses Dokument erklärt neuen Entwickler:innen:

- Ziel & Aufbau des Projekts
- Repository-Struktur
- Arbeitsweise (Tasks, Branches, Commits)
- Wo man anfangen soll (pro Rolle: Android, Crypto, Backend, OS)
- Sicherheits- und Coding-Grundregeln

---

## 1. Projektziel (Kurzfassung)

Das SecureCall Ecosystem ist ein **Hochsicherheits-Kommunikationssystem** mit:

- Android-Client(s) (Free / Pro / Premium)
- einer Core Crypto Engine (Rust)
- Backend-Infrastruktur (Signaling, GhostNet Relays, Key Directory, Management-API)
- gehärtetem Spezial-OS **GHOSTOS BlackRoot** (Military)

Ziel:  
**Verschleierte, Ende-zu-Ende-verschlüsselte Audio-Kommunikation** mit minimaler Angriffsfläche auf App-, Netzwerk- und OS-Ebene.

---

## 2. Repository-Struktur (High-Level)

Wichtige Verzeichnisse:

- `client_android/` – Android-Client(s) (GhostTalk, GhostShield, PhantomLine)
- `core_crypto/` – Rust-basierte Kryptografie-Engine (Identity, Session, AEAD)
- `backend/` – Signaling-Server, GhostNet-Relays, Management-API
- `rom_ghostos/` – GHOSTOS BlackRoot (Build-Chain, Policies, Kernel, OS-Tasks)
- `docs/` – zentrale Dokumentation
  - `PROJECT_PAPER.md` – Gesamtidee & Produktlinien
  - `ARCHITECTURE_OVERVIEW.md` – technische Architektur (Module & Flows)
  - `SECURITY_DESIGN.md` – Threat-Model & Security-Ziele
  - `DEV_ROADMAP.md` – Phasenplanung
  - `DEVELOPMENT_GUIDE.md` – Arbeitsweise, Branching, CI
  - `tasks/` – einzelne Task-Spezifikationen (ANDROID-xx, CRYPTO-xx, OS-xx, SEC-xx, …)
- `.github/workflows/` – Basis-CI (Linting etc.)

Jeder Entwickler sollte **mindestens** diese drei Dateien einmal gelesen haben:

- `docs/PROJECT_PAPER.md`
- `docs/ARCHITECTURE_OVERVIEW.md`
- `docs/SECURITY_DESIGN.md`

---

## 3. Arbeitsmodell – Tasks & Phasen

Die Entwicklung wird gesteuert über:

- **Phasen** (Phase 0–4) – siehe `DEV_ROADMAP.md`
- **Tasks** – eigene Dateien unter `docs/tasks/`

Beispiele:

- `docs/tasks/ANDROID-02.md` – GhostNet Audio Pipeline
- `docs/tasks/CRYPTO-01.md` – Crypto Engine Skeleton
- `docs/tasks/OS-04.md` – Verified Boot & Attestation
- `docs/tasks/SEC-02.md` – IMSI-Catcher Detection

Ein Task-Dokument beschreibt immer:

- Ziel
- Anforderungen
- Verzeichnisstruktur
- Deliverables
- Tests
- Q&A / FAQ

**Regel:**  
> **Kein Feature ohne Task-File.**  
> Wenn etwas Größeres entwickelt wird, gehört zuerst ein Task-Dokument dazu.

---

## 4. Branch- & Commit-Regeln

### 4.1 Branches

- `main` – stabiler Hauptstand, nur geprüfte Änderungen
- `dev` – Integrations-/Feature-Integration (kann später hinzukommen)
- `feature/<TASK-ID>-kurzbeschreibung` – Feature-Branches

**Beispiele:**

- `feature/ANDROID-02-ghostnet-audio`
- `feature/CRYPTO-01-core-skeleton`
- `feature/OS-04-verified-boot`

### 4.2 Commits

Commit-Messages beginnen mit der **Task-ID**:

- `[ANDROID-02] Implement GhostNet Audio MVP`
- `[CRYPTO-01] Add Rust core skeleton`
- `[OS-04] Add AVB config and signing script`

**Regel:**

> Ein Commit sollte möglichst **nur einen Task** betreffen und klein genug sein, um sauber reviewt zu werden.

---

## 5. Rollen / Einstiegspunkte

### 5.1 Android-Entwickler

Startpunkte:

- `docs/tasks/ANDROID-01.md` – UI-Dummy & Projektgrundstruktur
- `docs/tasks/ANDROID-02.md` – GhostNet Audio Pipeline
- `docs/tasks/ANDROID-03.md` – Secure Mode Checks (Free)
- `docs/tasks/ANDROID-04.md` – Policy Engine
- `docs/tasks/ANDROID-05.md` – VPN-Firewall & Ghost Tunnel
- `docs/tasks/ANDROID-06.md` – Device Owner & Whitelist (Premium)

Empfohlene Reihenfolge:

1. ANDROID-01 → Projekt aufsetzen
2. ANDROID-02 → erste verschlüsselte Audio-Verbindung
3. ANDROID-03 → Basic Security Checks
4. ANDROID-04/05 → Pro-Features (Policy, VPN)
5. ANDROID-06 → Premium Device Owner

---

### 5.2 Crypto-Entwickler (Rust)

Startpunkte:

- `docs/tasks/CRYPTO-01.md` – Core Crypto Engine Skeleton
- `docs/tasks/CRYPTO-02.md` – Android-Integration (JNI/FFI)
- spätere Tasks (noch anzulegen): CRYPTO-03+ für Protokolle, KDF, PQC optional

Empfohlene Reihenfolge:

1. CRYPTO-01: Modulstruktur & API-Signaturen
2. CRYPTO-02: Brücke zu Android + Dummy-Encrypt/Decrypt
3. Erweiterungen für Protokolle, Forward Secrecy, Fuzzing

---

### 5.3 Backend-Entwickler

Startpunkte:

- `docs/tasks/BACKEND-01.md` – Signaling Server MVP
- `docs/tasks/BACKEND-02.md` – Stable Signaling & STUN/TURN
- `docs/tasks/BACKEND-03.md` – Relay Optimierung
- `docs/tasks/BACKEND-04.md` – Management-API (Organisation/Policies)

Empfohlene Reihenfolge:

1. BACKEND-01: Minimaler Signaling-Server (REST + WS)
2. BACKEND-02: STUN/TURN-Anbindung & Session-Stabilität
3. BACKEND-03/04: Performance & Enterprise-Features

---

### 5.4 OS-/Kernel-Entwickler

Startpunkte:

- `docs/tasks/OS-01.md` – Build Chain Setup (GHOSTOS)
- `docs/tasks/OS-02.md` – Kernel-Hardening & Ressourcen-Isolation
- `docs/tasks/OS-03.md` – Entfernen von Google-Diensten & Minimal-OS
- `docs/tasks/OS-04.md` – Verified Boot & Attestation
- `docs/tasks/OS-05.md` – GhostNet Kernel Integration
- `docs/tasks/OS-06.md` – Baseband Isolation & Radio Lockdown
- `docs/tasks/OS-07.md` – Ultra Secure Mode (USM)

Empfohlene Reihenfolge:

1. OS-01: Build-Kette & Toolchain
2. OS-02/03: Hardening & Minimal-System
3. OS-04: Verified Boot & Integrität
4. OS-05/06: GhostNet + Baseband-Isolation
5. OS-07: Ultra Secure Mode

---

## 6. Sicherheitsregeln für Devs

**Grundsatz:**  
Sicherheit > Komfort

- keine Klartext-Schlüssel in Logs
- keine Debug-Prints mit sensiblen Daten
- keine fest verdrahteten Test-Passwörter
- keine Third-Party-SDKs ohne Review (Analytics, Ads, Crashlytics etc.)
- alle sicherheitsrelevanten Änderungen brauchen Code-Review
- kryptografische Funktionen nur aus der Core Crypto Engine nutzen, nicht „mal schnell selbst bauen“

---

## 7. CI & Qualität

Aktuell:

- `.github/workflows/ci-basic.yml` – Basis-Linting (Markdown, YAML)

Geplant (später):

- Unit-Tests pro Modul (Crypto, Android, Backend, OS)
- Security-Scans (Static Analysis)
- Reproducible-Build-Checks für GHOSTOS

**Regel:**  
> PRs sollten CI-grün sein, bevor sie in `main` gemerged werden.

---

## 8. Wie starte ich konkret?

1. Repo clonen / lokalen Branch erstellen:
   - `git checkout -b feature/<TASK-ID>-kurzbeschreibung`
2. Task-Datei in `docs/tasks/` lesen, z. B.:
   - `docs/tasks/ANDROID-02.md`
3. Verzeichnis öffnen, z. B.:
   - `client_android/` oder `core_crypto/`
4. Implementieren, regelmäßig kleine Commits machen:
   - `[ANDROID-02] Add basic AudioRecord wrapper`
5. Testen (Unit / manuell / Integration)
6. PR erstellen:
   - Titel beginnt mit `[TASK-ID]`
   - Beschreibung: Ziel, Änderungen, Tests

---

## 9. Fragen & Klärung

Wenn Unklarheiten bestehen:

- zuerst nachsehen in:
  - `PROJECT_PAPER.md`
  - `ARCHITECTURE_OVERVIEW.md`
  - `SECURITY_DESIGN.md`
  - relevanter `docs/tasks/*.md`
- offene Architekturfragen → an Architekt / Lead-Dev
- bei Konflikt zwischen „schnell“ und „sicher“:
  - Entscheidung immer zugunsten Sicherheit

---

## 10. Kurz-Checkliste für neue Devs

- [ ] Repo-Struktur verstanden
- [ ] PROJECT_PAPER & ARCHITECTURE_OVERVIEW gelesen
- [ ] SECURITY_DESIGN überflogen
- [ ] passende Task-Datei gewählt
- [ ] eigenen Feature-Branch erstellt
- [ ] Commits mit `[TASK-ID]` versehen
- [ ] keine sensitiven Daten geloggt
- [ ] Code baubar & testbar

Willkommen im Stealth/SecureCall-Projekt. 🕶️📞  
**Build it like someone’s life depends on it – weil es irgendwann so sein kann.**
