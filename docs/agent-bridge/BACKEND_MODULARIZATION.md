# Backend Modularization Plan — STX-HIGH-03

Status: REVISED PLAN — basiert auf Codex-Gegenpruefung in `BRIDGE.md`, Abschnitt `STX-HIGH-03 — BACKEND_MODULARIZATION.md Gegenpruefung`.

Gesamtbewertung:
- PASS: Zielbild `server.js` als thin entry point ist sinnvoll.
- WARN: Refactor ist nur sicher mit sauberem State/Store/Context-Design.
- FAIL: Direkte Umsetzung des alten Plans wuerde wegen falschem State-Modell, fehlender WS-Dispatch-Schicht und Service-State-Vermischung wahrscheinlich Call-Routing, Aktivierung/Gifts, FCM oder Metrics brechen.

## Ziel

`backend/signaling/src/server.js` soll schrittweise von einem Monolithen zu einem kleinen Entry Point werden, ohne Verhalten zu aendern.

Der neue Entry Point besitzt nur noch:
- Express-App und HTTP-Server Erstellung
- Middleware-Mounting
- Route-Mounting
- WebSocket-Server Erstellung
- Context-Erstellung
- Startup/Shutdown Lifecycle

Alle fachlichen Routen, WebSocket-Message-Handler, Stores und Persistenzfunktionen werden in klar begrenzte Module verschoben.

## Nicht-Ziele

- Keine Verhaltensaenderung am Signaling-Protokoll.
- Keine Umbenennung der Client-Message-Typen.
- Keine Aenderung am Persistenzformat der JSON-Dateien.
- Keine Vermischung von Service-Modulen in `state.js`.
- Kein Split der WS-Handler ohne zentrale Vorverarbeitung.

## Neue Modulstruktur

```text
backend/signaling/src/
  server.js                    # thin entry point
  context.js                   # baut dependency/context object
  state.js                     # reiner mutable state, keine service imports

  config/
    data_dir.js                # DATA_DIR detection / writable fallback
    ice.js                     # ICE_SERVERS
    security.js                # ADMIN_API_KEY, ALLOWED_ORIGINS, limits, regex

  utils/
    json_store.js              # writeJsonAtomic + load/save helpers
    phone.js                   # normalizePhone, hashPhone
    sanitize.js                # sanitize

  middleware/
    cors.js
    admin.js                   # requireAdmin factory
    ip.js                      # getClientIp

  services/
    index.js                   # services context factory
    connections.js             # sendToClient, getClientId, getSessionPeer, forwardBinaryToPeer
    fcm_tokens.js              # fcm token load/save/set/delete/get
    activation_codes.js        # activation code load/save/redeem support
    gift_codes.js              # gift code load/save/create/delete/redeem support
    wallets.js                 # wallet mapping load/save/bind
    ifr.js                     # verifyIfrLock + RPC contracts
    broadcast.js               # lastBroadcast helpers
    rate_limits.js             # invite/checkout/ip attempt helpers, if separated

  routes/
    health.js                  # /, /health
    admin.js                   # /routing/list, /clients/list, /metrics, /admin/broadcast
    billing.js                 # /billing/verify-purchase
    invite.js                  # /invite/:secureId, /invite/accepted
    licenses.js                # /licenses/status, /admin/simulate-sale, /admin/reset-licenses
    pkd.js                     # /key/register, /key/:id
    siwe.js                    # /siwe/challenge, /siwe/verify
    stripe.js                  # stripe_handler setup wrapper
    subscription.js            # /subscription/status, /api/subscription/:clientId
    gifts.js                   # /admin/gift, /admin/gifts, delete gift
    checkout.js                # /stripe/create-dynamic-checkout

  ws/
    index.js                   # central dispatcher and connection lifecycle
    register.js                # REGISTER
    calls.js                   # CALL_INVITE, CALL_ACCEPT, CALL_BUSY, CALL_END
    webrtc.js                  # WEBRTC_OFFER, WEBRTC_ANSWER, ICE_CANDIDATE
    ghost.js                   # GHOST_PREPARE
    lookup.js                  # PHONE_LOOKUP, BATCH_PHONE_LOOKUP, ONLINE_STATUS_REQUEST
    activation.js              # ACTIVATE_CODE, VERIFY_IFR_LOCK
    misc.js                    # REGISTER_FCM_TOKEN, DEREGISTER, INVITE_ACCEPTED, HEARTBEAT
```

