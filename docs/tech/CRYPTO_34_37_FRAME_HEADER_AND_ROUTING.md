# CRYPTO-34..37 – FrameHeaderV1, Flags & Inbound Routing

Status: DONE  
Bereich: GhostNet Wire Encryption (Android-Client)

## 1. FrameHeaderV1 (4-Byte Header)

Wir haben ein kompaktes Header-Format für verschlüsselte Frames eingeführt:

- **Byte 0 – VERSION**  
  - aktuell: `0x01` (FrameHeaderV1.VERSION)
- **Byte 1 – FLAGS**  
  - Bitfeld, z.B. AUDIO, CONTROL, KEEPALIVE
- **Byte 2 – KEY_ID**  
  - Version / Index des Session-Keys
- **Byte 3 – NONCE_PREFIX**  
  - High 8 Bits des Nonce-Counters (`(nonce >> 8) & 0xFF`)

Implementierung:

- `com.securecall.app.ghostnet.frame.header.FrameHeaderV1`
- `toBytes()` → ByteArray für das Wire-Format
- `parse()` → Header-Objekt aus einem ByteArray lesen

## 2. FrameFlags

Bitmask-Flags für den Header:

- `FrameFlags.AUDIO = 0x01`
- `FrameFlags.CONTROL = 0x02`
- `FrameFlags.KEEPALIVE = 0x04`

(Reserve für spätere Erweiterungen wie VIDEO etc.)

Datei:

- `com.securecall.app.ghostnet.frame.header.FrameFlags`

## 3. SessionCipherEngine – FrameV1 Encrypt/Decrypt

Wir haben eine erste, noch **kryptografisch stubhafte** Pipeline für Frames der Version 1:

- `buildFrameHeaderV1(ctx, flags, nonce)`  
  - baut das 4-Byte-Header-Array für FrameHeaderV1
- `encryptFrameV1(ctx, plain, flags)`  
  - ruft `ctx.nextNonce()`
  - baut Header
  - ruft `encrypt(ctx, plain)` (Stub)
  - gibt `header + encryptedPayload` zurück
- `decryptFrameV1(ctx, cipher)`  
  - prüft Mindestgröße (>= 4 Bytes)
  - parst Header via `FrameHeaderV1.parse`
  - loggt flags / keyId / noncePrefix
  - schneidet Payload ab `cipher[4..]`
  - ruft `decrypt(ctx, payload)` (Stub)

Datei:

- `com.securecall.app.ghostnet.crypto.SessionCipherEngine`

Binding-Helfer:

- `com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.encryptFrameV1(...)`
- `com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.decryptFrameV1(...)`

## 4. MediaDecryptor – FrameV1-Decrypt Einstieg

Auf Media-Ebene existiert ein dedizierter Einstieg für FrameV1:

- `MediaDecryptor.decryptFrameV1(frame: MediaFrame): ByteArray`
  - holt aktiven Session-Kontext aus `SessionCipherBinding.activeSession`
  - ruft `decryptFrameV1(ctx, frame.data)`
  - fällt ohne aktiven Kontext auf `frame.data` zurück

Datei:

- `com.securecall.app.ghostnet.media.crypto.MediaDecryptor`

## 5. GhostMediaRouter – Inbound Routing mit FrameV1

Der Media-Router nutzt nun den FrameV1-Decrypt-Pfad:

- `private fun decryptFrameV1(frame: MediaFrame): ByteArray`
  - Wrapper um `MediaDecryptor.decryptFrameV1(...)`
- im Inbound-Flow:
  - `val raw = decryptFrameV1(frame)`
  - danach: weitere Verarbeitung/Decoding

Datei:

- `com.securecall.app.ghostnet.media.GhostMediaRouter`

## 6. Debug / Logging

Zentrale Logpunkte:

- `SessionCipherEngine.decryptFrameV1`  
  - `decryptFrameV1(): flags=.. keyId=.. prefix=..`
- Media-Pipeline:
  - weitere Logs in Router / Decoder-Pfad können später ergänzt werden

## 7. Nächste Schritte (CRYPTO-38ff)

- Flags nutzen, um AUDIO / CONTROL / KEEPALIVE im Inbound-Flow differenziert zu behandeln
- Outbound-Pfad sauber auf FrameV1-Encrypt umstellen
- Vorbereitung JNI/Rust:
  - echte XChaCha20-Poly1305 / AES-GCM anstelle der Stub-Encrypt/Decrypt-Funktionen
