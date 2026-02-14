const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const { v4: uuidv4 } = require("uuid");

const HeartbeatManager = require("./heartbeat");

// --- App Setup ---
const app = express();
const server = http.createServer(app);

// --- Client Registry ---
// connId (UUID) -> { ws, lastSeen, clientId }
const clients = new Map();

// clientId (user-chosen) -> connId (reverse lookup)
const clientIds = new Map();

// --- Routing Table (Sessions) ---
const routingTable = new Map();
// sessionId -> { sessionId, from, to, state, created, updated }

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

// --- Routing Debug API ---
app.get("/routing/list", (req, res) => {
  res.json({
    routes: Array.from(routingTable.values())
  });
});

// --- Clients Debug API ---
app.get("/clients/list", (req, res) => {
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

// --- WebSocket Setup ---
const wss = new WebSocket.Server({ server, path: "/signal" });

wss.on("connection", (ws) => {
  const connId = uuidv4();
  console.log("[SIGNAL] connected:", connId);

  clients.set(connId, { ws, lastSeen: Date.now(), clientId: null });

  ws.on("pong", () => {
    hb.updateClient(connId);
  });

  ws.on("message", (data, isBinary) => {
    hb.updateClient(connId);

    // --- Binary frames (audio PCM): forward to peer ---
    if (isBinary) {
      if (!forwardBinaryToPeer(connId, data)) {
        // No active session — echo back as fallback (MVP)
        ws.send(data, { binary: true });
      }
      return;
    }

    let msg = null;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_json" }));
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

      // Prüfen ob clientId bereits vergeben
      if (clientIds.has(msg.clientId)) {
        const existingConnId = clientIds.get(msg.clientId);
        if (existingConnId !== connId && clients.has(existingConnId)) {
          return ws.send(JSON.stringify({
            type: "ERROR",
            error: "client_id_taken",
            message: `clientId '${msg.clientId}' is already registered`
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
      }
      client.clientId = msg.clientId;
      clientIds.set(msg.clientId, connId);

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

      // Prüfen ob Ziel online ist
      if (!clientIds.has(msg.to)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "peer_not_found",
          message: `Client '${msg.to}' is not online`
        }));
      }

      const sessionId = uuidv4();
      routingTable.set(sessionId, {
        sessionId,
        from: myClientId,
        to: msg.to,
        state: "INVITE",
        created: Date.now(),
        updated: Date.now()
      });

      console.log("[ROUTING] INVITE:", myClientId, "->", msg.to, "session:", sessionId);

      // Bestätigung an Caller
      ws.send(JSON.stringify({
        type: "CALL_INVITE_ACK",
        ok: true,
        sessionId,
        from: myClientId,
        to: msg.to
      }));

      // Weiterleiten an Empfänger
      sendToClient(msg.to, {
        type: "CALL_INVITE",
        sessionId,
        from: myClientId,
        to: msg.to
      });

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
          from: myClientId
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
        const peerClientId = getSessionPeer(msg.sessionId, myClientId);

        routingTable.delete(msg.sessionId);
        console.log("[ROUTING] END:", msg.sessionId, "by", myClientId);

        // Weiterleiten an Peer
        if (peerClientId) {
          sendToClient(peerClientId, {
            type: "CALL_END",
            sessionId: msg.sessionId,
            from: myClientId
          });
        }
      }

      return ws.send(JSON.stringify({
        type: "CALL_END_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // GHOST_PREPARE — GhostNet Pre-Handshake (BACKEND-23)
    // ===========================
    if (msg.type === "GHOST_PREPARE") {
      console.log("[GHOST] PREPARE received for session:", msg.sessionId);

      const ghostNetId = uuidv4();
      const reply = {
        type: "GHOST_ACK",
        sessionId: msg.sessionId,
        ghostNetId,
        relayHints: [
          { host: "relay1.securecall.local", port: 443 },
          { host: "relay2.securecall.local", port: 8443 }
        ]
      };

      ws.send(JSON.stringify(reply));
      console.log("[GHOST] ACK sent:", ghostNetId);
      return;
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

    console.log("[SIGNAL] disconnected:", connId, clientId ? `(${clientId})` : "");

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

// --- Start Server ---
const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log("[SIGNAL] listening on", PORT);
});
