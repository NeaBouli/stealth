# SecureCall — Status-Briefing für externes Sparring
**Datum:** 17. April 2026, 04:15 EEST
**Erstellt von:** Claude Code Session (Debug + Audit + Release)
**Repo:** `NeaBouli/stealth` — Branch `main` @ `51e4424`
**Zweck:** Vollständiger Projektstand ohne Repo-Zugriff

---

## 1. AKTUELLER STAND v1.0.22

### Release-Status (verifiziert 17.04. 09:51 EEST via Play Console)

| Kanal | Status | Version | Nächster Schritt |
|-------|--------|---------|-----------------|
| **GitHub** | ✅ Released | v1.0.22 (Tag `v1.0.22` @ `b974a1e`) | — |
| **Play Store — Interner Test** | 🟡 Aktiv, veraltet | v1.0.12 (vC29), 02.04.2026 | LOW: auf v1.0.22 syncen oder Track deaktivieren |
| **Play Store — Closed Alpha** | ✅ Aktiv | v1.0.22 (vC43), 17.04.2026 03:54 | Alpha-Stabilität beobachten → Production-Promotion |
| **Play Store — Production** | ❌ INAKTIV, Dashboard leer | — | Production-Promotion-Entscheidung ausstehend |
| **F-Droid** | 🟡 MR offen | MR !36495, Pipeline 8/9 grün | Wartet auf @linsui Re-Review |
| **Sideload** | ✅ APKs bereit | `releases/v1.0.22/` (free, premium, fdroid) | Auf stealthx.tech Download-Seite aktualisieren |

**Klarstellung:** Die Google-Mail vom 17.04. 02:31 bestätigte **Production-Account-Access** (Account-Level-Meilenstein), NICHT ein Release-Approval. Es gibt derzeit KEINE öffentliche Play-Store-Version der App.

### Rollout-Prozentsatz
- **Production: 0%.** Der Production-Track ist leer — kein Release jemals dort veröffentlicht.
- **Closed Alpha:** v1.0.22 verfügbar für eingeladene Tester-Gruppe "SecureCall β-test23".
- **Interner Test:** v1.0.12 (veraltet, irrelevant, max. 100 Tester).
- 3 Test-Geräte (S10, S7, Tab S4) laufen seit 16.04. via Sideload.

### Letzte Monitoring-Checks

| Check | Timestamp | Status | Highlights |
|-------|-----------|--------|-----------|
| #2 | 2026-04-17 03:41 EEST | **YELLOW** | REGISTER 8/0 (success/reject), FCM 3x EACCES (Volume uid-Mismatch), 5xx: 0, User-Reports: 0 |

**YELLOW-Grund:** Railway Volume `/app/data` gehört root, Container läuft als uid 1001 → alle JSON-Persistenz (FCM, Stripe, Subscriptions, Custom-IDs) schreibt nur in-memory, nicht auf Disk. FCM-Tokens gehen bei Redeploy verloren (Auto-Resync beim nächsten App-Start). Formalisiert als Issue #16.

---

## 2. BEKANNTE OFFENE BUGS

### GitHub Issues (offen)

