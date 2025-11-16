# ANDROID-02 – GhostNet Audio Pipeline (MVP)

## Ziel
Eine funktionierende Audio-Pipeline:
- AudioRecord (Input)
- AudioTrack (Output)
- Transport über GhostNet (MVP: WebRTC)
- Integration mit Signaling-MVP

---

## Erwartetes Ergebnis

### 1. Audio-Handling
- Aufnahme (16 Bit PCM, Mono)
- Playback (16 Bit PCM, Mono)
- Buffergrößen korrekt berechnet

### 2. Netzwerk
- WebRTC PeerConnection
- ICE-Kandidaten über Signaling
- Kanal für Audioframe-Transport

### 3. Integration
- CallFragment initiiert Start/Stop
- Anzeigen: „Verbunden“, „Getrennt“
- Debug-Log für Paketanzahl & Latenz

---

## Tests
- Zwei Android-Geräte verbinden sich
- Eine Richtung Audio funktioniert (später bidirektional)
- App stürzt nicht ab bei Paketverlust

---

## Developer FAQ

**Frage:** Muss alles verschlüsselt sein?  
Antwort: Das Ziel ist Verschlüsselung. Für Debugging darf kurzzeitig unverschlüsselt gearbeitet werden, **aber niemals in Git commiten**.

**Frage:** WebRTC oder QUIC?  
Antwort: MVP = WebRTC. QUIC kommt später (Premium/OS).

**Frage:** Opus Codecs jetzt schon?  
Antwort: Optional. PCM reicht für MVP.

