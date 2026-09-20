/**
 * BACKEND-05 - Public Key Directory (PKD)
 *
 * Pseudonymes In-Memory-Verzeichnis fuer oeffentliche Schluessel.
 * Kein Name, keine E-Mail, keine Telefonnummer - nur Key-IDs.
 *
 * STX-08: Das Verzeichnis ist jetzt beschraenkt. Maximale Eintragszahl und TTL
 * kommen aus sicheren Defaults, ueber Env ueberschreibbar:
 *   PKD_MAX_ENTRIES  (Default 10000, gueltig 1..50000)
 *   PKD_TTL_MS       (Default 30 Tage, gueltig 60000..31536000000)
 * Ungueltige oder fehlende Werte fallen auf die Defaults zurueck; die
 * Ganzzahl-Parsung ist strikt - teilweise numerische Strings ("10junk"),
 * Dezimal-Strings, leere Strings, NaN und Unendlich sind ungueltig. Gueltige
 * Ganzzahlen ausserhalb der Schranken werden geklemmt - nie 0 oder unendlich.
 * Abgelaufene Eintraege werden deterministisch bei jedem Zugriff entfernt und
 * gelten ueberall als logisch nicht vorhanden (get/rotate/list liefern sie
 * nicht, delete liefert false); vor einer Einfuegung ueber der Obergrenze wird
 * der aelteste lebende Eintrag (Insertionsreihenfolge) verdraengt.
 *
 * Nutzung:
 *   const pkd = require("./pkd");
 *   const entry = pkd.registerKey("base64-public-key...");
 *   const key = pkd.getKey(entry.keyId);
 *
 * Tests nutzen die injizierbare Factory:
 *   const { createPkd } = require("./pkd");
 *   const store = createPkd({ maxEntries: 3, ttlMs: 1000, now: () => fakeNow });
 */

const crypto = require("crypto");

const DEFAULT_MAX_ENTRIES = 10000;
const MIN_MAX_ENTRIES = 1;
const HARD_MAX_ENTRIES = 50000;

const DEFAULT_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 Tage
const MIN_TTL_MS = 60 * 1000; // 1 Minute
const MAX_TTL_MS = 365 * 24 * 60 * 60 * 1000; // 1 Jahr

/**
 * Strikte Ganzzahl-Parsung: nur vollstaendig numerische Strings (optional
 * mit Vorzeichen) und endliche Ganzzahlen werden akzeptiert. Teilweise numerische
 * Strings ("10junk"), Dezimal-Strings ("10.5"), leere Strings, NaN und
 * Unendlich liefern null und fallen damit auf den Fallback zurueck.
 */
function parseStrictInt(value) {
  if (typeof value === "number") {
    return Number.isInteger(value) ? value : null;
  }
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!/^[+-]?\d+$/.test(trimmed)) return null;
  const parsed = Number(trimmed);
  return Number.isInteger(parsed) ? parsed : null;
}

/**
 * Klemmt einen konfigurierbaren Wert in [min, max]. Ungueltige Eingaben
 * (undefined, nicht ganzzahlig parsebar, NaN, nicht endlich) fallen auf
 * fallback zurueck; gueltige Ganzzahlen werden geklemmt.
 */
function clampBound(value, fallback, min, max) {
  const parsed = parseStrictInt(value);
  if (parsed === null) return fallback;
  return Math.min(Math.max(parsed, min), max);
}

function readPkdConfig(env) {
  const source = env || process.env;
  return {
    maxEntries: clampBound(source.PKD_MAX_ENTRIES, DEFAULT_MAX_ENTRIES, MIN_MAX_ENTRIES, HARD_MAX_ENTRIES),
    ttlMs: clampBound(source.PKD_TTL_MS, DEFAULT_TTL_MS, MIN_TTL_MS, MAX_TTL_MS)
  };
}

