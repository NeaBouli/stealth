# TASK: ANDROID-02 – GhostNet Audio Pipeline (MVP)

## 1. Ziel des Tasks
Dieser Task implementiert die erste lauffähige Audio-Pipeline:

- AudioRecord → Opus → CryptoEngine.encrypt → Transport (WebRTC/QUIC)  
- Transport → CryptoEngine.decrypt → Opus → AudioTrack

Ziel: Ein **echter verschlüsselter Audio-Call** zwischen zwei Geräten.

Dies ist der Kern der Free-Version „GhostTalk Basic“.

---

## 2. Anforderungen

### 2.1 Module & Klassen
Unter `client_android/` muss folgendes entstehen:

client_android/
└── app/src/main/java/com/securecall/app/ghostnet/
├── AudioRecorder.kt
├── AudioPlayer.kt
├── GhostNetTransport.kt
├── GhostNetManager.kt
└── OpusCodec.kt (Dummy / Platzhalter)

yaml
Code kopieren

### 2.2 Funktionen

#### AudioRecorder.kt
- Initialisiert Android AudioRecord
- Liest PCM 16-bit Frames
- Übergibt Frames an Codec/Krypto

#### AudioPlayer.kt
- Nutzt AudioTrack
- Spielt entschlüsselte PCM-Frames ab

#### OpusCodec.kt
- Dummy-Einbettung für späteren Opus-Einsatz
- Für MVP: Unveränderte PCM-Passthrough oder Fake-Komprimierung

#### GhostNetTransport.kt
- Implementiert WebRTC-Datenkanal oder QUIC-Stream
- Sendet verschlüsselte Audioframes
- Empfängt fremde Audioframes

#### GhostNetManager.kt
- Verwaltet Starten/Stoppen des Calls
- Bindeglied zwischen AudioRecord ↔ Crypto ↔ Transport ↔ AudioTrack

---

## 3. Crypto-Integration

### 3.1 Nutzung der Rust Crypto Engine
Über JNI:

- `encrypt_frame(byte[]) -> byte[]`
- `decrypt_frame(byte[]) -> byte[]`

### 3.2 Temporäre Debug-Option
Für frühes Debugging darf **unverschlüsseltes Audio** übertragen werden.  
BEVOR der Pull Request gemerged wird → **zwingend deaktivieren**.

---

## 4. Flow (MVP)

(AudioRecord PCM)
↓
OpusCodec.encode() (Platzhalter)
↓
CryptoEngine.encrypt()
↓
GhostNetTransport.send()
↓
GhostNetTransport.receive()
↓
CryptoEngine.decrypt()
↓
OpusCodec.decode()
↓
(AudioTrack)

yaml
Code kopieren

---

## 5. Deliverables

- Funktionsfähiger verschlüsselter Audio-Call zwischen zwei Geräten
- UI-Integration: Call Screen zeigt „Connected“
- Fehlerhandling bei Verbindungsabbruch
- Kommentarblock im Code: „ANDROID-02“

---

## 6. Tests

### 6.1 Funktionstests
- Gerät A ↔ Gerät B hören sich gegenseitig
- Kein Audio hörbar ohne gültigen Schlüssel
- Call-Abbruch bringt App in stabilen Zustand

### 6.2 Netzwerkstests
- WLAN stabil
- Paketverlust < 5% toleriert
- Reconnect bei kurzen Disconnects

### 6.3 Security Tests
- Kein Klartext im internen Speicher
- Keine Logs mit Audio-Daten
- Keine Berechtigungsfehler

---

## 7. Q&A

**F:** Müssen wir den echten Opus-Codec sofort integrieren?  
**A:** Nein, MVP kann PCM-Passthrough sein.

**F:** Ist WebRTC Pflicht?  
**A:** Für MVP ja – es spart Zeit und liefert NAT-Traversal gratis.

**F:** Wie testen wir Encryption/Decryption?  
**A:** CRYPTO-02 liefert Dummy-Rückgaben, später echte Krypto. MVP funktioniert trotzdem.

---

## 8. Referenzen
- CRYPTO-01
- BACKEND-01
- ANDROID-01
- docs/ARCHITECTURE_OVERVIEW.md
