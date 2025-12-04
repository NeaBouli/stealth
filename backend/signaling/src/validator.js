/**
 * BACKEND-08 — Message Schema Validator (MVP)
 *
 * Prüft eingehende WebSocket-Nachrichten:
 *  - JSON korrekt?
 *  - Pflichtfelder vorhanden?
 *  - Nachrichtentyp unterstützt?
 *
 * Nutzung:
 *   const { validateMessage } = require("./validator");
 *   const result = validateMessage(jsonObj);
 *
 * Rückgabe:
 *   { ok: true, msg }        → Nachricht gültig
 *   { ok: false, errorObj }  → Fehlerobjekt von makeError()
 */

const { makeError } = require("./errors");

const VALID_TYPES = ["REGISTER", "INVITE", "ANSWER", "END"];

function validateMessage(msg) {
  // 1. Muss Objekt sein
  if (typeof msg !== "object" || msg === null) {
    return {
      ok: false,
      errorObj: makeError("INVALID_FORMAT", "Message must be a JSON object")
    };
  }

  // 2. Nachrichtentyp prüfen
  if (!msg.type || typeof msg.type !== "string") {
    return {
      ok: false,
      errorObj: makeError("TYPE_MISSING", "Field 'type' is required")
    };
  }

  // 3. Unbekannter Typ
  if (!VALID_TYPES.includes(msg.type)) {
    return {
      ok: false,
      errorObj: makeError("UNKNOWN_TYPE", "Unknown message type", {
        provided: msg.type
      })
    };
  }

  // 4. Pflichtfelder je nach Typ
  switch (msg.type) {
    case "REGISTER":
      if (!msg.clientId) {
        return {
          ok: false,
          errorObj: makeError("INVALID_REGISTER", "Field 'clientId' is required")
        };
      }
      break;

    case "INVITE":
      if (!msg.from || !msg.to || !msg.sessionId) {
        return {
          ok: false,
          errorObj: makeError("INVALID_INVITE", "Missing fields in INVITE", {
            required: ["from", "to", "sessionId"]
          })
        };
      }
      break;

    case "ANSWER":
      if (!msg.sessionId || !msg.from || !msg.to) {
        return {
          ok: false,
          errorObj: makeError("INVALID_ANSWER", "Missing fields in ANSWER", {
            required: ["from", "to", "sessionId"]
          })
        };
      }
      break;

    case "END":
      if (!msg.sessionId) {
        return {
          ok: false,
          errorObj: makeError("INVALID_END", "Field 'sessionId' is required")
        };
      }
      break;
  }

  // everything ok
  return {
    ok: true,
    msg
  };
}

module.exports = {
  validateMessage
};
