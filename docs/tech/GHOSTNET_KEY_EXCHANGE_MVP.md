# GhostNet Signaling Key-Exchange MVP (PATCH 201)

Dieses MVP implementiert eine einfache Offer/Answer Struktur:

## 1. Messages
- **key-offer**: enthält Public Key A
- **key-answer**: enthält Public Key B

Format:
{
  "type": "key-offer",
  "pub": "<BASE64>"
}

## 2. Ablauf Outgoing
A sendet → key-offer  
B antwortet → key-answer  
Session handshake wird ausgeführt.

## 3. Ablauf Incoming
B empfängt offer → startet acceptIncomingHandshake()  
B sendet answer zurück.

## 4. MVP-Status
- keine echte Kryptografie
- keine Identitäten
- keine Replay-/Integrity-Checks

## 5. Nächste Schritte
- Signaling-Routing (Call-ID)
- Session-Protokoll (CALL_INIT, ACCEPT, BYE)
- Austausch von Nonces / PreKeys
- Noise/X3DH Übergang
