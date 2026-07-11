# Rollenverteilung CC + Codex — verbindlich ab 2026-05-04

## Vollstaendige Codex-Ownership — verbindlich ab 2026-07-11

- Gio hat Codex als alleinigen Main Developer und Integrations-Owner fuer das oeffentliche SecureCall/StealthX-Repository eingesetzt.
- Codex uebernimmt Produktcode, Android, Signaling, Kryptografie-Integration, Tests, Website, Releases-Vorbereitung sowie Stripe, Fulfillment, Refunds und Etimologio/myDATA.
- Andere Devs arbeiten nur nach explizitem Bridge-Handover oder als unabhaengige Reviewer und duerfen keine parallelen Produkt-/Payment-Implementierungen beginnen.
- Secrets, AFM, Kunden-, Stripe-, Provider- und AADE-Daten duerfen nie in dieses oeffentliche Repository oder seine Bridge. Sie bleiben ausschliesslich in privaten Runtime-Secrets bzw. der privaten VLABS-Steuerzentrale.
- Live-Zahlungen, Deployments, Releases und produktive Rechnungsausgabe benoetigen weiterhin Gio-Freigabe; steuerliche Klassifizierung benoetigt Accountant-/Provider-Freigabe.

### Verbindliche Aufgabenaufteilung

**Codex uebernimmt:**
- VLABS/Stripe Checkout, Produkt-/Preis-IDs und Webhook-Verarbeitung.
- SecureCall-Fulfillment, Aktivierungscode-Auslieferung, Idempotenz, Refund/Revoke und Payment-Tests.
- Privat/Firma/AFM-Datenerfassung, interne Invoice-/Etimologio-Drafts und lizenzierten Provider-Adapter.
- Payment-Rechtscopy, Monitoring, Rollback-Plan und laufende Bridge-Dokumentation.

**Andere Devs/CC uebernehmen nur nach Handover:**
- Von Codex konkret zugewiesene, dateibezogene Teilaufgaben.
- Unabhaengige Code-, Security- und Regression-Reviews nach Codex-Handover.
- Keine parallele Produkt-, Krypto-, Stripe- oder Etimologio-Implementierung.

**Nur Gio/Accountant/Provider kann liefern oder freigeben:**
- Private Runtime-Secrets und Deployment-/Live-Start-Freigabe durch Gio.
- Steuerliche Produktklassifizierung durch Accountant und produktive Provider-Freigabe.

## Claude Code (CC) — Reviewer / Support nach Handover

**Darf:**
- Von Codex explizit uebergebene Produktcode-Dateien bearbeiten
- Uebergebene Builds/Tests und unabhaengige Reviews ausfuehren
- Commits nur im dokumentierten Handover-Scope erstellen
- Bridge-Dateien aktualisieren

**Darf nicht:**
- Eigene Fixes als "verified" markieren (das macht Codex)
- Findings ignorieren die Codex als STILL_OPEN markiert
- .env / Secrets / Keystores lesen oder ausgeben

---

## Codex — Main Developer / Owner

**Darf:**
- Produktcode, Backend, Android, Website und Tests implementieren
- Commits erstellen und pushen
- Builds, Releases-Vorbereitung und Audits ausfuehren
- Findings dokumentieren in CODEX_FINDINGS.md
- CC-Fixes re-verifizieren (VERIFIED_FIXED / STILL_OPEN)
- Neue Schwachstellen melden
- Bridge-Dateien aktualisieren (eigene Findings + Status)

**Darf nicht:**
- Secrets oder private Steuer-/Kundendaten in Git, Bridge oder Logs schreiben.
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