function createPkd(options) {
  const opts = options || {};
  const now = typeof opts.now === "function" ? opts.now : () => Date.now();
  // Factory-Schranken sichern nur gegen 0/unendlich ab, damit Tests kurze TTLs
  // injizieren koennen; die Env-Konfiguration (readPkdConfig) zieht zusaetzlich
  // strengere Betriebsgrenzen (MIN_TTL_MS).
  const maxEntries = clampBound(opts.maxEntries, DEFAULT_MAX_ENTRIES, 1, HARD_MAX_ENTRIES);
  const ttlMs = clampBound(opts.ttlMs, DEFAULT_TTL_MS, 1, MAX_TTL_MS);

  // In-Memory Store: keyId -> { keyId, publicKey, created, updated }
  // Insertionsreihenfolge = aeltester Eintrag zuerst (FIFO-Verdraengung).
  const keys = new Map();
  let lastObservedAt = Number.NEGATIVE_INFINITY;

  function currentTime() {
    const observed = now();
    if (!Number.isFinite(observed)) throw new TypeError("PKD clock must return a finite number");
    lastObservedAt = Math.max(lastObservedAt, observed);
    return lastObservedAt;
  }

  function isExpired(entry, at) {
    return at - entry.created >= ttlMs;
  }

  function pruneExpired(at) {
    // Entries are inserted with monotonic timestamps, so expired entries form
    // a prefix. Removing only that prefix keeps pruning amortized O(1) instead
    // of scanning the complete public map on every request.
    while (keys.size > 0) {
      const oldest = keys.entries().next().value;
      if (!oldest || !isExpired(oldest[1], at)) break;
      keys.delete(oldest[0]);
    }
  }

  /**
   * Registriert einen Public Key und gibt eine zufaellige 128-Bit Key-ID zurueck.
   * Entfernt vorher abgelaufene Eintraege und verdraengt noetigenfalls den
   * aeltesten lebenden Eintrag, damit der Store die Obergrenze nie ueberschreitet.
   */
  function registerKey(publicKey) {
    const at = currentTime();
    pruneExpired(at);
    while (keys.size >= maxEntries) {
      const oldest = keys.keys().next();
      if (oldest.done) break;
      keys.delete(oldest.value);
    }

    const keyId = crypto.randomBytes(16).toString("hex"); // 128-bit
    const entry = {
      keyId,
      publicKey,
      created: at,
      updated: at
    };

    keys.set(keyId, entry);
    return entry;
  }

  /**
   * Gibt den Key-Eintrag fuer eine Key-ID zurueck, oder null.
   * Abgelaufene Eintraege werden entfernt und als nicht gefunden behandelt.
   */
  function getKey(keyId) {
    const at = currentTime();
    pruneExpired(at);
    const entry = keys.get(keyId);
    if (!entry) return null;
    return entry;
  }

  /**
   * Rotiert den Public Key fuer eine bestehende Key-ID.
   * Gibt den aktualisierten Eintrag zurueck, oder null falls nicht gefunden
   * oder abgelaufen.
   */
  function rotateKey(keyId, newPublicKey) {
    const at = currentTime();
    pruneExpired(at);
    const entry = keys.get(keyId);
    if (!entry) return null;

    entry.publicKey = newPublicKey;
    entry.updated = at;

    return entry;
  }

  /**
   * Entfernt einen Key-Eintrag. Gibt true zurueck, wenn ein lebender Eintrag
   * gefunden wurde, sonst false. Ein abgelaufener Eintrag gilt als logisch
   * nicht vorhanden: er wird entfernt und liefert false.
   */
  function deleteKey(keyId) {
    const at = currentTime();
    pruneExpired(at);
    const entry = keys.get(keyId);
    if (!entry) return false;
    keys.delete(keyId);
    return true;
  }

  /**
   * Gibt alle lebenden Key-IDs zurueck (ohne die Schluessel selbst - nur fuer
   * Debug). Entfernt vorher abgelaufene Eintraege ueber die injizierte Uhr.
   */
  function listKeys() {
    pruneExpired(currentTime());
    return Array.from(keys.keys());
  }

  return {
    registerKey,
    getKey,
    rotateKey,
    deleteKey,
    listKeys,
    config: { maxEntries, ttlMs }
  };
}

// Produktions-Singleton: behaelt die bisherige Modul-API fuer bestehende Aufrufer.
const defaultStore = createPkd(readPkdConfig());

module.exports = {
  registerKey: defaultStore.registerKey,
  getKey: defaultStore.getKey,
  rotateKey: defaultStore.rotateKey,
  deleteKey: defaultStore.deleteKey,
  listKeys: defaultStore.listKeys,
  createPkd,
  readPkdConfig
};
