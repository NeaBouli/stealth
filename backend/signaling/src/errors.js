/**
 * BACKEND-07 – Unified Error Object (MVP)
 *
 * Dieses Modul erzeugt standardisierte Fehlerobjekte.
 * Jeder Fehler besitzt folgende Struktur:
 *
 * {
 *   type: "ERROR",
 *   errorCode: "...",
 *   message: "...",
 *   details: {...optional}
 * }
 */

function makeError(errorCode, message, details = null) {
  const obj = {
    type: "ERROR",
    errorCode,
    message
  };

  if (details) {
    obj.details = details;
  }

  return obj;
}

module.exports = {
  makeError
};
