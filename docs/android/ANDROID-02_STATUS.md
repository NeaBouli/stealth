# ANDROID-02 — GhostNet Audio Pipeline (MVP)

Dieses Dokument fasst die Ergebnisse des Arbeitsschrittes ANDROID-02 zusammen.
Die minimal funktionsfähige GhostNet-Audio-Pipeline wurde implementiert.

---

## 1. Implementierte Komponenten

**Neu angelegte Dateien:**
client_android/app/src/main/java/com/securecall/app/ghostnet/AudioCapture.kt
client_android/app/src/main/java/com/securecall/app/ghostnet/AudioPlayback.kt
client_android/app/src/main/java/com/securecall/app/ghostnet/TransportStub.kt
client_android/app/src/main/java/com/securecall/app/ghostnet/AudioPipeline.kt

markdown
Code kopieren

**Modifizierte Datei:**
client_android/app/src/main/java/com/securecall/app/CallActivity.java

yaml
Code kopieren

---

## 2. Funktionsumfang (MVP)

- Aufnahme über `AudioRecord`  
- Wiedergabe über `AudioTrack`  
- Pipeline-Controller (Start/Stop)  
- Loopback-Transport (keine Netzwerkfunktion)  
- Integration in `CallActivity`

Damit ist eine vollständige lokale Audio-Rundschleife realisiert.

---

## 3. Bekannte Probleme & Hinweise

### a) Kein Encoder/Decoder
Aktuell wird raw PCM übertragen.  
→ Opus wird in **ANDROID-07** integriert.

### b) Kein Crypto
Audio wird unverschlüsselt geloopt.  
→ CryptoEngine Integration erfolgt in **CRYPTO-02**.

### c) Kein JitterBuffer
Audio kann bei hoher Auslastung unregelmäßig werden.  
→ Buffer-Management kommt in **ANDROID-08**.

### d) Kein Netzwerk
TransportStub simuliert nur lokale Übertragung.  
→ echter Transport entsteht in **BACKEND-02** + **ANDROID-10**.

---

## 4. Quick-Debug

### Fehler: „AudioRecord.failed to initialize“
→ Meistens fehlende Berechtigung:

AndroidManifest:
<uses-permission android:name="android.permission.RECORD_AUDIO"/> ```
Fehler: Kein Ton
Gerät-Lautstärke prüfen

Mono-Ausgabe wird bei manchen Geräten leise geroutet

Test über Kopfhörer empfohlen

Fehler: App stürzt beim Stop ab
→ Recorder muss stop + release erhalten
→ Ist implementiert, sollte gefixt sein

5. Status (ANDROID-02 abgeschlossen)
Capture → OK

Playback → OK

TransportStub → OK

Pipeline → OK

Activity-Integration → OK

Bereit für:

ANDROID-03 (Secure Mode Monitor)

CRYPTO-02 (Rust/JNI Integration)

BACKEND-02 (Call Signaling Logic)