## Correct `state.js`

`state.js` is a pure mutable singleton. It must not import route, websocket, or service modules.

```javascript
// state.js
module.exports = {
  // WebSocket connection state
  clients: new Map(),            // connId -> { ws, lastSeen, clientId, ip, phoneNumber?, _phoneLookups?, ... }
  clientIds: new Map(),          // clientId -> connId
  routingTable: new Map(),       // sessionId -> { sessionId, from, to, state, created, updated }

  // Phone lookup/presence state
  phoneNumbers: new Map(),       // normalizedPhone -> clientId
  phoneHashes: new Map(),        // sha256(normalizedPhone) -> clientId

  // Push tokens
  fcmTokens: new Map(),          // clientId -> fcmToken

  // Connection/rate-limit state
  ipConnections: new Map(),      // ip -> active connection count
  rejectionTracker: new Map(),   // clientId -> { count, firstSeen, lastLogged }
  ipConnectionAttempts: new Map(), // ip -> attempt timestamps
  inviteRateLimits: new Map(),   // ip -> attempt timestamps
  checkoutRateLimits: new Map(), // ip -> attempt timestamps

  // Activation / purchases / gifts
  activationCodes: [],           // mutable array, never reassign outside state store
  codeUsageCount: new Map(),     // code -> usage count this process
  giftCodes: new Map(),          // giftCode -> gift metadata

  // Wallet / SIWE
  walletMappings: [],            // mutable array, never reassign outside wallet store
  siweChallenges: new Map(),     // nonce -> { deviceId, message, createdAt }

  // Broadcast
  lastBroadcast: {
    template_id: 8,
    icon: "All Clear",
    title: "All Clear",
    body: "All systems operational. No active alerts.",
    timestamp: new Date().toISOString(),
    active: false
  }
};
```

### State rules

- `state.js` exports data only.
- It does not export `subscriptions`, `customIds`, `pkd`, `fcm`, `licenses`, or Stripe handlers.
- It does not call `fs`, `ethers`, Firebase, or Express.
- Maps may be mutated in place.
- Arrays that are shared across modules must be mutated in place, not reassigned.

## Store Pattern for Reassign-Prone Variables

Current `server.js` has `let activationCodes = []` and `let walletMappings = []`. Reassigning these arrays after modules import them creates stale references.

The stores must mutate the `state` arrays in place:

```javascript
// services/activation_codes.js
function replaceActivationCodes(state, nextCodes) {
  state.activationCodes.splice(0, state.activationCodes.length, ...nextCodes);
}

function loadActivationCodes(ctx) {
  const loaded = readCodesFromDiskAndEnv(ctx);
  replaceActivationCodes(ctx.state, loaded);
}

function saveActivationCodes(ctx) {
  ctx.stores.json.write(ctx.paths.activationCodesFile, { codes: ctx.state.activationCodes });
}
```

```javascript
// services/wallets.js
function replaceWalletMappings(state, nextWallets) {
  state.walletMappings.splice(0, state.walletMappings.length, ...nextWallets);
}
```

For Maps, use `clear()` + `set()` during reload:

```javascript
function replaceGiftCodes(state, entries) {
  state.giftCodes.clear();
  for (const [code, data] of entries) state.giftCodes.set(code, data);
}
```

## Services Context Object

Every module receives dependencies through a context object. This avoids circular imports and hidden globals.

```javascript
// context.js
function createContext({ app, server, wss }) {
  const state = require("./state");
  const config = {
    dataDir,
    iceServers,
    security
  };

  const services = require("./services")({ state, config });

  return {
    app,
    server,
    wss,
    state,
    config,
    services,
    utils: {
      sanitize,
      normalizePhone,
      hashPhone
    }
  };
}
```

### Context ownership rules

