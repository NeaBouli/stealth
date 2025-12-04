/**
 * BACKEND-06 – JSON Logger (MVP)
 *
 * Funktionen:
 *  - logJSON(event, data)
 *  - schreibt strukturierte JSON-Logs auf stdout
 *  - optionaler File-Write (append)
 */

const fs = require("fs");
const path = require("path");

// Logfile (optional; wird nur benutzt wenn LOG_TO_FILE=true)
const LOG_DIR = path.join(__dirname, "..", "logs");
const LOG_FILE = path.join(LOG_DIR, "signaling.log");

// Flag für File-Logging
const LOG_TO_FILE = process.env.LOG_TO_FILE === "true";

// falls Ordner fehlt → erzeugen
if (LOG_TO_FILE) {
  try {
    if (!fs.existsSync(LOG_DIR)) {
      fs.mkdirSync(LOG_DIR, { recursive: true });
    }
  } catch (e) {
    console.error("[LOGGER] failed to create log directory:", e.message);
  }
}

// Hilfsfunktion für JSON-Ausgabe
function logJSON(event, data = {}) {
  const entry = {
    ts: Date.now(),
    event,
    ...data
  };

  const line = JSON.stringify(entry);

  // stdout
  console.log(line);

  // Datei-Logging
  if (LOG_TO_FILE) {
    try {
      fs.appendFileSync(LOG_FILE, line + "\n");
    } catch (e) {
      console.error("[LOGGER] file write error:", e.message);
    }
  }
}

module.exports = { logJSON };
