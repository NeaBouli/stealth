# Stealth Action Log

## 2026-07-11 — Codex Payment-Ownership dokumentiert

- Gio hat Codex als verantwortlichen Agenten fuer Stripe, Zahlung, Fulfillment, Refund und Etimologio/myDATA von SecureCall/StealthX eingesetzt.
- Rollen, Projektstatus und Payment-TODO aktualisiert.
- Private steuerliche Daten bleiben ausschliesslich in VLABS/Runtime-Secrets; keine Werte in Public Repo oder Bridge.
- Keine Produktcodeaenderung in diesem Schritt, keine Zahlung, keine Rechnung, kein Provider-/AADE-Request, kein Deploy.
- Aufgabenmatrix nachgeschaerft: Codex baut Payment/Etimologio; Core-Dev baut Produkt/Krypto und reviewt Integrationsgrenzen; Gio/Accountant/Provider liefern externe Freigaben.
- Gio-Folgeentscheidung eingetragen: Codex uebernimmt das gesamte Public Repo. Andere Devs arbeiten nur nach Handover oder als Reviewer; private Daten bleiben lokal/Runtime-only.

## 2026-05-09 - Codex: Rechnerwechsel-Handover fuer neuen Codex/CC

- Agent: Codex
- Anlass:
  - Gio wechselt den Rechner. Neuer Codex und neue Claude-Code-Session muessen ohne Chatverlauf up to date sein.
- Aktueller Repo-Stand vor Rechnerwechsel:
  - HEAD: `bb9c719` / `main` / `origin/main`
  - Letzte Commits:
    - `9a7e1f9` — Fork Protection Default `enforce` -> `warn`
    - `ed1d176` — Dockerfile kopiert `data/` ins Railway-Image
    - `4b3f783` — Version-Bump `v1.0.32` / `vC54`
    - `bb9c719` — Bridge/TODO Session State
- Aktuelle Upload-/Play-Console-AAB:
  - Datei: `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
  - package: `com.securecall.app.free`
  - versionCode: `54002`
  - versionName: `1.0.32-free`
  - Manifest enthaelt `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - Gio/CC meldeten: Play Console Upload fuer v1.0.32/vC54 ist erfolgt.
- Kritisches Produktionsproblem:
  - Play-Tester waren nach Update disconnected.
  - Codex-Hauptbefund: Backend-Forkschutz blockt Play-Store-signierte Apps, weil Google Play App Signing eine andere App-Signatur ausliefert als lokale ADB/Gradle-Installationen.
  - Backend-Codepfad:
    - `backend/signaling/src/server.js`
    - `REGISTER` prueft `msg.appSignature` gegen `ALLOWED_SIGNATURES`.
    - Bei `FORK_PROTECTION_MODE=enforce`: `ERROR unauthorized_client`, danach Close `4003 Unauthorized client`.
  - Client-Codepfad:
    - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
    - sendet beim `REGISTER` `appSignature`.
    - behandelt `4000..4099`/`unauthorized_client` als harte Ablehnung und stoppt Reconnect-Loops.
- Umgesetzter Code-Fix:
  - `server.js`: Default fuer `FORK_PROTECTION_MODE` wurde von `enforce` auf `warn` geaendert.
  - Wichtig: Wenn Railway env var `FORK_PROTECTION_MODE=enforce` gesetzt ist, ueberschreibt sie den Code-Default weiterhin.
- Railway-Status:
  - Railway CLI auf diesem Rechner war nicht nutzbar:
    - `railway whoami` -> `Unauthorized. Please run railway login again.`
    - `railway logs`/`railway status` konnten deshalb nicht ausgefuehrt werden.
  - CC-Eintrag sagt: Railway Redeploy ist NOETIG, damit Dockerfile + Fork-Protection-Fix live gehen.
  - CC-Eintrag sagt ebenfalls: Railway env var `FORK_PROTECTION_MODE` muss entfernt oder auf `warn` gesetzt werden.
- Noetige naechste Schritte auf neuem Rechner:
  1. Repo/Branch pruefen: `git status --short --branch` und `git log --oneline -8`.
  2. Railway CLI einloggen/verknuepfen.
  3. Railway env pruefen:
     - `FORK_PROTECTION_MODE` darf nicht `enforce` sein; entfernen oder `warn` setzen.
     - `ALLOWED_SIGNATURES` kann bleiben, blockt aber bei `warn` nicht.
  4. Railway redeploy/restart ausfuehren.
  5. Railway Logs pruefen:
     - vorher/bei Fehler: `[REGISTER] REJECTED — unauthorized signature: ...`
     - nach Fix: WARN statt Reject oder erfolgreiche `REGISTERED`.
  6. Play-Tester App oeffnen lassen und Connect testen.
  7. Erst danach weiteren AAB/Play-Upload oder Code-Aenderungen vornehmen.
- Geraetestand auf altem Rechner:
  - S10/S7/Tab S4 wurden zuletzt lokal auf `com.securecall.app.free vC53002 / v1.0.31-free` gebracht und alte Pro/Premium-Apps entfernt.
  - Danach wurde v1.0.32/vC54 gebaut/hochgeladen, aber nicht von Codex auf diese drei Geraete re-verifiziert.
  - Neuer Codex/CC soll nach Rechnerwechsel nicht annehmen, dass lokale Testgeraete schon v1.0.32 haben; bitte neu per ADB pruefen.
- Testergeraet:
  - Ein weiteres Samsung-Testgeraet liess sich auf altem Rechner nicht erkennen.
  - Es tauchte weder in `adb devices -l` noch im macOS USB-Bus auf.
  - Ursache wahrscheinlich Kabel/Port/USB-Modus/ADB-Handshake, nicht SecureCall.
- Bridge-Regel:
  - Bridge bei jedem relevanten Schritt aktualisieren.
  - Besonders nach Railway env/redeploy, Tester-Retest und Play-Console-Ergebnis.
- Keine Secrets gelesen oder ausgegeben.

## 2026-05-09 - CC: Fork Protection + Dockerfile Fix + v1.0.32 Play Console Upload

