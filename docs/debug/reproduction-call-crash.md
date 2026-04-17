# Bug-Reproduktion: Call-Crash + Disconnect + Audio-Routing
**Bugs:** #1a App-Crash am Call-Start, #1b Graceful Disconnect, #2 Lautsprecher-Routing
**Device:** Samsung S10 (RF8N313QMFL), v1.0.22-premium (vC43)
**Status:** Log-Session vorbereitet — wartet auf User-Repro

---

## Vorbereitung (Dev-Team)

```bash
# 1. ADB verifizieren
adb devices  # S10 muss als "device" erscheinen

# 2. Log-Session starten
cd ~/Desktop/stealth
chmod +x tools/debug/start-logcat.sh
./tools/debug/start-logcat.sh RF8N313QMFL

# → Logcat läuft jetzt, Output in /tmp/securecall-session.log
# → Ctrl+C zum Stoppen NACH allen Repro-Versuchen
```

---

## User-Schritte am S10

### Bug #1 — Call-Crash / Disconnect (5 Versuche)

**Ziel:** Mindestens 5 Call-Versuche, damit wir sowohl Crash (1a) als auch
Disconnect (1b) Muster sehen. Beide treten inkonsistent auf.

**Pro Versuch notieren:**
- Versuch-Nummer + Uhrzeit (z.B. "Versuch 1, 11:42:30")
- Richtung: Outgoing (du rufst an) oder Incoming (du wirst angerufen)
- Ausgang: **CRASH** (App weg) / **DISCONNECT** (Call-Screen weg, App bleibt) / **ERFOLG**
- Sekunden bis zum Event (z.B. "nach 3s")
- Was siehst du: "Klingelt" → "Verbunden" → "App schließt sich"?

#### Versuche 1-3: Outgoing Calls
1. App öffnen → Partner anrufen
2. Warten bis "Klingelt" / "Verbunden" erscheint
3. Wenn Call durchkommt: 15 Sekunden sprechen, dann auflegen
4. Wenn Crash: App automatisch wieder öffnen, Notiz machen
5. Wenn Disconnect: Notiz ob App noch offen oder im Hauptmenü

#### Versuche 4-5: Incoming Calls
1. Partner ruft dich an
2. Annehmen
3. Gleiche Beobachtung wie oben

#### Optional Versuch 6: Langer Call
Falls ein Call durchkommt: 2 Minuten drin bleiben, beobachten ob
Crash/Disconnect auch bei laufendem Gespräch auftritt.

### Bug #2 — Lautsprecher-Routing (im letzten erfolgreichen Call)

Im letzten Call der durchkommt:
1. Lautsprecher-Button antippen → aktivieren
2. Prüfen: Ton aus Lautsprecher?
3. Lautsprecher-Button erneut antippen → deaktivieren
4. **Prüfen: Schaltet der Ton zurück auf Hörer?**
5. Falls Audio WEITER aus Lautsprecher kommt trotz UI-Aus: **BUG BESTÄTIGT**
6. Uhrzeit notieren

---

## Session beenden

```bash
# 1. Im Terminal: Ctrl+C drücken (stoppt logcat)
# → Zeigt Zusammenfassung mit Zeilenanzahl

# 2. Notiz-Datei erstellen mit Versuch-Mapping:
cat > /tmp/securecall-repro-notes.txt << 'EOF'
Versuch 1: [UHRZEIT] [Outgoing] [CRASH/DISCONNECT/ERFOLG] [nach Xs]
Versuch 2: [UHRZEIT] [Outgoing] [CRASH/DISCONNECT/ERFOLG] [nach Xs]
Versuch 3: [UHRZEIT] [Outgoing] [CRASH/DISCONNECT/ERFOLG] [nach Xs]
Versuch 4: [UHRZEIT] [Incoming] [CRASH/DISCONNECT/ERFOLG] [nach Xs]
Versuch 5: [UHRZEIT] [Incoming] [CRASH/DISCONNECT/ERFOLG] [nach Xs]
Bug 2 Lautsprecher: [UHRZEIT] [Bestätigt Ja/Nein]
EOF
# → Datei editieren mit den echten Werten
```

---

## Log-Dateien für Analyse

Nach der Session liegen bereit:
- `/tmp/securecall-session.log` — Vollständiger Logcat (alle Buffer)
- `/tmp/securecall-app-info.txt` — App-Version + Device-Info
- `/tmp/securecall-repro-notes.txt` — User-Notizen pro Versuch

**Analyse-Befehle (Dev-Team):**
```bash
# Crashes finden:
grep -E 'AndroidRuntime|FATAL EXCEPTION|native crash|tombstone' /tmp/securecall-session.log

# WebSocket/Signaling:
grep -E 'WS_SERVICE|HB\b|WEBRTC' /tmp/securecall-session.log | tail -50

# WebRTC ICE/DTLS:
grep -iE 'iceConnectionState|dtls|peer.*connection|candidate' /tmp/securecall-session.log

# Audio-Routing:
grep -iE 'AudioManager|setSpeaker|SPEAKER|MODE_IN_COMMUNICATION|MEDIA_ROUTER|AUDIO' /tmp/securecall-session.log

# Zeitfenster um einen bestimmten Versuch (z.B. 11:42:30):
grep '11:42:2[5-9]\|11:42:3[0-5]' /tmp/securecall-session.log
```

---

## Hypothesen (offen bis Logs da sind)

### Bug #1a (Crash)
- **H1:** Native crash in Rust JNI (XChaCha20/X25519) beim Key-Exchange
- **H2:** WebRTC PeerConnection crash bei ICE failure
- **H3:** NullPointerException in CallActivity beim Session-Setup
- **H4:** OOM bei Opus-Decoder/JitterBuffer Init

### Bug #1b (Graceful Disconnect)
- **H5:** Server-seitiger CALL_END durch Heartbeat-Timeout (60s → 180s gefixt in v1.0.22, aber nur server-seitig)
- **H6:** ICE negotiation failure → kein Media-Pfad → UI schließt
- **H7:** WebSocket disconnect während Call-Setup → CALL_END propagiert

### Bug #2 (Audio-Routing)
- **H8:** `setSpeakerphoneOn(false)` wird aufgerufen aber AudioManager ignoriert es (Samsung AudioPolicy quirk)
- **H9:** WebRTC AudioDeviceModule überschreibt Android AudioManager-Einstellung
- **H10:** Race-Condition zwischen UI-Toggle und GhostAudioPlayer-State

**§6.2: Hypothesen bleiben offen. Keine Code-Analyse vor Logs.**

---

*Vorbereitet: 17. April 2026*
*Nächster Schritt: User führt Repro durch → Logs an Dev-Team → Analyse*
