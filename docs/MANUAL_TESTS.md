# SecureCall — Manual Test Cases

## Voraussetzungen
- Android-Gerät oder Emulator (API 23+)
- Debug-APK installiert: `tools/test_manual_android.sh`
- Für Netzwerk-Tests: `node backend/ghostnet_echo_server.js` auf dem Host
- Emulator WS-URL: `ws://10.0.2.2:8080/signal`
- Device im LAN: `ws://<HOST_IP>:8080/signal`

---

## MT-01: App Launch + UI Presence
1. App starten
2. **VERIFY:** Hauptbildschirm zeigt "Start Call" und "Settings" Buttons
3. **VERIFY:** Debug-Panel, Status-Anzeigen sichtbar
4. **VERIFY:** Kein Crash beim Kaltstart

## MT-02: Settings Navigation
1. "Settings" Button tippen
2. **VERIFY:** SettingsActivity öffnet ohne Crash
3. Zurück-Button drücken
4. **VERIFY:** Rückkehr zu MainActivity

## MT-03: Call Connect/Disconnect (mit Echo-Server)
1. Echo-Server starten: `node backend/ghostnet_echo_server.js`
2. "Start Call" tippen
3. **VERIFY:** Button-Text wechselt zu "IN CALL"
4. **VERIFY:** Logcat zeigt `GHOSTNET_WS: onOpen()`
5. **VERIFY:** Logcat zeigt KEEPALIVE alle ~5 Sekunden
6. 15 Sekunden warten
7. "IN CALL" Button tippen (Disconnect)
8. **VERIFY:** Button-Text zurück zu "Start Call"
9. **VERIFY:** Logcat zeigt `CONTROL_BYE` gesendet, dann `onClosed()`

## MT-04: Call ohne Server (Network Error)
1. Kein Echo-Server laufen lassen
2. "Start Call" tippen
3. **VERIFY:** Logcat zeigt `onFailure()` mit Connection Refused
4. **VERIFY:** CallSessionManager → IDLE mit `lastEndReason=NETWORK_ERROR`
5. **VERIFY:** Kein Crash, App bleibt bedienbar

## MT-05: Mikrofon-Permission Flow
1. Permission entziehen: `adb shell pm revoke com.securecall.app android.permission.RECORD_AUDIO`
2. "Start Call" tippen
3. **VERIFY:** Permission-Dialog erscheint
4. Permission verweigern
5. **VERIFY:** Toast/Log "Mikrofon-Berechtigung benötigt"
6. **VERIFY:** Kein Crash
7. Erneut tippen, diesmal Permission gewähren
8. **VERIFY:** Logcat zeigt "Audio capture STARTED"

## MT-06: Audio Capture Lifecycle
1. RECORD_AUDIO Permission gewähren
2. "Start Call" tippen (mit Echo-Server)
3. **VERIFY:** Logcat zeigt "Audio capture STARTED" oder "Capture thread started"
4. **VERIFY:** Logcat zeigt `sendBinary()` Aufrufe mit Audio-Daten
5. Disconnect
6. **VERIFY:** Logcat zeigt "Audio capture STOPPED"

## MT-07: Rapid Connect/Disconnect (Stress)
1. 5x schnell hintereinander "Start Call" / "IN CALL" tippen
2. **VERIFY:** Kein Crash, kein ANR
3. **VERIFY:** Finaler Zustand konsistent (Button-Text passt zum State)

## MT-08: App Kill während Call
1. Call starten (Connect)
2. Home-Button drücken
3. Recents → App wegwischen
4. **VERIFY:** Logcat zeigt `onDestroy()`, Audio gestoppt, WebSocket disconnected
5. App neu starten
6. **VERIFY:** Sauberer Zustand, "Start Call" Button

## MT-09: Screen Rotation
1. Call starten
2. Gerät drehen (Portrait → Landscape → Portrait)
3. **VERIFY:** Kein Crash
4. **VERIFY:** Call-State bleibt erhalten oder wird sauber zurückgesetzt

## MT-10: Native Crypto Self-Test
1. App starten
2. **VERIFY:** Logcat zeigt `CORE_CRYPTO: Native crypto library loaded successfully`
3. **VERIFY:** Oder `CORE_CRYPTO: Failed to load native library — using fallback` (wenn .so nicht verfügbar)
4. Bei geladenem Native: Call starten und Audio senden
5. **VERIFY:** Logcat zeigt `SESSION_CIPHER: encrypt()` mit `plainSize > 0`

---

## Logcat-Filter
```bash
adb logcat | grep -E "(GHOSTNET_WS|CORE_CRYPTO|SESSION_CIPHER|GHOST_SESSION|NONCE_MANAGER|REPLAY_DETECTOR)"
```
