# Resume Next Session — Stealth / SecureCall

Stand: 2026-05-09 — Rechnerwechsel-Handover

## Sofort Lesen

1. `docs/agent-bridge/ACTION_LOG.md`
2. `docs/agent-bridge/PROJECT_STATE.md`
3. `docs/agent-bridge/QUESTIONS.md`
4. `docs/agent-bridge/TODO.md`

## Aktueller Repo-Stand

- Projektpfad alter Rechner: `/Users/gio/Desktop/repo/stealth`
- HEAD vor Rechnerwechsel: `bb9c719` auf `main` / `origin/main`
- Relevante letzte Commits:
  - `9a7e1f9` — Fork Protection Default `enforce` -> `warn`
  - `ed1d176` — Dockerfile kopiert `data/` ins Railway-Image
  - `4b3f783` — Release-Bump `v1.0.32` / `vC54`
  - `bb9c719` — Bridge/TODO Session State

## Aktueller AAB-/Play-Stand

- Upload-Datei alter Rechner: `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
- Package: `com.securecall.app.free`
- versionCode: `54002`
- versionName: `1.0.32-free`
- Manifest enthaelt: `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Gio/CC meldeten: Play Console Upload v1.0.32/vC54 erfolgt.

## Kritisches Problem

Externe Play-Tester konnten nach Play-Store-Update nicht connecten. Lokal per Gradle/ADB installierte Geraete konnten connecten.

Wahrscheinlichste Ursache:

- Backend-Forkschutz prueft beim `REGISTER` `appSignature` gegen `ALLOWED_SIGNATURES`.
- Lokal installierte Builds sind mit dem lokalen Upload-/Release-Key signiert:
  - `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
- Play Store liefert bei aktivem Play App Signing sehr wahrscheinlich Google-signierte APKs aus.
- Diese Signatur ist anders.
- Bei `FORK_PROTECTION_MODE=enforce` lehnt der Server Play-Tester ab:
  - `ERROR unauthorized_client`
  - Close Code `4003 Unauthorized client`
- Client stoppt danach den Reconnect-Loop.

## Bereits Umgesetzt

- `backend/signaling/src/server.js`:
  - Default `FORK_PROTECTION_MODE` wurde von `enforce` auf `warn` geaendert.
- `backend/signaling/Dockerfile`:
  - `data/` wird ins Docker-Image kopiert, damit `data/activation_codes.json` auf Railway nicht fehlt.
- `v1.0.32` / `vC54` wurde gebaut/hochgeladen.

## Noch Blockierend / Naechster Schritt

Railway muss aktualisiert werden.

Auf neuem Rechner:

1. Railway CLI einloggen/verknuepfen.
2. Railway env pruefen:
   - `FORK_PROTECTION_MODE` darf nicht `enforce` sein.
   - Entweder env var entfernen oder auf `warn` setzen.
3. Railway redeploy/restart ausfuehren, damit `9a7e1f9` und `ed1d176` live sind.
4. Railway Logs pruefen:
   - Vor Fix erwartbar: `[REGISTER] REJECTED — unauthorized signature: ...`
   - Nach Fix erwartbar: Warn-Log oder erfolgreicher `REGISTERED`, aber kein Disconnect.
5. Externe Play-Tester App oeffnen lassen und Connect bestaetigen.
6. Bridge aktualisieren.

## Railway Zugriff Auf Altem Rechner

- `railway whoami` meldete:
  - `Unauthorized. Please run railway login again.`
- `railway status`/`railway logs` konnten nicht genutzt werden.
- Keine Secrets aus Railway gelesen.

## Lokaler Geraetestand Auf Altem Rechner

- S10/S7/Tab S4 waren zuletzt lokal auf `com.securecall.app.free vC53002 / v1.0.31-free`.
- Alte Test-Flavors wurden entfernt:
  - S10 `com.securecall.app.premium`
  - S7 `com.securecall.app.pro`
- Danach kam v1.0.32/vC54.
- Nicht annehmen, dass diese Geraete schon v1.0.32 haben; nach Rechnerwechsel per ADB neu pruefen.

## Testergeraet

- Ein weiteres Samsung-Testgeraet wurde angeschlossen, war aber auf altem Rechner nicht sichtbar.
- Es erschien nicht in `adb devices -l` und nicht im macOS USB-Bus.
- Wahrscheinlich Kabel/Port/USB-Modus/ADB-Handshake.

## Bridge-Regel

- Bridge bei jedem relevanten Schritt updaten.
- Besonders nach:
  - Railway env-Aenderung
  - Railway redeploy/restart
  - Tester-Retest
  - Play-Console-Ergebnis

## Grenzen

- Keine Secrets lesen oder ausgeben.
- Keine User-/CC-Aenderungen revertieren.
- Keine Pro/Premium-App neu installieren, ausser Gio fordert das explizit an.
- Nach Rechnerwechsel erst Status pruefen, dann handeln.
