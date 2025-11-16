# Developer Handbook – SecureCall Ecosystem

## 1. Zweck dieses Dokuments
Dieses Handbuch definiert alle Regeln, Standards und Prozesse für die Entwicklung
am SecureCall Ecosystem. Es legt fest, wie Code geschrieben, geprüft, dokumentiert,
getestet und gemerged wird. Ziel ist eine klare, reproduzierbare und skalierbare
Arbeitsweise für alle Entwickler.

## 2. Projektüberblick (Kurzfassung)
Das Projekt besteht aus vier Produktlinien (Free, Pro, Premium, OS) und vier
technischen Hauptbereichen:

- Android Client (client_android/)
- Core Crypto Engine in Rust (core_crypto/)
- Backend/Signaling/GhostNet (backend/)
- GHOSTOS BlackRoot ROM (rom_ghostos/)
- Dokumentation (docs/)

Weitere Details siehe:
- docs/PROJECT_PAPER.md
- docs/ARCHITECTURE_OVERVIEW.md
- docs/SECURITY_DESIGN.md

## 3. Wie dieses Handbuch gelesen wird
- Neue Entwickler starten bei Abschnitt 4 (Onboarding)
- Codestil & Commit-Regeln → Abschnitt 7
- Branching & Workflows → Abschnitt 6
- Sicherheitsrichtlinien → Abschnitt 9
- Dokumentationsregeln → Abschnitt 10


## 4. Entwickler-Onboarding

Dieser Abschnitt beschreibt alle Schritte, die ein neuer Developer durchführen muss,
um im SecureCall Ecosystem arbeitsfähig zu werden.

### 4.1 Voraussetzungen
Entwickler benötigen Grundkenntnisse in:
- Android (Kotlin/Java)
- Rust (für Crypto Engine)
- moderne Netzwerkprotokolle (WebRTC/QUIC)
- Git & GitHub Workflows
- Linux/Unix-Kommandozeile

### 4.2 Zugang & Sicherheit
Für Mitarbeit sind notwendig:
- GitHub Account
- Zwei-Faktor-Authentifizierung (2FA Pflicht)
- SSH-Key oder PAT für GitHub
- Keine Nutzung öffentlicher WLANs ohne VPN
- Keine Speicherung sensibler Daten in lokalen Projektlogs

### 4.3 Verzeichnisstruktur (lokal)
Nach Klonen des Repos:
- `client_android/` → Android Client
- `core_crypto/` → Rust Crypto Engine
- `backend/` → Signaling/GhostNet
- `rom_ghostos/` → OS-Buildchain & Kernel
- `docs/` → Dokumentation
- `.github/workflows/` → CI

### 4.4 Lokales Setup

#### Android Development
Erforderlich:
- Android Studio (aktuelle Version)
- JDK 17+
- Android SDK Platform 33+
- NDK (latest)

#### Rust Development
Erforderlich:
- Rustup
- Rust stable (Edition 2021)
- cargo, cbindgen, bindgen
- rustfmt & clippy

#### Backend Development
Abhängig von Implementierungssprache:
- Node.js (>= 18) ODER
- Go (>= 1.21) ODER
- Rust (>= 1.70)

#### OS Development
Erforderlich:
- Ubuntu 20.04/22.04 Build-Host
- Repo Tool (AOSP)
- Python3
- Java 11/17
- 300GB freier Speicher für Builds
- Gerätetreiber für Pixel/Fairphone

### 4.5 Erstes Klonen & Branch Setup

