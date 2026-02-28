const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const { v4: uuidv4 } = require("uuid");

const HeartbeatManager = require("./heartbeat");
const pkd = require("./pkd");
const rateLimit = require("./rate_limit");
const subscriptions = require("./subscriptions");
const fcm = require("./fcm");

// Initialize Firebase Cloud Messaging
fcm.initFcm();

// --- STUN/TURN Configuration (BACKEND-02) ---
// SECURITY: TURN credentials should be set via environment variables
if (process.env.NODE_ENV === "production" && (!process.env.TURN_USER || !process.env.TURN_PASS)) {
  console.warn("[WARN] TURN_USER and TURN_PASS not set — TURN relay disabled. Set via Railway Dashboard.");
}

const ICE_SERVERS = [
  { urls: process.env.STUN_URL || "stun:stun.l.google.com:19302" },
  ...(process.env.TURN_URL ? [{
    urls: process.env.TURN_URL,
    username: process.env.TURN_USER,
    credential: process.env.TURN_PASS
  }] : [])
];

// --- Security Configuration ---
const ADMIN_API_KEY = process.env.ADMIN_API_KEY || null;
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || "").split(",").filter(Boolean);
const MAX_CONNS_PER_IP = parseInt(process.env.MAX_CONNS_PER_IP || "10", 10);
const CLIENT_ID_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;

// Per-IP connection tracking
const ipConnections = new Map();

// --- App Setup ---
const app = express();
app.use(express.json());

// CORS configuration
app.use((req, res, next) => {
  const allowedOrigins = ALLOWED_ORIGINS.length > 0 ? ALLOWED_ORIGINS : ["*"];
  const origin = req.headers.origin;
  if (allowedOrigins.includes("*") || allowedOrigins.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin || "*");
  }
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Admin-Key");
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

function sanitize(str) {
  if (typeof str !== "string") return "";
  return str.replace(/[<>"'&]/g, "").substring(0, 64);
}

const server = http.createServer(app);

// --- Client Registry ---
// connId (UUID) -> { ws, lastSeen, clientId }
const clients = new Map();

// clientId (user-chosen) -> connId (reverse lookup)
const clientIds = new Map();

// --- Routing Table (Sessions) ---
const routingTable = new Map();
// sessionId -> { sessionId, from, to, state, created, updated }

// --- FCM Token Storage ---
// clientId -> fcmToken
const fcmTokens = new Map();

// --- Phone Number Registry ---
// normalized phone number -> clientId
const phoneNumbers = new Map();

function normalizePhone(num) {
  if (typeof num !== "string") return "";
  return num.replace(/[^0-9+]/g, "");
}

// --- Helper: send JSON to a clientId ---
function sendToClient(clientId, payload) {
  const connId = clientIds.get(clientId);
  if (!connId) return false;

  const client = clients.get(connId);
  if (!client || client.ws.readyState !== WebSocket.OPEN) return false;

  client.ws.send(JSON.stringify(payload));
  return true;
}

// --- Helper: find clientId by connId ---
function getClientId(connId) {
  const client = clients.get(connId);
  return client ? client.clientId : null;
}

// --- Helper: find peer in session ---
function getSessionPeer(sessionId, myClientId) {
  const session = routingTable.get(sessionId);
  if (!session) return null;

  if (session.from === myClientId) return session.to;
  if (session.to === myClientId) return session.from;
  return null;
}

// --- Helper: send binary to peer ---
function forwardBinaryToPeer(connId, data) {
  const myClientId = getClientId(connId);
  if (!myClientId) return false;

  // Find an active session where this client is a participant
  for (const [, session] of routingTable) {
    if (session.state !== "ACTIVE") continue;

    let peerClientId = null;
    if (session.from === myClientId) peerClientId = session.to;
    else if (session.to === myClientId) peerClientId = session.from;
    else continue;

    const peerConnId = clientIds.get(peerClientId);
    if (!peerConnId) continue;

    const peer = clients.get(peerConnId);
    if (!peer || peer.ws.readyState !== WebSocket.OPEN) continue;

    peer.ws.send(data, { binary: true });
    return true;
  }
  return false;
}

// --- Heartbeat Manager starten ---
const hb = new HeartbeatManager(routingTable, clients);
hb.start();

// --- HTTP Endpoint ---
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    message: "SecureCall Signaling Server (Client IDs + Forwarding)"
  });
});

