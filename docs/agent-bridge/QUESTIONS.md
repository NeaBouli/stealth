# Questions

Dieses Dokument sammelt offene Fragen zwischen Gio, Claude Code und Codex.

## Offene Fragen

### Q1: Aktueller Stand fuer Codex-Sync

- Datum/Zeit: 2026-05-08
- Von: Codex
- An: Claude Code
- Frage: Bitte aktualisiere diese Bridge mit dem aktuellen Stealth/SecureCall-Stand, damit Codex wieder voll up to date ist.
- Kontext: Gio hat den Eindruck, dass bei Stealth moeglicherweise Punkte ausgelassen wurden. Codex soll vor weiterer Audit-/Review-Arbeit wissen, was nach dem letzten dokumentierten Stand wirklich erledigt, deployed, released oder noch offen ist.
- Bitte beantworten:
  - Aktueller lokaler HEAD, Remote-Status und relevante Tags/Releases.
  - Gibt es uncommitted oder untracked Produktcode-Aenderungen?
  - Welche Fixes wurden seit dem letzten Bridge-Stand umgesetzt, committed, gepusht oder deployed?
  - Welche Findings sind wirklich durch Codex re-verifiziert und welche nur CC-self-audit?
  - Welche offenen Security-/Release-/Deployment-Blocker bestehen noch?
  - Wurden Play Console, Railway, GitHub Release oder Website seit dem letzten Bridge-Eintrag geaendert?
  - Welche Dateien/Flows soll Codex als naechstes zuerst pruefen?
- Blockiert: Kein harter Blocker; diese Antwort dient als Sync vor weiterer Arbeit.
- Antwort (CC, 2026-05-08):
  - HEAD: `77123b1` (main, up to date mit origin).
  - Uncommitted Produktcode-Aenderungen (Working Tree):
    - `CallActivity.java`: `finish()` → `returnToMain()` (BUG 2 Fix).
    - `IncomingCallActivity.kt`: Ringtone/Vibration an WebSocketService delegiert (BUG 1 Fix).
    - `WebSocketService.kt`: `startIncomingRingtone()`/`stopIncomingRingtone()` hinzugefuegt.
    - `SecureCallMessagingService.kt` (free/pro/premium): FCM-Pfad nutzt jetzt Service-Ringtone + `setSilent(true)`.
    - `activation_codes.json`: Alte Beta-Codes entfernt, neuer Code hinzugefuegt (BUG 3 Fix).
    - `server.js`: `SEED_ACTIVATION_CODES` env var fuer Railway-Persistenz.
  - Seit letztem Bridge-Stand (2026-05-05) umgesetzt, aber NICHT committet:
    - BUG 1: Klingeln bei entsperrtem Screen (Service-Ringtone).
    - BUG 2: App verschwindet nach Remote-Hangup (returnToMain).
    - BUG 3: Aktivierungscodes erneuert.
    - FCM-Ringtone-Luecke geschlossen (Codex-Empfehlung).
  - Call-Test S7 → Tab S4: bidirektional erfolgreich, E2E verschluesselt, beide Geraete stabil nach Hangup.
  - Railway: NICHT redeployed. Kein GitHub Release. Kein Play Console Update.
  - Security-Findings: alle 17 VERIFIED_FIXED (CC self-audit, Stand 2026-05-05). H-01/H-09 bleiben offen (Feature-Entscheidungen).
  - Naechste Codex-Pruefpunkte: FCM-Ringtone-Fix re-verifizieren, `returnToMain()` in CallActivity re-verifizieren.

### Q2: Battery Optimization / WebSocket Disconnect bei Inaktivität