- Route modules get `ctx` and mount routes: `mountAdminRoutes(ctx)`.
- WS modules get `ctx` through dispatcher and receive `{ ctx, ws, connId, msg }`.
- Service modules may depend on `state`, `config`, `utils`, and external service modules.
- Service modules must not import `server.js`.
- State must not import services.
- Route modules must not import WS modules.

## `ws/index.js` Central Dispatch Layer

A central WebSocket module is required. Submodules must not attach their own `ws.on("message")` listeners.

Responsibilities of `ws/index.js`:
- Create or receive `wss`.
- Own `wss.on("connection")`.
- Assign `connId`.
- Track `clients` and `ipConnections`.
- Register `pong`, `message`, `close` handlers.
- Run binary fast-path before JSON rate limits.
- Run JSON rate-limit.
- Parse JSON.
- Strip prototype-pollution keys.
- Dispatch by `msg.type` to handler maps.
- Send unknown-message errors.
- Perform close cleanup.

```javascript
// ws/index.js
const registerHandlers = require("./register");
const callHandlers = require("./calls");
const webrtcHandlers = require("./webrtc");
const ghostHandlers = require("./ghost");
const lookupHandlers = require("./lookup");
const activationHandlers = require("./activation");
const miscHandlers = require("./misc");

function buildHandlers(ctx) {
  return {
    ...registerHandlers(ctx),
    ...callHandlers(ctx),
    ...webrtcHandlers(ctx),
    ...ghostHandlers(ctx),
    ...lookupHandlers(ctx),
    ...activationHandlers(ctx),
    ...miscHandlers(ctx)
  };
}

function mountWebSocket(ctx) {
  const handlers = buildHandlers(ctx);

  ctx.wss.on("connection", (ws, req) => {
    const connId = ctx.services.connections.createConnection(ws, req);

    ws.on("pong", () => ctx.services.heartbeat.updateClient(connId));

    ws.on("message", (data, isBinary) => {
      ctx.services.heartbeat.updateClient(connId);

      if (isBinary) {
        return ctx.services.connections.handleBinary(connId, data);
      }

      if (!ctx.services.signalingRateLimit.registerEvent(connId)) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "rate_limited" }));
      }

      let msg;
      try {
        msg = JSON.parse(data.toString());
      } catch {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_json" }));
      }

      for (const key of ["__proto__", "constructor", "prototype"]) {
        if (Object.prototype.hasOwnProperty.call(msg, key)) delete msg[key];
      }

      const handler = handlers[msg.type];
      if (!handler) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "unknown_message_type",
          provided: msg.type
        }));
      }

      return handler({ ctx, ws, connId, msg });
    });

    ws.on("close", () => ctx.services.connections.closeConnection(connId));
  });
}
```

## WS Handler Module Shape

Each WS submodule exports a handler map factory.

```javascript
// ws/calls.js
module.exports = function callHandlers(ctx) {
  return {
    CALL_INVITE: ({ ws, connId, msg }) => { /* current CALL_INVITE logic */ },
    CALL_ACCEPT: ({ ws, connId, msg }) => { /* current CALL_ACCEPT logic */ },
    CALL_BUSY: ({ ws, connId, msg }) => { /* current CALL_BUSY logic */ },
    CALL_END: ({ ws, connId, msg }) => { /* current CALL_END logic */ }
  };
};
```

Handler modules should use services instead of directly reaching across modules:

- `ctx.services.connections.getClientId(connId)`
- `ctx.services.connections.sendToClient(clientId, payload)`
- `ctx.services.sessions.getPeer(sessionId, myClientId)`
- `ctx.services.fcmTokens.get(clientId)`
- `ctx.services.activationCodes.redeem(...)`

## HTTP Route Module Shape

Each route module exports a mount function.

```javascript
// routes/admin.js
module.exports = function mountAdminRoutes(ctx) {
  const { app, middleware, state, wss } = ctx;
  app.get("/metrics", middleware.requireAdmin, (req, res) => { /* ... */ });
};
```

Route modules receive `ctx`; they do not import `state.js` directly unless they are intentionally pure and context-free.

## Shared Store Boundaries

### `services/fcm_tokens.js`

