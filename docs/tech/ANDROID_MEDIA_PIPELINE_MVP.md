# ANDROID – GhostNet Media Pipeline (MVP-Skeleton)

**Status:** Skeleton / Platzhalter – keine echte Crypto, kein echtes Opus.  
**Ziel:** Vollständiger Datenfluss von Transport → Router → Decrypt → Decode → Playback.

---

## 1. Übersicht

Aktueller Datenfluss (MVP):

TransportThread  
→ `GhostControlRouter.routeIncoming(frame: ByteArray)`  
→  
- CONTROL: `ControlFrameParser.parse(frame)` (PING/PONG, später mehr)  
- MEDIA: `GhostMediaRouter.route(MediaFrame)`

Im MEDIA-Zweig:

`MediaFrame`  
→ `MediaDecryptor.decrypt(frame)` (Platzhalter, gibt Originaldaten zurück)  
→ `MediaDecoder.decode(bytes)` (Platzhalter, liefert Dummy-PCM)  
→ `AudioPlayer.play(pcm)` (AudioTrack-Wrapper)

---

## 2. Wichtige Klassen

### 2.1 Transport

- `ghostnet.transport.GhostTransport`
  - Queue für eingehende Frames
  - `enqueueTestFrame(...)` für Debug

- `ghostnet.transport.thread.GhostTransportThread`
  - Holt Frames aus der Queue
  - Ruft `GhostControlRouter.routeIncoming(frame)` auf

### 2.2 Routing

- `ghostnet.control.GhostControlRouter`
  - `routeIncoming(frame: ByteArray)`
  - Primitive Heuristik `isControlFrame(...)`
  - CONTROL → `ControlFrameParser.parse(frame)`
  - MEDIA → `GhostMediaRouter.route(MediaFrame)`

### 2.3 Control

- `ghostnet.control.ControlFrameParser`
  - Header-Byte-Dispatch (0x01 = PING, 0x02 = PONG, Platzhalter)
  - Später: Session-Control, Reconnect, Negotiation, etc.

- `ghostnet.control.ControlFrameBuilder`
  - `ping()`, `pong()` (Header-Bytes gesetzt)

### 2.4 Media

- `ghostnet.media.MediaFrame`
  - Wrapper für `ByteArray data` + `timestamp`

- `ghostnet.media.GhostMediaRouter`
  - zentraler Entry-Point für MediaFrames
  - ruft nacheinander:
    - `decrypt(frame)`
    - `decodeAudio(bytes)`
    - `playPcm(pcm)`

---

## 3. Crypto-Schicht (Platzhalter)

- `ghostnet.media.crypto.MediaDecrypt`
  - Liefert momentan `MediaFrame` unverändert zurück
  - Kommentar: später XChaCha20-Poly1305 / AES-GCM via Session-Key

- `ghostnet.media.crypto.MediaDecryptor`
  - `decrypt(frame: MediaFrame): ByteArray`
  - Derzeit: Return = Originaldaten

**TODO (später):**

- Austausch durch JNI-Bindings zu Rust-Crypto-Engine
- Session-Key-Handling aus Core Crypto Engine (nicht Android-spezifisch)

---

## 4. Decode-Schicht (Platzhalter)

- `ghostnet.media.decode.MediaDecoder`
  - `decode(bytes: ByteArray): ShortArray`
  - Derzeit: erzeugt Dummy-PCM (`ShortArray` fester Länge)
  - Später: Opus (libopus, native)

**TODO (später):**

- Integration von Opus
- Sample-Rate/Channel-Konfiguration dynamisch
- Fehlerhandling bei beschädigten Frames

---

## 5. Playback-Schicht

- `ghostnet.media.playback.AudioPlayer`
  - Einfacher `AudioTrack`-Wrapper
  - `ensureTrack()`: lazy Init, 8 kHz, mono, 16-bit PCM
  - `play(samples: ShortArray)`
  - `stopAndRelease()`

**TODO (später):**

- Anpassung auf echte Sample-Rate (z.B. 16 kHz / 48 kHz)
- Auslagerung in eigenen Thread
- Lautstärke-/Routing-Steuerung (Earpiece vs. Speaker)

---

## 6. Debug-Hooks (MainActivity)

Aktuell vorhandene Buttons (Debug):

- `setupRandomFrameButton()`
- `setupSendEncryptedMediaFrameButton()`
- `setupSendAudioTestButton()`
- diverse Status-/Logging-Buttons (SessionState, StateFlow, WS-Status, etc.)

Diese helfen, die Pipeline schrittweise zu testen, bevor echte Crypto/Opus/WebRTC integriert sind.

---

## 7. Nächste Schritte (aus Architektursicht)

1. **Krypto-Engine anbinden (CRYPTO-02):**
   - Rust-Library (Core Crypto Engine)
   - JNI-Bridge nach Android
   - Austausch der Platzhalter-Decryptor-Implementierungen

2. **Echten Transport einbauen (ANDROID-02 / GhostNet Transport):**
   - WebRTC oder QUIC
   - SRTP / DTLS
   - Multi-Hop Routing (später)

3. **Decode ersetzen:**
   - Opus-Decoding (native)
   - Anpassung an Netzwerkparameter

4. **Playback verfeinern:**
   - Echte Sample-Rates
   - Audio-Routing (Earpiece / Speakerphone)
   - Echo Cancellation / AGC (falls nötig)

Dieses Dokument ist bewusst knapp gehalten, damit Entwickler schnell verstehen,  
wo die Skeleton-Stellen sind und welche Komponenten bereits existieren.

