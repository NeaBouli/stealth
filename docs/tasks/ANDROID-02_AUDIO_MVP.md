# ANDROID-02 – GhostNet Audio Pipeline (MVP)

## Ziel
Erstellen einer funktionierenden Audio-Pipeline im Android-Client, die:
1. über den Signaling-Server einen Call aufbaut,
2. Audio mit `AudioRecord` aufnimmt,
3. Audio vor dem Senden (optional) durch die Crypto-Engine schickt,
4. Audio über WebRTC/QUIC transportiert,
5. Ton über `AudioTrack` wiedergibt.

Dies ist der erste echte Schritt zur Ende-zu-Ende-Kommunikation.

---

## Erwartetes Ergebnis

Ein funktionierender Call-Prototyp:

- App kann „Call starten“ drücken.
- Signaling erstellt eine Session zwischen A ↔ B.
- Audio wird live aufgenommen und übertragen.
- Ankommender Audio-Stream wird abgespielt.
- Debug-Modus erlaubt unverschlüsselten Transport (nur während Entwicklung!).

---

## Technische Anforderungen

### 1. AudioRecord Setup
- Sample Rate: 16 kHz oder 48 kHz  
- Channel: MONO  
- Format: `ENCODING_PCM_16BIT`  
- Buffer dynamisch berechnet über `AudioRecord.getMinBufferSize(...)`  
- AudioRecord läuft in eigenem Thread.

### 2. AudioTrack Setup
- gleiche Parameter wie AudioRecord  
- Streaming-Modus (`MODE_STREAM`)  
- eigener Empfangs-Thread

### 3. Transport Layer
- WebRTC Audio DataChannel **oder**
- QUIC-basierter Custom-Transport (für MVP ist WebRTC empfohlen)
- Fallback: direkter UDP-Socket (nur Debug)

### 4. Signaling (Backend-01 Voraussetzung)
- /invite
- /answer
- /cancel
- WebSocket-Events:
  - CALL_INVITE
  - CALL_ACCEPT
  - CALL_END

### 5. Crypto-Integration (optional in MVP)
- Audio-Daten als ByteArray an Crypto-Engine leiten:
  - `encrypt_frame(frame)`
  - `decrypt_frame(frame)`
- Debug-Flag erlaubt unverschlüsselte Frames (vor Merge deaktivieren!)

---

## UI-Verhalten

- CallScreen zeigt einfachen Status:
  - „Verbinde…“
  - „Live“
  - „Call beendet“
- Kein Design nötig, Fokus: Funktionalität.

---

## Tests

### Manuelle Tests (Minimum)
- Call A → B funktioniert
- Audio geht in beide Richtungen
- Latenz bleibt < 400 ms bei gutem Netz
- Bei schlechtem Netz:
  - Jitter-Buffer verhindert Aussetzer
  - Verbindung bleibt stabil

### Automatisierte Tests
- Placeholder-Tests erlaubt (Instrumented Tests später)

---

## Developer FAQ

**Frage:** Muss Verschlüsselung im MVP schon aktiv sein?  
**Antwort:** Optional. Wichtig ist, dass die Pipeline funktioniert. Verschlüsselung wird in CRYPTO-02 aktiviert.

**Frage:** Reicht WebRTC oder muss QUIC implementiert werden?  
**Antwort:** WebRTC reicht. QUIC kommt später für Premium/OS.

**Frage:** Wie testen wir ohne Gegenstelle?  
**Antwort:** Loopback-Modus nutzen: Audio von A an sich selbst senden (nur Dev).

---

