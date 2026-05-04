# Backend Modularization Plan — STX-HIGH-03

Status: PLAN — nicht implementiert. Groesseres Refactoring fuer Post-Release.

## Aktueller Zustand

`backend/signaling/src/server.js` ist ein ~2160 Zeilen Monolith mit:
- Express HTTP Routes (admin, billing, invite, custom-id, stripe, SIWE, PKD)
- WebSocket Handlers (REGISTER, CALL_*, ICE, GHOST, PHONE_LOOKUP, etc.)
- Middleware (CORS, auth, rate limits)
- Shared State (Maps fuer clients, sessions, FCM tokens, etc.)
- Utility Functions (sanitize, IP extraction, etc.)

## Vorgeschlagene Modulstruktur

```
backend/signaling/src/
  server.js          — Thin entry point (~80 lines): Express + WS setup, module mounting
  state.js           — Shared state (all Maps/Sets exported)
  middleware.js       — CORS, requireAdmin, getClientIp, sanitize
  routes/
    admin.js         — /admin/*, /metrics, /routing, /clients
    billing.js       — /billing/verify-purchase
    invite.js        — /invite/:secureId, /invite/accepted
    licenses.js      — (existing) /licenses/status
    pkd.js           — /key/register, /key/:id
    siwe.js          — /siwe/challenge, /siwe/verify
    stripe.js        — (existing) stripe_handler.js
    subscription.js  — /subscription/status, /api/subscription/:clientId
  ws/
    register.js      — REGISTER + fork protection
    calls.js         — CALL_INVITE, CALL_ACCEPT, CALL_BUSY, CALL_END
    webrtc.js        — WEBRTC_OFFER, WEBRTC_ANSWER, ICE_CANDIDATE
    ghost.js         — GHOST_PREPARE, GHOST_ACK
    lookup.js        — PHONE_LOOKUP, BATCH_PHONE_LOOKUP, ONLINE_STATUS
    activation.js    — ACTIVATE_CODE, VERIFY_IFR_LOCK
    misc.js          — HEARTBEAT, DEREGISTER, REGISTER_FCM_TOKEN, INVITE_ACCEPTED
```

## Shared State (state.js)

```javascript
module.exports = {
  clients,          // connId -> ws
  clientIds,        // connId -> clientId
  routingTable,     // clientId -> connId
  phoneNumbers,     // phone -> clientId
  phoneHashes,      // hashedPhone -> clientId
  sessions,         // sessionId -> session
  fcmTokens,       // clientId -> fcmToken
  activationCodes, // array
  giftCodes,       // Map
  subscriptions,   // (module)
  customIds,       // (module)
}
```

## Implementierungsstrategie

1. `state.js` extrahieren (alle shared Maps)
2. `middleware.js` extrahieren
3. HTTP Routes einzeln auslagern (eine nach der anderen, testen nach jeder)
4. WS Handlers auslagern
5. server.js auf thin entry point reduzieren

## Risiken

- Jeder Schritt braucht vollstaendigen Regressionstest
- Shared State muss konsistent bleiben
- WS Handlers haben enge Kopplung an client/routing Maps

## Empfehlung

Nach v1.0.29 Release durchfuehren. Nicht waehrend des Release-Prozesses.