// --- Admin Auth Middleware ---
function requireAdmin(req, res, next) {
  if (!ADMIN_API_KEY) {
    return res.status(403).json({ error: "admin_api_disabled" });
  }
  const provided = req.headers["x-admin-key"] || req.query.admin_key;
  if (provided !== ADMIN_API_KEY) {
    return res.status(401).json({ error: "unauthorized" });
  }
  next();
}

// --- Routing Debug API (admin-only) ---
app.get("/routing/list", requireAdmin, (req, res) => {
  res.json({
    routes: Array.from(routingTable.values())
  });
});

// --- ICE Servers API (BACKEND-02) ---
app.get("/ice-servers", requireAdmin, (req, res) => {
  res.json({ iceServers: ICE_SERVERS });
});

// --- Clients Debug API (admin-only) ---
app.get("/clients/list", requireAdmin, (req, res) => {
  const list = [];
  for (const [connId, client] of clients) {
    list.push({
      connId,
      clientId: client.clientId || null,
      connected: client.ws.readyState === WebSocket.OPEN
    });
  }
  res.json({ clients: list });
});

// --- Public Key Directory API (BACKEND-05) ---
app.post("/key/register", (req, res) => {
  const { publicKey } = req.body || {};
  if (!publicKey || typeof publicKey !== "string") {
    return res.status(400).json({
      error: "missing_public_key",
      message: "Field 'publicKey' is required"
    });
  }
  if (publicKey.length > 256) {
    return res.status(400).json({ error: "public_key_too_large" });
  }

  const entry = pkd.registerKey(publicKey);
  res.status(201).json({ keyId: entry.keyId, publicKey: entry.publicKey, created: entry.created });
});

app.get("/key/:id", (req, res) => {
  const entry = pkd.getKey(req.params.id);
  if (!entry) {
    return res.status(404).json({ error: "key_not_found" });
  }
  res.json({ keyId: entry.keyId, publicKey: entry.publicKey, created: entry.created });
});

app.put("/key/:id", (req, res) => {
  const { publicKey } = req.body || {};
  if (!publicKey || typeof publicKey !== "string") {
    return res.status(400).json({
      error: "missing_public_key",
      message: "Field 'publicKey' is required"
    });
  }
  if (publicKey.length > 256) {
    return res.status(400).json({ error: "public_key_too_large" });
  }

  const entry = pkd.rotateKey(req.params.id, publicKey);
  if (!entry) {
    return res.status(404).json({ error: "key_not_found" });
  }
  res.json({ keyId: entry.keyId, publicKey: entry.publicKey, updated: entry.updated });
});

app.delete("/key/:id", (req, res) => {
  const deleted = pkd.deleteKey(req.params.id);
  if (!deleted) {
    return res.status(404).json({ error: "key_not_found" });
  }
  res.json({ ok: true });
});

// --- Subscription Admin API ---
app.get("/api/subscription/:clientId", (req, res) => {
  const adminKey = req.headers["x-admin-key"];
  if (adminKey !== process.env.ADMIN_KEY && adminKey !== "dev-admin-key") {
    return res.status(403).json({ error: "Forbidden" });
  }
  const sub = subscriptions.getSubscription(req.params.clientId);
  if (!sub) {
    return res.status(404).json({ error: "No subscription found" });
  }
  res.json(sub);
});

// --- WebSocket Setup ---
const wss = new WebSocket.Server({
  server,
  path: "/signal",
  maxPayload: 64 * 1024, // 64 KB max message size
  verifyClient: (info, done) => {
    // Origin validation
    if (ALLOWED_ORIGINS.length > 0) {
      const origin = info.origin || info.req.headers.origin;
      if (!origin || !ALLOWED_ORIGINS.includes(origin)) {
        return done(false, 403, "Origin not allowed");
      }
    }
    // Per-IP connection limit
    const ip = info.req.socket.remoteAddress;
    const count = ipConnections.get(ip) || 0;
    if (count >= MAX_CONNS_PER_IP) {
      return done(false, 429, "Too many connections from this IP");
    }
    done(true);
  }
});

