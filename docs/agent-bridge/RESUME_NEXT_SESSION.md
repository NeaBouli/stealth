# Resume Next Session — Stealth / SecureCall

Stand: 2026-05-09

## Sofort Lesen

1. `docs/agent-bridge/ACTION_LOG.md`
2. `docs/agent-bridge/PROJECT_STATE.md`
3. `docs/agent-bridge/QUESTIONS.md`
4. `docs/agent-bridge/TODO.md`

## Aktueller AAB-Stand

- Upload-Datei: `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
- Package: `com.securecall.app.free`
- versionCode: `53002`
- versionName: `1.0.31-free`
- SHA-256: `6d0ee1b70efea5f7470544d0d5ba184b027025175c407597e3635ea0e3749433`
- Permission im Manifest: `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `bundletool validate`: PASS

## Aktueller Geraetestand

Alle drei Testgeraete haben jetzt nur noch die Play-Console-nahe Free-Version:

- S10 / `RF8N313QMFL`:
  - Package: `com.securecall.app.free`
  - versionCode: `53002`
  - versionName: `1.0.31-free`
  - altes `com.securecall.app.premium` deinstalliert: `Success`
- S7 / `ce10160adc00152604`:
  - Package: `com.securecall.app.free`
  - versionCode: `53002`
  - versionName: `1.0.31-free`
  - altes `com.securecall.app.pro` deinstalliert: `Success`
- Tab S4 / `ce12182c68644439037e`:
  - Package: `com.securecall.app.free`
  - versionCode: `53002`
  - versionName: `1.0.31-free`

Battery-/Service-Status zuletzt verifiziert:

- `dumpsys deviceidle whitelist` enthaelt `user,com.securecall.app.free,...` auf allen drei Geraeten.
- `WebSocketService` lief auf allen drei Geraeten als Foreground-Service:
  - `isForeground=true`
  - `startRequested=true`
  - `stopIfKilled=false`
- Free-App wurde auf allen drei Geraeten gestartet.

## Q2 Battery Optimization

Finaler Codex-Status:

- Manifest-Permission: geloest.
- Battery-Erklaer-Dialog vor Systemdialog: geloest.
- Samsung-Dialog-Stacking im Allow-Pfad: geloest.
- WakeLock-Refresh:
  - 30-Min-Timeout.
  - non-reference-counted.
  - Refresh bei jedem `WebSocketService.onStartCommand()`.
- Settings-Warnung: bleibt UX-Verbesserung, kein harter Upload-Blocker.

## Naechster Schritt Nach Neustart

1. `git status --short --branch` pruefen.
2. `adb devices -l` pruefen.
3. Pro Geraet verifizieren:
   - nur `package:com.securecall.app.free`
   - `versionCode=53002`
   - `versionName=1.0.31-free`
   - DeviceIdle-Whitelist fuer `com.securecall.app.free`
   - `WebSocketService` als Foreground-Service
4. Danach Lockscreen-Langzeittest:
   - Geraete sperren.
   - 20-30 Minuten warten.
   - Eingehenden Call testen.
   - Bei Problemen Logcat: `WS_SERVICE`, `HeartbeatClient`, `PowerManager`, `ActivityManager`.

## Grenzen

- Keine Secrets lesen.
- Keine User-/CC-Aenderungen revertieren.
- Keine Pro/Premium-App neu installieren, ausser Gio fordert das explizit an.
- Nach Neustart erst Status pruefen, dann testen.
