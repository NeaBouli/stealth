"use strict";

const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const { v4: uuidv4 } = require("uuid");

const app = express();
const server = http.createServer(app);

// --- HTTP health endpoint ---
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    message: "SecureCall Signaling Server MVP (BACKEND-01)"
  });
});

// --- WebSocket Signaling MVP ---
const wss = new WebSocket.Server({ server, path: "/signal" });

wss.on("connection", (ws) => {
  const id = uuidv4();
  console.log("[SIGNAL] client connected:", id);

  ws.on("message", (msg) => {
    const text = msg.toString();
    console.log("[SIGNAL] message from", id, ":", text);
    // MVP: Echo zurück zum Client
    ws.send(text);
  });

  ws.on("close", () => {
    console.log("[SIGNAL] client disconnected:", id);
  });
});

// --- Start Server ---
const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log("[SIGNAL] server listening on port", PORT);
});