wss.on("connection", (ws, req) => {
  const connId = uuidv4();
  const ip = req.socket.remoteAddress;
  console.log("[SIGNAL] connected:", connId, "ip:", ip);

  // Track per-IP connections
  ipConnections.set(ip, (ipConnections.get(ip) || 0) + 1);

  clients.set(connId, { ws, lastSeen: Date.now(), clientId: null, ip });

  ws.on("pong", () => {
    hb.updateClient(connId);
  });

  ws.on("message", (data, isBinary) => {
    hb.updateClient(connId);

    // --- Binary frames (audio relay): handle before rate limit ---
    // Binary audio at 50fps would instantly exhaust the signaling rate
    // limit (40/10s). forwardBinaryToPeer() already validates: registered
    // client + ACTIVE session + valid peer.
    if (isBinary) {
      if (!rateLimit.registerBinaryEvent(connId)) {
        return; // silently drop — client flooding binary frames
      }
      if (!forwardBinaryToPeer(connId, data)) {
        // Silently drop — no active session or peer not connected
      }
      return;
    }

    // Rate limiting (JSON signaling messages only)
    if (!rateLimit.registerEvent(connId)) {
      ws.send(JSON.stringify({ type: "ERROR", error: "rate_limited" }));
      return;
    }

    let msg = null;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_json" }));
    }

    if (msg.__proto__ || msg.constructor !== undefined) {
      delete msg.__proto__;
      delete msg.constructor;
      delete msg.prototype;
    }

    // ===========================
    // REGISTER — Client ID zuweisen
    // ===========================
    if (msg.type === "REGISTER") {
      if (!msg.clientId || typeof msg.clientId !== "string") {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_client_id",
          message: "Field 'clientId' is required"
        }));
      }

      if (!CLIENT_ID_REGEX.test(msg.clientId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "invalid_client_id",
          message: "clientId must be 1-64 alphanumeric characters, hyphens, or underscores"
        }));
      }

      // TODO: Implement challenge-response auth using PKD public keys
      // For now, accept registration if clientId is valid and not taken

      // Prüfen ob clientId bereits vergeben
      if (clientIds.has(msg.clientId)) {
        const existingConnId = clientIds.get(msg.clientId);
        if (existingConnId !== connId && clients.has(existingConnId)) {
          return ws.send(JSON.stringify({
            type: "ERROR",
            error: "client_id_taken",
            message: `clientId '${sanitize(msg.clientId)}' is already registered`
          }));
        }
        // Alte Zuordnung aufräumen falls connId nicht mehr existiert
        clientIds.delete(msg.clientId);
      }

      // Registrieren
      const client = clients.get(connId);
      // Falls vorher schon eine andere clientId registriert war, alte entfernen
      if (client.clientId) {
        clientIds.delete(client.clientId);
        // Clean up old phone mapping for previous clientId — only if it still belongs to THIS client
        if (client.phoneNumber) {
          if (phoneNumbers.get(client.phoneNumber) === client.clientId || phoneNumbers.get(client.phoneNumber) === msg.clientId) {
            phoneNumbers.delete(client.phoneNumber);
          }
        }
      }
      client.clientId = msg.clientId;
      clientIds.set(msg.clientId, connId);

      // Store phone number if provided
      const phone = normalizePhone(msg.phoneNumber);
      if (phone.length >= 4) {
        // Remove any OTHER phone that currently maps to this clientId (phone number changed)
        for (const [existingPhone, existingClientId] of phoneNumbers) {
          if (existingClientId === msg.clientId && existingPhone !== phone) {
            phoneNumbers.delete(existingPhone);
            break;
          }
        }
        phoneNumbers.set(phone, msg.clientId);
        client.phoneNumber = phone;
        console.log("[REGISTER] Phone:", phone, "->", msg.clientId);
      }

      console.log("[REGISTER]", msg.clientId, "->", connId);
      return ws.send(JSON.stringify({
        type: "REGISTERED",
        clientId: msg.clientId
      }));
    }

    // ===========================
    // CALL_INVITE — Anruf starten + an Empfänger weiterleiten
    // ===========================
    if (msg.type === "CALL_INVITE") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending CALL_INVITE"
        }));
      }

      if (!msg.to) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_to",
          message: "Field 'to' is required"
        }));
      }

      const sessionId = uuidv4();

      // Resolve target: try clientId first, then phone number lookup
      let targetClientId = msg.to;
      if (!clientIds.has(targetClientId)) {
        const phoneLookup = phoneNumbers.get(normalizePhone(targetClientId));
        if (phoneLookup) {
          console.log("[ROUTING] Phone resolved:", targetClientId, "->", phoneLookup);
          targetClientId = phoneLookup;
        }
      }

      // Check if peer is online
      if (clientIds.has(targetClientId)) {
        // Peer is online — normal flow
        routingTable.set(sessionId, {
          sessionId,
          from: myClientId,
          to: targetClientId,
          state: "INVITE",
          created: Date.now(),
          updated: Date.now()
        });

        console.log("[ROUTING] INVITE:", myClientId, "->", targetClientId, "session:", sessionId);

        ws.send(JSON.stringify({
          type: "CALL_INVITE_ACK",
          ok: true,
          sessionId,
          from: myClientId,
          to: targetClientId
        }));

        sendToClient(targetClientId, {
          type: "CALL_INVITE",
          sessionId,
          from: myClientId,
          to: targetClientId,
          pubKey: msg.pubKey
        });
      } else {
        // Peer is offline — try FCM push
        const fcmToken = fcmTokens.get(msg.to);
        if (fcmToken && fcm.isInitialized()) {
          routingTable.set(sessionId, {
            sessionId,
            from: myClientId,
            to: msg.to,
            state: "INVITE_PENDING_PUSH",
            created: Date.now(),
            updated: Date.now()
          });

          console.log("[ROUTING] INVITE (offline, sending push):", myClientId, "->", msg.to, "session:", sessionId);

          fcm.sendCallInvitePush(fcmToken, sessionId, myClientId);

          ws.send(JSON.stringify({
            type: "CALL_INVITE_ACK",
            ok: true,
            sessionId,
            from: myClientId,
            to: msg.to,
            pushSent: true
          }));
        } else {
          return ws.send(JSON.stringify({
            type: "ERROR",
            error: "peer_not_found",
            message: `Client '${sanitize(msg.to)}' is not online`
          }));
        }
      }

      return;
    }

    // ===========================
    // CALL_ACCEPT — Anruf annehmen + an Caller weiterleiten
    // ===========================
    if (msg.type === "CALL_ACCEPT") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending CALL_ACCEPT"
        }));
      }

      const session = routingTable.get(msg.sessionId);
      if (!session) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      // Verify sender is the intended callee
      if (session.to !== myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_callee",
          message: "Only the intended callee can accept this call"
        }));
      }

      session.state = "ACTIVE";
      session.updated = Date.now();

      console.log("[ROUTING] ACCEPT:", msg.sessionId, "by", myClientId);

      // Bestätigung an Callee
      ws.send(JSON.stringify({
        type: "CALL_ACCEPT_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));

      // Weiterleiten an Caller
      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "CALL_ACCEPT",
          sessionId: msg.sessionId,
          from: myClientId,
          pubKey: msg.pubKey
        });
      }

      return;
    }

    // ===========================
    // CALL_END — Anruf beenden + an Peer weiterleiten
    // ===========================
    if (msg.type === "CALL_END") {
      const myClientId = getClientId(connId);

      if (msg.sessionId && routingTable.has(msg.sessionId)) {
        const session = routingTable.get(msg.sessionId);
        // Verify sender is a participant
        if (session.from !== myClientId && session.to !== myClientId) {
          return ws.send(JSON.stringify({
            type: "ERROR",
            error: "not_participant",
            message: "Only call participants can end this call"
          }));
        }

        const peerClientId = getSessionPeer(msg.sessionId, myClientId);

        // Forward to peer BEFORE deleting session (prevents race condition)
        if (peerClientId) {
          sendToClient(peerClientId, {
            type: "CALL_END",
            sessionId: msg.sessionId,
            from: myClientId
          });
        }

        routingTable.delete(msg.sessionId);
        console.log("[ROUTING] END:", msg.sessionId, "by", myClientId);
      }

      return ws.send(JSON.stringify({
        type: "CALL_END_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // WEBRTC_OFFER — SDP Offer an Peer weiterleiten (BACKEND-02)
    // ===========================
    if (msg.type === "WEBRTC_OFFER") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      if (!msg.sdp) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_sdp",
          message: "Field 'sdp' is required for WEBRTC_OFFER"
        }));
      }

      if (typeof msg.sdp !== "string" || msg.sdp.length > 10000) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_sdp" }));
      }

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "WEBRTC_OFFER",
          sessionId: msg.sessionId,
          from: myClientId,
          sdp: msg.sdp
        });
        console.log("[WEBRTC] OFFER:", myClientId, "->", peerClientId);
      }

      return ws.send(JSON.stringify({
        type: "WEBRTC_OFFER_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // WEBRTC_ANSWER — SDP Answer an Peer weiterleiten (BACKEND-02)
    // ===========================
    if (msg.type === "WEBRTC_ANSWER") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      if (!msg.sdp) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_sdp",
          message: "Field 'sdp' is required for WEBRTC_ANSWER"
        }));
      }

      if (typeof msg.sdp !== "string" || msg.sdp.length > 10000) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_sdp" }));
      }

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "WEBRTC_ANSWER",
          sessionId: msg.sessionId,
          from: myClientId,
          sdp: msg.sdp
        });
        console.log("[WEBRTC] ANSWER:", myClientId, "->", peerClientId);
      }

      return ws.send(JSON.stringify({
        type: "WEBRTC_ANSWER_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // ICE_CANDIDATE — ICE-Kandidat an Peer weiterleiten (BACKEND-02)
    // ===========================
    if (msg.type === "ICE_CANDIDATE") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      if (!msg.candidate) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_candidate",
          message: "Field 'candidate' is required for ICE_CANDIDATE"
        }));
      }

      if (typeof msg.candidate !== "object" && typeof msg.candidate !== "string") {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_candidate" }));
      }

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "ICE_CANDIDATE",
          sessionId: msg.sessionId,
          from: myClientId,
          candidate: msg.candidate
        });
      }

      return ws.send(JSON.stringify({
        type: "ICE_CANDIDATE_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // GHOST_PREPARE — GhostNet Pre-Handshake (BACKEND-23)
    // ===========================
    if (msg.type === "GHOST_PREPARE") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending GHOST_PREPARE"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      console.log("[GHOST] PREPARE received for session:", msg.sessionId);

      const ghostNetId = uuidv4();
      const reply = {
        type: "GHOST_ACK",
        sessionId: msg.sessionId,
        ghostNetId,
        iceServers: ICE_SERVERS,
        relayHints: [
          { host: "relay1.securecall.local", port: 443 },
          { host: "relay2.securecall.local", port: 8443 }
        ]
      };

      ws.send(JSON.stringify(reply));
      console.log("[GHOST] ACK sent:", ghostNetId);
      return;
    }

    // ===========================
    // REGISTER_FCM_TOKEN — Store FCM token for push notifications
    // ===========================
    if (msg.type === "REGISTER_FCM_TOKEN") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending REGISTER_FCM_TOKEN"
        }));
      }

      if (!msg.fcmToken || typeof msg.fcmToken !== "string") {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_fcm_token"
        }));
      }

      fcmTokens.set(myClientId, msg.fcmToken);
      console.log("[FCM] Token registered for:", myClientId);

      return ws.send(JSON.stringify({
        type: "REGISTER_FCM_TOKEN_ACK",
        ok: true
      }));
    }

    // ===========================
    // SUBSCRIPTION_VERIFY — Verify and store subscription
    // ===========================
    if (msg.type === "SUBSCRIPTION_VERIFY") {
      const { purchaseToken, productId } = msg;
      if (!purchaseToken || !productId) {
        ws.send(JSON.stringify({ type: "ERROR", message: "Missing purchaseToken or productId" }));
        return;
      }
      const result = subscriptions.verifySubscription(connId, purchaseToken, productId);
      ws.send(JSON.stringify({
        type: "SUBSCRIPTION_VERIFY_ACK",
        tier: result.tier,
        expiresAt: result.expiresAt
      }));
      console.log(`[SUBSCRIPTION] Verified: connId=${connId}, tier=${result.tier}`);
      return;
    }

    // ===========================
    // PHONE_LOOKUP — Resolve phone number to clientId
    // ===========================
    if (msg.type === "PHONE_LOOKUP") {
      if (!msg.phoneNumber || typeof msg.phoneNumber !== "string") {
        return ws.send(JSON.stringify({
          type: "PHONE_LOOKUP_RESULT",
          phoneNumber: "",
          clientId: null,
          online: false
        }));
      }

      const normalized = normalizePhone(msg.phoneNumber);
      const resolvedClientId = phoneNumbers.get(normalized) || null;
      const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;

      return ws.send(JSON.stringify({
        type: "PHONE_LOOKUP_RESULT",
        phoneNumber: msg.phoneNumber,
        clientId: resolvedClientId,
        online
      }));
    }

    // ===========================
    // BATCH_PHONE_LOOKUP — Resolve multiple phone numbers at once
    // ===========================
    if (msg.type === "BATCH_PHONE_LOOKUP") {
      const phoneList = Array.isArray(msg.phoneNumbers) ? msg.phoneNumbers : [];
      const results = phoneList.slice(0, 200).map(phone => {
        const normalized = normalizePhone(phone);
        const resolvedClientId = phoneNumbers.get(normalized) || null;
        const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
        return { phoneNumber: phone, clientId: resolvedClientId, online };
      });
      return ws.send(JSON.stringify({
        type: "BATCH_PHONE_LOOKUP_RESULT",
        results
      }));
    }

    // HEARTBEAT — client keepalive, reply so client's onMessage updates lastSeen
    if (msg.type === "HEARTBEAT") {
      return ws.send(JSON.stringify({ type: "HEARTBEAT_ACK" }));
    }

    // Fallback: unknown message type
    ws.send(JSON.stringify({
      type: "ERROR",
      error: "unknown_message_type",
      provided: msg.type
    }));
  });

  ws.on("close", () => {
    const client = clients.get(connId);
    const clientId = client ? client.clientId : null;
    const clientIp = client ? client.ip : null;

    console.log("[SIGNAL] disconnected:", connId, clientId ? `(${clientId})` : "");

    // Decrement per-IP connection count
    if (clientIp) {
      const count = ipConnections.get(clientIp) || 1;
      if (count <= 1) {
        ipConnections.delete(clientIp);
      } else {
        ipConnections.set(clientIp, count - 1);
      }
    }

    // Clean up rate limit bucket
    rateLimit.clear(connId);

    // Clean up phone number mapping — only if this phone still maps to THIS client
    if (client && client.phoneNumber) {
      if (phoneNumbers.get(client.phoneNumber) === clientId) {
        phoneNumbers.delete(client.phoneNumber);
      }
    }

    // clientId Mapping aufräumen
    if (clientId) {
      clientIds.delete(clientId);
    }
    clients.delete(connId);

    // Sessions aufräumen wo dieser Client beteiligt war
    if (clientId) {
      for (const [sessionId, session] of routingTable) {
        if (session.from === clientId || session.to === clientId) {
          // Peer benachrichtigen
          const peerId = session.from === clientId ? session.to : session.from;
          sendToClient(peerId, {
            type: "CALL_END",
            sessionId,
            reason: "peer_disconnected"
          });
          routingTable.delete(sessionId);
          console.log("[ROUTING] Session cleaned up (disconnect):", sessionId);
        }
      }
    }
  });
});

