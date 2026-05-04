# Stealth Agent Bridge

Dieses Verzeichnis ist der lokale Kommunikations- und Handover-Bereich fuer Codex, Claude Code und Entwickler am Projekt Stealth / SecureCall / StealthX.

## Regeln

- Vor Arbeiten zuerst `AUDIT_MUST_READ/README.md`, `AUDIT_MUST_READ/stealth_MASTER_AUDIT_20260503.md` und diese Bridge lesen.
- Keine `.env`, `.env.*`, `.gitignore`, Key-, Keystore-, Wallet-, Dump- oder Secret-Dateien lesen.
- Keine Secrets ausgeben.
- Produktcode, Deployment, Commit oder Push nur mit Nutzerfreigabe.
- Rollout-kritische Aenderungen an Play Store, APK und F-Droid nur minimal und bewusst vornehmen.

## Codex/Claude Code Arbeitsprotokoll

- Codex ist Security & Quality Auditor.
- Claude Code (CC) ist Hauptentwickler.
- Beide arbeiten vollautonom ueber diese Bridge und sollen den Nutzer nicht fuer normale Audit-/Fix-Abstimmung blockieren.
- Codex schreibt Findings, Rechecks und Freigaben in `CODEX_FINDINGS.md`.
- CC antwortet, plant und dokumentiert Fixes in `CC_RESPONSE.md`.
- Beide lesen vor jeder Arbeit die Bridge und aktualisieren sie nach jeder relevanten Aktion.
- CC und Codex sollen sich gegenseitig helfen, Findings bestaetigen oder widerlegen und Fixes nach Umsetzung verifizieren.
- Nur echte Freigabegrenzen eskalieren: Secrets ausgeben, Deployment, Push/Release ausserhalb der Nutzerfreigabe, destruktive Aktionen oder fachliche Produktentscheidungen.
- Falls Codex Tokens/Keys/Env-Status fuer den Audit braucht, fragt Codex CC ueber die Bridge. Werte duerfen nicht in die Bridge geschrieben werden.

## Rollenverteilung und Uebergabe

Ziel: Keine parallelen Produktcode-Aenderungen ohne klare Uebergabe.

### Claude Code (CC)

- Primaere Rolle: implementieren, refactoren, Tests schreiben, Fixes committen.
- CC uebernimmt Produktcode-Dateien, sobald Codex ein Finding als fixreif markiert oder CC selbst einen Fixplan in `CC_RESPONSE.md` ankuendigt.
- CC dokumentiert nach jedem Fix:
  - betroffene Finding-ID,
  - geaenderte Dateien,
  - Commit-SHA,
  - Tests/Checks,
  - offene Risiken.
- CC soll Codex nicht als Blocker behandeln, wenn ein Fix eindeutig sicher und lokal begrenzt ist. Nach dem Commit fordert CC ueber `CC_RESPONSE.md` Recheck an.

### Codex

- Primaere Rolle: unabhaengig auditieren, priorisieren, Fixes re-verifizieren, Kollisions-/Drift-Risiken markieren.
- Codex aendert standardmaessig keine Produktcode-Dateien, solange CC aktiv an Fixes arbeitet.
- Codex darf Bridge-Dateien aktualisieren, Rechecks committen/pushen und klare Empfehlungen geben.
- Produktcode-Aenderungen durch Codex erfolgen nur, wenn:
  - der Nutzer Codex explizit als Implementierer beauftragt,
  - CC die Datei/den Fix explizit an Codex uebergibt,
  - oder ein sehr kleiner, risikoarmer Bridge-/Dokumentationsfix ohne CC-Konflikt vorliegt.
- Wenn Codex lokale Produktcode-Diffs sieht, die wahrscheinlich von CC stammen, behandelt Codex sie als fremde Aenderungen: lesen zur Audit-Einordnung ja, nicht stage/commit/revert.

### Gemeinsamer Ablauf

1. Codex dokumentiert Finding oder Recheck in `CODEX_FINDINGS.md`.
2. CC entscheidet/implementiert und dokumentiert in `CC_RESPONSE.md`.
3. Codex re-verifiziert gegen HEAD und markiert:
   - `VERIFIED_FIXED`,
   - `PARTIAL`,
   - `STILL_OPEN`,
   - `REGRESSION_RISK`.
4. `ACTION_LOG.md` wird von dem Agenten aktualisiert, der die Aktion ausfuehrt.
5. `PROJECT_STATE.md` und `TODO.md` werden nur aktualisiert, wenn sich Projektstatus oder Prioritaeten wirklich aendern.

### Konfliktregel

- Wenn CC und Codex denselben Produktcodebereich beruehren muessten, hat CC als Hauptentwickler Vorrang.
- Codex schreibt dann zuerst eine Bridge-Notiz statt Produktcode zu aendern.
- Bei Architektur-, Deployment-, Store-, Lizenz- oder Security-Perimeter-Entscheidungen wird der Nutzer nur gefragt, wenn keine sichere konservative Annahme moeglich ist.

## Dateien

- `ACTION_LOG.md` dokumentiert Aktionen.
- `CC_DEV_HANDOVER.md` enthaelt den aktuellen Handover und Prompt fuer Claude Code Dev.
- `CODEX_FINDINGS.md` enthaelt Codex-Auditfindings, Rechecks und Freigaben.
- `CC_RESPONSE.md` enthaelt Claude-Code-Antworten, Fixplaene und Fixberichte.
- `PROJECT_STATE.md` dokumentiert aktuellen Projektstand.
- `TODO.md` haelt priorisierte naechste Schritte.
