# TODO-047 — Beta-Code-Deaktivierung: Analyse

**Datum:** 17. April 2026
**Status:** Analyse abgeschlossen — wartet auf Fix-Pfad-Entscheidung
**§3:** Zahlende Premium-User dürfen NICHT betroffen werden.

---

## 1. Validierungs-Ort

### Server-seitig
**Datei:** `backend/signaling/src/server.js`

**Laden der Codes (L148-216):**
```
CODES_FILE = path.join(__dirname, "..", "data", "activation_codes.json")

loadActivationCodes():
  1. Versucht activation_codes.json zu lesen
  2. Falls ENOENT → fällt zurück auf FALLBACK_CODES (L154-190)
  3. Merged danach sold_codes.json (Stripe-Purchases) hinzu
```

**FALLBACK_CODES (L154-190):**
```javascript
// L157: // DEACTIVATED: {code: "BETA-PRO0-2026", tier: "pro", maxUses: 50}
// L158: // DEACTIVATED: {code: "BETA-PREM-2026", tier: "premium", maxUses: 25}
// → BEREITS AUSKOMMENTIERT — nicht in Fallback enthalten
```

**Jedoch** aktiv in der lokalen Datei `backend/signaling/data/activation_codes.json`:
```json
{"code": "BETA-PRO0-2026", "tier": "pro", "maxUses": 50, "currentUses": 0},
{"code": "BETA-PREM-2026", "tier": "premium", "maxUses": 25, "currentUses": 0}
```

**Redeem-Handler (L1284-1370):**
```javascript
// L1297: const entry = activationCodes.find(c => c.code === code);
// → Sucht im in-memory-Array (geladen aus JSON oder Fallback)
// Prüft: maxUses, currentUses, usedBy-Array
// Bei Erfolg: devices.push(myClientId), saveActivationCodes()
```

### Client-seitig
**Datei:** `client_android/.../ui/SettingsFragment.kt:583`
```kotlin
ws.activateCode(code) { success, tier, error ->
    if (success) TierManager.setActivatedTier(ctx, tier)
    // → App startet neu mit neuem Tier
}
```

**Tier-Persistenz:** `TierManager.kt:37` → SharedPreferences `activated_tier`

---

## 2. Betroffene Daten-Records

### Railway Production (direkt verifiziert via GraphQL API)
```
[ACTIVATION] Could not load activation_codes.json: ENOENT: no such file
or directory, open '/app/data/activation_codes.json' — using fallback codes
```

**→ Railway-Server lädt FALLBACK_CODES, NICHT die JSON-Datei.**
**→ In FALLBACK_CODES sind BETA-Codes AUSKOMMENTIERT (L157-158).**
**→ BETA-Codes sind auf Production aktuell NICHT einlösbar.**

### Railway-Logs: Redemption-Versuche
```
Filter "BETA": 0 Ergebnisse
Filter "ACTIVATION": 1 Ergebnis (nur der ENOENT-Load-Log)
```

**→ 0 Redemptions der BETA-Codes. Jemals. Auf keinem Deployment.**

### Lokale JSON-Datei (nicht auf Railway, nur im Repo)
```
data/activation_codes.json: BETA-PRO0-2026 currentUses=0, BETA-PREM-2026 currentUses=0
```

**→ Bestätigt: 0 Einlösungen, 0 betroffene User.**

---

## 3. Weitere Risiko-Codes (Analyse-Bonus)

Neben den BETA-Codes sind in FALLBACK_CODES (die AKTUELL auf Railway aktiv sind):

| Code | Tier | maxUses | Risiko |
|------|------|---------|--------|
| `TEST-PRO1-CODE` | pro | 10 | **MITTEL** — trivial zu erraten |
| `TEST-PREM-CODE` | premium | 10 | **MITTEL** — trivial zu erraten |
| 30× `PREM-xxxx-xxxx-xxxx` | premium | je 2 | **NIEDRIG** — zufällig, schwer zu erraten |

Die `TEST-*`-Codes sind ebenfalls ein Hygiene-Problem: jeder der den
Codenamen errät (oder im GitHub-Repo liest — **das Repo ist public!**)
kann sich Pro/Premium geben. **Das Repo ist öffentlich** →
FALLBACK_CODES im Quelltext sind einsehbar.

---

## 4. Abhängigkeits-Karte (§3)

```
User gibt Code ein (SettingsFragment.kt:583)
        │
        ▼
WebSocketService.activateCode() → sendet {"type":"ACTIVATE_CODE","code":"..."}
        │
        ▼
server.js ACTIVATE_CODE Handler (L1284)
  │
  ├── activationCodes.find(c => c.code === code)  ← HIER greifen Beta-Codes
  │   └── Prüft: maxUses, usedBy-Array
  │
  ├── Bei Erfolg: entry.usedBy.push(clientId), saveActivationCodes()
  │   └── Sendet: {type: "ACTIVATE_CODE_RESULT", success: true, tier: "pro"|"premium"}
  │
  └── Client empfängt → TierManager.setActivatedTier(tier) → SharedPreferences
      └── App restartet mit neuem Tier
```

**Abhängige Module:**
- `SubscriptionManager.kt` — separater Pfad (Google Play Billing), NICHT betroffen
- `IfrLockManager.kt` — separater Pfad (Ethereum IFR Lock), NICHT betroffen
- `TrialManager.kt` — Trial-Timer, unabhängig von Activation-Codes
- Stripe-generierte Codes (sold_codes.json) — separater Merge-Pfad, NICHT betroffen

**§3 Bewertung:** Zahlende User (Stripe-Codes, IFR-Lock, Google Play) nutzen
ANDERE Pfade und werden von keiner Beta-Code-Änderung berührt.

---

## 5. Ist-Zustand zusammengefasst

| Aspekt | Status |
|--------|--------|
| BETA-Codes auf Railway live? | **NEIN** — ENOENT auf JSON, Fallback hat sie auskommentiert |
| BETA-Codes jemals eingelöst? | **NEIN** — 0 Redemptions in Logs + currentUses=0 |
| User von Deaktivierung betroffen? | **0 User** |
| TEST-Codes auf Railway live? | **JA** — in FALLBACK_CODES, trivial erratbar, Repo ist public |
| PREM-Tester-Codes auf Railway live? | **JA** — 30 Stück, je maxUses=2, zufällige Strings |
| Stripe-/IFR-/Play-Billing-User betroffen? | **NEIN** — komplett separate Pfade |

---

## 6. Fix-Optionen

### Option A: Ablaufdatum serverseitig setzen
In `activation_codes.json` und `FALLBACK_CODES` ein `expiresAt`-Feld hinzufügen,
im ACTIVATE_CODE-Handler prüfen.

**Pro:** Granular, kann in Zukunft für alle Codes genutzt werden.
**Contra:** Neues Feature (Expiry-Logik), braucht Code-Änderung im Handler,
Overkill für das aktuelle Problem (0 betroffene User).

### Option B: Codes aus FALLBACK_CODES + JSON-Datei entfernen
Beta-Codes sind schon auskommentiert in FALLBACK. JSON-Datei bereinigen.
TEST-Codes ebenfalls entfernen (public Repo → einsehbar).

**Pro:** Einfachste Lösung, 0 User betroffen, kein neues Feature nötig.
**Contra:** Wenn die JSON-Datei auf Railway nie existiert, ist nur der
Fallback-Pfad relevant — die JSON-Bereinigung ist defensive Hygiene.

### Option C: Hard-Disable-Flag (Env-Var `BETA_CODES_DISABLED=true`)
Neues Env-Var auf Railway das alle Codes mit `BETA-*` oder `TEST-*` Prefix blockiert.

**Pro:** Konfigurativ, kein Code-Deploy nötig zum Ein/Ausschalten.
**Contra:** Neues Feature, Over-Engineering für 0 betroffene User.

---

## 7. Empfehlung

**Option B — Codes aus FALLBACK_CODES und JSON-Datei entfernen.**

Begründung:
1. **0 User betroffen** — keine Einlösungen jemals, keine Downgrades nötig.
2. **BETA-Codes sind de facto bereits deaktiviert** auf Railway (auskommentiert in Fallback, JSON-Datei existiert nicht). Fix ist rein defensiv.
3. **TEST-Codes sind das eigentliche Risiko** — `TEST-PRO1-CODE` und `TEST-PREM-CODE` stehen im öffentlichen GitHub-Repo und sind trivial erratbar. Jeder kann sie einlösen (10 Slots pro Code).
4. **PREM-Tester-Codes belassen** — zufällige Strings, maxUses=2, geringes Risiko, werden ggf. noch von Testern gebraucht.
5. **§3 erfüllt:** Stripe/IFR/Play-Billing-User sind auf komplett separaten Pfaden und werden nicht berührt.

**Scope der Änderung:**
- `server.js:155-158` — `TEST-*` und `BETA-*` Einträge aus FALLBACK_CODES entfernen
- `data/activation_codes.json` — `TEST-*` und `BETA-*` Einträge entfernen
- Kein Client-Code betroffen
- Kein Redeploy-Impact (ENOENT → Fallback, Fallback ohne die Codes → fertig)

---

*Analyse abgeschlossen: 17. April 2026*
*Nächster Schritt: Fix-Pfad-Entscheidung mit Projektleitung*