// --- Production Error Handling ---
process.on('unhandledRejection', (reason, promise) => {
  console.error('[ERROR] Unhandled Rejection at:', promise, 'reason:', reason);
});

process.on('uncaughtException', (error) => {
  console.error('[ERROR] Uncaught Exception:', error);
  process.exit(1);
});

// --- Health Check Endpoint ---
app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    uptime: Math.round(process.uptime()),
    timestamp: new Date().toISOString()
  });
});

// --- Metrics Endpoint ---
app.get("/metrics", (req, res) => {
  const used = process.memoryUsage();
  res.json({
    memory: {
      rss: Math.round(used.rss / 1024 / 1024) + " MB",
      heapTotal: Math.round(used.heapTotal / 1024 / 1024) + " MB",
      heapUsed: Math.round(used.heapUsed / 1024 / 1024) + " MB"
    },
    uptime: Math.round(process.uptime()) + " seconds",
    activeConnections: wss.clients.size,
    registeredClients: clientIds.size,
    activeSessions: routingTable.size
  });
});

// --- Start Server ---
const PORT = process.env.PORT || 8080;
server.listen(PORT, "0.0.0.0", () => {
  console.log(`[SIGNAL] Server running on port ${PORT}`);
  console.log(`[SIGNAL] WebSocket endpoint: ws://0.0.0.0:${PORT}/signal`);
  console.log(`[SIGNAL] Health check: http://0.0.0.0:${PORT}/health`);
});

// --- Graceful Shutdown ---
process.on('SIGTERM', () => {
  console.log('[SIGNAL] SIGTERM received, shutting down gracefully');
  server.close(() => {
    console.log('[SIGNAL] Server closed');
    process.exit(0);
  });
});
