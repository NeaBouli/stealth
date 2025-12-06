// ghostnet_echo_server.js
//
// Minimaler WebSocket-Echo-Server für GhostNet-Tests.
// - Nimmt Binärdaten entgegen
// - Spiegelt sie 1:1 zurück
//
// Start (im backend-Verzeichnis):
//   npm install ws   # einmalig
//   node ghostnet_echo_server.js
//
// Standard-Port: 8080 (konfigurierbar via GHOSTNET_ECHO_PORT)

const WebSocket = require('ws');

const PORT = process.env.GHOSTNET_ECHO_PORT || 8080;

const wss = new WebSocket.Server({ port: PORT });

console.log("[GHOSTNET-ECHO] listening on port " + PORT);

wss.on('connection', (ws, req) => {
  const addr = req.socket.remoteAddress + ":" + req.socket.remotePort;
  console.log("[GHOSTNET-ECHO] client connected:", addr);

  ws.on('message', (data, isBinary) => {
    const len = data ? data.length : 0;
    console.log("[GHOSTNET-ECHO] received " + len + " bytes, echoing back");
    ws.send(data, { binary: isBinary === true });
  });

  ws.on('close', () => {
    console.log("[GHOSTNET-ECHO] client disconnected:", addr);
  });

  ws.on('error', (err) => {
    console.error("[GHOSTNET-ECHO] socket error:", err);
  });
});

wss.on('error', (err) => {
  console.error("[GHOSTNET-ECHO] server error:", err);
});
