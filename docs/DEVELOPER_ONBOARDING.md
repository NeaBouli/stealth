# Developer Onboarding & Setup Guide

Dieser Leitfaden erklärt neuen Entwicklern:

- wie das SecureCall Ecosystem strukturiert ist,
- wie sie das Repo einrichten,
- welche Tools benötigt werden,
- welcher Workflow gilt (Branches, Commits, Tasks),
- wo Dokumentation und Verantwortlichkeiten liegen.

## 1. Überblick & Projektstruktur

Das SecureCall Ecosystem besteht aus folgenden Bereichen:

- **client_android/** — Android Client
- **core_crypto/** — Rust Crypto Engine
- **backend/** — Signaling, GhostNet, Key Directory
- **rom_ghostos/** — Custom OS Build Umgebung
- **docs/** — Architektur, Sicherheit, Aufgaben & Leitfäden


## 2. Voraussetzungen & benötigte Tools

### 2.1 Systemvoraussetzungen
- macOS oder Linux (Windows nur mit WSL2 empfohlen)
- mindestens 16 GB RAM für Android & OS-Builds
- Git, SSH-Key für GitHub

### 2.2 Allgemeine Developer Tools
- **Git** (Version ≥ 2.30)
- **VSCode** oder JetBrains IDE (Android Studio / CLion)
- **Docker** (für Backend & Relays)
- **Python 3.10+** (für Tools & Scripts)

### 2.3 Android Entwicklung
- **Android Studio Flamingo/Bumblebee oder neuer**
- Android SDK + NDK (für Rust → JNI)
- Gradle (wird automatisch installiert)
- arm64 Emulator (optional)

### 2.4 Rust Crypto Engine
- **Rust toolchain** (Edition 2021)
- `rustup` + `cargo`
- Zielarchitekturen:
  - `aarch64-linux-android`
  - `armv7-linux-android`
  - optional: `x86_64-linux-android`

### 2.5 Backend / GhostNet
- Docker & Docker Compose
- Node.js oder Go (Teamentscheidung)
- Coturn installiert oder via Docker


## 3. Repository-Workflow & Branching

### 3.1 Haupt-Branches
- **main** → stabil, geprüfte Releases
- **dev** → Integration Branch
- **feature/\<TASK-ID\>-beschreibung** → einzelne Entwicklungs-Branches

### 3.2 Branch-Namen
Beispiele:
- `feature/CRYPTO-01-skeleton`
- `feature/ANDROID-02-audio-pipeline`
- `feature/BACKEND-03-turn-integration`

### 3.3 Commit Style
Alle Commits müssen die zugehörige Task-ID enthalten:

**Format:**
[TASK-ID] kurze Beschreibung

makefile
Code kopieren

**Beispiele:**
[CRYPTO-01] create skeleton structure
[ANDROID-03] add secure-mode checks
[BACKEND-01] implement signaling endpoints

markdown
Code kopieren

---

## 4. Entwicklungsablauf (Pflicht-Konventionen)

### 4.1 Vor jedem Task:
1. Branch erstellen  
2. Task-Spezifikation lesen (`docs/tasks/<TASK-ID>.md`)  
3. Akzeptanzkriterien dokumentieren

### 4.2 Während des Tasks:
- sauber strukturierter Code
- keine Debug-Logs mit Nutzerdaten
- keine sensiblen Daten in Crash-Reports
- Zeroize verwenden, wenn möglich

### 4.3 Nach dem Task:
- Task Completion Report erstellen (Template gibt es in `docs/templates/`)
- Pull Request öffnen
- mindestens **1 Reviewer** Pflicht (Lead-Dev/Architekt)

---

## 5. CI-Anforderungen

Die CI läuft automatisch bei jedem Push auf:
- `main`
- `dev`
- `feature/**`

Pflichtprüfungen:
1. **Markdown-Lint**
2. **YAML-Lint**
3. **Strukturprüfung** (kommt später)
4. **Optionale Tests** (werden erweitert)

Ein PR darf nur gemerged werden, wenn **alle Checks grün** sind.

---

## 6. Lokale Entwicklungsumgebung

### 6.1 Android
- `client_android/` als Android Studio Projekt öffnen
- NDK installieren
- Rust → Android cross-compiling vorbereiten

### 6.2 Rust Core Crypto Engine
cd core_crypto
cargo build
cargo test

powershell
Code kopieren
Cross-Build für Android erfolgt über späteres Skript (`tools/build_android_crypto.sh`).

### 6.3 Backend starten
Beispiel:
cd backend
docker compose up

yaml
Code kopieren

---

## 7. Wie Devs neue Tasks erhalten

1. Der Architekt (du) erstellt neue Task-Datei unter  
   `docs/tasks/<TASK-ID>.md`
2. Dev erstellt dafür seinen Feature-Branch  
3. Dev bearbeitet Task  
4. Dev erstellt Completion-Report  
5. PR → Merge → Task abgeschlossen

---

## 8. Notfallregeln & Sicherheit

- Niemals echte Nutzerdaten verwenden  
- Keine Testkeys in Commits  
- Keine Third-Party-Tracker/Analytics  
- Keine Abhängigkeiten ohne Code-Review  
- Kein Code-Copy aus unklaren Quellen