- Agent: Claude Code
- Fixes:
  1. **Fork Protection Default enforce → warn** (`server.js`): Play Store re-signiert APKs mit Google App Signing Key. Enforce-Modus blockte alle Play-Tester. Default jetzt "warn" (loggen, nicht blocken). Commit `9a7e1f9`.
  2. **Dockerfile COPY data/** (`Dockerfile`): Nur `src/` wurde kopiert, `data/activation_codes.json` fehlte → ENOENT auf Railway. Fix: `COPY data/ ./data/`. Commit `ed1d176`.
  3. **Version-Bump v1.0.32 (vC54)**: Play Console brauchte neuen versionCode. Commit `4b3f783`.
- Play Console: AAB v1.0.32 (vC54→54002) hochgeladen (Gio bestaetigt).
- Railway: Redeploy NOETIG damit Dockerfile + Fork Protection live gehen.
- Railway env var: `FORK_PROTECTION_MODE` muss entfernt oder auf `warn` gesetzt werden (ueberschreibt sonst Code-Default).
- Keine Secrets gelesen oder ausgegeben.

## 2026-05-09 - Codex: Externe Play-Tester disconnected — Signatur-Forkschutz als Hauptursache

- Agent: Codex
- Anlass:
  - Gio meldete: Alle Tester, die ueber Play Store/AAB upgedatet haben, koennen nicht connecten.
  - Nur die drei lokal am Rechner upgedateten Geraete verbinden.
  - Ein Tester-Samsung sollte angeschlossen werden, wurde aber vom Rechner/ADB nicht erkannt.
- Testergeraet-Status:
  - Neues Samsung war weder in `adb devices -l` noch im macOS USB-Bus sichtbar.
  - Sichtbar waren nur S7 `ce10160adc00152604` und Tab S4 `ce12182c68644439037e`.
  - Testergeraet konnte deshalb nicht direkt ausgelesen werden.
- Codebefund:
  - Android sendet beim `REGISTER` die SHA-256-Signatur des installierten App-Zertifikats:
    - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
    - Feld: `appSignature`
  - Backend prueft beim `REGISTER` hart gegen `ALLOWED_SIGNATURES`:
    - `backend/signaling/src/server.js`
    - Wenn `appSignature` fehlt oder nicht erlaubt ist und `FORK_PROTECTION_MODE=enforce`, sendet der Server:
      - `ERROR unauthorized_client`
      - danach Close Code `4003` / `Unauthorized client`
  - Client behandelt `4000..4099` und `unauthorized_client` als harte Ablehnung und stoppt Reconnect-Loops.
- Lokaler Referenzbefund:
  - Lokal installierte Free-Release-App wurde per `./gradlew :app:installFreeRelease` installiert.
  - Diese lokale Release-App ist mit dem lokalen Upload-/Release-Zertifikat signiert.
  - AAB/APK-Zertifikat laut `keytool -printcert -jarfile`:
    - SHA-256: `1E:0A:8E:B4:19:54:0D:E8:54:5F:77:0E:78:DC:DB:93:AB:1B:A8:A0:71:3D:A8:99:92:22:FC:88:C3:FD:B2:1D`
    - normalisiert: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
  - Bridge/TODO dokumentiert Railway:
    - `ALLOWED_SIGNATURES=1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
    - `FORK_PROTECTION_MODE=enforce`
- Wahrscheinlichste Ursache:
  - Google Play App Signing signiert ausgelieferte Play-Store-APKs mit dem Google-App-Signing-Zertifikat.
  - Dieses Zertifikat ist normalerweise NICHT identisch mit dem lokalen Upload-/Release-Zertifikat.
  - Play-Tester senden daher eine andere `appSignature`.
  - Backend lehnt diese Tester als `unauthorized_client` ab.
  - Lokal installierte Geraete connecten, weil deren Signatur exakt der erlaubten lokalen Signatur entspricht.
- Server-Logs:
  - Railway CLI ist lokal nicht nutzbar: `railway whoami` meldet `Unauthorized. Please run railway login again.`
  - `railway logs` konnte deshalb nicht ausgefuehrt werden.
  - Erwarteter Log-Eintrag bei bestaetigter Ursache:
    - `[REGISTER] REJECTED — unauthorized signature: <play-signing-sha256> from <clientId>`
- Empfohlener Sofort-Fix:
  1. In Google Play Console den **App signing key certificate SHA-256** holen:
     - Play Console -> App -> Setup -> App integrity -> App signing key certificate -> SHA-256.
  2. Railway `ALLOWED_SIGNATURES` auf beide Signaturen setzen:
     - `<lokale-upload-signatur>,<google-play-app-signing-signatur>`
  3. Railway Service neu starten/redeployen.
  4. Danach Play-Tester App oeffnen lassen; sie sollten beim `REGISTER` wieder `REGISTERED` erhalten.
- Emergency-Fallback, falls sofortiger Restore wichtiger ist als Fork-Schutz:
  - Railway `FORK_PROTECTION_MODE=warn` setzen und Service neu starten.
  - Danach blockt der Server fremde Signaturen nicht mehr, loggt sie aber.
  - Das ist nur als temporaere Recovery empfohlen.
- Kein Code geaendert.
- Kein Deployment.

## 2026-05-09 - Codex: Alte Pro/Premium-Apps von Testgeraeten entfernt

- Agent: Codex
- Anlass:
  - Gio bat, die alten Apps von den Geraeten zu entfernen, nachdem die Play-Console-Free-Version installiert war.
- Entfernt:
  - S10 / `RF8N313QMFL`:
    - `adb uninstall com.securecall.app.premium`
    - Ergebnis: `Success`
  - S7 / `ce10160adc00152604`:
    - `adb uninstall com.securecall.app.pro`
    - Ergebnis: `Success`
- Nicht entfernt:
  - `com.securecall.app.free` blieb auf allen drei Geraeten installiert.
- Finaler Paketstand laut `pm list packages securecall`:
  - S10: nur `package:com.securecall.app.free`
  - S7: nur `package:com.securecall.app.free`
  - Tab S4: nur `package:com.securecall.app.free`
- Finaler Versionsstand:
  - S10:
    - package: `com.securecall.app.free`
    - versionCode: `53002`
    - versionName: `1.0.31-free`
    - lastUpdateTime: `2026-05-09 01:32:18`
  - S7:
    - package: `com.securecall.app.free`
    - versionCode: `53002`
    - versionName: `1.0.31-free`
    - lastUpdateTime: `2026-05-09 01:30:44`
  - Tab S4:
    - package: `com.securecall.app.free`
    - versionCode: `53002`
    - versionName: `1.0.31-free`
    - lastUpdateTime: `2026-05-09 01:31:18`
- Bewertung:
  - Alle drei Geraete haben jetzt nur noch die Play-Console-nahe Free-Variante fuer den Test.
  - Pro/Premium-Testvarianten sind von S10/S7 entfernt.
- Kein Commit/Push/Deployment.

## 2026-05-09 - Codex: Play-Console-Free-Version auf alle drei Geraete installiert

- Agent: Codex
- Anlass:
  - Gio wollte explizit die Version testen, die auch in der Google Play Console laeuft.
- Referenz-Artefakt:
  - `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
  - package: `com.securecall.app.free`
  - versionCode: `53002`
  - versionName: `1.0.31-free`
  - Manifest enthaelt `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Vorgehen:
  - `bundletool build-apks` wurde verworfen, weil Bundletool ohne Release-Key lokale APKs mit Debug-Keystore signieren wuerde.
  - Stattdessen wurde `./gradlew :app:installFreeRelease` verwendet, damit die Free-Release-Variante mit Projekt-Release-Konfiguration auf die Geraete kommt.
  - Gradle meldete: `Installed on 3 devices. BUILD SUCCESSFUL`.
- Installierter Zielstand:
  - S10 / `RF8N313QMFL`:
    - package: `com.securecall.app.free`
    - versionCode: `53002`
    - versionName: `1.0.31-free`
    - lastUpdateTime: `2026-05-09 01:32:18`
  - S7 / `ce10160adc00152604`:
    - package: `com.securecall.app.free`
    - versionCode: `53002`
    - versionName: `1.0.31-free`
    - lastUpdateTime: `2026-05-09 01:30:44`
  - Tab S4 / `ce12182c68644439037e`:
    - package: `com.securecall.app.free`
    - versionCode: `53002`
    - versionName: `1.0.31-free`
    - lastUpdateTime: `2026-05-09 01:31:18`
- Battery-/Service-Setup fuer das Play-Console-Package:
  - `dumpsys deviceidle whitelist +com.securecall.app.free` auf allen drei Geraeten gesetzt.
  - `WAKE_LOCK` AppOp fuer `com.securecall.app.free` auf allen drei Geraeten erlaubt.
  - Free-App auf allen drei Geraeten gestartet.
- Final verifiziert:
  - S10:
    - Whitelist: `user,com.securecall.app.free,10697`
    - `WebSocketService`: `isForeground=true`, `startRequested=true`, `stopIfKilled=false`
  - S7:
    - Whitelist: `user,com.securecall.app.free,10334`
    - `WebSocketService`: `isForeground=true`, `startRequested=true`, `stopIfKilled=false`
  - Tab S4:
    - Whitelist: `user,com.securecall.app.free,10509`
    - `WebSocketService`: `isForeground=true`, `startRequested=true`, `stopIfKilled=false`
- Eindeutigkeit fuer Test:
  - Alte Flavor-Prozesse wurden gestoppt, nicht deinstalliert:
    - S10: `am force-stop com.securecall.app.premium`
    - S7: `am force-stop com.securecall.app.pro`
  - `pidof` fuer Premium/Pro lieferte danach keinen laufenden Prozess.
- Bewertung:
  - Alle drei Geraete testen jetzt die Play-Console-nahe Free-Variante `com.securecall.app.free vC53002`.
  - Pro/Premium bleiben installiert, laufen aber aktuell nicht.
- Kein Commit/Push/Deployment.

## 2026-05-09 - Codex: Battery-Optimization-Ausnahme auf S10/S7/Tab S4 gesetzt

- Agent: Codex
- Anlass:
  - Gio gab explizit frei, direkt an den angeschlossenen Geraeten zu arbeiten und zu testen.
  - Danach bat Gio, den Fix auch auf S7 und Tab S4 anzuwenden.
- Geaendert per ADB:
  - `dumpsys deviceidle whitelist +com.securecall.app.premium` auf S10
  - `dumpsys deviceidle whitelist +com.securecall.app.pro` auf S7
  - `dumpsys deviceidle whitelist +com.securecall.app.free` auf Tab S4
  - `WAKE_LOCK` AppOp explizit auf `allow` fuer alle drei Packages
  - `START_FOREGROUND` AppOp explizit auf `allow` fuer S10 und Tab S4
    - S7 meldet `Unknown operation string: START_FOREGROUND`; auf dieser Android-Version ist der AppOp-Name nicht verfuegbar. DeviceIdle-Whitelist wurde trotzdem gesetzt.
- Verifiziert:
  - S10 / `RF8N313QMFL`:
    - Whitelist: `user,com.securecall.app.premium,10686`
    - Package: `com.securecall.app.premium`
    - Version: `1.0.31-premium`, versionCode `53001`
    - Prozess: PID `26521`
    - `WebSocketService`: `isForeground=true`, `startRequested=true`, `stopIfKilled=false`
  - S7 / `ce10160adc00152604`:
    - Whitelist: `user,com.securecall.app.pro,10328`
    - Package: `com.securecall.app.pro`
    - Version: `1.0.31-pro`, versionCode `53001`
    - Prozess: PID `24671`
    - `WebSocketService`: `isForeground=true`, `startRequested=true`, `stopIfKilled=false`
  - Tab S4 / `ce12182c68644439037e`:
    - Whitelist: `user,com.securecall.app.free,10509`
    - Package: `com.securecall.app.free`
    - Version: `1.0.31-free`, versionCode `53001`
    - SecureCall wurde gestartet, weil Prozess/WebSocketService vorher nicht aktiv war.
    - Prozess: PID `18359`
    - `WebSocketService`: `isForeground=true`, `startRequested=true`, `stopIfKilled=false`
    - AppOps: `WAKE_LOCK: allow`, `START_FOREGROUND: allow`
- Bewertung:
  - Alle drei Testgeraete sind jetzt fuer den Lockscreen-/Battery-Langzeittest vorbereitet.
  - Vorheriger S10-Blocker "nicht in DeviceIdle-Whitelist" ist behoben.
- Kein App-Install/Update.
- Kein Commit/Push/Deployment.

## 2026-05-09 - Codex: S10 Battery-/Service-Precheck vor Langzeittest

- Agent: Codex
- Anlass:
  - Gio bat, die Checks direkt am S10 zu uebernehmen.
- Geraet:
  - S10 / `RF8N313QMFL` / `SM_G973F`
- Installierte App:
  - package: `com.securecall.app.premium`
  - versionCode: `53001`
  - versionName: `1.0.31-premium`
  - lastUpdateTime: `2026-05-09 00:51:25`
- Permissions laut `dumpsys package`:
  - `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: vorhanden, granted=true
  - `android.permission.WAKE_LOCK`: vorhanden, granted=true
  - `android.permission.FOREGROUND_SERVICE`: vorhanden, granted=true
- Aktiver Runtime-Status:
  - Prozess laeuft: PID `26521`
  - `WebSocketService` laeuft als Foreground Service:
    - `isForeground=true`
    - `foregroundId=1001`
    - `startRequested=true`
    - `stopIfKilled=false`
  - AppOps:
    - `WAKE_LOCK: allow ... (running)`
    - `START_FOREGROUND: allow ... (running)`
  - Standby-Bucket: `10` / Active
  - Geraet aktuell nicht in DeviceIdle:
    - `mDeviceIdleMode=false`
    - `mLightDeviceIdleMode=false`
- Kritischer Befund:
  - `com.securecall.app.premium` taucht nicht in `dumpsys deviceidle whitelist` auf.
  - Damit ist die App trotz Manifest-Permission und granted Permission nicht als Battery-Optimization-Exemption/DeviceIdle-Whitelist bestaetigt.
- Bewertung:
  - S10 ist fuer einen Service-/WakeLock-Check aktiv und erreichbar.
  - Fuer einen belastbaren 30-Minuten-Lockscreen-Langzeittest sollte Gio auf dem S10 die Battery-Optimization-Ausnahme/Unrestricted Battery Usage fuer SecureCall Premium sichtbar erlauben und danach die Whitelist erneut pruefen lassen.
- Keine App installiert oder veraendert.
- Kein Commit/Push/Deployment.

## 2026-05-09 - Codex: Geraete-Installstand gegen aktuelle Desktop-AAB geprueft

- Agent: Codex
- Anlass:
  - Gio bezweifelte CCs Aussage, dass alle drei Geraete die neue AAB installiert haben.
- Aktuelle Desktop-AAB:
  - Datei: `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
  - package: `com.securecall.app.free`
  - versionCode: `53002`
  - versionName: `1.0.31-free`
  - SHA-256: `6d0ee1b70efea5f7470544d0d5ba184b027025175c407597e3635ea0e3749433`
  - Manifest enthaelt `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - `bundletool validate`: PASS
- ADB-Geraetestand:
  - S10 / `RF8N313QMFL` / `SM_G973F`:
    - installiertes Package: `com.securecall.app.premium`
    - versionCode: `53001`
    - versionName: `1.0.31-premium`
    - lastUpdateTime: `2026-05-09 00:51:25`
  - S7 / `ce10160adc00152604` / `SM_G930F`:
    - installiertes Package: `com.securecall.app.pro`
    - versionCode: `53001`
    - versionName: `1.0.31-pro`
    - lastUpdateTime: `2026-05-09 00:50:24`
  - Tab S4 / `ce12182c68644439037e` / `SM_T835`:
    - installiertes Package: `com.securecall.app.free`
    - versionCode: `53001`
    - versionName: `1.0.31-free`
    - lastUpdateTime: `2026-05-09 00:52:12`
- Bewertung:
  - CCs Aussage ist in dieser Form falsch: Alle drei Geraete haben nicht die aktuelle Desktop-AAB `53002/1.0.31-free` installiert.
  - S10 und S7 koennen diese Free-AAB schon wegen anderer Package-IDs (`premium`/`pro`) nicht als Update dieser installierten App erhalten haben.
  - Alle drei installierten Varianten enthalten aber die Battery-/WakeLock-Permissions laut `dumpsys package`, inklusive `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Keine App installiert oder veraendert.
- Kein Commit/Push/Deployment.

## 2026-05-08/09 - CC: Session-Abschluss — v1.0.31 (vC53) uploadbereit

- Agent: Claude Code
- Session-Zusammenfassung:
  - 4 Commits auf main gepusht:
    1. `3f85d0b` — fix: incoming call ring, hangup stability, FCM ringtone, activation codes
    2. `8d08742` — release: bump v1.0.30 (vC52)
    3. `efe0dbd` — fix: battery optimization permission + dialog + WakeLock refresh
    4. `69fdfd2` — release: bump v1.0.31 (vC53) ← HEAD
  - AAB: `~/Desktop/SecureCall-FINAL-UPLOAD.aab` (v1.0.31, vC53→53002)
  - Play Console: Upload ausstehend (Gio manuell)
  - Railway: NICHT redeployed (Backend-Aenderung: SEED_ACTIVATION_CODES env var)
  - GitHub Release: NICHT erstellt
- Codex-Kooperation:
  - Q2 (Battery Optimization): CC analysiert → Codex bestaetigt → CC fixt → Codex findet 2 Nachbesserungen → Codex fixt selbst → Codex revalidiert AAB
  - FCM-Ringtone-Luecke: Codex identifiziert → CC fixt
  - Instrumentation-Test: Codex repariert (`MainActivityInstrumentedTest.java`), S10 18/18 PASS
- Offene Punkte:
  - Play Console AAB Upload (v1.0.31)
  - Railway Redeploy (SEED_ACTIVATION_CODES)
  - GitHub Release erstellen
  - Geraete-Langzeittest: 20-30 Min gesperrt → eingehender Call
  - Settings-Warnung bei aktiver Battery Optimization (UX-Verbesserung, kein Blocker)

## 2026-05-08 - Codex: CC-Findings 1+2 final korrigiert und AAB revalidiert

- Agent: Codex
- Anlass:
  - Gio bat: Bridge lesen, CC-Fix gegenpruefen und Testlauf unterstuetzen.
  - CC hatte gemeldet, Dialog-Stacking und WakeLock-Refresh seien gefixt.
- Recheck-Befund:
  1. **Dialog-Stacking war noch nicht sauber geloest**:
     - Der Positive-Button im Battery-Erklaer-Dialog startete `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` und rief danach sofort `openSamsungBatterySettings()` auf.
     - Dadurch konnte auf Samsung weiter ein Settings-/Hinweis-Flow direkt ueber/kurz nach dem Systemdialog gestapelt werden.
     - Codex-Fix: Positive-Button startet nur noch den System-Exemption-Intent; Samsung-Hinweis bleibt nur im "Later"-Pfad bzw. ausserhalb dieses Startzyklus.
  2. **WakeLock-Refresh war noch nicht sauber geloest**:
     - `onStartCommand()` rief zwar `acquireCpuWakeLock()` auf, aber `acquireCpuWakeLock()` returned frueh, solange der WakeLock noch `isHeld` war.
     - Dadurch wurde der 30-Min-Timeout nicht auf jedem 15-Min-Alarm verlaengert; kurz nach einem Alarm konnte weiter eine Luecke entstehen.
     - Codex-Fix: WakeLock wird einmal erzeugt, `setReferenceCounted(false)` gesetzt und bei jedem `onStartCommand()` per `acquire(30 * 60 * 1000L)` refreshed.
- Geaenderte Dateien:
  - `client_android/app/src/main/java/com/securecall/app/MainActivity.java`
  - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
  - `docs/agent-bridge/ACTION_LOG.md`
  - `docs/agent-bridge/QUESTIONS.md`
- Verifikation:
  - `git diff --check`: PASS
  - `./gradlew :app:testFreeDebugUnitTest`: PASS
  - `./gradlew :app:bundleFreeRelease`: PASS
  - `bundletool validate --bundle=client_android/app/build/outputs/bundle/freeRelease/app-free-release.aab`: PASS
  - Desktop-Upload-Artefakt wurde aus dem neuen Build aktualisiert: `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
  - Desktop-AAB:
    - package: `com.securecall.app.free`
    - versionCode: `52002`
    - versionName: `1.0.30-free`
    - Permission vorhanden: `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
    - SHA-256: `39f09af7475209e3b2ead6ca9bce48c74a51e5f3a8161f0c1abde37aa9699f38`
    - `bundletool validate`: PASS
- Bewertung:
  - Release-Blocker aus Q2 sind fuer Manifest, Dialog-Stacking und WakeLock-Refresh geloest.
  - Settings-Warnung bleibt eine UX-Verbesserung, aber kein harter Upload-Blocker fuer diese AAB.
- Keine Secrets gelesen oder ausgegeben.
- Keine Geraete veraendert.
- Kein Commit/Push/Deployment.

## 2026-05-08 - CC: Codex-Findings 1+2 gefixt (Dialog-Stacking + WakeLock-Refresh)

- Agent: Claude Code
- Ausloeser: Codex-Gegenpruefung empfiehlt 2 Nachbesserungen vor Upload.
- Fixes:
  1. **Dialog-Stacking (Codex Finding 1)**:
     - `requestBatteryOptimizationExemption()` zeigt jetzt NUR den Erklaer-Dialog.
     - Samsung-Dialog wird erst NACH Abschluss des Erklaer-Dialogs aufgerufen (via Button-Callback).
     - `return` nach Dialog-Anzeige verhindert dass `openSamsungBatterySettings()` am Ende nochmal laeuft.
  2. **WakeLock-Refresh (Codex Finding 2)**:
     - `acquireCpuWakeLock()` prueft jetzt `cpuWakeLock != null && isHeld` statt nur `!= null`.
     - Wenn WakeLock abgelaufen ist (isHeld=false), wird er re-acquired.
     - `onStartCommand()` ruft jetzt `acquireCpuWakeLock()` auf → AlarmManager-Restart alle 15 Min refreshed den WakeLock.
     - Kette: 30-Min-WakeLock + 15-Min-AlarmManager-Refresh = lueckenlos.
- Geaenderte Dateien:
  - `client_android/app/src/main/java/com/securecall/app/MainActivity.java`
  - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
- Codex: Bitte re-verify. Alle 3 Findings sollten jetzt geloest sein.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-08 - CC: Battery Optimization Fix (Q2 — CC + Codex Konsens)

- Agent: Claude Code
- Ausloeser: Gio beobachtet WS-Disconnect bei Inaktivitaet/Sperrbildschirm.
- Analyse (CC): `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` fehlt im Manifest → Battery-Opt-Request schlaegt still fehl.
- Codex-Bestaetigung: Q2 in QUESTIONS.md beantwortet, Konsens auf 3-Punkte-Fix.
- Fixes:
  1. **Manifest-Permission**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in `AndroidManifest.xml` deklariert.
  2. **UX-Dialog**: `requestBatteryOptimizationExemption()` in `MainActivity.java` umgebaut — zeigt erklaerenden Dialog ("Background Connection Required") BEVOR System-Dialog kommt. Fallback auf App-Detail-Settings falls Intent fehlschlaegt.
  3. **WakeLock-Timeout**: `WebSocketService.kt` PARTIAL_WAKE_LOCK von 10 Min auf 30 Min erhoeht. Kein permanenter WakeLock (Play-Review-konform).
- Geaenderte Dateien:
  - `client_android/app/src/main/AndroidManifest.xml`
  - `client_android/app/src/main/java/com/securecall/app/MainActivity.java`
  - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
- Codex-Gegenpruefung erbeten:
  - Manifest-Permission korrekt deklariert?
  - UX-Dialog Text und Flow OK?
  - WakeLock 30 Min Timeout akzeptabel?
  - Keine Seiteneffekte auf bestehende Battery/Samsung-Dialoge?
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-08 - Codex: Battery Optimization Fix gegengeprueft

- Agent: Codex
- Anlass:
  - Gio bat: Bridge lesen und CCs Battery-Optimization-Fix gegenpruefen.
- Gepruefter Code:
  - `client_android/app/src/main/AndroidManifest.xml`
  - `client_android/app/src/main/java/com/securecall/app/MainActivity.java`
  - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
  - neues AAB: `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
- Verifiziert:
  - Manifest-Permission ist im Source vorhanden:
    - `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - Neues AAB enthaelt die Permission laut `bundletool dump manifest`.
  - Neues AAB:
    - package: `com.securecall.app.free`
    - versionCode: `52002`
    - versionName: `1.0.30-free`
    - `bundletool validate`: PASS
    - SHA-256: `ae89f16e23f4c137920dccdb86c22932d417898fa9a58f5e1a441fbb3bf48325`
  - WakeLock ist von 10 Min auf 30 Min erhoeht und weiterhin zeitbegrenzt, also kein permanenter WakeLock.
  - Battery-Intent hat Fallback auf App-Detail-Settings.
- Findings / Nachbesserungen:
  1. **Dialog-Stacking auf Samsung moeglich**:
     - `requestBatteryOptimizationExemption()` zeigt jetzt den erklaerenden Dialog.
     - Danach wird im selben Flow weiterhin direkt `openSamsungBatterySettings()` aufgerufen.
     - Auf Samsung kann dadurch der Samsung-Hinweis ueber/kurz nach dem neuen Erklaer-Dialog erscheinen.
     - Empfehlung: Wenn der neue Battery-Erklaer-Dialog gezeigt wurde, Samsung-Dialog in diesem `onCreate()` ueberspringen oder sequentiell erst nach Abschluss des ersten Dialogs anbieten.
  2. **WakeLock wird nicht erneuert**:
     - `acquireCpuWakeLock()` laeuft nur in `onCreate()`.
     - Der 30-Min-WakeLock laeuft aus, wenn der Service laenger lebt.
     - `scheduleServiceRestart()` ruft bei lebendem Service nur `onStartCommand()`, dort wird der WakeLock aktuell nicht reacquired/refreshed.
     - Empfehlung: In `onStartCommand()` `acquireCpuWakeLock()` erneut aufrufen, falls `cpuWakeLock == null || !isHeld`, oder einen expliziten Refresh-Pfad implementieren.
  3. **Settings-Warnung nicht sichtbar verbessert**:
     - `SettingsFragment.configureBatteryOptimization()` wirkt im Diff unveraendert.
     - Statusanzeige existiert, aber die in Q2 empfohlene deutlichere Warnung ist noch nicht umgesetzt.
- Bewertung:
  - Hauptblocker "fehlende Manifest-Permission" ist behoben.
  - AAB ist technisch gueltig und hat neuen Play-Console-VersionCode.
  - Vor finalem Upload/Release wuerde Codex mindestens Finding 1 und 2 fixen, weil sie direkt die beobachtete Background-Erreichbarkeit betreffen.
- Keine Produktcodeaenderung durch Codex.
- Keine Geraete veraendert.
- Kein Commit/Push/Deployment.

## 2026-05-08 - CC: 3 Bug-Fixes + Call-Test S7 → Tab S4

- Agent: Claude Code
- Ausloeser: Nutzerauftrag — 3 Bugs fixen + Call-Test
- Bug-Fixes (alle im Working Tree, nicht committet):
  1. **BUG 1 — Klingeln bei entsperrtem Screen**:
     - Ringtone/Vibration von `IncomingCallActivity` nach `WebSocketService.startIncomingRingtone()` verschoben.
     - Service startet Klingeln BEVOR Activity gestartet wird → funktioniert auch wenn Activity auf Android 10+ nicht launchen kann.
     - `IncomingCallActivity.stopRingtoneAndVibration()` delegiert jetzt an `WebSocketService.stopIncomingRingtone()`.
     - Notification: `setSilent(true)` verhindert Doppel-Sound, `setContentIntent()` hinzugefuegt.
  2. **BUG 2 — App bricht weg nach Remote-Hangup**:
     - `finish()` durch `returnToMain()` ersetzt in `CallActivity` (7 Stellen).
     - `returnToMain()` startet `MainActivity` mit `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` bevor `finish()` aufgerufen wird.
     - Ohne diesen Fix: `finish()` auf einer Activity mit `FLAG_ACTIVITY_NEW_TASK` entfernt den gesamten Task aus Recents.
  3. **BUG 3 — Aktivierungscodes erneuern**:
     - Alte Beta-Codes `BETA-PRO0-2026` und `BETA-PREM-2026` entfernt aus `activation_codes.json`.
     - Neuer Code `PREM-ES4X-LDCT-LZ8U` (premium, maxUses=5) hinzugefuegt.
     - Server: `SEED_ACTIVATION_CODES` env var fuer Railway-Persistenz (Filesystem ist ephemeral).
- Build + Deploy:
  - `assembleFreeRelease` BUILD SUCCESSFUL.
  - APK auf S7 (`ce10160adc00152604`) und Tab S4 (`ce12182c68644439037e`) installiert.
  - `node --check src/server.js` erfolgreich.
- Call-Test S7 → Tab S4:
  - S7 Dialer → `+491752536807` → Call
  - Tab S4: `IncomingCallActivity` gestartet, Ringtone ueber WebSocketService (BUG 1 ✅)
  - Tab S4: Accept → `CallActivity` mit "Anruf aktiv", "Ende-zu-Ende verschluesselt"
  - S7: End Call → beide Geraete zeigen "Save Contact" Dialog
  - Skip → beide landen auf `MainActivity`, Prozesse stabil (BUG 2 ✅)
- Geaenderte Produktcode-Dateien (nicht committet):
  - `client_android/app/src/main/java/com/securecall/app/IncomingCallActivity.kt`
  - `client_android/app/src/main/java/com/securecall/app/CallActivity.java`
  - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
  - `backend/signaling/data/activation_codes.json`
  - `backend/signaling/src/server.js`
- Codex-Empfehlung aufgegriffen:
  - FCM-only Incoming-Call-Pfad (`SecureCallMessagingService.kt`) fehlte Service-Ringtone.
  - Fix: `startIncomingRingtone()` im FCM-Pfad hinzugefuegt (alle 3 Flavors: free, pro, premium).
  - FCM-Notification: `setSilent(true)` hinzugefuegt (verhindert Doppel-Sound).
- Codex Q1 beantwortet: Sync-Status ist jetzt aktuell.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment (Railway nicht redeployed).

## 2026-05-05 - CC (solo): Master Audit Abgleich + STX-MED-04 Fix

- Agent: Claude Code (allein, Codex-Tokens aufgebraucht)
- Master Audit abgeglichen: STX-CRIT-01 nicht kritisch (gitignored), STX-MED-04 gefixt (Rate Limit custom-id).
- Alle 17 Security Findings VERIFIED_FIXED (CC self-audit).
- Cron Timer gestoppt.
- Keine Secrets gelesen. Kein Deployment.

## 2026-05-03 - Codex: Lizenz-/Rollout-Text leicht korrigiert

- Agent: Codex
- Nutzerziel: Play Store/APK/F-Droid-Rollout nicht gefaehrden, aber Lizenz-/Branding-Widerspruch bereinigen.
- Aktion:
  - `README.md`, `CONTRIBUTING.md`, `docs/REPO_SECURITY.md` angepasst.
  - Commit `5d56f1e` auf `main` gepusht.
- Ergebnis:
  - GPL-3.0 fuer Client-Code bleibt stabil.
  - Offizielle SecureCall/StealthX-Marke, Backend-Services, Store-Releases und paid Pro/Premium-Lizenzierung sind klar als kontrolliert durch Vendetta Labs beschrieben.
- GitHub-Hinweis beim Push:
  - 6 Dependabot/Security-Warnungen gemeldet: 1 critical, 1 high, 2 moderate, 2 low.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-03 - Codex: Website-Textangleichung und Stealth-Bridge angelegt

- Agent: Codex
- Aktion:
  - Stealth Bridge unter `docs/agent-bridge/` angelegt.
  - Dependabot/Security-Warnungen in `TODO.md` aufgenommen.
  - Website-Texte lokal leicht angeglichen: `source-available`/unklare Open-Source-Claims werden auf `GPL-3.0 client source + official services controlled` gefuehrt.
- Geaenderte Dateien:
  - `website/index.html`
  - `website/privacy.html`
  - `website/terms.html`
  - `website/wiki/security-design.html`
  - `website/llms.txt`
  - `website/assets/og-image.svg`
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/PROJECT_STATE.md`
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Secrets gelesen oder ausgegeben.
- Noch kein Commit/Push/Deployment fuer diesen zweiten Schritt.

## 2026-05-03 - Codex: Website-Lizenztexte live veroeffentlicht

- Agent: Codex
- Aktion:
  - Commit `6ecf7f9` auf `main` gepusht.
  - GitHub Pages Workflow `Deploy to GitHub Pages` lief erfolgreich.
- Live-Verifikation:
  - `https://stealthx.tech/` liefert neue Texte wie `GPL-3.0 client source`.
  - `https://stealthx.tech/privacy.html` liefert `GPL Client Source` und den Hinweis, dass offizielle Marke, Backend-Services, Store-Releases und paid Pro/Premium-Lizenzierung von Vendetta Labs betrieben werden.
- Ergebnis:
  - Website ist mit GitHub-Repo-Lizenzkommunikation konsistenter.
  - Play/APK/F-Droid-Rollout bleibt unveraendert.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment; GitHub Pages Auto-Deploy durch Workflow.

## 2026-05-03 - Codex: Dependabot Alerts ausgelesen

- Agent: Codex
- Aktion: GitHub Dependabot Alerts fuer `NeaBouli/stealth` read-only per GitHub API ausgelesen.
- Offene Alerts:
  - Critical: `protobufjs < 7.5.5`
  - High: `path-to-regexp < 0.1.13`
  - Medium: `uuid < 14.0.0`
  - Medium: `fast-xml-parser < 5.7.0`
  - Low: `rand >= 0.7.0, < 0.8.6`
  - Low: `@tootallnate/once < 3.0.1`
- Betroffene Manifeste:
  - `backend/signaling/package-lock.json`
  - `core_crypto/Cargo.lock`
- Keine Secrets gelesen oder ausgegeben.
- Noch keine Dependency-Fixes committed.

## 2026-05-03 - Codex: Dependabot High/Critical Fix lokal vorbereitet

- Agent: Codex
- Aktion:
  - `backend/signaling/package-lock.json` mit `npm update --package-lock-only` aktualisiert.
  - `core_crypto/Cargo.lock` mit `cargo update -p rand --precise 0.8.6` aktualisiert.
- Lokal behobene bekannte Alerts:
  - Critical: `protobufjs < 7.5.5` -> lockfile jetzt `protobufjs 7.5.6`.
  - High: `path-to-regexp < 0.1.13` -> lockfile jetzt `path-to-regexp 0.1.13`.
  - Medium: `fast-xml-parser < 5.7.0` -> lockfile jetzt `fast-xml-parser 5.7.2`.
  - Low: `rand >= 0.7.0, < 0.8.6` -> lockfile jetzt `rand 0.8.6`.
- Checks:
  - `node --check src/server.js` erfolgreich.
  - `npm audit --audit-level=high` erfolgreich; keine high/critical npm-Audit-Treffer mehr.
  - `cargo test --locked` erfolgreich; 34 Tests passed.
- Bewusst nicht automatisch gefixt:
  - Moderate/low transitive npm-Audit-Treffer, fuer die `npm audit fix --force` Breaking Changes/Downgrades vorschlaegt.
- Keine Secrets gelesen oder ausgegeben.
- Noch kein Commit/Push fuer diesen Dependency-Fix.

## 2026-05-04 - Codex: Dependabot High/Critical Fix gepusht und verifiziert

- Agent: Codex
- Aktion:
  - Commit `da9b1bb` auf `main` gepusht.
  - GitHub Dependabot Alerts danach read-only per GitHub API erneut abgefragt.
- Geaenderte Dateien:
  - `backend/signaling/package-lock.json`
  - `core_crypto/Cargo.lock`
  - `docs/agent-bridge/ACTION_LOG.md`
  - `docs/agent-bridge/TODO.md`
- Ergebnis laut GitHub API nach Push:
  - Behoben: `protobufjs < 7.5.5` (critical).
  - Behoben: `path-to-regexp < 0.1.13` (high).
  - Behoben: `fast-xml-parser < 5.7.0` (medium).
  - Behoben: `rand >= 0.7.0, < 0.8.6` (low).
  - Weiterhin offen: `uuid < 14.0.0` (medium).
  - Weiterhin offen: `@tootallnate/once < 3.0.1` (low).
- Checks:
  - `node --check src/server.js` erfolgreich.
  - `npm audit --audit-level=high` erfolgreich.
  - `cargo test --locked` erfolgreich; 34 Tests passed.
- Hinweis:
  - Weitere npm-Audit-Fixes nicht erzwungen, weil `npm audit fix --force` Breaking Changes/Downgrades fuer rolloutkritische FCM-/Mail-/Backend-Flows vorschlaegt.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment.

## 2026-05-04 - Codex: Restliche Dependabot Alerts analysiert

- Agent: Codex
- Aktion:
  - Dependency-Tree fuer `uuid` und `@tootallnate/once` im Signaling-Backend analysiert.
  - Direkte Nutzung von `uuid` im Backend-Code geprueft.
  - Keine Produktcodeaenderung vorgenommen.
- Gelesene Dateien/Befehle:
  - `backend/signaling/package.json`
  - `backend/signaling/src/sessions.js`
  - `backend/signaling/src/server.js`
  - `npm ls uuid @tootallnate/once`
  - `npm explain uuid`
  - `npm explain @tootallnate/once`
  - `npm outdated`
- Ergebnis:
  - `uuid` ist direkt als `uuid@^9.0.1` eingebunden und wird in CommonJS-Code mit `require("uuid")`/`v4` genutzt.
  - Der GitHub-Alert verlangt `uuid@14`, also ein Major-Upgrade mit moeglicher API-/Module-System-Auswirkung.
  - `@tootallnate/once` kommt transitiv ueber Google/Firebase-Storage-Pfade und wird nicht direkt vom App-Code genutzt.
  - `npm outdated` nennt ausserdem `express@5` als Major-Upgrade und `nodemailer@8.0.7` als Patch.
- Entscheidung:
  - Keine funktionsabhaengigen Aenderungen ohne Abstimmung mit Claude Code/Dev.
  - Restliche Alerts bleiben als gezielter Abstimmungspunkt in `TODO.md`.
- Keine Secrets gelesen oder ausgegeben.
- Kein Commit/Push/Deployment fuer Produktcode.

## 2026-05-04 - CC: F-Droid/GPL Restdrift bereinigt (Codex-Gegenpruefung)

- Agent: Claude Code
- Ausloeser: Codex fand nach Phase-3-Cleanup noch F-Droid-Reste und GPL-3.0 Texte.
- CC-Gegenpruefung: Alle Funde bestaetigt und in einem Commit behoben (`8064dbd`).
- Geaenderte Dateien (15):
  - README.md, CONTRIBUTING.md — GPL → BUSL-1.1
  - website/index.html, terms.html, faq.html, wiki/security-design.html, llms.txt — GPL → BUSL-1.1, F-Droid entfernt
  - marketing/play_store/de/store_listing.md — F-Droid Zeile entfernt
  - docs/PLAY_STORE_LISTING.md, PLAY_STORE_LISTING_DE.md — F-Droid entfernt
  - backend/signaling/src/payments/email_handler.js — F-Droid Link aus Mail entfernt
  - fastlane/metadata/android/en-US/full_description.txt — F-Droid Edition → Free Edition
  - tools/debug/start-logcat.sh — fdroid Package-ID entfernt
  - client_android: MainActivity.java, UpdateChecker.kt — Kommentare korrigiert
- Bewusst belassen: Historische Session-/Handover-/Bug-Docs (Archiv-Charakter).
- Keine funktionalen Aenderungen. Nur Text/Kommentare.
- Keine Secrets gelesen oder ausgegeben.

## 2026-05-04 - CC: BUSL-1.1 Lizenz + Fork Protection enforce

- Agent: Claude Code
- Aktionen:
  1. LICENSE ersetzt: GPL-3.0 → BUSL-1.1 (Commit `10a2ca4`)
     - Change Date: 2030-05-04
     - Change License: GPL-3.0-only
     - Commercial use restricted
  2. SHA-256 Fingerprint ermittelt via apksigner:
     - `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
  3. FORK_PROTECTION_MODE Default geaendert: "warn" → "enforce" (Commit `b9202cb`)
  4. docs/LIZENZ_VORSCHLAG.md entfernt (war nur Entwurf)
- Railway-Konfiguration (MUSS MANUELL GESETZT WERDEN):
  - `ALLOWED_SIGNATURES=1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
  - `FORK_PROTECTION_MODE=enforce` (oder weglassen, ist jetzt Default)
- Codex-Info:
  - Lizenz ist jetzt BUSL-1.1, NICHT mehr GPL-3.0.
  - Fork-Schutz ist enforce by default. Ohne ALLOWED_SIGNATURES env var passiert nichts (Code prueft `if (allowedSigs && allowedSigs.trim().length > 0)`).
  - Railway Redeploy noetig damit beides live wirkt.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment (Railway manuell).

## 2026-05-04 - CC: F-Droid komplett entfernt (3 Phasen)

- Agent: Claude Code
- Aktionen:
  Phase 1 (Commit `e59f966`):
  - GitLab MR !37087 geschlossen via API
  - docs/FDROID_SETUP.md + docs/FDROID_SUBMISSION.md geloescht
  - F-Droid Button aus website/index.html entfernt
  - README, RELEASE_PROCESS, GITHUB_RELEASES, privacy.html bereinigt
  Phase 2 (Commit `58ec3c0`):
  - GitLab Fork TrueRepublic/securecall-fdroid (81115682) geloescht via API
  - Alle fdroid APK Assets aus GitHub Releases v1.0.17-v1.0.28 entfernt
  - fdroid/ Metadata-Ordner aus Repo entfernt
  Phase 3 (Commits `5c877c2` + `82b67dc`):
  - fdroid productFlavor aus build.gradle entfernt
  - fdroid Source Set (11 Dateien + proguard) entfernt
  - AndroidManifest fdroid-Package-Queries entfernt
  - UpdateManager: FDROID InstallSource + openFDroid() entfernt
  - CallActivity: Kommentar korrigiert
  - BUILD SUCCESSFUL: free, pro, premium (alle 3 OK)
- Codex-Info:
  - F-Droid existiert NICHT mehr im Projekt.
  - Nur noch 3 Flavors: `free` (public), `pro` + `premium` (internal mit -Pinternal).
  - UpdateManager hat nur noch: PLAY_STORE, SIDELOAD, OTHER_STORE.
  - BUSL-1.1 Lizenz-Entwurf liegt in `docs/LIZENZ_VORSCHLAG.md` (nicht committet).
  - ALLOWED_SIGNATURES wird NACH Lizenz-Entscheidung reaktiviert.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment.

## 2026-05-04 - CC: uuid-Migration + BUSL-1.1 Entwurf

- Agent: Claude Code
- Aktionen:
  1. uuid-Migration umgesetzt:
     - `uuid` Paket aus package.json entfernt.
     - `require("uuid")` durch `crypto.randomUUID()` ersetzt in server.js + sessions.js.
     - Lockfile regeneriert via `npm ci`.
     - Syntax-Check bestanden.
     - Commit `d13e8f4` auf main gepusht.
     - GitHub Dependabot: uuid-Alert sollte sich aufloesen (direkte Dep entfernt).
  2. Lizenz-Entwurf erstellt:
     - `docs/LIZENZ_VORSCHLAG.md` — BUSL-1.1 angepasst fuer StealthX/SecureCall.
     - Change Date: 2030-05-04, Change License: GPL-3.0.
     - NICHT committet — wartet auf Nutzer-Review und Entscheidungen.
  3. Bridge aktualisiert fuer Codex-Sichtbarkeit.
- Codex-Info:
  - `uuid` ist NICHT mehr direkte Dependency. `crypto.randomUUID()` ist die neue API.
  - Falls Codex am Backend arbeitet: `uuidv4()` existiert nicht mehr, `crypto.randomUUID()` verwenden.
  - Lizenz-Diskussion laeuft. LICENSE-Datei noch NICHT aendern bis Gio entscheidet.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment (Railway muss separat redeployed werden fuer uuid-Fix).

## 2026-05-04 - CC: Rollback-Tag + uuid-Analyse + Koordinationsprotokoll

- Agent: Claude Code
- Aktionen:
  1. Rollback-Tag `rollback-stable-vC50` auf HEAD `d24fbc7` erstellt und nach origin gepusht.
  2. uuid-Nutzung im Backend analysiert:
     - 4 Stellen: server.js (Z.4, 596, 818, 1187) + sessions.js (Z.11)
     - Nur `uuidv4()` Aufrufe, triviales Drop-in fuer `crypto.randomUUID()`
     - Node >= 18 ist Engine-Requirement → `crypto.randomUUID()` immer verfuegbar
  3. Empfehlung: `uuid` Paket entfernen, durch native Node-API ersetzen → Alert geloest, keine Dep mehr.
  4. `@tootallnate/once`: Monitoring empfohlen, kein Handlungsbedarf (transitiv, low severity).
  5. Diskussionspunkte in TODO.md dokumentiert: ALLOWED_SIGNATURES + Lizenz-Entscheidung.
  6. Koordinationsprotokoll: CC liest/updated Bridge ab sofort bei jeder Stealth-Arbeit.
- Keine Produktcode-Aenderung.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Claude Code Dev Handover erstellt

- Agent: Codex
- Aktion:
  - Handover und Prompt fuer Claude Code Dev erstellt.
  - Bridge-Kommunikationsregeln explizit dokumentiert.
  - README-Dateiliste um Handover-Datei erweitert.
- Geaenderte Dateien:
  - `docs/agent-bridge/CC_DEV_HANDOVER.md`
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Inhalt:
  - Claude Code soll vor jeder Arbeit die Bridge lesen.
  - Claude Code soll nach jeder Arbeit `ACTION_LOG.md`, `PROJECT_STATE.md` und `TODO.md` aktualisieren, falls betroffen.
  - Restliche Dependabot-Themen `uuid` und `@tootallnate/once` sind als vorsichtige Abstimmungspunkte beschrieben.
- Keine Produktcodeaenderung.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Security Audit gestartet und Findings an CC uebergeben

- Agent: Codex
- Aktion:
  - Bridge gelesen.
  - CC/Codex Autonomie- und Kommunikationsprotokoll in `README.md` ergaenzt.
  - `CODEX_FINDINGS.md` angelegt.
  - Security Audit 1 gestartet und erste Findings priorisiert dokumentiert.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Hinweise:
  - Keine Secret-Werte in Findings aufgenommen.
  - CC soll Env-/Token-/Key-Status nur als Ja/Nein/Risiko bewerten, keine Werte ausgeben.
  - `docs/agent-bridge/CC_RESPONSE.md` existiert lokal als CC-Datei und wurde nicht von Codex ueberschrieben.
- Keine Produktcodeaenderung.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Fixplan beantwortet

- Agent: Codex
- Aktion:
  - Aktuelle `CC_RESPONSE.md` gelesen.
  - In `CODEX_FINDINGS.md` direkte Antwort an CC ergaenzt.
  - Gruenes Licht fuer eindeutige Sofort-Fixes gegeben.
  - Critical/High Findings nach Codex-Sicht priorisiert und Env-Fragen ohne Secret-Ausgabe formuliert.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Security-Fixcommit re-verifiziert

- Agent: Codex
- Aktion:
  - Bridge erneut gelesen.
  - `git pull --ff-only` ausgefuehrt; Remote war bereits aktuell.
  - CC-Commit `edc6dc7` gegen die Security-Findings geprueft.
  - Recheck-Status in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - `C-02` / `/licenses/status` Wildcard-CORS: verifiziert gefixt.
  - `H-02` / `/metrics` public: verifiziert gefixt.
  - `H-03` / `DEREGISTER`: verifiziert gefixt.
  - `C-01`, `C-03`, `H-07`, `H-01`, `H-04`, `H-05`, `H-06`, `H-08`, `H-09` bleiben offen beziehungsweise muessen weiter gefixt/re-verifiziert werden.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Lokale C-01-Fremdaenderung erkannt

- Agent: Codex
- Aktion:
  - Nach weiterer autonomer Pruefung `git status --short` gelesen.
  - Lokale uncommitted Aenderung an `backend/signaling/src/server.js` erkannt.
  - Diff nur zur Security-Recheck-Einordnung gelesen; keine Produktcodeaenderung vorgenommen.
  - In `CODEX_FINDINGS.md` dokumentiert, dass C-01 lokal offenbar adressiert ist, aber noch nicht als HEAD-Fix gilt.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Nicht von Codex geaendert:
  - `backend/signaling/src/server.js`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-08 - Codex: Status-Sync-Frage an Claude Code gestellt

- Agent: Codex
- Aktion:
  - `QUESTIONS.md` fuer die Stealth-Bridge angelegt.
  - Status-Sync-Frage an Claude Code eingetragen.
- Ziel:
  - Aktuellen HEAD, Remote-/Release-/Deployment-Stand, offene Findings, Re-Verify-Status und naechste Codex-Pruefpunkte klaeren.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/QUESTIONS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-08 - Codex: lokaler Testlauf fuer aktuelle Stealth-Diffs

- Agent: Codex
- Anlass:
  - Gio bat darum, die Stealth-Bridge zu lesen und den Testlauf zu unterstuetzen.
  - Aktuelle lokale Produktcode-Diffs betreffen Backend-Aktivierungscode-Seeding sowie Android-Incoming-Call-/Ringtone-/Return-to-Main-Flows.
- Arbeitsbaum vor Test:
  - Branch: `main...origin/main`
  - HEAD: `77123b1`
  - Lokale Produktcode-Diffs vorhanden in:
    - `backend/signaling/data/activation_codes.json` (nicht ausgelesen)
    - `backend/signaling/src/server.js`
    - `client_android/app/src/main/java/com/securecall/app/CallActivity.java`
    - `client_android/app/src/main/java/com/securecall/app/IncomingCallActivity.kt`
    - `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
- Durchgefuehrte Checks:
  - `node --check src/server.js` in `backend/signaling`: PASS
  - `npm audit --audit-level=high` in `backend/signaling`: PASS exit 0; nur bekannte Low-Transitives (`@tootallnate/once` via Firebase/Google chain)
  - `cargo test --locked` in `core_crypto`: PASS, 34 Tests total
  - `./gradlew :app:testFreeDebugUnitTest` in `client_android`: PASS, BUILD SUCCESSFUL
  - `./gradlew :app:assembleFreeDebug` in `client_android`: PASS, BUILD SUCCESSFUL nach Sandbox-Freigabe fuer Gradle File-Lock-Socket
  - `git diff --check`: PASS
- Build-Artefakte:
  - Debug APKs erzeugt/aktualisiert unter `client_android/app/build/outputs/apk/free/debug/`.
- Grenzen:
  - `adb` war nicht im Shell-`PATH` (`command not found`); spaeter wurde ADB ueber `/Users/gio/Library/Android/sdk/platform-tools/adb` genutzt, siehe Geraete-Gegencheck unten.
  - `backend/signaling/data/activation_codes.json` wurde wegen potentiell sensibler Aktivierungscode-Inhalte nicht gelesen.
  - `cargo test` hat die getrackte Build-Metadatei `core_crypto/target/.rustc_info.json` lokal veraendert; Produktcode wurde dadurch nicht geaendert.
  - Keine `.env`, Secret-, Key-, Keystore-, Wallet- oder Dump-Dateien gelesen.
  - Kein Commit, Push oder Deployment.
- Manuelle Testempfehlung fuer Claude Code/Gio:
  - Eingehender Anruf bei gesperrtem Bildschirm: Service-Ringtone/Vibration startet auch wenn `IncomingCallActivity` nicht sofort sichtbar wird.
  - Accept, Decline, Caller-Cancel und 60s Timeout: Ringtone/Vibration stoppt immer.
  - Nach Call-Ende bleibt App in Recents/MainActivity sichtbar.
  - Prüfen, ob es einen aktiven FCM-only Incoming-Call-Pfad ausserhalb der lokalen Source gibt; falls ja, muss dort ebenfalls Service-Ringtone gestartet werden.

## 2026-05-08 - Codex: Android-Geraete-Gegencheck gestartet

- Agent: Codex
- Anlass:
  - Gio stellte klar, dass die angeschlossenen Android-Geraete fuer Codex erreichbar sind.
  - Ziel: Tests gegenchecken, mindestens die gegenpruef-wuerdigen Android-Pfade.
- Verbundene Geraete per ADB:
  - `RF8N313QMFL` — Samsung SM-G973F / Android 12
  - `ce10160adc00152604` — Samsung SM-G930F / Android 8.0.0
  - `ce12182c68644439037e` — Samsung SM-T835 / Android 10
- Test-Fix durch Codex:
  - `client_android/app/src/androidTest/java/com/securecall/app/MainActivityInstrumentedTest.java` war veraltet und referenzierte entfernte IDs `btnCall`/`btnSettings`.
  - Test auf aktuelle `MainActivity`-UI umgestellt: `bottomNav`, `topAppBar`, `nav_calls`, `nav_settings`.
  - Test-Launch setzt Test-Preferences fuer Onboarding, Samsung-Battery-Hinweis und bestaetigte Telefonnummer, damit System-/Setup-Dialoge nicht die UI-Assertions verdecken.
  - `GrantPermissionRule` fuer `READ_PHONE_NUMBERS`, `READ_PHONE_STATE` und `RECORD_AUDIO` ergaenzt; Logcat zeigte vorher `GrantPermissionsActivity` ueber `MainActivity`, was Espresso kuenstlich blockierte.
- Instrumentation-Ergebnisse:
  - `./gradlew :app:connectedFreeDebugAndroidTest` kompiliert nach Test-Fix.
  - S10-Lauf war einmal komplett gruen: 18/18 Tests PASS.
  - Isolierter S10-Re-Lauf nach Permission-Rule-Fix:
    - Command: `ANDROID_SERIAL=RF8N313QMFL ./gradlew :app:connectedFreeDebugAndroidTest`
    - Ergebnis: BUILD SUCCESSFUL, 18/18 Tests PASS, 0 failures, 0 errors.
    - XML: `client_android/app/build/outputs/androidTest-results/connected/debug/flavors/free/TEST-SM-G973F - 12-_app-free.xml`
    - Logcat: kein `FATAL EXCEPTION`/`AndroidRuntime` fuer SecureCall im erfolgreichen Lauf beobachtet.
  - Weitere parallele Laeufe auf S10/S7/Tab S4 wurden durch Activity-/Permission-/Setup-Dialoge instabil:
    - Fehlerbild: `NoActivityResumedException` in `MainActivityInstrumentedTest`.
    - Kein Produkt-Crash im Call/Ringtone-Code beobachtet; die Fehler liegen im Test-Harness/Device-State.
  - Paketlage ist uneinheitlich:
    - S10 meldet `com.securecall.app.premium`
    - S7 meldet `com.securecall.app.pro`
    - Tab S4 meldete aktuell kein `securecall`-Paket via `pm list packages`
  - Mehrgeraete-Paralleltest wirkt auf den Displays unruhig, weil Instrumentation Activities startet/stoppt, Permissions triggert und der Foreground-WebSocket-Service reconnectet.
- Sicherheits-/Datenhinweis:
  - Keine App-Daten geloescht.
  - Kein Uninstall durchgefuehrt.
  - App- und Testprozesse auf allen drei Geraeten per `am force-stop` gestoppt.
- Empfehlung:
  - Naechste Instrumentation nur einzeln pro Zielgeraet ausfuehren.
  - Vorher einheitliche Flavor/Paket-ID festlegen (`free`, `pro` oder `premium`) und Permissions/Setup-State vorbereiten.
  - Die aktuellen Incoming-Call-Ringtone-Aenderungen bleiben am besten per gezieltem manuellen Call-Test S7 ↔ Tab/S10 zu verifizieren; die vorhandene Instrumentation deckt diesen Service-Ringtone-Pfad nicht direkt ab.

## 2026-05-08 - Codex: Q2 Battery-Optimization bewertet

- Agent: Codex
- Anlass:
  - Claude Code fragte in `QUESTIONS.md` nach Codex-Bewertung zu Battery Optimization / WebSocket-Disconnect bei Inaktivitaet.
- Ergebnis:
  - CCs Hauptbefund bestaetigt: `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` fehlt im Manifest.
  - Bestehende Aufrufe von `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in `MainActivity` und `SettingsFragment` sind ohne diese Manifest-Permission nicht verlaesslich.
  - Empfehlung: Manifest-Permission sofort ergaenzen und danach AAB-Manifest mit `bundletool dump manifest` pruefen.
  - WakeLock-Diagnose plausibel: 10-Min-Timeout plus 15-Min-Alarm kann eine Luecke erzeugen.
  - Empfehlung WakeLock: nicht unbegrenzt halten; konservativ 30-60 Min max oder kontrolliert refreshen, sauber releasen, bei aktivem Call separat behandeln.
  - UX: erklaerender Dialog vor Systemdialog; Settings dauerhaft deutlich warnen, solange Battery Optimization aktiv ist.
  - Play-Console-Hinweis: Permission ist fuer SecureCall fachlich begruendbar, muss aber als Call-Erreichbarkeit im Hintergrund erklaert werden.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/QUESTIONS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
  - Keine Secret-Dateien gelesen.
  - Kein Geraet veraendert.
  - Kein Commit/Push/Deployment.

## 2026-05-04 - Codex: Weitere lokale CC-Fixes eingeordnet

- Agent: Codex
- Aktion:
  - Weitere lokale uncommitted Aenderungen in `backend/signaling/src/server.js` und `backend/signaling/src/payments/stripe_handler.js` erkannt.
  - Diffs nur zur Recheck-Einordnung gelesen; keine Produktcodeaenderung vorgenommen.
  - In `CODEX_FINDINGS.md` dokumentiert, dass C-03 und H-06 lokal offenbar adressiert sind, aber noch nicht als HEAD-Fix gelten.
  - H-07 als nur teilweise lokal verbessert markiert, bis alle Code-Logging-Pfade gegen HEAD re-verifiziert sind.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Nicht von Codex geaendert:
  - `backend/signaling/src/server.js`
  - `backend/signaling/src/payments/stripe_handler.js`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Commits 21b0957/4422adc re-verifiziert

- Agent: Codex
- Aktion:
  - Neue `CC_RESPONSE.md` gelesen.
  - CC-Commits `21b0957` und `4422adc` gegen HEAD re-verifiziert.
  - `CODEX_FINDINGS.md` mit aktualisiertem Status ergaenzt.
- Ergebnis:
  - `C-01` verifiziert gefixt.
  - `C-03` verifiziert gefixt.
  - `H-06` verifiziert gefixt, aber mit kleinem Response-Type-Regressionsrisiko bei `ONLINE_STATUS_REQUEST`.
  - `H-07` nicht vollstaendig gefixt; weitere Code-Logging-Pfade bleiben offen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Lokale H-05-Fremdaenderung erkannt

- Agent: Codex
- Aktion:
  - Nach Staging der Bridge-Antwort erneut lokale Produktcode-Aenderungen erkannt.
  - Diff nur zur Recheck-Einordnung gelesen.
  - In `CODEX_FINDINGS.md` dokumentiert, dass H-05 lokal offenbar in Arbeit ist, aber noch nicht als HEAD-Fix gilt.
- Beobachtung:
  - Lokaler Diff fuegt Rate Limits fuer Checkout-Endpunkte hinzu.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Nicht von Codex geaendert:
  - `backend/signaling/src/server.js`
  - `backend/signaling/src/payments/stripe_handler.js`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Commit cbbbcd6 re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commit `cbbbcd6` gegen HEAD gelesen.
  - H-05 Checkout-Rate-Limits re-verifiziert.
  - Status und Caveats in `CODEX_FINDINGS.md` ergaenzt.
- Ergebnis:
  - `H-05` als kurzfristig gefixt bewertet.
  - Caveat: In-Memory-Rate-Limit ist nicht global/persistent bei mehreren Instanzen oder Restarts.
  - Caveat: IP-Erkennung ist zwischen `server.js` und `stripe_handler.js` nicht ganz einheitlich.
  - `H-07` und `ONLINE_STATUS_REQUEST` Response-Type-Regressionsrisiko bleiben offen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Commit b7f81e2 re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commit `b7f81e2` gegen HEAD gelesen.
  - Log-Masking und `ONLINE_STATUS` Response-Type re-verifiziert.
  - Gegenbefund in `CODEX_FINDINGS.md` ergaenzt.
- Ergebnis:
  - `ONLINE_STATUS_RESPONSE` Regressionsrisiko verifiziert gefixt.
  - `server.js` Code-Logs sind maskiert.
  - `H-07` bleibt insgesamt offen, weil `stripe_handler.js` und `email_handler.js` weiterhin volle Codes beziehungsweise E-Mail/Code-Kombinationen loggen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Dependabot/npm-audit re-geprueft

- Agent: Codex
- Aktion:
  - `npm audit --audit-level=low` in `backend/signaling` ausgefuehrt.
  - GitHub Dependabot Alerts via `gh api` gelesen.
  - Ergebnis in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - `npm audit` meldet weiterhin 12 Vulnerabilities: 10 moderate, 2 low.
  - GitHub hat weiterhin 2 offene Alerts: `uuid` medium und `@tootallnate/once` low.
  - `npm audit fix --force` waere ein Breaking-Change-Pfad und soll nicht blind ausgefuehrt werden.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Fixserie re-verifiziert

- Agent: Codex
- Aktion:
  - Neue CC-Commits gegen HEAD re-verifiziert: `cf30743`, `c7c7e06`, `1b39f9b`, `0b64d09`, `281320f`.
  - Ergebnisse in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - `H-08` Android JSON Injection verifiziert gefixt.
  - `M-01` PKD PUT/DELETE Admin-Gating verifiziert gefixt.
  - `L-02` OG-Image GPL-Text verifiziert gefixt.
  - `H-07` bleibt mit einem Stripe-No-Email-Log-Pfad teilweise offen.
  - `H-04` ist per Rate Limit entschaerft, aber ohne Auth-/Invite-Token-Nachweis nur teilweise gefixt.
  - `L-01` bleibt in README/llms teilweise offen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Nicht von Codex geaendert:
  - `docs/agent-bridge/TODO.md` ist lokal modified und bleibt unangetastet.
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Rollenverteilung und cf741a0 re-verifiziert

- Agent: Codex
- Aktion:
  - Nutzerwunsch nach klarer Rollenverteilung umgesetzt.
  - `README.md` um CC/Codex Rollen, Uebergabeablauf und Konfliktregel erweitert.
  - CC-Commit `cf741a0` gegen HEAD geprueft.
  - Ergebnisse in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - H-07 letzter Stripe-No-Email-Logpfad verifiziert maskiert.
  - README L-01 verbessert.
  - Syntaxchecks fuer Backend-Hauptdateien erfolgreich.
  - Weitere Textdrift in `website/llms.txt` und `website/index.html` markiert.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: H-09 Pinning-Drift auditiert

- Agent: Codex
- Aktion:
  - Kontinuierlichen Bridge-/Repo-Lesemodus fortgesetzt.
  - CC-Commits `9afaed4` und `c15b955` gelesen.
  - `ROLES.md` und Textdrift-Fixes verifiziert.
  - H-09 Certificate-Pinning-Status im Android-Code und Website/Wiki-Claims geprueft.
  - Findings in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - Rollenverteilung per `ROLES.md` verifiziert.
  - `website/index.html` und `website/llms.txt` BUSL/GPL-Drift verbessert.
  - H-09 bleibt offen: keine Pinning-Implementierung sichtbar.
  - Zusaetzliche Drift: `build.gradle` setzt Pro/Premium `CERTIFICATE_PINNING=false`, waehrend Pro/Premium `FeatureFlags.kt` weiterhin `true` meldet und UI/Website aktive Pinning-Claims machen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: H-01/H-09 CC-Fixes re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commits `79efb32` und `385386a` gegen HEAD gelesen.
  - H-01 `/ice-servers` Fix und Android ICE-Injection re-verifiziert.
  - H-09 Certificate-Pinning-Herabstufung re-verifiziert.
  - Ergebnisse in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - H-01 als verifiziert gefixt mit funktionalen Test-Caveats bewertet.
  - H-09 nur teilweise gefixt: Pro ist herabgestuft, Premium-FeatureFlags und Website/Wiki-Claims bleiben inkonsistent.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: H-09 b64ee25 re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commit `b64ee25` gegen HEAD gelesen.
  - Certificate-Pinning-Flags und Website/Wiki-Claims erneut geprueft.
  - Ergebnis in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - Pro/Premium FeatureFlags jetzt beide `CERTIFICATE_PINNING=false`.
  - Mehrere Website/Wiki-Claims wurden auf planned/herabgestuft.
  - H-09 bleibt teilweise offen wegen Onboarding-Strings und restlicher Claim-/Audit-Textdrift.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Download-/Release-Statusdrift auditiert

- Agent: Codex
- Aktion:
  - Priority-2 Download-/Release-Statusdrift geprueft.
  - Android `build.gradle`, README, Website/Wiki/llms und GitHub latest release via `gh api` gelesen.
  - `UpdateChecker.kt` gegen aktuelle GitHub-Release-Assets geprueft.
  - Findings in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - Aktueller Repo-/GitHub-Release-Stand: `v1.0.28`, versionCode `50`.
  - README/Website/Wiki/llms enthalten weiterhin mehrere alte Versionsangaben (`1.0.6`, `1.0.12`, `1.0.13`, `1.0.22`).
  - Funktionales Risiko: `UpdateChecker.kt` erwartet `-vC...apk` Assetnamen, aber aktueller GitHub Release nutzt APK-Namen ohne `vC`, wodurch sideload Update-Erkennung fehlschlagen kann.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: 9cc47ae Version-/Pinning-Textfix re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commit `9cc47ae` gegen HEAD gelesen.
  - README, Website, llms, Android Strings und `UpdateChecker.kt` erneut geprueft.
  - Ergebnis in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - README, `website/index.html` und `website/llms.txt` sind jetzt auf `v1.0.28` / versionCode `50` verbessert.
  - Englischer Onboarding-Pinning-Claim ist bereinigt.
  - Weitere Wiki-Versiondrift bleibt offen (`bug-report`, `wiki/index`, `roadmap`, `security-audit`, `beta-testing`, `changelog`).
  - Deutscher Onboarding-String nennt weiterhin Zertifikat-Pinning.
  - `UpdateChecker.kt` bleibt riskant, weil aktuelle Release-Assets kein `vC` im Dateinamen enthalten.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: UpdateChecker-Fallback re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commit `f5e46cf` gegen HEAD gelesen.
  - Aktuellen GitHub latest release via `gh api` geprueft.
  - `UpdateChecker.kt` Fallback-Logik gegen aktuellen Release-Body `vC50` eingeordnet.
  - Ergebnisse in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - UpdateChecker-Fallback sollte aktuellen Release `v1.0.28` mit Assets ohne `vC` erkennen, weil Release-Body `vC50` enthaelt.
  - Testluecke bleibt: keine sichtbaren Unit-Tests fuer `parseRelease`.
  - Wiki-Versiondrift und H-09 Textdrift bleiben teilweise offen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: 2e6f67c Wiki-/Pinning-Drift re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commit `2e6f67c` gegen HEAD gelesen.
  - H-09 Textdrift und Wiki-Versiondrift erneut geprueft.
  - Ergebnisse in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - Deutscher Onboarding-Pinning-Claim verifiziert bereinigt.
  - Wiki-Index Current Version verifiziert auf `v1.0.28`.
  - Bug-Report-Latest-Dropdown verbessert.
  - Verbleibende Drift: roadmap/security-audit/beta-testing/changelog enthalten weiterhin `v1.0.12`/vC30-Kontext ohne ausreichend klare Archiv-/Historisch-Markierung.
  - H-09 Textdrift bleibt in `website/wiki/security-audit.html` teilweise offen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: F-Droid-/Lizenz-Kohaerenz nachgeprueft

- Agent: Codex
- Aktion:
  - Nach F-Droid-Entfernung lokal read-only auf Restreferenzen, Kollisionen und Inkohärenzen geprueft.
  - Keine Produktcodeaenderung vorgenommen.
- Gelesene Bereiche:
  - Android Build/Source-Struktur unter `client_android/app/`
  - Website-/Marketing-/Docs-/Fastlane-Texte
  - Payment-E-Mail-Template
  - Bridge-Dateien
- Findings:
  - F-Droid Product Flavor ist aus `client_android/app/build.gradle` entfernt; nur `free`, `pro`, `premium` sind sichtbar.
  - Dennoch existieren noch F-Droid-Resttexte in Marketing/Docs/Website/Fastlane/Payment-E-Mail.
  - Android-Code enthaelt noch F-Droid-Kommentare bzw. Legacy-Branch fuer `FDROID`-Tier.
  - Lokale/verwaiste Ordner `client_android/app/src/fdroid/` und `fdroid/` existieren noch.
  - Lizenz-Kollision: `LICENSE` ist BUSL-1.1, aber mehrere Texte nennen noch GPL-3.0 client source.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/PROJECT_STATE.md`
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Secrets gelesen oder ausgegeben.
- Kein Commit/Push/Deployment.

## 2026-05-04 - Codex: CC-Gegenpruefung fuer F-Droid-/Lizenz-Drift angefordert

- Agent: Codex
- Aktion:
  - Nutzerauftrag umgesetzt: Claude Code soll die Codex-Fundliste zur F-Droid-/Lizenz-Restdrift gegenpruefen.
  - `TODO.md` um expliziten CC-Gegenpruefungsauftrag ergaenzt.
- CC soll pruefen:
  - Ob alle genannten F-Droid-Reste real und noch relevant sind.
  - Ob verwaiste Ordner gefahrlos entfernbar sind.
  - Welche GPL-3.0-Texte auf BUSL-1.1/source-available angepasst werden muessen.
  - Welche historischen Eintraege bewusst unveraendert bleiben sollen.
  - Welche Aenderungen rein textlich sind und welche Build/Funktion beeinflussen koennten.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: fd7c0de Roadmap-/Pinning-Drift re-verifiziert

- Agent: Codex
- Aktion:
  - Bridge erneut gelesen.
  - CC-Commit `fd7c0de` gegen HEAD geprueft.
  - Roadmap-Versionstext und Security-Audit-Pinning-Text re-verifiziert.
  - Ergebnisse in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - Roadmap markiert v1.0.12 jetzt als historischen Stand und nennt current release `v1.0.28 (vC50)`.
  - Security-Audit spricht bei Certificate Pinning jetzt von geplantem Pinning, nicht mehr Enforcement.
  - H-09 bleibt als Feature-/Claim-Kontrollpunkt offen, da keine echte Pinning-Implementierung sichtbar ist.
  - `UpdateChecker`-Unit-Test-Luecke bleibt offen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: offene Backend-/Dependency-Punkte re-verifiziert

- Agent: Codex
- Aktion:
  - Aktuellen HEAD read-only gegen H-04, H-05, Dependabot und UpdateChecker-Testluecke geprueft.
  - Ergebnisse in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - H-05 `/stripe/create-dynamic-checkout` ist mit IP-Rate-Limit verifiziert, Caveat: in-memory pro Instanz.
  - H-04 `/invite/accepted` ist nur teilweise geloest: IP-Rate-Limit vorhanden, aber kein Invite-Token/Auth-Nachweis sichtbar.
  - Dependabot bleibt offen: `uuid` medium, `@tootallnate/once` low.
  - `UpdateChecker.parseRelease` bleibt ohne sichtbare Unit-Tests; Kommentare beschreiben teils noch das alte Assetnamenmodell.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Dependabot-Lockfile-Detail geprueft

- Agent: Codex
- Aktion:
  - `backend/signaling/package.json` und `backend/signaling/package-lock.json` read-only gegen `uuid`/`@tootallnate/once` geprueft.
  - `npm audit --audit-level=low` erneut ausgefuehrt.
  - Ergebnis in `CODEX_FINDINGS.md` ergaenzt.
- Ergebnis:
  - Kein direkter `uuid`-Dependency-Eintrag mehr in `package.json`.
  - Lockfile enthaelt weiterhin `uuid`-Root-/Transitiv-Eintraege.
  - `npm audit` bleibt rot mit 12 low/moderate Vulnerabilities.
  - Force-Fix wuerde riskant auf `firebase-admin@10.1.0` gehen.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Lockfile-Cleanup `80eb8a0` re-verifiziert

- Agent: Codex
- Aktion:
  - CC-Commit `80eb8a0` read-only gegen Dependabot und `npm audit` geprueft.
  - Ergebnis in `CODEX_FINDINGS.md` dokumentiert.
- Ergebnis:
  - `package-lock.json` enthaelt weiterhin `node_modules/uuid` und `node_modules/@tootallnate/once`.
  - GitHub Dependabot meldet weiterhin `uuid` medium und `@tootallnate/once` low.
  - `npm audit --audit-level=low` bleibt rot mit 12 Vulnerabilities.
  - `80eb8a0` schliesst die Alerts nicht.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: veralteten Re-Verify-Status in TODO bereinigt

- Agent: Codex
- Aktion:
  - Bridge/TODO gegen `CODEX_FINDINGS.md` abgeglichen.
  - Alte Liste "Codex Re-Verify ausstehend" in einen aktuellen Re-Verify-Status umformuliert.
- Ergebnis:
  - H-08 und M-01 sind als `VERIFIED_FIXED` dokumentiert.
  - H-04 ist als `PARTIAL` dokumentiert.
  - L-01/L-02 sind fuer FAQ/OG-Image als erledigt markiert; weitere Doku-Drift bleibt separat.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: uuid-TODO gegen aktuellen Code synchronisiert

- Agent: Codex
- Aktion:
  - Aktuellen Code read-only auf `uuid`-/`crypto.randomUUID()`-Nutzung geprueft.
  - Veralteten TODO-Text zur direkten `uuid`-Nutzung korrigiert.
- Ergebnis:
  - `src/sessions.js` und `src/server.js` nutzen `crypto.randomUUID()`.
  - `package.json` enthaelt keinen direkten `uuid`-Dependency-Eintrag mehr.
  - Offenes Risiko liegt weiterhin im Lockfile und in Transitives, nicht mehr in direktem App-Code-Verbrauch.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Privacy-/Metadaten-Claims re-geprueft

- Agent: Codex
- Aktion:
  - README/Privacy-Texte und Backend-Signaling/FCM-Code read-only gegeneinander geprueft.
  - Finding `P-01` in `CODEX_FINDINGS.md` dokumentiert.
  - `TODO.md` um konkrete Bereinigung der Privacy-/Metadaten-Claims ergaenzt.
- Ergebnis:
  - E2E-Call-Content-Claim bleibt plausibel: kein Hinweis auf serverseitige Call-Content-Entschluesselung.
  - Absolute Claims wie "No metadata", "No logs", "No personal data stored" sind zu stark.
  - Code verarbeitet/speichert FCM Tokens, Signaling-/Routing-/Sessiondaten und nutzt FCM/STUN/TURN-Infrastruktur.
  - Empfehlung: Claims praezisieren, FCM/STUN/TURN/IP/Retention ehrlich dokumentieren, Logs weiter redigieren.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Download-/Version-Statusdrift re-geprueft

- Agent: Codex
- Aktion:
  - README, Website, Wiki-Index, Android `build.gradle`, GitHub latest release und oeffentliche HTTP-Erreichbarkeit geprueft.
  - Ergebnis in `CODEX_FINDINGS.md` dokumentiert.
  - `TODO.md` fuer README-/Download-Statusdrift aktualisiert.
- Ergebnis:
  - Lokaler Android-Stand und Doku zeigen `v1.0.28` / `versionCode 50`.
  - GitHub latest release ist `v1.0.28`, Release-Body enthaelt `vC50`, APK Assets sind vorhanden.
  - `stealthx.tech` antwortet HTTP 200.
  - GitHub `releases/latest` redirectet auf `v1.0.28`.
  - Resthinweise: Play-Console-Status extern; `UpdateChecker`-Unit-Tests fehlen weiter.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung durch Codex.
- Keine Secret-Dateien gelesen.
- Keine Secrets ausgegeben.
- Kein Deployment.
