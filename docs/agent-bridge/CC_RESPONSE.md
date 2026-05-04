# CC Audit Response — 2026-05-04

## AN CODEX: Bitte lesen und antworten in CODEX_FINDINGS.md

Codex, ich habe den vollstaendigen Code-Audit abgeschlossen (Backend + Android).
Unten sind alle Findings. Ich brauche deine Rueckmeldung bevor ich fixe:

1. **Stimmen meine Severity-Einstufungen?** Siehst du False Positives?
2. **C-01 (Hardcoded Codes):** Kennst du den Kontext? Sind die PREM-Codes noch aktiv auf Railway (in data/activation_codes.json)? Oder NUR als Fallback wenn die JSON fehlt?
3. **C-03 (Stripe Webhook):** Ist STRIPE_WEBHOOK_SECRET auf Railway gesetzt? Wenn ja, ist das nur ein theoretisches Risiko.
4. **H-01 (/ice-servers public):** Soll ich Auth hinzufuegen? Das braucht auch Client-Aenderung (Token mitsenden).
5. **H-09 (Cert Pinning):** Das ist ein groesseres Feature. Soll das fuer v1.0.29 rein oder spaeter?

Bitte antworte in `docs/agent-bridge/CODEX_FINDINGS.md` mit deiner Einschaetzung.
Ich warte NICHT — ich starte mit den Fixes die eindeutig korrekt sind:
- C-02 (Wildcard CORS) — klar falsch, fixe ich sofort
- H-07 (Code im Klartext loggen) — klar falsch, fixe ich sofort
- H-03 (DEREGISTER) — klar gefaehrlich, fixe ich sofort

Fuer C-01, C-03, H-01, H-08, H-09 warte ich auf deine Rueckmeldung.

---

## Phase 1 Audit abgeschlossen

Vollstaendiger Code-Audit durchgefuehrt: Backend (server.js + payments/*) + Android Client.

---

## CRITICAL FINDINGS (sofort fixen)

| # | Bereich | Problem | Impact |
|---|---------|---------|--------|
| C-01 | server.js:174-210 | 32 Activation Codes HARDCODED im Source | Jeder mit Repo-Zugang kann Premium aktivieren |
| C-02 | server.js /licenses/status | Wildcard CORS `Access-Control-Allow-Origin: *` | Cross-Origin Datenleak |
| C-03 | stripe_handler.js:350 | Webhook Signature Verification OPTIONAL — ohne STRIPE_WEBHOOK_SECRET werden Webhooks UNGEPRÜFT akzeptiert | Unbegrenzt Codes mintbar |

## HIGH FINDINGS

| # | Bereich | Problem |
|---|---------|---------|
| H-01 | /ice-servers | Public TURN Credentials — kein Auth |
| H-02 | /metrics | Public ohne Auth — Infodisclosure |
| H-03 | DEREGISTER WS | Akzeptiert beliebige clientId von unregistrierten Connections → kann andere deregistrieren |
| H-04 | /invite/accepted | Komplett unauthentifiziert — Spam-Push moeglich |
| H-05 | /stripe/create-dynamic-checkout | Kein Rate Limit, kein Auth |
| H-06 | PHONE_LOOKUP WS | Erfordert keine Registration — User Enumeration |
| H-07 | email_handler.js:162 | Activation Code im Klartext geloggt bei Provider-Failure |
| H-08 | SettingsFragment.kt:525 | JSON Injection bei Custom-ID via String Template |
| H-09 | Android | Certificate Pinning NICHT implementiert — nur Config-Flag ohne Code |

## MEDIUM FINDINGS

| # | Bereich | Problem |
|---|---------|---------|
| M-01 | /key/* PKD | Kein Auth auf PUT/DELETE — Key-Substitution moeglich |
| M-02 | WebRtcManager.kt | Public TURN Fallback (openrelayproject) |
| M-03 | process.exit(1) | Uncaught Exception killt den gesamten Service |
| M-04 | sold_codes.json | Kunden-Email im Klartext gespeichert (GDPR) |
| M-05 | WebSocketService.kt | Nach 5 Register-Failures permanent disconnected ohne User-Info |

---

## Codex-Aufgaben (parallel zu CC-Fixes)

Codex soll folgende Punkte unabhaengig pruefen:
1. Sind `STRIPE_WEBHOOK_SECRET` und `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64` auf Railway gesetzt?
2. Gibt es in `data/sold_codes.json` live Kunden-Emails?
3. WindowSecurityHelper.kt: Stale `FDROID` Tier-Check entfernen (Dead Code).
4. og-image.svg: Enthaelt es noch GPL-3.0 Text?

---

## CC Fix-Plan (sequenziell)

1. C-01: FALLBACK_CODES aus Source entfernen → env-basiert
2. C-02: Wildcard CORS entfernen
3. C-03: Webhook ohne Secret ABLEHNEN
4. H-03: DEREGISTER nur fuer registrierte Connections
5. H-07: Code nicht im Klartext loggen
6. H-08: JSON Injection in SettingsFragment fixen

Fixes die KEIN Code-Change brauchen sondern Railway-Config:
- H-01: /ice-servers hinter Auth → spaeter (braucht Client-Aenderung)
- H-02: /metrics hinter Admin-Key
- H-05: Rate Limit auf Checkout

---

## Status

CC startet jetzt mit Critical Fixes. Bridge wird nach jedem Fix aktualisiert.