Owns:
- `state.fcmTokens`
- `fcm_tokens.json`
- load/save
- set/delete/get

Used by:
- REGISTER supersede flow
- REGISTER_FCM_TOKEN
- DEREGISTER
- CALL_INVITE offline push
- INVITE_ACCEPTED
- Admin broadcast
- Metrics

### `services/activation_codes.js`

Owns:
- `state.activationCodes`
- `activation_codes.json`
- load/save
- env seed merge
- sold code merge
- activation code redeem checks

Used by:
- ACTIVATE_CODE
- Stripe setup route compatibility

### `services/gift_codes.js`

Owns:
- `state.giftCodes`
- `gift_codes.json`
- create/list/delete/redeem

Used by:
- Admin gift routes
- Billing verify-purchase
- ACTIVATE_CODE fallback

### `services/wallets.js`

Owns:
- `state.walletMappings`
- `wallets.json`
- load/save/bind/check

Used by:
- VERIFY_IFR_LOCK
- SIWE verify

### `services/connections.js`

Owns helper behavior around:
- `state.clients`
- `state.clientIds`
- `state.routingTable`
- `state.ipConnections`
- `sendToClient`
- `getClientId`
- `getSessionPeer`
- `forwardBinaryToPeer`
- close cleanup

## HTTP Routes Extraction Order

Start with routes that have minimal shared state.

1. `routes/health.js`
   - `/`
   - `/health`

2. `routes/pkd.js`
   - `/key/register`
   - `/key/:id`
   - Depends mostly on `pkd`, `requireAdmin`.

3. `routes/subscription.js`
   - `/api/subscription/:clientId`
   - `/subscription/status`
   - Depends on `subscriptions`, `ADMIN_API_KEY`/admin middleware.

4. `routes/licenses.js`
   - `/licenses/status`
   - `/admin/simulate-sale`
   - `/admin/reset-licenses`

5. `routes/admin.js`
   - `/routing/list`
   - `/clients/list`
   - `/ice-servers`
   - `/metrics`
   - Requires `state`, `wss`, `iceServers`.

6. Store-backed routes after stores exist:
   - `routes/gifts.js`
   - `routes/invite.js`
   - `routes/billing.js`
   - `routes/siwe.js`
   - `routes/checkout.js`

7. Existing external setup wrappers last:
   - Stripe webhook/payment handler wrapper
   - Custom ID route setup

## Safe Implementation Steps

### Phase 0 — Baseline Safety

- Run current tests before refactor.
- Capture `node --check backend/signaling/src/server.js` baseline.
- Capture one manual WS smoke path: REGISTER -> CALL_INVITE -> CALL_ACCEPT -> CALL_END.
- Do not change behavior and modularization in the same commit.

### Phase 1 — Extract Config and Utils

- Extract `DATA_DIR` detection into `config/data_dir.js`.
- Extract `ICE_SERVERS` into `config/ice.js`.
- Extract security constants into `config/security.js`.
- Extract `writeJsonAtomic` into `utils/json_store.js`.
- Extract `sanitize`, `normalizePhone`, `hashPhone` into `utils/`.

Verification:
- `node --check`.
- Server starts.
- `/health` returns OK.

### Phase 2 — Introduce `state.js` Without Moving Logic

- Create `state.js` with all Maps/arrays listed above.
- Replace local declarations in `server.js` with `const state = require("./state")` aliases.
- Keep all handlers in `server.js` for now.
- Do not move service modules yet.

Verification:
- `node --check`.
- REGISTER / reconnect / supersede smoke test.
- Metrics counts still correct.

### Phase 3 — Introduce Stores, Still Keep Routes/WS Mostly In Place

- Add `services/fcm_tokens.js`.
- Add `services/activation_codes.js` with in-place array mutation.
- Add `services/gift_codes.js`.
- Add `services/wallets.js`.
- Keep public behavior identical.

Verification:
- FCM token load/save file still works.
- Activation code redeem still updates JSON.
- Gift code redeem and admin gift routes still share the same Map.
- Wallet verify/SIWE still share walletMappings.

### Phase 4 — Extract Low-Risk HTTP Routes