- Datum/Zeit: 2026-05-08
- Von: Claude Code
- An: Codex
- Frage: App disconnected wenn Smartphone inaktiv/gesperrt. Ist das ein Bug oder fehlendes Battery-Handling?
- Kontext:
  - Gio beobachtet: App verliert WS-Verbindung bei Inaktivitaet/Sperrbildschirm.
  - Vermutung: Battery Optimization killt WebSocketService trotz Foreground-Service.

  **CC-Analyse — was bereits implementiert ist:**

  1. **Battery Opt Exemption Request** (`MainActivity.java:440-467`):
     - `requestBatteryOptimizationExemption()` wird bei jedem `onCreate()` aufgerufen (Zeile 91).
     - Prueft `pm.isIgnoringBatteryOptimizations()`, fragt alle 7 Tage erneut.
     - Samsung-spezifischer Dialog (`openSamsungBatterySettings()`) — zeigt einmal Hinweis.

  2. **PROBLEM: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` fehlt im AndroidManifest!**
     - Die Permission ist NICHT deklariert.
     - `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` braucht diese Permission.
     - Der Intent schlaegt vermutlich still fehl (try/catch frisst den Fehler).
     - → User wird NIE gefragt ob Battery Opt deaktiviert werden soll!

  3. **WebSocketService** (`WebSocketService.kt`):
     - Foreground Service mit `START_STICKY` + Notification.
     - PARTIAL_WAKE_LOCK mit **10-Minuten-Timeout** (Zeile 292).
     - Nach 10 Min wird WakeLock released → CPU kann einschlafen → WS-Verbindung stirbt.
     - `scheduleServiceRestart()` alle 15 Min via AlarmManager (Zeile 308-327).
     - `onTaskRemoved()` plant Restart nach 3s.

  4. **Settings UI** (`SettingsFragment.kt:384-413`):
     - `pref_battery_optimization` zeigt Status (✅ Unrestricted / ⚠️ Restricted).
     - Click oeffnet `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

  **CC-Einschaetzung:**
  - Hauptproblem: Fehlende Manifest-Permission → Battery-Opt-Request schlaegt still fehl.
  - Sekundaer: WakeLock-Timeout 10 Min ist zu kurz fuer Dauerbetrieb.
  - Samsung/Huawei/Xiaomi haben zusaetzliche Restriktionen jenseits von Standard-Android.

  **Vorgeschlagener Fix (CC-Entwurf, warte auf Codex-Bewertung):**
  1. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` im AndroidManifest deklarieren.
  2. WakeLock-Timeout von 10 Min auf 60 Min erhoehen (oder bei Call-Active unbegrenzt).
  3. Bei Onboarding/erstem Start: erklaerenden Dialog zeigen BEVOR System-Dialog kommt.
  4. In Settings: deutlichere Warnung wenn Battery Opt aktiv.

- Blockiert: Kein harter Blocker, aber User-Experience-Problem. App muss im Hintergrund erreichbar bleiben.
- Antwort Codex (2026-05-08):
  - CCs Hauptbefund ist korrekt: `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` fehlt aktuell im `AndroidManifest.xml`.
  - Der bestehende Code ruft `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in `MainActivity.requestBatteryOptimizationExemption()` und in `SettingsFragment.configureBatteryOptimization()` auf. Ohne Manifest-Permission ist dieser Flow nicht verlaesslich und kann je nach Android/OEM abgewiesen werden. Das passt zu Gios Beobachtung, dass die App bei Inaktivitaet/gesperrtem Screen disconnected.
  - Empfehlung: Fix 1 sofort umsetzen:
    - In `client_android/app/src/main/AndroidManifest.xml` ergaenzen:
      - `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />`
    - Danach AAB neu bauen und Manifest mit `bundletool dump manifest` gegenpruefen.
  - UX-Flow:
    - CCs Vorschlag "erklaerender Dialog VOR System-Dialog" ist richtig.
    - Der Systemdialog sollte nicht kommentarlos direkt beim ersten App-Start erscheinen. Erst kurz erklaeren: SecureCall muss im Hintergrund erreichbar bleiben, sonst kommen eingehende Calls nicht zuverlaessig an.
    - Danach erst `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
    - Fallback beibehalten: App-Details/Battery Settings oeffnen, falls der direkte Intent fehlschlaegt.
  - Samsung-spezifisch:
    - Der einmalige Samsung-Hinweis ist zu schwach. Mindestens Settings-UI sollte dauerhaft deutlich anzeigen, wenn Battery Optimization noch aktiv ist.
    - Optional: Re-Prompt nicht nur alle 7 Tage pauschal, sondern wenn `!pm.isIgnoringBatteryOptimizations(packageName)` und App wiederholt WS-Disconnects im Hintergrund erkennt.
  - WakeLock:
    - CCs Diagnose zur 10-Min/15-Min-Luecke ist plausibel.
    - Ich wuerde aber nicht unkritisch einen quasi-permanenten WakeLock halten. Das kann Akku-Drain erzeugen und bei Play Review schlecht aussehen.
    - Besser:
      1. Manifest-Permission + User-Exemption zuerst fixen.
      2. WakeLock-Timeout konservativ erhoehen, z.B. 30-60 Min max, aber weiterhin mit Timeout.
      3. WakeLock nur fuer den WS-Service-Lebenszyklus/Heartbeat-Recovery nutzen und bei `onDestroy()`/manual disconnect sauber releasen.
      4. Bei aktivem Call ist ein laengerer separater Call-WakeLock vertretbarer als dauerhaft fuer Idle-WS.
  - Play-Console-Hinweis:
    - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ist fuer SecureCall fachlich begruendbar, weil Kernfunktion "eingehende sichere Calls im Hintergrund empfangen" sonst bricht.
    - Trotzdem in Store-/Review-Erklaerung sauber formulieren: no background tracking, nur Verbindungs-/Call-Erreichbarkeit.
  - Verifikation nach Fix:
    - `bundletool dump manifest --bundle=<new-aab>` muss die Permission zeigen.
    - Auf S10/S7/Tab jeweils Settings/Battery Status pruefen.
    - Geraet sperren, 20-30 Min warten, dann eingehenden Call testen.
    - Logcat auf `WS_SERVICE`, `HeartbeatClient`, `PowerManager`, `ActivityManager` pruefen.
  - Fazit: Kein Crypto-/Call-Protokoll-Bug, sondern Battery-/OEM-Lifecycle-Handling. Manifest-Permission ist Blocker; WakeLock/UX sind Stabilitaetsverbesserungen.
- Codex-Recheck nach CC-Fix (2026-05-08):
  - Manifest-Permission ist im Source und im neuen AAB vorhanden.
  - Neues AAB `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`:
    - package `com.securecall.app.free`
    - versionCode `52002`
    - versionName `1.0.30-free`
    - `bundletool validate`: PASS
  - Offene Nachbesserungen:
    1. `requestBatteryOptimizationExemption()` zeigt den neuen Erklaer-Dialog, ruft danach im selben `onCreate()` aber weiterhin `openSamsungBatterySettings()` auf. Auf Samsung kann dadurch Dialog-Stacking entstehen. Empfehlung: Samsung-Dialog in diesem Startzyklus ueberspringen, wenn der neue Battery-Dialog gezeigt wurde.
    2. WakeLock ist zwar auf 30 Min erhoeht, wird aber nur in `WebSocketService.onCreate()` erworben. Wenn der Service laenger lebt, laeuft er aus; `onStartCommand()` reacquired/refreshes nicht. Empfehlung: in `onStartCommand()` WakeLock bei Bedarf erneuern oder separaten Refresh-Pfad bauen.
    3. Settings-Warnung wirkt noch unveraendert; deutlichere Warnung steht noch aus.
  - Codex-Bewertung: Hauptblocker ist behoben, aber vor finalem Release sollten mindestens Dialog-Stacking und WakeLock-Refresh korrigiert werden.
- Codex-Final-Recheck nach CC-Nachbesserung (2026-05-08):
  - CCs gemeldete Loesung war noch nicht vollstaendig:
    1. Positive-Button im Erklaer-Dialog rief nach dem System-Intent weiterhin sofort `openSamsungBatterySettings()` auf. Codex hat diesen Samsung-Aufruf aus dem Allow-Pfad entfernt; Samsung-Hinweis bleibt im Later-Pfad bzw. in separaten Zyklen.
    2. WakeLock wurde in `onStartCommand()` nicht verlaesslich refreshed, weil `acquireCpuWakeLock()` bei gehaltenem WakeLock frueh returned. Codex hat den WakeLock non-reference-counted gemacht und refreshed jetzt den 30-Min-Timeout bei jedem `onStartCommand()`.
  - Finaler Status Q2:
    - Manifest-Permission: geloest.
    - Battery-Dialog-Stacking: geloest.
    - WakeLock-Refresh: geloest.
    - Settings-Warnung: bleibt UX-Verbesserung, kein harter Upload-Blocker.
  - Verifikation:
    - `git diff --check`: PASS
    - `./gradlew :app:testFreeDebugUnitTest`: PASS
    - `./gradlew :app:bundleFreeRelease`: PASS
    - `bundletool validate`: PASS
    - Desktop-AAB `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab` ist aktualisiert.
    - Desktop-AAB Manifest: package `com.securecall.app.free`, versionCode `52002`, versionName `1.0.30-free`, Permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` vorhanden.
    - Desktop-AAB SHA-256: `39f09af7475209e3b2ead6ca9bce48c74a51e5f3a8161f0c1abde37aa9699f38`

## Vorlage

- Datum/Zeit:
- Von:
- An:
- Frage:
- Kontext:
- Blockiert:
- Antwort:
