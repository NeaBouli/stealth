# TODO-047 — BETA-Codes Deaktivierung: Analyse

**Analyst:** Dev-Claude-Code
**Datum:** 17.04.2026
**Status:** ANALYSE ABGESCHLOSSEN — wartet auf Koordinator-Entscheidung
**KEIN CODE-CHANGE ohne Freigabe.**

## Validierungs-Ort

**Datei:** `backend/signaling/src/server.js`
**Zeilen:** 149-230 (Code-Loading), 1280-1370 (ACTIVATE_CODE Handler)

## Aktueller Stand

BETA-Codes sind **bereits auskommentiert** im Code (Zeile 157-158):
```javascript
// DEACTIVATED: {code: "BETA-PRO0-2026", tier: "pro", maxUses: 50},
// DEACTIVATED: {code: "BETA-PREM-2026", tier: "premium", maxUses: 25},
```

**ABER:** `data/activation_codes.json` auf Railway Volume koennte noch aktive BETA-Codes enthalten (UNVERIFIZIERT — kein Volume-Zugriff verifiziert).

## Aktive Codes (gewollt)
- 30x PREM-XXXX-XXXX-XXXX Tester Reward Codes (maxUses: 2)
- TEST-PRO1-CODE + TEST-PREM-CODE (maxUses: 10, intern)
- Stripe-generierte Codes (eigener Pfad via sold_codes.json)

## Abhaengigkeits-Karte
```
ACTIVATE_CODE Handler (Zeile 1280)
  └─> activationCodes[] (In-Memory)
      ├─> FALLBACK_CODES (hardcoded) — BETA auskommentiert
      ├─> activation_codes.json (Railway Volume) — UNVERIFIZIERT
      └─> sold_codes.json (Stripe) — kein BETA
```
NICHT betroffen: Google Play Subscriptions, IFR Token Lock

## 3 Fix-Optionen

### Option A — activation_codes.json bereinigen
Aufwand: 10min | Risiko: niedrig | Erfordert: Railway Volume-Zugriff

### Option B — Hard-Disable-Flag im Code (EMPFOHLEN)
```javascript
const BLOCKED_CODES = ["BETA-PRO0-2026", "BETA-PREM-2026"];
if (BLOCKED_CODES.includes(code)) {
  return ws.send(JSON.stringify({
    type: "ACTIVATE_CODE_RESULT", success: false, error: "expired"
  }));
}
```
Aufwand: 5min + Deploy | Risiko: niedrig | Schliesst beide Pfade

### Option C — Ablaufdatum-Feld
Aufwand: 30min | Risiko: mittel | Overengineered fuer 2 Codes

## Empfehlung: Option B
BETA-Codes im FALLBACK bereits inaktiv, aber JSON auf Railway UNVERIFIZIERT. Option B schliesst beide Pfade mit 5 Zeilen Code. Sofort wirksam nach Deploy.

## Test-Matrix
| Test | Erwartung |
|------|-----------|
| Bereits eingeloeste User | PASS (Tier unabhaengig in subscriptions.js) |
| Neuer BETA-PRO0-2026 | REJECT ("expired") |
| Neuer BETA-PREM-2026 | REJECT ("expired") |
| PREM-XXXX Tester-Code | PASS |
| Stripe-Code | PASS |
| Premium-Zahlkunden | PASS (Google Play, eigener Pfad) |
