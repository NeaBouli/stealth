// BACKEND-20 – Call Routing Layer
// ordnet jeder Call-Session einen Routing-Pfad zu (caller <-> callee)

const routing = new Map();

// Beispiel einer Routing-Struktur:
/*
{
  sessionId: "...",
  caller: "...",
  callee: "...",
  route: {
    hops: [ "direct" ],     // spaeter: ["relay-1","relay-2"]
    createdAt: 123456789,
    updatedAt: 123456999
  }
}
*/

function createRoute(sessionId, caller, callee) {
  const now = Date.now();

  const entry = {
    sessionId,
    caller,
    callee,
    route: {
      hops: ["direct"],   // spaeter konfigurierbar
      createdAt: now,
      updatedAt: now
    }
  };

  routing.set(sessionId, entry);
  return entry;
}

function updateRoute(sessionId, hopList) {
  if (!routing.has(sessionId)) return null;

  const entry = routing.get(sessionId);
  entry.route.hops = hopList;
  entry.route.updatedAt = Date.now();

  return entry;
}

function getRoute(sessionId) {
  return routing.get(sessionId) || null;
}

function deleteRoute(sessionId) {
  routing.delete(sessionId);
}

function listRoutes() {
  return Array.from(routing.values());
}

module.exports = {
  createRoute,
  updateRoute,
  getRoute,
  deleteRoute,
  listRoutes
};
