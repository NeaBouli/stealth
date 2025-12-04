const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const { v4: uuidv4 } = require("uuid");

const HeartbeatManager = require("./heartbeat");

// --- App Setup ---
const app = express();
const server = http.createServer(app);

// --- Client Registry ---
const clients = new Map(); 
// Struktur: { id: { ws, lastSeen } }

// --- Routing Table (Sessions) ---
const routingTable = new Map();
// Struktur: { sessionId: { from, to, state, created, updated } }

// --- Heartbeat Manager starten ---
const hb = new HeartbeatManager(routingTable, clients);
hb.start();

// --- HTTP Endpoint ---
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    message: "SecureCall Signaling Server MVP (BACKEND-21)"
  });
});

// --- Routing Debug API ---
app.get("/routing/list", (req, res) => {
  res.json({
    routes: Array.from(routingTable.values())
  });
});

// --- WebSocket Setup ---
const wss = new WebSocket.Server({ server, path: "/signal" });

wss.on("connection", (ws) => {
  const id = uuidv4();
  console.log("[SIGNAL] connected:", id);

  clients.set(id, { ws, lastSeen: Date.now() });

  ws.on("pong", () => {
    hb.updateClient(id);
  });

  ws.on("message", (data) => {
    hb.updateClient(id);

    let msg = null;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return ws.send(JSON.stringify({ error: "invalid_json" }));
    }

    // --- Message Types ---
    if (msg.type === "CALL_INVITE") {
      const sessionId = uuidv4();

      routingTable.set(sessionId, {
        sessionId,
        from: id,
        to: msg.to || "unknown",
        state: "INVITE",
        created: Date.now(),
        updated: Date.now()
      });

      console.log("[ROUTING] INVITE:", sessionId);
      return ws.send(JSON.stringify({ ok: true, sessionId }));
    }

    if (msg.type === "CALL_ACCEPT") {
      const s = routingTable.get(msg.sessionId);
      if (!s) return ws.send(JSON.stringify({ error: "session_not_found" }));

      s.state = "ACTIVE";
      s.updated = Date.now();

      console.log("[ROUTING] ACCEPT:", msg.sessionId);
      return ws.send(JSON.stringify({ ok: true }));
    }

    if (msg.type === "CALL_END") {
      if (routingTable.has(msg.sessionId)) {
        routingTable.delete(msg.sessionId);
        console.log("[ROUTING] END:", msg.sessionId);
      }
      return ws.send(JSON.stringify({ ok: true }));
    }

    // Fallback echo (MVP)
    ws.send(data.toString());
  });

  ws.on("close", () => {
    console.log("[SIGNAL] disconnected:", id);
    clients.delete(id);
  });
});

// --- Start Server ---
const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log("[SIGNAL] listening on", PORT);
});

// BACKEND-22: Logging für CALL_INVITE Sessions
wss.on("connection", (ws) => {
  // Patch wird nur angehängt, nicht überschrieben
  ws.on("message", (data) => {
    let msg = null;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return;
    }

    if (msg.type === "CALL_INVITE") {
      console.log("[CALL] INVITE received, session prepared");
    }
  });
});

// BACKEND-22: Verbessertes Logging für CALL_END
wss.on("connection", (ws) => {
  ws.on("message", (raw) => {
    let msg = {};
    try { msg = JSON.parse(raw.toString()); } catch (_) {}

    if (msg.type === "CALL_END") {
      console.log("[CALL] END received for session:", msg.sessionId);
    }
  });
});

// BACKEND-23: GhostNet Pre-Handshake Handler
wss.on("connection", (ws) => {
  ws.on("message", (raw) => {
    let msg = {};
    try { msg = JSON.parse(raw.toString()); } catch { return; }

    // --- 1) GHOST_PREPARE empfangen ---
    if (msg.type === "GHOST_PREPARE") {
      console.log("[GHOST] PREPARE received for session:", msg.sessionId);

      const ghostNetId = require("uuid").v4();

      // --- 2) GhostNet Routing Info vorbereiten ---
      const reply = {
        type: "GHOST_ACK",
        sessionId: msg.sessionId,
        ghostNetId: ghostNetId,
        relayHints: [
          { host: "relay1.securecall.local", port: 443 },
          { host: "relay2.securecall.local", port: 8443 }
        ]
      };

      ws.send(JSON.stringify(reply));
      console.log("[GHOST] ACK sent:", ghostNetId);
    }

  });
});
