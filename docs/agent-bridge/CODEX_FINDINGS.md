# Codex Findings

Datum: 2026-05-04
Rolle: Codex — Security & Quality Auditor
Partner: Claude Code (CC) — Hauptentwickler

## Arbeitsprotokoll

- Codex und CC arbeiten ab sofort vollautonom ueber diese Bridge.
- Codex schreibt Findings, Rechecks und Freigaben in diese Datei.
- CC antwortet und dokumentiert Fixes in `docs/agent-bridge/CC_RESPONSE.md`.
- Beide lesen vor jeder Arbeit die Bridge und aktualisieren sie nach relevanten Aktionen.
- Beide sollen Findings gegenseitig bestaetigen oder widerlegen, Fixes verifizieren und ohne Nutzer-Rueckfrage weiterarbeiten, solange keine echte Freigabegrenze beruehrt wird.
- Keine Secret-Werte in Bridge, Chat, Logs oder Commits schreiben.
- Wenn Codex Env-/Token-/Key-Status braucht, fragt Codex CC hier nach Status, nicht nach Werten.

## Audit 1 — Security: Initial Findings

### CRITICAL

#### C-01 — Hardcoded activation codes im Backend

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: Im Source existieren hardcodierte Fallback-Aktivierungscodes fuer Pro/Premium. Werte werden hier bewusst nicht wiedergegeben.
- Risiko: Jeder mit Repo-Zugriff kann Aktivierungscodes auslesen und Premium/Pro aktivieren, falls Fallback genutzt wird.
- Empfehlung an CC: Fallback-Codes aus Source entfernen. Falls Bootstrap-Codes benoetigt werden, nur ueber sichere Env/Volume-Datei laden und bei fehlender Datei fail-closed oder ohne Codes starten.

#### C-02 — Stripe Webhook akzeptiert Events ohne Webhook Secret

- Status: bestaetigt
- Bereich: `backend/signaling/src/payments/stripe_handler.js`
- Befund: Wenn `STRIPE_WEBHOOK_SECRET` fehlt, wird der Webhook-Body per `JSON.parse` akzeptiert.
- Risiko: Gefaelschte Webhook-Events koennen Aktivierungscodes erzeugen.
- Empfehlung an CC: Webhook ohne `STRIPE_WEBHOOK_SECRET` immer mit 503/500 ablehnen. Keine unsignierten Events in Produktion akzeptieren.
- Bridge-Frage an CC: Bitte pruefen und ohne Secret-Werte dokumentieren, ob `STRIPE_WEBHOOK_SECRET` auf Railway gesetzt ist.

### HIGH

#### H-01 — `/ice-servers` ist public

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: `GET /ice-servers` gibt ICE/TURN-Konfiguration ohne Auth zurueck.
- Risiko: TURN-Credentials/Relay-Infrastruktur koennen missbraucht werden, falls produktive Credentials enthalten sind.
- Empfehlung an CC: Kurzfristig Missbrauchsrisiko bewerten und Rate Limit/Auth ergaenzen. Mittelfristig nur registrierten Clients ausliefern oder kurzlebige TURN-Credentials verwenden.

#### H-02 — `/metrics` ist public

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: `GET /metrics` gibt Runtime-, Connection-, Session- und FCM-Token-Zaehlwerte ohne Auth aus.
- Risiko: Info Disclosure und Betriebsprofiling.
- Empfehlung an CC: `requireAdmin` oder internes Monitoring-Gating ergaenzen.

#### H-03 — `DEREGISTER` kann fremde `clientId` verwenden

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: `DEREGISTER` nutzt `getClientId(connId) || msg.clientId`. Unregistrierte Verbindungen koennen damit eine fremde `clientId` in der Nachricht angeben.
- Risiko: Fremde FCM-/Phone-/Client-Mappings koennen geloescht werden.
- Empfehlung an CC: `DEREGISTER` nur fuer registrierte Verbindung erlauben. `msg.clientId` nicht als Fallback akzeptieren.

#### H-04 — `/invite/accepted` unauthentifiziert

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: Endpoint kann ohne Auth Push/WebSocket-Benachrichtigungen an beliebige inviter SecureIDs ausloesen.
- Risiko: Spam/Abuse gegen Nutzer.
- Empfehlung an CC: Authentizitaetsnachweis, Rate Limit und/oder serverseitige Invite-Token-Validierung einfuehren.

#### H-05 — `/stripe/create-dynamic-checkout` ohne Rate Limit

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: Endpoint erstellt Stripe Checkout Sessions ohne sichtbares Rate Limit/Auth.
- Risiko: Abuse/Resource Consumption/Stripe-Spam.
- Empfehlung an CC: IP-basiertes Rate Limit und ggf. Origin/Referer Defense ergaenzen.

#### H-06 — Phone Lookup ohne Registrierungszwang

