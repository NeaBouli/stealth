
SecureCall Ecosystem – Development Guide

Dieses Dokument beschreibt den Arbeitsmodus für alle Entwickler im Stealth-Projekt.

1. Branch-Konventionen

main – stabiler Hauptbranch, nur geprüfte Stände

dev – Integrationsbranch (optional, später)

feature/<TASK-ID>-kurzbeschreibung

Beispiele:

feature/ANDROID-01-client-skeleton

feature/CRYPTO-01-core-skeleton

feature/BACKEND-01-signaling-mvp

Regel:

Nie direkt auf main entwickeln

Immer Feature-Branch pro Task

2. Task-Struktur

Zu jedem größeren Arbeitspaket existiert eine Task-Datei unter docs/tasks/.

Beispiele:

docs/tasks/ANDROID-01.md

docs/tasks/CRYPTO-01.md

docs/tasks/BACKEND-01.md

docs/tasks/OS-01.md

Jeder Entwickler:

liest zuerst den zugehörigen Task

hält sich an Ziel, Anforderungen und Deliverables

3. Commit-Regeln

Commit-Messages Format:

[TASK-ID] kurze Beschreibung

Beispiele:

[ANDROID-01] create initial android project skeleton

[CRYPTO-01] add rust crate with api signatures

[BACKEND-01] minimal signaling http server

[OS-01] document build chain requirements

Keine riesigen Monster-Commits, lieber mehrere kleine, saubere Schritte.

4. Patches & Workflow (für Middleman)

Der Projektkoordinator (Middleman) arbeitet mit:

cat <<'EOF' > datei (neu/überschreiben)

cat <<'EOF' >> datei (anhängen)

Keine manuellen Editoränderungen auf dem System, soweit möglich.

5. CI / GitHub Actions

Die Datei .github/workflows/ci-basic.yml führt aus:

YAML Lint

Markdown Lint

Ziel:

saubere Dokumentation

frühe Fehlererkennung

Später:

zusätzliche Jobs für:

Rust Build & Tests (Core Crypto)

Android Lint & Build

Backend Tests

OS-Build-Checks

6. Modulordner

client_android/ – Android App (SecureCall Client)

core_crypto/ – Rust Crypto Engine

backend/ – Signaling & GhostNet Backend

rom_ghostos/ – GHOSTOS Build-Umgebung

tools/ – Hilfsskripte (Build, Checks, Dev-Tools)

7. Qualitätsanforderungen

keine Klartext-Schlüssel im Code oder Logs

keine sensiblen Daten in Commits

alle sicherheitsrelevanten Änderungen müssen Code-Review bekommen

Tests und Builds dürfen den CI nicht brechen

