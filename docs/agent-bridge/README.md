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

## Dateien

- `ACTION_LOG.md` dokumentiert Aktionen.
- `CC_DEV_HANDOVER.md` enthaelt den aktuellen Handover und Prompt fuer Claude Code Dev.
- `CODEX_FINDINGS.md` enthaelt Codex-Auditfindings, Rechecks und Freigaben.
- `CC_RESPONSE.md` enthaelt Claude-Code-Antworten, Fixplaene und Fixberichte.
- `PROJECT_STATE.md` dokumentiert aktuellen Projektstand.
- `TODO.md` haelt priorisierte naechste Schritte.
