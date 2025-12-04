// BACKEND-24 — GhostNet Routing Core (MVP)
//
// Dieses Modul kapselt die Auswahl der Relays für GhostNet.
// Später können wir hier Regionen, Lastverteilung, Premium-Routing,
// Multi-Hop-Pfade usw. einbauen.

function getRelayHintsForSession(sessionId) {
  // MVP: statische Liste. Später:
  // - Geo-basiertes Routing
  // - Ausfallsicherheit
  // - Priorisierung nach Latenz
  return [
    { host: "relay1.securecall.local", port: 443 },
    { host: "relay2.securecall.local", port: 8443 }
  ];
}

module.exports = {
  getRelayHintsForSession
};
