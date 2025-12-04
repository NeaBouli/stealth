// BACKEND-24 — GhostNet Transport Server (Stub)
//
// Dieser Stub dient als Platzhalter für den späteren GhostNet-Transport.
// Optionen für die Zukunft:
// - QUIC-basiertes Protokoll (über node-quic / eigene Lib / Go-Bridge)
// - WebRTC DataChannel (separater Signalisierung oder reuse)
// - Eigenes UDP/TCP Protokoll mit starker E2E-Verschlüsselung.
//
// Aktuell: nur Logging + Struktur.

const http = require("http");

function startGhostNetServer(options = {}) {
  const port = options.port || 9090;

  const server = http.createServer((req, res) => {
    // MVP: Nur Health-Check / Platzhalter
    if (req.url === "/health") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ status: "ok", component: "ghostnet-server-stub" }));
      return;
    }

    res.writeHead(404);
    res.end();
  });

  server.listen(port, () => {
    console.log("[GHOST] GhostNet server stub listening on port", port);
  });

  return server;
}

module.exports = {
  startGhostNetServer
};
