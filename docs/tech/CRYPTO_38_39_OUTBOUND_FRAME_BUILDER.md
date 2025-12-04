# CRYPTO-38/39 – FrameV1 Inbound/Outbound Pipeline

## 1. Inbound (Recall)

- decryptFrameV1(ctx, cipher) → liest HeaderV1
- FrameHeaderV1.parse(...) → VERSION, FLAGS, KEY_ID, NONCE_PREFIX
- FrameTypeResolver → FrameType (AUDIO / CONTROL / KEEPALIVE)
- FrameBodyParser:
  - AudioBodyParser
  - ControlBodyParser
  - KeepAliveBodyParser
- GhostMediaRouter:
  - val raw = decryptFrameV1(frame)
  - val body = extractBodyV1(raw)
  - val parsedBody = parseBody(type, body)

## 2. Outbound (CRYPTO-39)

Outbound wird über SessionCipherEngine + FrameHeaderUtils + MediaEncryptor gebaut:

1. `FrameHeaderUtils.flagsForFrameType(FrameType)`:
   - AUDIO → FrameFlags.AUDIO
   - CONTROL → FrameFlags.CONTROL
   - KEEPALIVE → FrameFlags.KEEPALIVE

2. `FrameHeaderUtils.encryptFrameV1ForType(ctx, type, body)`:
   - berechnet Flags
   - ruft `SessionCipherBinding.encryptFrameV1(ctx, body, flags)` auf

3. `MediaEncryptor` Outbound-Builder:
   - `buildAndEncryptAudioFrameV1(ctx, pcm)`:
     - FrameType.AUDIO
     - Body = PCM / später Opus
   - `buildAndEncryptControlFrameV1(ctx, code, text)`:
     - FrameType.CONTROL
     - Body = "code:text"
   - `buildAndEncryptKeepAliveFrameV1(ctx)`:
     - FrameType.KEEPALIVE
     - Body = leer

4. Debug:
   - `buildDummyCallInviteFrameV1(ctx)`:
     - 100:CALL-INVITE

## 3. TODO

- Audio-Path mit echtem Opus-Encoding verbinden
- CONTROL-Codes sauber definieren (Spec/Enum)
- KEEPALIVE-Body mit Sequenz/Timestamp füllen
- Outbound Network-Sender mit FrameV1-Builder verknüpfen
