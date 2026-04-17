# SecureCall — Status-Briefing für externes Sparring
**Datum:** 17. April 2026, 04:15 EEST
**Erstellt von:** Claude Code Session (Debug + Audit + Release)
**Repo:** `NeaBouli/stealth` — Branch `main` @ `51e4424`
**Zweck:** Vollständiger Projektstand ohne Repo-Zugriff

---

## 1. AKTUELLER STAND v1.0.22

### Release-Status
| Kanal | Status | Version | Nächster Schritt |
|-------|--------|---------|-----------------|
| **GitHub** | ✅ Released | v1.0.22 (Tag `v1.0.22` @ `b974a1e`) | — |
| **Play Store** | 🟡 AAB gebaut, nicht hochgeladen | vC43, `securecall-v1.0.22-free.aab` | Manuell in Play Console hochladen |
| **F-Droid** | 🟡 MR offen | MR !36495, Pipeline 8/9 grün | Wartet auf @linsui Re-Review |
| **Sideload** | ✅ APKs bereit | `releases/v1.0.22/` (free, premium, fdroid) | Auf stealthx.tech Download-Seite aktualisieren |

### Rollout-Prozentsatz
- **Aktuell: 0% öffentlich.** AAB noch nicht in Play Console. Nur die 3 Test-Geräte (S10, S7, Tab S4) laufen v1.0.22 via Sideload.
- Empfehlung: Staged Rollout 10% → 50% → 100% über 3 Tage nach Upload.

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

1. **Play Console Upload:** Wann wird die AAB hochgeladen? Soll Staged Rollout (10% → 50% → 100%) oder Immediate Rollout?

2. **Beta-Codes (TODO-047):** Sollen `BETA-PRO0-2026` + `BETA-PREM-2026` vor Go-Live deaktiviert werden? Falls ja: welche Geräte sollen auf FREE zurückgesetzt werden?

3. **Volume-Fix (Issue #16):** Soll Option A (Dockerfile `chown`) sofort auf den Hotfix-Branch gemergt werden, auch ohne weitere Fixes? Oder warten bis es einen zweiten Grund für v1.0.23 gibt?

4. **F-Droid MR !36495:** @linsui hat nicht reagiert. Soll ein Follow-up-Ping erfolgen? Oder abwarten?

5. **Google Play Service Account (TODO-029):** Ist das für den Launch blockierend? Ohne ihn kann der Server Chargebacks/Refunds nicht erkennen — `verifyAgainstServer()` prüft nur den lokalen Subscription-State.

6. **Monitoring-Intervalle:** Wer führt die Check-Zyklen nach den ersten 24h durch? Claude Code kann `./tools/monitor-rollout.sh` laufen lassen, aber Play Console Vitals erfordern manuellen Login.

---

## 6. EMPFOHLENE REIHENFOLGE FÜR MORGEN

### Top 5 Tasks (priorisiert)

| # | Task | Begründung | Geschätzter Aufwand |
|---|------|-----------|-------------------|
| **1** | AAB in Play Console hochladen + Staged Rollout starten | Release blockiert — alles andere wartet darauf | 15 min (manuell) |
| **2** | Beta-Codes deaktivieren (TODO-047) | Security — jeder kann sich gratis Pro/Premium geben solange Codes aktiv sind | 10 min (server.js activation_codes) |
| **3** | Volume-Fix #16 auf `release/v1.0.23-hotfix` mergen | Einziger YELLOW-Alarm im Monitoring, verhindert Daten-Loss bei nächstem Redeploy | 5 min (1 Zeile Dockerfile) |
| **4** | Monitoring Check-Zyklen nach Rollout-Start | Crash-Rate + Connection-Success messen, Pre-Launch-Report auswerten | 2-3h (15min-Intervalle erste Stunde) |
| **5** | F-Droid Follow-up Ping auf MR !36495 | Freigabe für F-Droid-Kanal, zweiter wichtiger Distributionskanal | 5 min |

### Kontext für die Priorisierung

- **Task 1** ist der einzige echte Blocker — ohne Play Console Upload gibt es keinen öffentlichen Release.
- **Task 2** ist ein Sicherheits-Hygiene-Task — die Beta-Codes waren für die Testphase gedacht und sollten nicht im Produktions-Modus aktiv sein.
- **Task 3** ist ein Quick-Win der das einzige YELLOW-Signal im Monitoring beseitigt und die Daten-Integrität bei Redeployments sichert.
- **Task 4** ist erst relevant nachdem Task 1 erledigt ist (kein Traffic = nichts zu monitoren).
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