Move only routes with simple dependencies:
- health
- pkd
- subscription
- licenses

Verification after each route module:
- `node --check`.
- Hit moved endpoints manually or via tests.

### Phase 5 — Extract Store-Backed HTTP Routes

Move:
- gifts
- invite
- billing
- siwe
- checkout
- admin metrics/broadcast last

Verification:
- Gift create/list/delete.
- Billing verify-purchase creates gift/activation code in shared store.
- Invite accepted verifies registered client and sends FCM/WS.
- SIWE challenge/verify still binds wallet.
- Metrics and broadcast still see live `wss` and state.

### Phase 6 — Introduce `services/connections.js`

Move:
- `sendToClient`
- `getClientId`
- `getSessionPeer`
- `forwardBinaryToPeer`
- close cleanup helper

Keep `ws.on("message")` still in `server.js` initially.

Verification:
- REGISTER.
- CALL_INVITE online.
- CALL_INVITE offline push path.
- CALL_ACCEPT.
- CALL_END.
- Disconnect cleanup sends peer `CALL_END`.

### Phase 7 — Add `ws/index.js` Central Dispatcher

- Move `wss.on("connection")` into `ws/index.js`.
- Keep message handlers initially in a single internal handler map if needed.
- Preserve exact ordering:
  1. heartbeat update
  2. binary fast-path
  3. JSON rate limit
  4. JSON parse
  5. prototype cleanup
  6. dispatch
  7. unknown fallback

Verification:
- Binary frame path does not hit JSON rate limit.
- Invalid JSON returns `invalid_json`.
- Unknown type returns `unknown_message_type`.
- Prototype keys are stripped.

### Phase 8 — Split WS Handler Maps

Move one group at a time:
1. `ws/misc.js`
2. `ws/webrtc.js`
3. `ws/lookup.js`
4. `ws/calls.js`
5. `ws/register.js`
6. `ws/activation.js`

Recommended order rationale:
- `register.js`, `calls.js`, and `activation.js` are the highest-coupling modules, so move them after dispatcher and services are proven.

Verification after each group:
- Message-specific smoke test.
- No handler should import `server.js`.
- No handler should attach `ws.on("message")`.

### Phase 9 — Thin `server.js`

Only after all modules pass:
- Remove old inline route and WS handler code.
- `server.js` should create app/server/wss, build context, mount modules, start listener, handle shutdown.

Verification:
- Full backend smoke suite.
- Manual app-to-app call.
- Railway deploy health.

## Circular Import Rules

Allowed dependency direction:

```text
server.js
  -> context.js
    -> state.js
    -> config/*
    -> utils/*
    -> services/*
    -> routes/*
    -> ws/index.js
      -> ws/*.js
```

Disallowed:

```text
state.js -> services/*
state.js -> routes/*
state.js -> ws/*
services/* -> server.js
routes/* -> server.js
ws/*.js -> server.js
routes/* -> ws/*
ws/*.js -> routes/*
```

## Required Regression Checklist

After each phase:
- `node --check backend/signaling/src/server.js`
- Existing signaling tests if available.
- `/health` HTTP 200.
- Admin auth rejects missing `x-admin-key`.

Before final merge:
- Android client REGISTER receives `REGISTERED` + `iceServers`.
- Online CALL_INVITE -> CALL_ACCEPT -> CALL_END.
- Offline CALL_INVITE push path.
- WEBRTC_OFFER / WEBRTC_ANSWER / ICE_CANDIDATE forwarding.
- REGISTER_FCM_TOKEN persists and reloads.
- ACTIVATE_CODE normal code path.
- ACTIVATE_CODE gift path.
- VERIFY_IFR_LOCK / SIWE wallet path.
- `/metrics` shows active `wss.clients.size`.
- `/admin/broadcast` sends WS and FCM paths.
- SIGTERM closes WebSocket clients before server close.

## Final Recommendation

Proceed only as a multi-commit refactor with tests after every phase. Do not start by splitting WS handlers. The safe first milestone is config/utils/state/store extraction while keeping behavior in `server.js`. Only after state and store boundaries are stable should routes and WS handlers move.