```bash
git clone https://github.com/NeaBouli/stealth.git
cd stealth
git checkout -b feature/<TASK-ID>-beschreibung
Beispiel:

bash
Code kopieren
git checkout -b feature/CRYPTO-01-skeleton

## 5. Git Workflow & Branching Strategy

Dieser Abschnitt beschreibt den standardisierten Workflow, den alle Entwickler
im SecureCall Ecosystem einhalten müssen.

### 5.1 Branching-Modell

Wir nutzen ein mehrstufiges, eindeutiges Branch-Modell:

- **main**  
  → stabiler Release-Branch, nur über Pull Requests

- **dev**  
  → Integrationsbranch aller laufenden Arbeiten

- **feature/<TASK-ID>-beschreibung**  
  → Branch pro Task  
  Beispiel: `feature/ANDROID-02-audio-pipeline`

- **hotfix/<beschreibung>**  
  → nur für kritische Fixes nach Release

### 5.2 Regeln für Feature-Branches

1. Ein Branch = genau ein Task  
2. Branch-Namen müssen die Task-ID enthalten  
3. Keine Fremd-Commits in andere Branches pushen  
4. Commits müssen klein, klar, nachvollziehbar bleiben

### 5.3 Commit-Konventionen

**Format:**

[TASK-ID] Kurze Beschreibung

makefile
Code kopieren

**Beispiele:**

[CRYPTO-01] Add Rust module skeleton for identity & session
[ANDROID-02] Implement AudioRecord + AudioTrack pipeline
[SEC-01] Add root/emulator detection logic

markdown
Code kopieren

### 5.4 Pull Requests

Alle PRs müssen beinhalten:

- Task-ID im Titel
- kurze Beschreibung der Änderungen
- betroffene Module
- Teststatus (lokal ausgeführt?)
- Reviewer: Team-Lead + Security-Reviewer für kritische Komponenten

PR-Titel Beispiel:

[ANDROID-03] Implement Secure Mode Screen Recording Detection

markdown
Code kopieren

### 5.5 Merge-Regeln

- Merge nach **Review + Passing CI**
- Kein Merge bei offenen Sicherheitswarnungen
- Keine direkten Pushes auf main oder dev
- Rebase optional → Merge fast-forward bevorzugt

### 5.6 Umgang mit Konflikten

Falls Merge-Konflikte auftreten:

1. Entwickler löst Konflikte lokal  
2. führt `git add .` und `git commit` aus  
3. pusht wieder zum PR  
4. Reviewer prüft erneut

Es gilt: **Konflikte IMMER sauber dokumentieren**.


## 6. Code-Qualität & Sicherheitsrichtlinien

Die Qualität des Codes ist entscheidend, da SecureCall sicherheitskritisch ist.
Alle Entwickler müssen diese Regeln strikt einhalten.

---

## 6.1 Clean-Code-Grundsätze

- Keine unnötige Komplexität  
- Klare Modulgrenzen  
- Funktionen dürfen nur **eine Aufgabe** haben  
- Keine "magischen Werte" – alles klar benannt  
- String-Konstanten in eigenen Files bündeln  
- Keine Logik in UI-Elementen  
- Krypto-Code niemals mischen mit UI-Code  

---

## 6.2 Sicherheitsrichtlinien (sehr strikt)

1. **Keine Klartext-Schlüssel oder unverschlüsselte Audiodaten in Logs**  
2. **Keine Debug-Informationen im Release-Build**  
3. **Rust: alle sensitiven Buffers mit zeroize löschen**  
4. **Android: niemals persistente Speicherung sensitiver Daten**  
5. **Backend: keine IP- oder Geräte-ID-Logs**  
6. **OS: Kernel-Debugging und ADB im Release-Build deaktivieren**  
7. **WebRTC/QUIC nur über verschlüsselte Kanäle (DTLS/SRTP/TLS1.3)**  

---

## 6.3 Logging-Regeln (Minimalprinzip)

### Erlaubt:
- technische Fehler (Stacktraces ohne Nutzerdaten)
- Statusmeldungen ohne Identitätsbezug
- anonymisierte Performance-Metriken

### Nicht erlaubt:
- IP-Adressen
- Nutzer-IDs
- Public Keys
- Session-IDs
- Zellinformationen
- Audio-Längen/Spikes, die Rückschlüsse erlauben könnten

---

## 6.4 Code-Reviews

Jeder Pull-Request (PR) muss von mindestens 2 Personen geprüft werden:

- **Reviewer 1:** Modul-Entwickler  
- **Reviewer 2:** Security Reviewer  

Checkliste für Reviewer:

- Sind alle sicherheitskritischen Operationen korrekt?  
- Wurden zeroize / memory clear angewendet?  
- Ist der Code modular, dokumentiert und nachvollziehbar?  
- Keine unnötigen Abhängigkeiten?  
- Keine persistente Speicherung sensibler Daten?  
- Sind alle Änderungen in der Doku/Tasks referenziert?  

---

## 6.5 Dokumentationspflicht

Nach jedem Task müssen folgende Dateien aktualisiert werden:

- `docs/tasks/<TASK-ID>.md`  
- `docs/DEV_ROADMAP.md`  
- `docs/CHANGELOG.md` (wird später angelegt)  
- betroffene Architektur-Dokumente  

Keine Implementierung gilt als abgeschlossen, solange die Dokumentation fehlt.

---

## 6.6 Build-Regeln

- Android: Release-Builds mit ProGuard/R8  
- Rust: `cargo fmt`, `cargo clippy`, `cargo test`  
- Backend: unit tests + linter  
- OS: reproducible builds, keine vendor-blobs außer device-firmware  


# 7. Task-Abschluss & Development Flow

Jede Entwicklungseinheit (Task) muss vollständig dokumentiert, getestet und nachvollziehbar abgeschlossen werden.

---

## 7.1 Kriterien für „Task abgeschlossen“

Ein Task gilt nur als abgeschlossen, wenn:

1. **Code implementiert**  
2. **Tests vorhanden und bestanden**  
3. **Dokumentation geschrieben**  
   - docs/tasks/<TASK-ID>.md  
   - DEV_ROADMAP.md aktualisiert  
4. **Security-Review bestanden**  
5. **PR erstellt, reviewt und gemergt**  
6. **Branch gelöscht** (feature/<TASK-ID>)  
7. **Changelog-Eintrag vorhanden**  

---

## 7.2 Branch- und Merge-Policy

- Hauptentwicklung erfolgt in **dev**
- Nur stabile Releases gehen nach **main**
- Feature-Branches folgen dem Pattern:

feature/<TASK-ID>-beschreibung

yaml
Code kopieren

Beispiele:

- feature/CRYPTO-01-core-skeleton  
- feature/ANDROID-02-audio-pipeline  
- feature/BACKEND-01-signaling-mvp  

Merges auf main dürfen **nie** direkt erfolgen → nur über Release-Merges.

---

## 7.3 Release-Prozess

Ein Release besteht aus:

1. **Release-Tag**  
   Format:
vX.Y.Z

markdown
Code kopieren
- X = Major (neue Produktgenerationen, Sicherheitsarchitektur)
- Y = Minor (neue Features)
- Z = Patch (Bugfixes)

2. **Release-Notes**  
Muss enthalten:
- neue Features  
- Fixes  
- Breaking Changes  
- Sicherheitsrelevante Änderungen  

3. **Build-Artefakte**  
- Android: signierte .apk / .aab  
- Crypto Engine: Versionierte Library builds  
- Backend: Docker-Image-Tags  
- OS: Flashbares image (`.img`, `.bin`, `.zip`)

---

## 7.4 Developer Security Checklist (Pflicht!)

Bevor ein Entwickler einen PR erstellt, muss diese Liste vollständig erfüllt sein:

### 🔐 **Code-Sicherheit**

- [ ] Keine Klartextschlüssel irgendwo im Code  
- [ ] Sensitive Daten mit zeroize gelöscht  
- [ ] Keine Prints von Schlüsseln / Audio / Identitäten  
- [ ] Keine Debug- oder Testpfade im produktiven Code  

### 🔐 **Android-Sicherheit**

- [ ] Keine permissiven Android-Permissions  
- [ ] Keine ungesicherten BroadcastReceiver  
- [ ] Keine WebViews ohne restriktive Konfiguration  
- [ ] Kein persistent gespeicherter Klartext  

### 🔐 **Crypto-Sicherheit**

- [ ] AEAD-Modi korrekt eingesetzt  
- [ ] Nonces nie wiederverwendet  
- [ ] Fehler bei decrypt() führen zu sofortigem Abbruch  
- [ ] Parameter & Returnwerte streng validiert  

### 🔐 **Backend-Sicherheit**

- [ ] Keine IP-Adressen oder User-IDs in Logs  
- [ ] TLS erzwungen  
- [ ] Rate-Limits aktiv  
- [ ] Keine persistente Speicherung sensibler Call-Daten  

### 🔐 **OS-Sicherheit (GHOSTOS)**

- [ ] ADB disabled  
- [ ] Debug-Features entfernt  
- [ ] Verified Boot aktiv  
- [ ] Kernel debugfs deaktiviert  
- [ ] Mikrofon/Kamera isoliert  
- [ ] Funkprofile restriktiv gesetzt  

---

## 7.5 Dokumentation der abgeschlossenen Tasks

Für jeden Task wird in folgendem Format dokumentiert:

<TASK-ID> – <Titel>
Status
Abgeschlossen am: <Datum>
Verantwortlicher: <Dev-Name>

Zusammenfassung
Kurze Beschreibung, was umgesetzt wurde.

Änderungen
Liste der Dateipfade

Implementierte Klassen/Funktionen

API-Anpassungen

Tests
Unit Tests

Integration Tests

Security Tests

Review
Reviewer: <Name>
Ergebnis: OK / Änderungen notwendig

Notizen
Weitere relevante Infos

yaml
Code kopieren

Diese Datei wird gespeichert unter:

docs/tasks/<TASK-ID>_DONE.md

yaml
Code kopieren

---