| Issue | Titel | Schweregrad | Modul | Status | Zielversion |
|-------|-------|------------|-------|--------|-------------|
| [#16](https://github.com/NeaBouli/stealth/issues/16) | FCM-Persistenz: Dockerfile uid-Mismatch mit Railway Volume | severity-medium | backend (Dockerfile) | triaged, ready-for-fix | **v1.0.23** |
| [#15](https://github.com/NeaBouli/stealth/issues/15) | Reaktivierung Fork-Protection (ALLOWED_SIGNATURES) | priority-high, security | Railway Env + server.js | triaged, blocked (wartet auf v1.0.22-Adoption) | **v1.0.23** |
| #14 | [Feature] SecureCall SOS | enhancement | — | offen, nicht priorisiert | backlog |
| #13 | [Feature] Area Code Name | enhancement | — | offen, nicht priorisiert | backlog |

### Bekannte Limitierungen (kein Issue, dokumentiert in BACKLOG.md)

| Problem | Schweregrad | Status | Details |
|---------|------------|--------|---------|
| **WalletConnect v2 init schlägt fehl** | LOW | **Workaround aktiv** | `android-core:1.26.0` vs `sign:2.26.0` Version-Mismatch → init wirft `NoClassDefFoundError`. Seit vC36 (`69dd7c6`) mit `catch(Throwable)` gefangen — non-fatal, kein Crash. Wallet-Verifizierung funktioniert über SIWE-Alternative (TODO-097, fertig). WalletConnect-Protokoll selbst ist kaputt, aber User-Impact null da SIWE den gleichen Zweck erfüllt. |
| **Beta-Codes noch aktiv** | MEDIUM | TODO-047 (OPEN, HIGH) | `BETA-PRO0-2026` + `BETA-PREM-2026` sind live und gewähren Pro/Premium-Tier. Müssen VOR oder unmittelbar NACH Go-Live deaktiviert werden. |
| **Google Play Billing Verification** | MEDIUM | TODO-029 (OPEN, HIGH) | Kein Service Account für server-seitige Purchase-Verification. `verifyAgainstServer()` (v1.0.22) prüft nur lokalen State, nicht Google Play API. |

### Audit-MEDIUM-Findings (aus Session 2026-04-16, kein Issue)

| Finding | Modul | Impact |
|---------|-------|--------|
| Phone-Lookup Rate-Limit per-Connection statt per-ClientId | server.js:1137 | Directory-Enumeration möglich |
| BATCH_PHONE_LOOKUP 200 Queries/Request | server.js:1179 | Amplified Enumeration |
| Keine Auth-Failure-Logs auf Admin-Endpoints | server.js:470 | Security-Monitoring blind |
| SDP keine Bandwidth-Limitierung (10KB × 4/s) | server.js:921 | Memory-Pressure unter Last |

---

## 3. GEPLANTE FIXES FÜR v1.0.23

**Branch:** `release/v1.0.23-hotfix` (basiert auf Tag `v1.0.22`)
**versionCode:** 44 / **versionName:** 1.0.23
**Build-Pipeline:** Trockenlauf bestanden (AAB signiert, 30MB, 7m34s)
**Template:** `docs/handover/v1.0.23-hotfix-template.md`

### Eingeplante Fixes

| # | Issue | Beschreibung | Abhängigkeiten |
|---|-------|-------------|---------------|
| 1 | [#16](https://github.com/NeaBouli/stealth/issues/16) | Dockerfile `chown /app/data` vor USER-Switch → Volume beschreibbar | Keine |
| 2 | [#15](https://github.com/NeaBouli/stealth/issues/15) | Fork-Protection reaktivieren (Railway `ALLOWED_SIGNATURES`) | Abhängig von v1.0.22-Adoption (>80% senden `appSignature`) |

### Abhängigkeiten zwischen Fixes

```
#16 (Volume-Fix) ─── unabhängig, kann sofort ───► deploy
#15 (Fork-Protection) ─── blocked by: v1.0.22 Adoption >80% ───► frühestens ~2 Wochen
```

#16 ist ein Quick-Win (1 Zeile Dockerfile). #15 erfordert Adoption-Messung und ist daher zeitlich entkoppelt. Beide können unabhängig voneinander gemergt werden.

---

## 4. TECHNISCHE SCHULDEN

### Deaktivierte Sicherheitsfeatures

| Feature | Status | Grund | Plan |
|---------|--------|-------|------|
| **Fork-Protection** (`ALLOWED_SIGNATURES`) | **DEAKTIVIERT** seit 16.04.2026 | P0 Connection-Loop: Env-Var wurde gesetzt bevor v1.0.22-Clients draußen waren → alle User rejected | Reaktivierung nach v1.0.22-Adoption >80%, Issue #15 |
| **Beta-Codes** (TODO-047) | **AKTIV** — gewähren Pro/Premium gratis | Aus Testphase nie deaktiviert | Deaktivieren bei/nach Go-Live |
| **Google Play Billing Verification** (TODO-029) | **FEHLT** — keine server-seitige Purchase-Validation | Service Account nie eingerichtet | Unklar ob v1.0.23 oder später |

### Code-Qualitäts-Schulden

| Thema | Umfang | Priorität |
|-------|--------|----------|
| 551 `Log.d()` direkt statt `AppLogger` | Alle `.kt`-Files | LOW (ProGuard strippt sie in Release) |
| WalletConnect SDK Version-Mismatch | `android-core:1.26.0` vs `sign:2.26.0` | LOW (SIWE-Workaround aktiv) |
| Dependabot: 8 Vulnerabilities (5 high, 1 mod, 2 low) | npm dependencies | MEDIUM |
| Kein `UncaughtExceptionHandler` in Application-Klasse | `SecureCallApplication.kt` | LOW |
| Dead-Code `rateLimit.js` (nicht importiert, parallel zu `rate_limit.js`) | backend | LOW |

---

## 5. OFFENE FRAGEN AN DEVS

1. **Production-Promotion-Entscheidung:** Wann und mit welchem Rollout-Prozentsatz soll v1.0.22 vom Closed-Alpha-Track in den Production-Track promoted werden? Wie lange Alpha-Stabilität beobachten bevor Promotion? (Empfehlung: mind. 48h Alpha-Feedback + Pre-Launch-Report auswerten)

2. **Beta-Codes (TODO-047):** Sollen `BETA-PRO0-2026` + `BETA-PREM-2026` vor Production-Promotion deaktiviert werden? Aktuell nur Alpha-Tester betroffen (kein öffentlicher Impact), aber Hygiene-Fix vor Production sinnvoll. Welche Geräte sollen auf FREE zurückgesetzt werden?

3. **Volume-Fix (Issue #16):** Soll Option A (Dockerfile `chown`) sofort auf den Hotfix-Branch gemergt werden, auch ohne weitere Fixes? Oder warten bis es einen zweiten Grund für v1.0.23 gibt?

4. **F-Droid MR !36495:** @linsui hat nicht reagiert. Soll ein Follow-up-Ping erfolgen? Oder abwarten?

5. **Google Play Service Account (TODO-029):** Ist das für die Production-Promotion blockierend? Ohne ihn kann der Server Chargebacks/Refunds nicht erkennen — `verifyAgainstServer()` prüft nur den lokalen Subscription-State.

6. **Monitoring-Intervalle:** Wer führt die Check-Zyklen durch? Claude Code kann `./tools/monitor-rollout.sh` laufen lassen, aber Play Console Vitals erfordern manuellen Login. Alpha-Phase: stündlich reicht. Production-Phase: 15min-Kadenz in erster Stunde.

---

## 6. EMPFOHLENE REIHENFOLGE FÜR MORGEN

### Top 5 Tasks (priorisiert)

| # | Task | Begründung | Geschätzter Aufwand |
|---|------|-----------|-------------------|
| **1** | Alpha-Stabilität beobachten + Pre-Launch-Report auswerten | v1.0.22 ist in Closed Alpha — Feedback der Tester-Gruppe sammeln, Play Console auf Crashes/ANRs prüfen, Railway-Logs monitoren | laufend (stündliche Checks) |
| **2** | Production-Promotion-Entscheidung vorbereiten | Kriterien definieren (mind. 48h Alpha ohne CRITICAL, Pre-Launch-Report sauber, Monitoring GREEN) → dann Promote zu Production mit Staged Rollout 20% → 50% → 100% | Entscheidung nach Alpha-Phase |
| **3** | Volume-Fix #16 auf `release/v1.0.23-hotfix` mergen | Einziger YELLOW-Alarm im Monitoring, verhindert Daten-Loss bei nächstem Redeploy | 5 min (1 Zeile Dockerfile) |
| **4** | Beta-Codes deaktivieren (TODO-047) | Hygiene — Codes gewähren Pro/Premium gratis. Kein öffentlicher Impact solange Production leer, aber sollte VOR Promotion erledigt sein | 10 min (server.js activation_codes) |
| **5** | F-Droid Follow-up Ping auf MR !36495 | Freigabe für F-Droid-Kanal, zweiter wichtiger Distributionskanal | 5 min |

### Kontext für die Priorisierung

- **Task 1** ist die Basis für alles weitere — ohne Alpha-Stabilität keine Production-Promotion. Railway-Monitoring (`./tools/monitor-rollout.sh`) + Play Console Vitals regelmäßig prüfen.
- **Task 2** ist die strategische Entscheidung — Production-Track ist leer, der Weg dorthin erfordert mindestens 48h stabile Alpha + sauberen Pre-Launch-Report.
- **Task 3** ist ein Quick-Win der das einzige YELLOW-Signal im Monitoring beseitigt und die Daten-Integrität bei Redeployments sichert. Kann unabhängig von der Promotion-Entscheidung gemergt werden.
- **Task 4** ist ein Hygiene-Task — kein unmittelbares Risiko (nur Alpha-Tester betroffen), aber MUSS vor Production-Promotion erledigt sein.
- **Task 5** läuft async und blockiert nichts, aber je früher der Ping, desto eher die Antwort.

---

## ANHANG: Commit-Historie dieser Session

```
51e4424 docs: link Volume EACCES to Issue #16
06cab63 docs: v1.0.23 hotfix pipeline ready, trigger TBD
196f634 ops: v1.0.22 rollout monitoring setup + first check cycle
0b301e4 docs: link fork-protection reactivation to GitHub Issue #15
b974a1e docs(handover): v1.0.22 release session log + artifact hashes
03e83ee docs: v1.0.22 release notes + play store upload guide
572b58d release: v1.0.22 — changelog + website + fdroid metadata
fda8b53 docs: v1.0.22 client release verified on S10/S7/TabS4
354dd81 feat(client): v1.0.22 — REGISTERED-gated registration + 4003 stop + subscription resync
32c6ba3 fix(signaling): HIGH-002/005 + custom-ID token validation + subscription verify endpoint
acbb95e docs: session status — items 1-6 shipped, follow-ups recorded
35ce2f2 fix(client): redact PII from release logs + tighten LOGGING_LEVEL
be3c47d fix(signaling): allow origin-less WS clients (native apps)
b79d9f7 fix(signaling): CORS allowlist + Stripe idempotency + remove PW from metadata + unify admin key
c74caaa docs: agent debug session 2026-04-16 — connection-loop root cause + audit
b610663 fix(signaling): harden SECUREID_CHANGED + atomic JSON writes
```

---

*Briefing erstellt: 17. April 2026, 04:15 EEST*
*Nächstes Update: nach Play Console Upload + erstem Monitoring-Zyklus mit echtem User-Traffic*