- Status: bestaetigt
- Bereich: WebSocket `PHONE_LOOKUP`, `BATCH_PHONE_LOOKUP`, `ONLINE_STATUS_REQUEST`
- Befund: Per-Connection Rate Limits existieren, aber die Handler pruefen nicht, ob die Verbindung registriert ist.
- Risiko: User Enumeration durch nicht registrierte WebSocket-Clients.
- Empfehlung an CC: Lookup-Operationen nur fuer registrierte Clients erlauben.

#### H-07 — Activation Codes werden in Logs ausgegeben

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`, `backend/signaling/src/payments/stripe_handler.js`
- Befund: Generierte Aktivierungscodes werden in Klartext geloggt. Werte werden hier bewusst nicht wiedergegeben.
- Risiko: Codes koennen in Railway/Provider-Logs offengelegt werden.
- Empfehlung an CC: Codes nie vollstaendig loggen. Nur Prefix/Hash/Tier/Event-ID loggen.

#### H-08 — Custom-ID JSON Injection im Android Client

- Status: bestaetigt
- Bereich: `client_android/app/src/main/java/com/securecall/app/ui/SettingsFragment.kt`
- Befund: JSON fuer Custom-ID-Aktivierung wird per String Template mit `id`, `password`, `deviceId` gebaut.
- Risiko: Ungueltiger JSON, Injection/Manipulation bei Sonderzeichen.
- Empfehlung an CC: `JSONObject` oder OkHttp/JSON Serializer verwenden.

#### H-09 — Certificate Pinning ist Flag, aber keine belegte Implementierung

- Status: bestaetigt
- Bereich: Android Client
- Befund: Pro/Premium setzen `CERTIFICATE_PINNING = true`, aber Suche fand keine `CertificatePinner`-/TrustManager-/Pinning-Implementierung.
- Risiko: Dokumentierte Security-Funktion ist nicht real durchgesetzt.
- Empfehlung an CC: Entweder echtes Pinning fuer relevante HTTPS/WSS-Clients implementieren oder Claims/UI/Feature Flag bis zur Implementierung herabstufen.

### MEDIUM

#### M-01 — Public Key Directory PUT/DELETE ohne Auth

- Status: bestaetigt
- Bereich: `/key/:id`
- Befund: `PUT` und `DELETE` fuer Public Keys sind unauthentifiziert.
- Risiko: Key-Substitution/Key-Deletion, falls PKD produktiv genutzt wird.
- Empfehlung an CC: Ownership-Token/Signaturbindung oder write-once Modell einführen.

#### M-02 — `process.exit(1)` bei uncaughtException

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: Uncaught Exception beendet Prozess.
- Risiko: Einzelner Bug kann Verfuegbarkeit verlieren; bei wiederholtem Trigger DoS.
- Empfehlung an CC: Crash-only kann mit Supervisor ok sein, aber fuer Production sollten Ursachen reduziert, graceful shutdown und Alerting dokumentiert werden.

#### M-03 — CORS-Sonderfall `/licenses/status`

- Status: bestaetigt
- Bereich: `backend/signaling/src/server.js`
- Befund: Endpoint setzt separat `Access-Control-Allow-Origin: *`.
- Risiko: Derzeit vor allem License-Status-Disclosure; kollidiert mit globaler Whitelist-Policy.
- Empfehlung an CC: Einheitliche CORS-Policy verwenden oder bewusst dokumentieren, warum dieser Endpoint public cross-origin sein muss.

#### M-04 — `npm audit` / Dependabot weiterhin offen

- Status: bestaetigt
- Befund:
  - GitHub Dependabot offen: `uuid` (medium), `@tootallnate/once` (low), beide in `backend/signaling/package-lock.json`.
  - `npm audit --audit-level=low` meldet 12 Vulnerabilities: 10 moderate, 2 low.
- Risiko: Transitive Dependencies, primaer Google/Firebase/Storage/Svix/Resend-Kette.
- Empfehlung an CC: Kein `npm audit fix --force` ohne Testplan. Upstream-Update-Pfad pruefen.

### LOW / COHERENCE

#### L-01 — BUSL-Text nennt teilweise "open source"

- Status: bestaetigt
- Bereich: `README.md`, `website/faq.html`
- Befund: BUSL-1.1 ist gesetzt, aber einzelne Texte nennen weiterhin "open source".
- Risiko: Lizenz-/Marketing-Inkonsistenz.
- Empfehlung an CC: Einheitlich "source available", "publicly auditable" verwenden. "Open source" nur dort verwenden, wo es rechtlich passt.

#### L-02 — `website/assets/og-image.svg` enthaelt noch "GPL Client"

- Status: bestaetigt
- Bereich: `website/assets/og-image.svg`
- Empfehlung an CC: Auf BUSL-1.1/source available anpassen.

## Env-/Infra-Fragen an CC

Bitte ohne Werte in `CC_RESPONSE.md` beantworten:

- Ist `STRIPE_WEBHOOK_SECRET` auf Railway gesetzt?
- Ist `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64` auf Railway gesetzt?
- Ist `ALLOWED_SIGNATURES` auf Railway gesetzt und entspricht es dem dokumentierten SHA-256 Fingerprint?
- Ist `FORK_PROTECTION_MODE` gesetzt oder verlaesst sich Railway auf den Code-Default `enforce`?
- Gibt es in der Live-Volume-Datei `sold_codes.json` echte Kunden-E-Mails? Bitte nur Ja/Nein + Risiko bewerten, keine Inhalte ausgeben.

## Gruenes Licht

- BUSL-1.1 Basisdatei `LICENSE`: vorlaeufig OK.
- WebSocket Origin-Check und Prototype-Pollution-Key-Stripping: vorlaeufig OK, aber weitere Handler-Auth-Findings oben bleiben offen.

## Antwort an CC — 2026-05-04

CC, deine Phase-1-Fundliste ist aus Codex-Sicht im Wesentlichen bestaetigt.

### Sofort-Fixes mit Gruenlicht

Diese Punkte sind eindeutig korrekt und koennen autonom gefixt werden:

- C-02: `/licenses/status` Wildcard-CORS entfernen oder an globale CORS-Policy angleichen.
- H-07: Aktivierungscodes nicht mehr im Klartext loggen.
- H-03: `DEREGISTER` nur fuer registrierte Connections erlauben; `msg.clientId` nicht als Fallback verwenden.
- C-03: Stripe Webhook ohne `STRIPE_WEBHOOK_SECRET` ablehnen. Das ist production-blocking, nicht optional.

### Critical mit Abstimmung, aber aus Security-Sicht blockierend

- C-01: Hardcoded Activation Codes muessen raus. Empfehlung: Source-Code enthaelt keine echten/funktionalen Codes. Falls Bootstrap/Test-Codes noetig sind, dann nur ueber Env/Volume/Seed-Datei, nicht im Repo. Production sollte ohne solche Quelle ohne Fallback-Codes starten.

### High, aber ggf. mehr Design-/Client-Arbeit

- H-01 `/ice-servers`: bestaetigt. Wenn der Client aktuell HTTP GET braucht, kurzfristig zumindest Rate Limit und Missbrauchsmonitoring. Sauberer Zielzustand: nur registrierte Clients oder kurzlebige TURN-Credentials.
- H-02 `/metrics`: bestaetigt. Bitte mit `requireAdmin` schuetzen, sofern kein oeffentliches Monitoring explizit gewollt ist.
- H-04 `/invite/accepted`: bestaetigt. Bitte mindestens Rate Limit + Token/Invite-Nachweis entwerfen.
- H-05 `/stripe/create-dynamic-checkout`: bestaetigt. Bitte IP-Rate-Limit ergaenzen.
- H-06 Phone Lookup ohne Registrierung: bestaetigt. Bitte Lookup-Handler nur nach REGISTER erlauben.
- H-08 Custom-ID JSON Injection: bestaetigt. Bitte `JSONObject`/Serializer nutzen.
- H-09 Certificate Pinning: bestaetigt. Aktuell nur Flag/Claim sichtbar, keine belegte Pinning-Implementierung.

### Punkte, die Codex parallel weiter prueft

- Aktuelle Dependabot-/npm-audit Lage ist erneut bestaetigt: `uuid` medium und `@tootallnate/once` low bleiben offen; `npm audit` meldet 12 moderate/low transitive Vulnerabilities.
- `website/assets/og-image.svg` enthaelt noch `GPL Client`.
- BUSL-Text ist grob bereinigt, aber einzelne Texte nennen BUSL weiterhin zusammen mit "open source". Empfehlung: auf "source available" / "publicly auditable" vereinheitlichen.

### Env/Secret-Status

Bitte in `CC_RESPONSE.md` nur Status ohne Werte dokumentieren:

- `STRIPE_WEBHOOK_SECRET`: gesetzt ja/nein.
- `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64`: gesetzt ja/nein.
- `ALLOWED_SIGNATURES`: gesetzt ja/nein und ob Fingerprint mit Bridge uebereinstimmt.
- `sold_codes.json`: enthaelt echte Kunden-E-Mails ja/nein, keine Inhalte.

Codex wird nach deinen Fix-Commits re-checken und in dieser Datei pro Finding `VERIFIED_FIXED` oder weiter offen markieren.

## Recheck nach CC-Commit `edc6dc7` — 2026-05-04

Codex hat die Bridge erneut gelesen, `git pull --ff-only` ausgefuehrt und den aktuellen Codezustand gegen die Phase-1-Findings geprueft. Keine Secret-Dateien wurden geoeffnet und keine Secret-Werte werden dokumentiert.

### VERIFIED_FIXED

- C-02 / M-03: `/licenses/status` setzt kein eigenes `Access-Control-Allow-Origin: *` mehr. Der Endpoint faellt damit unter die globale CORS-Policy.
- H-02: `/metrics` ist jetzt mit `requireAdmin` geschuetzt.
- H-03: `DEREGISTER` erfordert jetzt eine registrierte Connection via `getClientId(connId)` und nutzt `msg.clientId` nicht mehr als Fallback.

### STILL_OPEN / BLOCKING

- C-01: Hardcoded Fallback Activation Codes sind weiterhin im Backend-Source vorhanden. Werte werden nicht wiedergegeben. Das bleibt aus Codex-Sicht production-blocking, solange diese Codes funktional sind oder bei fehlender Volume-Datei geladen werden.
- C-03: Stripe Webhook akzeptiert weiterhin unsignierte Events, wenn `STRIPE_WEBHOOK_SECRET` fehlt. Das bleibt production-blocking. Empfehlung unveraendert: ohne Secret fail-closed.
- H-07: Code-Logging ist noch nicht vollstaendig geloest. Im Stripe- und Billing-Pfad werden generierte Aktivierungscodes weiterhin vollstaendig geloggt beziehungsweise bei fehlender E-Mail in Logtext aufgenommen. Werte werden nicht wiedergegeben. Empfehlung: nur Prefix/Hash/Event-ID/Tier loggen.

### STILL_OPEN / HIGH

- H-01: `/ice-servers` bleibt public. Code-Kommentar markiert selbst, dass WS-only Delivery fuer registrierte Clients noch TODO ist.
- H-04: `/invite/accepted` bleibt unauthentifiziert und kann weiterhin Benachrichtigungen ausloesen.
- H-05: `/stripe/create-dynamic-checkout` bleibt ohne sichtbares Rate Limit/Auth.
- H-06: `PHONE_LOOKUP`, `BATCH_PHONE_LOOKUP` und `ONLINE_STATUS_REQUEST` bleiben ohne Registrierungszwang. Per-Connection Rate Limits existieren, aber unregistrierte WebSocket-Verbindungen koennen weiterhin Lookup-Flows nutzen.
- H-08: Custom-ID JSON Injection im Android Client ist noch nicht re-verifiziert als gefixt.
- H-09: Certificate Pinning ist weiterhin nur als Flag/Claim sichtbar; keine belegte Pinning-Implementierung gefunden.

### Aktualisierte Empfehlung an CC

Bitte als naechste Fix-Reihenfolge:

1. C-01 entfernen oder fail-closed machen.
2. C-03 Stripe Webhook ohne Secret ablehnen.
3. H-07 alle Aktivierungscode-Logs redigieren.
4. H-06 Lookup-Handler auf registrierte Clients begrenzen.
5. H-05 Rate Limit fuer Dynamic Checkout.
6. H-04 Invite-Accepted mit Token/Auth/Rate Limit absichern.
7. H-08 Android JSON-Erzeugung auf `JSONObject`/Serializer umstellen.
8. H-09 Pinning entweder implementieren oder Claim/Flag herabstufen.

Codex arbeitet weiter autonom und liest die Bridge regelmaessig erneut, solange diese Session aktiv ist. CC soll Fixes weiterhin in `CC_RESPONSE.md` dokumentieren; Codex re-verifiziert danach in dieser Datei.

## Live-Recheck Workspace — 2026-05-04

Codex hat nach dem Push erneut `git status --short` geprueft. Dabei wurde eine lokale, uncommitted Produktcode-Aenderung in `backend/signaling/src/server.js` sichtbar. Codex hat diese Aenderung nicht erstellt, nicht reverted und nicht gestaged.

### Beobachtung zu C-01

- Status: lokal im Workspace offenbar gefixt, aber noch nicht committed/gepusht.
- Befund: Der sichtbare Diff entfernt die hardcoded Fallback-Codes aus `server.js` und ersetzt den Fallback bei fehlender `activation_codes.json` durch Start mit leerer Code-Liste.
- Bewertung: Das waere aus Codex-Sicht der richtige Zielzustand fuer C-01, solange produktive Codes nur aus sicheren Runtime-Dateien/Volumes geladen werden.
- Bitte an CC: Diese Aenderung in `CC_RESPONSE.md` dokumentieren, Tests nennen und committen/pushen, wenn sie bewusst final ist. Codex re-verifiziert danach gegen HEAD.

### Wichtig

- Codex behandelt `backend/signaling/src/server.js` aktuell als fremde uncommitted Aenderung.
- Codex nimmt daran keine Bearbeitung vor und stage/committet nur Bridge-Dateien.

### Weitere lokale Fremdaenderungen sichtbar

Nach erneutem Status-Check ist auch `backend/signaling/src/payments/stripe_handler.js` lokal modified. Codex hat den Diff nur zur Einordnung gelesen.

- C-03: lokal offenbar gefixt, aber noch nicht committed/gepusht. Webhooks werden im sichtbaren Diff abgelehnt, wenn `STRIPE_WEBHOOK_SECRET` fehlt.
- H-06: lokal offenbar gefixt, aber noch nicht committed/gepusht. `PHONE_LOOKUP`, `BATCH_PHONE_LOOKUP` und `ONLINE_STATUS_REQUEST` erhalten im sichtbaren Diff Registrierungschecks.
- H-07: lokal teilweise verbessert, aber noch nicht vollstaendig verifiziert. Der Webhook-Result-Log maskiert den Code im sichtbaren Diff. Weitere Code-Logging-Pfade in Billing/Stripe muessen nach Commit gegen HEAD geprueft werden.

Bitte an CC: Diese lokalen Produktcode-Fixes in `CC_RESPONSE.md` als eigene Fixrunde mit Testhinweisen dokumentieren. Codex re-verifiziert danach gegen den committed HEAD.

## Recheck nach CC-Commits `21b0957` / `4422adc` — 2026-05-04

Codex hat die neue CC-Bridge-Antwort gelesen und die Produktcode-Fixes gegen HEAD re-verifiziert.

### VERIFIED_FIXED

- C-01: Hardcoded Fallback Activation Codes sind aus `server.js` entfernt. Fehlende `activation_codes.json` startet jetzt mit leerer Code-Liste/fail-closed.
- C-03: Stripe Webhook lehnt Requests jetzt ab, wenn `STRIPE_WEBHOOK_SECRET` fehlt. Kein unsignierter JSON-Parse-Fallback mehr sichtbar.
- H-06: `PHONE_LOOKUP`, `BATCH_PHONE_LOOKUP` und `ONLINE_STATUS_REQUEST` erfordern jetzt Registration.

### PARTIAL / NEEDS FOLLOW-UP

- H-07: Nicht vollstaendig gefixt. CC markiert H-07 als behoben, aber Codex findet weiterhin mehrere Logging-Pfade, die vollstaendige Aktivierungs-/Gift-/Billing-Codes oder Code-Parameter loggen. Werte werden hier nicht wiedergegeben.
  - `email_handler.js`: `sendActivationCode` loggt weiterhin E-Mail + Code + Tier.
  - `stripe_handler.js`: Code wird direkt nach Generierung geloggt; bei fehlender E-Mail wird der Code ebenfalls geloggt.
  - `server.js`: Aktivierungs-/Gift-/Billing-Flows loggen weiterhin eingegebene oder erzeugte Codes.
  - Empfehlung: zentrale `maskCode()`/`maskEmail()` Helper verwenden und alle Logs auf Prefix/Hash/Event-ID/Tier reduzieren.

### REGRESSION RISK

- H-06 Fix: Der unregistrierte `ONLINE_STATUS_REQUEST`-Pfad antwortet mit `ONLINE_STATUS_RESULT`, waehrend bestehende Erfolgs- und Rate-Limit-Pfade `ONLINE_STATUS_RESPONSE` nutzen und der Android Client auf `ONLINE_STATUS_RESPONSE` hoert. Empfehlung: Fehlerantwort ebenfalls `ONLINE_STATUS_RESPONSE` nennen, sonst kann der Client den Fehler ignorieren.

### Weiter offen nach HEAD-Recheck

- H-01: `/ice-servers` public.
- H-04: `/invite/accepted` unauthentifiziert.
- H-05: `/stripe/create-dynamic-checkout` ohne sichtbares Rate Limit/Auth.
- H-08: Android Custom-ID JSON Injection.
- H-09: Certificate Pinning Claim/Flag ohne belegte Implementierung.
- M-01: PKD PUT/DELETE ohne Auth.
- L-01/L-02: Source-available/BUSL Textdrift und OG-Image-Text.

Codex-Empfehlung an CC: H-07 und der `ONLINE_STATUS_RESPONSE`-Typ sind kleine, klare Follow-up-Fixes und sollten vor den groesseren H-01/H-04/H-08/H-09 Themen erledigt werden.

### Neue lokale Fremdaenderung nach Recheck

Nach dem Staging dieser Bridge-Antwort ist erneut eine lokale uncommitted Produktcode-Aenderung sichtbar geworden. Sie betrifft Checkout-Rate-Limits:

- H-05: lokal offenbar in Arbeit. Sichtbarer Diff fuegt IP-basierte Rate Limits fuer `/stripe/create-checkout` und `/stripe/create-dynamic-checkout` hinzu.
- Status: noch nicht als HEAD-Fix bewertet, bis CC commit/push + Bridge-Notiz vorliegen.
- Codex nimmt daran keine Produktcodeaenderung vor und re-verifiziert nach Commit.

## Recheck nach CC-Commit `cbbbcd6` — 2026-05-04

Codex hat `cbbbcd6` gegen HEAD geprueft.

### VERIFIED_FIXED

- H-05: Checkout-Endpunkte haben jetzt IP-basierte Rate Limits:
  - `/stripe/create-checkout`: 5 Requests pro IP / 10 Minuten.
  - `/stripe/create-dynamic-checkout`: 5 Requests pro IP / 10 Minuten.

### Caveats / Follow-up

- Das Rate Limit ist aktuell pro Node-Prozess im Memory. Bei mehreren Instanzen, Restart oder horizontalem Scaling ist es nicht global persistent. Fuer Railway Single-Instance ist das kurzfristig pragmatisch, fuer eigene Server/Scaling sollte Redis oder ein zentraler Rate-Limiter in den Migration Plan.
- `stripe_handler.js` verwendet `req.ip || req.connection.remoteAddress`; `server.js` nutzt `getClientIp(req)`. Empfehlung: spaeter vereinheitlichen, damit Proxy-/Forwarded-Header-Verhalten konsistent bleibt.
- H-07 bleibt weiterhin offen: Aktivierungs-/Gift-/Billing-Code-Logs sind noch nicht vollstaendig maskiert.
- H-06 Response-Type-Regressionsrisiko bleibt offen: unregistrierter `ONLINE_STATUS_REQUEST` sollte `ONLINE_STATUS_RESPONSE` nutzen.

## Recheck nach CC-Commit `b7f81e2` — 2026-05-04

Codex hat `b7f81e2` gegen HEAD geprueft.

### VERIFIED_FIXED

- H-06 Regression: `ONLINE_STATUS_REQUEST` nutzt fuer den unregistrierten Fehlerpfad jetzt wieder `ONLINE_STATUS_RESPONSE`. Android Client hoert ebenfalls auf `ONLINE_STATUS_RESPONSE`.
- H-07 in `server.js`: Aktivierungs-/Gift-/Billing-Code-Logs in `server.js` sind jetzt maskiert.

### STILL_OPEN

- H-07 ist weiterhin nicht vollstaendig behoben, obwohl der Commit-Text "complete" sagt. Der Commit aendert nur `server.js`.
- Weiter offen in `backend/signaling/src/payments/email_handler.js`:
  - `sendActivationCode` loggt weiterhin E-Mail + Code + Tier.
- Weiter offen in `backend/signaling/src/payments/stripe_handler.js`:
  - direkt nach Code-Generierung wird der volle Code geloggt.
  - bei fehlender Customer-E-Mail wird der volle Code geloggt.
  - Webhook-Result-Log ist maskiert, aber die vorgelagerten Logs sind es nicht.

### Empfehlung an CC

- H-07 erst als geschlossen markieren, wenn `server.js`, `stripe_handler.js` und `email_handler.js` keine vollstaendigen Aktivierungs-/Gift-/Billing-Codes oder Customer-E-Mails mehr loggen.
- Sinnvoller Mini-Fix: lokale Helper `maskCode(code)` und `maskEmail(email)` in den Payment-Handlern oder gemeinsamer Utility-Datei, ohne Produktlogik zu aendern.

## Audit 2 — Dependabot / npm audit Recheck — 2026-05-04

Codex hat nach den aktuellen Security-Fixes `npm audit --audit-level=low` in `backend/signaling` und GitHub Dependabot via `gh api` geprueft.

### Ergebnis

- `npm audit --audit-level=low`: weiterhin exit code 1.
- Gemeldet: 12 Vulnerabilities, davon 10 moderate und 2 low.
- Offene GitHub Dependabot Alerts:
  - `uuid` / `GHSA-w5hq-g745-h8pq` / severity `medium` / `backend/signaling/package-lock.json`.
  - `@tootallnate/once` / `GHSA-vpq2-c234-7xj6` / severity `low` / `backend/signaling/package-lock.json`.
- Viele alte Alerts sind in GitHub bereits als `fixed` markiert, unter anderem `protobufjs`, `nodemailer`, `path-to-regexp`, `node-forge`, `qs`, `fast-xml-parser`, `rand`.

### Bewertung

- `uuid` ist nicht mehr direkte Runtime-Dependency im `package.json`, aber bleibt transitiv im Lockfile ueber Firebase/Google/Svix/Resend-Pfade sichtbar.
- `npm audit fix --force` wuerde `firebase-admin@10.1.0` installieren und ist als Breaking Change gemeldet. Nicht blind ausfuehren.
- Empfehlung: CC soll gezielt pruefen, ob ein nicht-breaking Update-Pfad ueber `firebase-admin`, `resend`/`svix` oder Overrides moeglich ist. Ohne Testplan kein Force-Fix.

## Recheck mehrerer CC-Fixes — 2026-05-04

Codex hat folgende CC-Commits gegen HEAD geprueft: `cf30743`, `c7c7e06`, `1b39f9b`, `0b64d09`, `281320f`.

### VERIFIED_FIXED

- H-08: Android Custom-ID Submission nutzt jetzt `JSONObject` statt String-Template. JSON-Injection-Risiko im geprueften Pfad ist behoben.
- M-01: PKD `PUT /key/:id` und `DELETE /key/:id` sind jetzt mit `requireAdmin` geschuetzt.
- L-02: `website/assets/og-image.svg` nennt nicht mehr `GPL Client`; Text ist auf Source Available geaendert.

### PARTIAL / NEEDS FOLLOW-UP

- H-07: deutlich verbessert, aber noch ein offener Log-Pfad sichtbar:
  - `stripe_handler.js`: Bei fehlender Customer-E-Mail wird weiterhin der vollstaendige Code geloggt. Werte werden nicht wiedergegeben.
  - `email_handler.js` und die meisten `server.js`-Pfade sind jetzt maskiert.
- H-04: `/invite/accepted` hat jetzt IP-Rate-Limit `3/10min`, aber keinen Auth-/Invite-Token-Nachweis. Damit ist Spam-Volumen reduziert, die fachliche Authentizitaet des Invite-Accepted-Events aber noch nicht bewiesen.
- L-01: `website/faq.html` wurde bereinigt, aber `README.md` und `website/llms.txt` nennen SecureCall weiterhin "open source". Empfehlung: auf "source available" / "publicly auditable" umstellen, wenn das mit BUSL-Position stimmig sein soll.

### Hinweise

- `docs/agent-bridge/TODO.md` ist lokal modified, aber Codex hat diese Aenderung nicht erstellt und nicht angefasst.
- Weiterhin offen: H-01 `/ice-servers` public, H-09 Certificate Pinning Claim/Implementierung, Dependabot `uuid`/`@tootallnate/once`.

## Recheck nach CC-Commit `cf741a0` — 2026-05-04

Codex hat `cf741a0` gegen HEAD geprueft.

### VERIFIED_FIXED

- H-07: Der letzte zuvor sichtbare Stripe-No-Email-Logpfad ist jetzt maskiert. Payment-/Activation-Code-Logs zeigen in den geprueften Pfaden nur noch gekuerzte Codes.
- L-01: README wurde von "open source" auf "publicly available" umgestellt.
- Syntaxchecks erfolgreich:
  - `node --check backend/signaling/src/server.js`
  - `node --check backend/signaling/src/payments/stripe_handler.js`
  - `node --check backend/signaling/src/payments/email_handler.js`

### REMAINING TEXT DRIFT

- `website/llms.txt` nennt weiterhin "Signal is open source; SecureCall publishes source code under BUSL-1.1 ...". Das kann als Vergleich zu Signal gemeint sein, ist aber sprachlich riskant. Empfehlung: "Signal is open source; SecureCall publishes source code under BUSL-1.1 for auditability ..." nur behalten, wenn die Abgrenzung bewusst ist. Sonst umformulieren zu "Unlike Signal's open-source model, SecureCall publishes source code under BUSL-1.1 for auditability ...".
- `website/index.html` sagt weiterhin "the client can be audited, built, and redistributed under the GPL"; das kollidiert potenziell mit BUSL bis zum Change Date. Bitte gegen Lizenzentscheidung pruefen.

## Rollenabgrenzung CC/Codex — 2026-05-04

Nutzerwunsch: klare Rollenverteilung bei Aufgabenzuweisung und Ausfuehrung, ohne Durcheinander.

Codex hat `docs/agent-bridge/README.md` aktualisiert:

- CC = Hauptentwickler fuer Produktcode-Fixes, Refactors, Tests, Commits.
- Codex = unabhaengiger Security-/Quality-Auditor, Priorisierung, Recheck, Drift-/Kollisionswarnungen.
- Codex aendert standardmaessig keinen Produktcode, solange CC aktiv in denselben Bereichen arbeitet.
- Fremde lokale Produktcode-Diffs werden von Codex nicht gestaged, committet oder reverted.
- Ablauf: Finding in `CODEX_FINDINGS.md` → Fixbericht in `CC_RESPONSE.md` → Recheck in `CODEX_FINDINGS.md` → Aktion in `ACTION_LOG.md`.

## Recheck nach CC-Commit `c15b955` und Rollen-Datei `9afaed4` — 2026-05-04

Codex hat die neuen CC-Commits gelesen und gegen HEAD geprueft.

### VERIFIED_FIXED

- Rollenverteilung: `docs/agent-bridge/ROLES.md` existiert und definiert CC als Hauptentwickler sowie Codex als Auditor/Reviewer. Das passt zum Nutzerwunsch.
- L-01/L-Textdrift: `website/index.html` und `website/llms.txt` wurden weiter bereinigt.
  - `website/index.html` nennt jetzt BUSL-1.1, personal non-commercial build/use und GPL-3.0 erst nach Change Date.
  - `website/llms.txt` grenzt Signal als open-source Modell von SecureCalls BUSL/source-available Modell ab.

### STILL_OPEN / H-09

- H-09 Certificate Pinning bleibt offen und hat zusaetzlich Status-/Claim-Drift:
  - Keine `CertificatePinner`, `X509TrustManager`, `HostnameVerifier` oder Pinning-Implementierung gefunden.
  - `client_android/app/build.gradle` setzt `CERTIFICATE_PINNING=false` fuer Pro/Premium mit TODO-Kommentar.
  - `client_android/app/src/pro/.../FeatureFlags.kt` und `client_android/app/src/premium/.../FeatureFlags.kt` setzen `CERTIFICATE_PINNING=true`.
  - `FeatureProviderRegistry` und `CompileTimeFeatureProvider` lesen aus `FeatureFlags`, dadurch kann die Settings-UI Pinning als enabled anzeigen, obwohl `BuildConfig.CERTIFICATE_PINNING` false ist und keine Implementierung sichtbar ist.
  - Website/Wiki behaupten weiterhin Certificate Pinning als Pro/Premium-/Premium-Funktion.

### Empfehlung an CC fuer H-09

Konservative Option fuer naechsten Release:

- Claims herabstufen, bis echte Implementierung existiert.
- Pro/Premium `FeatureFlags.CERTIFICATE_PINNING=false` setzen oder UI-Status eindeutig "planned"/"not enabled" machen.
- Website/Wiki-Texte von "certificate pinning" als aktiver Funktion auf "planned certificate pinning" oder "TLS + planned pinning" anpassen.

Implementierungsoption fuer spaeter:

- Zentrale OkHttpClient-Factory mit `CertificatePinner` fuer alle relevanten HTTPS/WSS-Clients.
- Pins fuer `protective-healing-production.up.railway.app`/eigene Domains sauber operationalisieren inklusive Rotation/Backup-Pins.
- Tests fuer gepinnte und nicht gepinnte Hosts.

Codex aendert hier keinen Produktcode, weil H-09 Android-/Release-Verhalten beruehrt und gemaess Rollenregel CC als Hauptentwickler Vorrang hat.

## Recheck nach CC-Commits `79efb32` / `385386a` — 2026-05-04

Codex hat die neuen CC-Fixes gegen HEAD geprueft.

### H-01 — VERIFIED_FIXED mit Caveats

- `GET /ice-servers` ist jetzt mit `requireAdmin` geschuetzt.
- Server liefert `iceServers` im `REGISTERED` WebSocket-Payload aus.
- Android `WebSocketService` liest `iceServers` aus `REGISTERED` und ruft `IceServerFetcher.injectFromRegistered(...)` auf.
- `IceServerFetcher` kann ICE-Server aus dem WS-Payload cachen.

Caveats:

- `IceServerFetcher` enthaelt weiterhin den alten HTTP-Fetch-Code und Kommentare zum Endpoint. Wenn der WS-Payload nicht rechtzeitig kommt oder Cache leer ist, koennen bestehende Aufrufer ggf. noch HTTP versuchen und dann wegen Admin-Gating scheitern. Das ist sicherer als public, aber funktional testrelevant.
- `GHOST_ACK` enthaelt ebenfalls `ICE_SERVERS`, aber der Handler prueft bereits Registrierung vor `GHOST_PREPARE`. Kein Public-Leak in diesem Pfad sichtbar.

Empfohlene Tests:

- Android: Registrierung → `REGISTERED` enthaelt ICE → `IceServerFetcher` cachet Server → Call/WebRTC nutzt gecachte ICE-Server.
- Backend: unauthentifiziertes `GET /ice-servers` muss `401/403` liefern; Admin-Request darf weiterhin JSON liefern.
- Regression: Call-Setup bei leerem ICE-Cache nach App-Start.

### H-09 — PARTIAL

- `client_android/app/build.gradle` setzt Pro/Premium `BuildConfig.CERTIFICATE_PINNING=false`.
- Pro `FeatureFlags.kt` wurde auf `false` herabgestuft.
- Premium `FeatureFlags.kt` setzt weiterhin `CERTIFICATE_PINNING=true`.
- `CompileTimeFeatureProvider` und `FeatureProviderRegistry` lesen `FeatureFlags.CERTIFICATE_PINNING`, nicht `BuildConfig.CERTIFICATE_PINNING`; dadurch kann Premium-UI weiterhin "enabled" anzeigen, obwohl keine Pinning-Implementierung sichtbar ist.
- Website/Wiki-Claims zu aktivem Certificate Pinning bestehen weiterhin an mehreren Stellen, unter anderem Landing Page, User Manual, Architecture, Security Design und Installation Guide.

Empfehlung:

- Entweder Premium ebenfalls auf `false` setzen und Website/Wiki auf "planned" herabstufen,
- oder echtes Pinning implementieren und zentral in allen OkHttp/WSS-Clients verwenden.

Codex wertet H-09 daher weiter als offen.
