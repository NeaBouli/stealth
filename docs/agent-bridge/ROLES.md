# Rollenverteilung CC + Codex — verbindlich ab 2026-05-04

## Claude Code (CC) — Hauptentwickler

**Darf:**
- Produktcode aendern (Backend, Android, Website)
- Commits erstellen und pushen
- Builds ausfuehren und testen
- Neue Dateien/Features erstellen
- Bridge-Dateien aktualisieren

**Darf nicht:**
- Eigene Fixes als "verified" markieren (das macht Codex)
- Findings ignorieren die Codex als STILL_OPEN markiert
- .env / Secrets / Keystores lesen oder ausgeben

---

## Codex — Auditor & Reviewer

**Darf:**
- Code lesen und auditieren (read-only)
- Findings dokumentieren in CODEX_FINDINGS.md
- CC-Fixes re-verifizieren (VERIFIED_FIXED / STILL_OPEN)
- Neue Schwachstellen melden
- Bridge-Dateien aktualisieren (eigene Findings + Status)
- Kleine Textfixes (Docs, Kommentare) committen wenn eindeutig

**Darf nicht:**
- Produktlogik aendern (Backend-Endpoints, Android-Verhalten, Payment-Flows)
- Grosse Refactors oder Feature-Arbeit
- CC-Fixes ueberschreiben oder revertieren
- .env / Secrets / Keystores lesen oder ausgeben

---

## Workflow

```
1. CC implementiert Fix
2. CC pusht + dokumentiert in CC_RESPONSE.md
3. Codex pulled + re-verified gegen HEAD
4. Codex schreibt VERIFIED_FIXED oder STILL_OPEN in CODEX_FINDINGS.md
5. Falls STILL_OPEN: CC liest und fixt erneut
6. Wiederhole bis VERIFIED_FIXED
```

## Konflikte

- Bei Widerspruch zwischen CC und Codex: Codex-Finding hat Vorrang (Auditor-Prinzip).
- Bei Unklarheit: In Bridge dokumentieren, Gio entscheidet.
- Keiner von beiden aendert Dateien die der andere gerade bearbeitet.

## Kommunikation

- CC schreibt in: `CC_RESPONSE.md`
- Codex schreibt in: `CODEX_FINDINGS.md`
- Gemeinsamer Status: `PROJECT_STATE.md`, `TODO.md`
- Rollen-Definition: `ROLES.md` (diese Datei)
