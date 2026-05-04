const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const { ethers } = require("ethers");

const HeartbeatManager = require("./heartbeat");
const pkd = require("./pkd");
const rateLimit = require("./rate_limit");
const subscriptions = require("./subscriptions");
const fcm = require("./fcm");
const customIds = require("./custom_ids");

// Atomic JSON write: writes to .tmp then renames (POSIX rename is atomic).
// Prevents corruption when multiple handlers write concurrently or process
// is killed mid-write. (Fix CRIT-003, 2026-04-16.)
function writeJsonAtomic(targetFile, data) {
  const tmp = targetFile + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), "utf8");
  fs.renameSync(tmp, targetFile);
}

// Initialize Firebase Cloud Messaging
fcm.initFcm();

// --- STUN/TURN Configuration (BACKEND-02) ---
// SECURITY: TURN credentials should be set via environment variables
if (process.env.NODE_ENV === "production" && (!process.env.TURN_USER || !process.env.TURN_PASS)) {
  console.warn("[WARN] TURN_USER and TURN_PASS not set — TURN relay disabled. Set via Railway Dashboard.");
}

const ICE_SERVERS = [
  { urls: "stun:stun.relay.metered.ca:80" },
  { urls: process.env.STUN_URL || "stun:stun.l.google.com:19302" },
  ...(process.env.TURN_USER && process.env.TURN_PASS ? [
    // UDP TURN (standard, fastest)
    { urls: "turn:a.relay.metered.ca:80?transport=udp",
      username: process.env.TURN_USER, credential: process.env.TURN_PASS },
    // TCP TURN (works through most firewalls)
    { urls: "turn:a.relay.metered.ca:80?transport=tcp",
      username: process.env.TURN_USER, credential: process.env.TURN_PASS },
    // TCP TURN on port 443 (works through VPNs + strict firewalls)
    { urls: "turn:a.relay.metered.ca:443?transport=tcp",
      username: process.env.TURN_USER, credential: process.env.TURN_PASS },
    // TLS TURN on port 443 (maximum VPN compatibility)
    { urls: "turns:a.relay.metered.ca:443?transport=tcp",
      username: process.env.TURN_USER, credential: process.env.TURN_PASS }
  ] : [])
];

// --- Security Configuration ---
const ADMIN_API_KEY = process.env.ADMIN_API_KEY || null;
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || "").split(",").filter(Boolean);
const MAX_CONNS_PER_IP = parseInt(process.env.MAX_CONNS_PER_IP || "10", 10);
const CLIENT_ID_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;

// Per-IP connection tracking
const ipConnections = new Map();

// Per-clientId rejection tracking — prevent reconnect spam from rejected clients
// Maps clientId -> { count, firstSeen, lastLogged }
const rejectionTracker = new Map();

// Per-IP connection attempt tracking (sliding 60s window) — anti-spam
const ipConnectionAttempts = new Map();

// Extract real client IP behind proxy (Railway, nginx, etc.)
// Only trusts X-Forwarded-For in production (behind known reverse proxy).
function getClientIp(req) {
  if (process.env.TRUST_PROXY === "true" || process.env.RAILWAY_ENVIRONMENT) {
    const xff = req.headers["x-forwarded-for"];
    if (xff) {
      // First IP in chain is the original client
      const clientIp = xff.split(",")[0].trim();
      if (clientIp) return clientIp;
    }
  }
  return req.socket.remoteAddress;
}

// --- App Setup ---
const app = express();
// Stripe webhook needs raw body for signature verification — must come BEFORE express.json()
app.use('/stripe/webhook', express.raw({ type: 'application/json' }));
app.use(express.json());

// CORS configuration
// Fix HIGH-001 (2026-04-16): no wildcard fallback. If ALLOWED_ORIGINS is not set
// or empty, we only emit Allow-Origin for requests coming from stealthx.tech
// (the app's canonical web origin). Explicit whitelist prevents cross-origin
// hijacking from attacker-controlled websites.
const DEFAULT_ALLOWED_ORIGINS = ["https://stealthx.tech", "https://www.stealthx.tech"];
app.use((req, res, next) => {
  const allowedOrigins = ALLOWED_ORIGINS.length > 0 ? ALLOWED_ORIGINS : DEFAULT_ALLOWED_ORIGINS;
  const origin = req.headers.origin;
  if (origin && allowedOrigins.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
  }
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Admin-Key");
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

// Bug Report API (GitHub Issues integration)
require("./reportRoute")(app);

function sanitize(str) {
  if (typeof str !== "string") return "";
  return str.replace(/[<>"'&]/g, "").substring(0, 64);
}

const server = http.createServer(app);

// --- Client Registry ---
// connId (UUID) -> { ws, lastSeen, clientId }
const clients = new Map();

// clientId (user-chosen) -> connId (reverse lookup)
const clientIds = new Map();

// --- Routing Table (Sessions) ---
const routingTable = new Map();
// sessionId -> { sessionId, from, to, state, created, updated }

// --- FCM Token Storage (persistent) ---
const FCM_TOKENS_FILE = path.join(__dirname, "..", "data", "fcm_tokens.json");
const fcmTokens = new Map();

function loadFcmTokens() {
  try {
    if (fs.existsSync(FCM_TOKENS_FILE)) {
      const data = JSON.parse(fs.readFileSync(FCM_TOKENS_FILE, "utf8"));
      for (const [k, v] of Object.entries(data)) {
        fcmTokens.set(k, v);
      }
      console.log(`[FCM] Loaded ${fcmTokens.size} persisted tokens`);
    }
  } catch (e) {
    console.warn("[FCM] Could not load persisted tokens:", e.message);
  }
}

function saveFcmTokens() {
  try {
    const dir = path.dirname(FCM_TOKENS_FILE);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const obj = {};
    for (const [k, v] of fcmTokens) obj[k] = v;
    writeJsonAtomic(FCM_TOKENS_FILE, obj);
  } catch (e) {
    console.error("[FCM] Failed to persist tokens:", e.message);
  }
}

loadFcmTokens();

// --- Phone Number Registry ---
// normalized phone number -> clientId
const phoneNumbers = new Map();
// SHA-256(normalized phone) -> clientId (for privacy-preserving lookups)
const phoneHashes = new Map();

// --- Activation Codes ---
const CODES_FILE = path.join(__dirname, "..", "data", "activation_codes.json");
let activationCodes = [];

// No hardcoded fallback codes — activation codes are loaded exclusively from
// data/activation_codes.json and data/sold_codes.json. If files are missing,
// the system starts with zero codes (fail-closed).

function loadActivationCodes() {
  try {
    const raw = fs.readFileSync(CODES_FILE, "utf8");
    const data = JSON.parse(raw);
    activationCodes = data.codes || [];
    console.log(`[ACTIVATION] Loaded ${activationCodes.length} activation codes from file`);
  } catch (e) {
    console.warn("[ACTIVATION] Could not load activation_codes.json:", e.message, "— starting with zero codes (fail-closed)");
    activationCodes = [];
  }

  // Also load codes generated from Stripe purchases (sold_codes.json)
  try {
    const sold = require("./payments/sold_codes").loadAsActivationCodes();
    if (sold.length > 0) {
      // De-duplicate: skip codes already present
      const existing = new Set(activationCodes.map(c => c.code));
      const toAdd = sold.filter(c => !existing.has(c.code));
      activationCodes.push(...toAdd);
      console.log(`[ACTIVATION] Merged ${toAdd.length} sold codes from Stripe purchases`);
    }
  } catch (e) {
    console.warn("[ACTIVATION] Could not load sold_codes.json:", e.message);
  }
}

function saveActivationCodes() {
  try {
    writeJsonAtomic(CODES_FILE, { codes: activationCodes });
  } catch (e) {
    console.error("[ACTIVATION] Failed to save activation_codes.json:", e.message);
  }
}

loadActivationCodes();

// FIX 3: Track code usage in-memory (survives within a deploy, resets on restart — acceptable for beta)
const codeUsageCount = new Map(); // code -> usage count this session

// --- IFR Token Verification ---
// Primary: check IFR ERC-20 token balance (balanceOf)
// Optional: check IFRLock contract (isLocked) if deployed
const IFR_TOKEN_ADDRESS = "0x77e99917Eca8539c62F509ED1193ac36580A6e7B";
const IFR_LOCK_ADDRESS = process.env.IFR_LOCK_ADDRESS || "";
const IFR_DECIMALS = 9;
const IFR_PRO_THRESHOLD = BigInt(1000) * BigInt(10 ** IFR_DECIMALS);
const IFR_PREMIUM_THRESHOLD = BigInt(5000) * BigInt(10 ** IFR_DECIMALS);
const ETH_RPC_URLS = (process.env.ETH_RPC_URL || "https://eth.llamarpc.com")
  .split(",")
  .concat(["https://rpc.ankr.com/eth", "https://cloudflare-eth.com"]);

const IFR_TOKEN_ABI = ["function balanceOf(address) view returns (uint256)"];

let ifrTokenContracts = [];

for (const url of ETH_RPC_URLS) {
  try {
    // Don't use staticNetwork on cloud platforms — DNS resolution may differ
    const provider = new ethers.JsonRpcProvider(url);
    const tokenContract = new ethers.Contract(IFR_TOKEN_ADDRESS, IFR_TOKEN_ABI, provider);
    ifrTokenContracts.push({ contract: tokenContract, url });
  } catch (e) {
    console.warn("[IFR] Failed to init provider:", url, e.message);
  }
}
console.log(`[IFR] Initialized ${ifrTokenContracts.length} Ethereum RPC endpoints for IFR token ${IFR_TOKEN_ADDRESS}`);

// Wallet → clientId mappings (prevent multi-device abuse for manual entry)
const WALLETS_FILE = path.join(__dirname, "..", "data", "wallets.json");
let walletMappings = [];

function loadWalletMappings() {
  try {
    const raw = fs.readFileSync(WALLETS_FILE, "utf8");
    walletMappings = JSON.parse(raw).wallets || [];
    console.log(`[IFR] Loaded ${walletMappings.length} wallet mappings`);
  } catch (e) {
    walletMappings = [];
  }
}

function saveWalletMappings() {
  try {
    writeJsonAtomic(WALLETS_FILE, { wallets: walletMappings });
  } catch (_) {}
}

loadWalletMappings();

async function verifyIfrLock(walletAddress) {
  if (ifrTokenContracts.length === 0) return { success: false, error: "eth_unavailable" };

  for (const { contract, url } of ifrTokenContracts) {
    try {
      const timeoutMs = 10000;
      const withTimeout = (p) => Promise.race([p, new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), timeoutMs))]);

      const balance = await withTimeout(contract.balanceOf(walletAddress));
      const humanAmount = (balance / BigInt(10 ** IFR_DECIMALS)).toString();
      console.log("[IFR] balanceOf(" + walletAddress + ") = " + humanAmount + " IFR (via " + url + ")");

      if (balance >= IFR_PREMIUM_THRESHOLD) {
        return { success: true, tier: "premium", lockedAmount: humanAmount };
      }
      if (balance >= IFR_PRO_THRESHOLD) {
        return { success: true, tier: "pro", lockedAmount: humanAmount };
      }
      return { success: false, error: "insufficient", lockedAmount: humanAmount };
    } catch (e) {
      console.warn("[IFR] RPC failed (" + url + "):", e.message, "— trying next");
      continue;
    }
  }
  return { success: false, error: "all_rpc_failed" };
}

function normalizePhone(num) {
  if (typeof num !== "string") return "";
  // Strip ALL formatting: spaces, dashes, parens, dots, brackets, slashes
  let normalized = num.replace(/[\s\-().\[\]/]/g, "");
  // Convert 00-prefix to + (international dialing)
  if (normalized.startsWith("00")) {
    normalized = "+" + normalized.substring(2);
  }
  // Strip remaining non-digit/non-plus chars
  normalized = normalized.replace(/[^0-9+]/g, "");
  return normalized;
}

function hashPhone(normalizedPhone) {
  return crypto.createHash("sha256").update(normalizedPhone).digest("hex");
}

// --- Helper: send JSON to a clientId ---
function sendToClient(clientId, payload) {
  const connId = clientIds.get(clientId);
  if (!connId) return false;

  const client = clients.get(connId);
  if (!client || client.ws.readyState !== WebSocket.OPEN) return false;

  client.ws.send(JSON.stringify(payload));
  return true;
}

// --- Helper: find clientId by connId ---
function getClientId(connId) {
  const client = clients.get(connId);
  return client ? client.clientId : null;
}

// --- Helper: find peer in session ---
function getSessionPeer(sessionId, myClientId) {
  const session = routingTable.get(sessionId);
  if (!session) return null;

  if (session.from === myClientId) return session.to;
  if (session.to === myClientId) return session.from;
  return null;
}

// --- Helper: send binary to peer ---
function forwardBinaryToPeer(connId, data) {
  const myClientId = getClientId(connId);
  if (!myClientId) return false;

  // Find an active session where this client is a participant
  for (const [, session] of routingTable) {
    if (session.state !== "ACTIVE") continue;

    let peerClientId = null;
    if (session.from === myClientId) peerClientId = session.to;
    else if (session.to === myClientId) peerClientId = session.from;
    else continue;

    const peerConnId = clientIds.get(peerClientId);
    if (!peerConnId) continue;

    const peer = clients.get(peerConnId);
    if (!peer || peer.ws.readyState !== WebSocket.OPEN) continue;

    peer.ws.send(data, { binary: true });
    return true;
  }
  return false;
}

// --- Heartbeat Manager starten ---
const hb = new HeartbeatManager(routingTable, clients);
hb.start();

// --- HTTP Endpoint ---
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    message: "SecureCall Signaling Server (Client IDs + Forwarding)"
  });
});

// --- Admin Auth Middleware ---
function requireAdmin(req, res, next) {
  if (!ADMIN_API_KEY) {
    return res.status(403).json({ error: "admin_api_disabled" });
  }
  // BUG-076: Only accept admin key via header, not query param (prevents log leak)
  const provided = req.headers["x-admin-key"];
  if (provided !== ADMIN_API_KEY) {
    return res.status(401).json({ error: "unauthorized" });
  }
  next();
}

// --- Routing Debug API (admin-only) ---
app.get("/routing/list", requireAdmin, (req, res) => {
  res.json({
    routes: Array.from(routingTable.values())
  });
});

// --- ICE Servers API (BACKEND-02) ---
// H-13: TODO — move to WS-only delivery for registered clients.
// Currently public because IceServerFetcher.kt uses HTTP GET.
app.get("/ice-servers", (req, res) => {
  res.json({ iceServers: ICE_SERVERS });
});

// --- Clients Debug API (admin-only) ---
app.get("/clients/list", requireAdmin, (req, res) => {
  const list = [];
  for (const [connId, client] of clients) {
    list.push({
      connId,
      clientId: client.clientId || null,
      connected: client.ws.readyState === WebSocket.OPEN
    });
  }
  res.json({ clients: list });
});

// --- Public Key Directory API (BACKEND-05) ---
app.post("/key/register", (req, res) => {
  const { publicKey } = req.body || {};
  if (!publicKey || typeof publicKey !== "string") {
    return res.status(400).json({
      error: "missing_public_key",
      message: "Field 'publicKey' is required"
    });
  }
  if (publicKey.length > 256) {
    return res.status(400).json({ error: "public_key_too_large" });
  }

  const entry = pkd.registerKey(publicKey);
  res.status(201).json({ keyId: entry.keyId, publicKey: entry.publicKey, created: entry.created });
});

app.get("/key/:id", (req, res) => {
  const entry = pkd.getKey(req.params.id);
  if (!entry) {
    return res.status(404).json({ error: "key_not_found" });
  }
  res.json({ keyId: entry.keyId, publicKey: entry.publicKey, created: entry.created });
});

app.put("/key/:id", (req, res) => {
  const { publicKey } = req.body || {};
  if (!publicKey || typeof publicKey !== "string") {
    return res.status(400).json({
      error: "missing_public_key",
      message: "Field 'publicKey' is required"
    });
  }
  if (publicKey.length > 256) {
    return res.status(400).json({ error: "public_key_too_large" });
  }

  const entry = pkd.rotateKey(req.params.id, publicKey);
  if (!entry) {
    return res.status(404).json({ error: "key_not_found" });
  }
  res.json({ keyId: entry.keyId, publicKey: entry.publicKey, updated: entry.updated });
});

app.delete("/key/:id", (req, res) => {
  const deleted = pkd.deleteKey(req.params.id);
  if (!deleted) {
    return res.status(404).json({ error: "key_not_found" });
  }
  res.json({ ok: true });
});

// --- Subscription Admin API ---
// Fix HIGH-007 (2026-04-16): unified on ADMIN_API_KEY. The old ADMIN_KEY
// variant is dropped — all admin routes now check a single env var.
app.get("/api/subscription/:clientId", (req, res) => {
  const adminKey = req.headers["x-admin-key"];
  if (!ADMIN_API_KEY || adminKey !== ADMIN_API_KEY) {
    return res.status(403).json({ error: "Forbidden" });
  }
  const sub = subscriptions.getSubscription(req.params.clientId);
  if (!sub) {
    return res.status(404).json({ error: "No subscription found" });
  }
  res.json(sub);
});

// --- Client-facing subscription status (CLIENT-CRIT-002 support) ---
// The client polls this endpoint (on resume, once per day) to detect
// server-side cancellations / chargebacks. We require the caller to supply
// the purchaseToken they originally registered with, otherwise we return
// FREE — this prevents a random clientId from learning another user's tier.
//
// Responses:
//   { valid: true,  tier, expiresAt, verifiedAt }
//   { valid: false, tier: "FREE", reason: "not_found" | "expired" | "token_mismatch" }
app.post("/subscription/status", (req, res) => {
  const { clientId, purchaseToken } = req.body || {};
  if (!clientId || typeof clientId !== "string") {
    return res.status(400).json({ error: "missing_client_id" });
  }
  const sub = subscriptions.getSubscription(clientId);
  if (!sub) {
    return res.json({ valid: false, tier: "FREE", reason: "not_found" });
  }
  if (purchaseToken && sub.purchaseToken && purchaseToken !== sub.purchaseToken) {
    return res.json({ valid: false, tier: "FREE", reason: "token_mismatch" });
  }
  if (Date.now() > sub.expiresAt) {
    return res.json({ valid: false, tier: "FREE", reason: "expired", expiresAt: sub.expiresAt });
  }
  res.json({
    valid: true,
    tier: sub.tier,
    expiresAt: sub.expiresAt,
    verifiedAt: sub.verifiedAt
  });
});

// --- WebSocket Setup ---
const wss = new WebSocket.Server({
  server,
  path: "/signal",
  maxPayload: 64 * 1024, // 64 KB max message size
  verifyClient: (info, done) => {
    // Origin validation.
    // Fix (2026-04-16): native mobile clients (OkHttp WebSocket on Android/iOS)
    // do not send an Origin header — Origin is a browser-only SOP concept.
    // We only reject requests that DO send an Origin that is not in the
    // allowlist (blocks cross-site attacks from malicious web pages).
    // Missing Origin passes through; those clients cannot be CSRF-hijacked
    // because they are not browsers.
    if (ALLOWED_ORIGINS.length > 0) {
      const origin = info.origin || info.req.headers.origin;
      if (origin && !ALLOWED_ORIGINS.includes(origin)) {
        return done(false, 403, "Origin not allowed");
      }
    }
    // Per-IP connection limit
    const ip = getClientIp(info.req);
    const count = ipConnections.get(ip) || 0;
    if (count >= MAX_CONNS_PER_IP) {
      return done(false, 429, "Too many connections from this IP");
    }

    // Throttle rapid reconnects from rejected clients (anti-spam)
    // Track connection attempts per IP in a sliding 60s window
    const now = Date.now();
    const attempts = ipConnectionAttempts.get(ip) || [];
    // Purge entries older than 60s
    const recent = attempts.filter(t => now - t < 60000);
    recent.push(now);
    ipConnectionAttempts.set(ip, recent);
    if (recent.length > 30) {
      // >30 connections in 60s from same IP = spam, throttle for 60s
      console.warn("[SIGNAL] Throttled IP:", ip, `(${recent.length} attempts in 60s)`);
      return done(false, 429, "Too many connection attempts");
    }

    done(true);
  }
});

wss.on("connection", (ws, req) => {
  const connId = crypto.randomUUID();
  const ip = getClientIp(req);
  console.log("[SIGNAL] connected:", connId, "ip:", ip);

  // Track per-IP connections
  ipConnections.set(ip, (ipConnections.get(ip) || 0) + 1);

  clients.set(connId, { ws, lastSeen: Date.now(), clientId: null, ip });

  ws.on("pong", () => {
    hb.updateClient(connId);
  });

  ws.on("message", (data, isBinary) => {
    hb.updateClient(connId);

    // --- Binary frames (audio relay): handle before rate limit ---
    // Binary audio at 50fps would instantly exhaust the signaling rate
    // limit (40/10s). forwardBinaryToPeer() already validates: registered
    // client + ACTIVE session + valid peer.
    if (isBinary) {
      if (!rateLimit.registerBinaryEvent(connId)) {
        return; // silently drop — client flooding binary frames
      }
      if (!forwardBinaryToPeer(connId, data)) {
        // Silently drop — no active session or peer not connected
      }
      return;
    }

    // Rate limiting (JSON signaling messages only)
    if (!rateLimit.registerEvent(connId)) {
      ws.send(JSON.stringify({ type: "ERROR", error: "rate_limited" }));
      return;
    }

    let msg = null;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_json" }));
    }

    // BUG-074: Unconditionally strip prototype pollution keys from parsed JSON.
    // Previous guard condition was always-true (msg.constructor !== undefined),
    // making it not a guard but accidental unconditional cleanup.
    for (const key of ["__proto__", "constructor", "prototype"]) {
      if (Object.prototype.hasOwnProperty.call(msg, key)) {
        delete msg[key];
      }
    }

    // ===========================
    // REGISTER — Client ID zuweisen
    // ===========================
    if (msg.type === "REGISTER") {
      if (!msg.clientId || typeof msg.clientId !== "string") {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_client_id",
          message: "Field 'clientId' is required"
        }));
      }

      if (!CLIENT_ID_REGEX.test(msg.clientId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "invalid_client_id",
          message: "clientId must be 1-64 alphanumeric characters, hyphens, or underscores"
        }));
      }

      // App signature verification — fork protection
      // ALLOWED_SIGNATURES: comma-separated list of allowed SHA-256 cert hashes
      // FORK_PROTECTION_MODE: "enforce" (reject, default) or "warn" (log only)
      const allowedSigs = process.env.ALLOWED_SIGNATURES;
      if (allowedSigs && allowedSigs.trim().length > 0) {
        const allowed = allowedSigs.split(",").map(s => s.trim().toLowerCase());
        const clientSig = (msg.appSignature || "").toLowerCase();
        const forkMode = (process.env.FORK_PROTECTION_MODE || "enforce").toLowerCase();
        if (!clientSig || !allowed.includes(clientSig)) {
          if (forkMode === "enforce") {
            // Throttle rejection logging — log only first occurrence + every 50th per clientId
            const tracker = rejectionTracker.get(msg.clientId) || { count: 0, firstSeen: Date.now(), lastLogged: 0 };
            tracker.count++;
            if (tracker.count === 1 || tracker.count % 50 === 0) {
              console.log("[REGISTER] REJECTED — unauthorized signature:", clientSig, "from", msg.clientId, `(attempt #${tracker.count})`);
              tracker.lastLogged = Date.now();
            }
            rejectionTracker.set(msg.clientId, tracker);

            // After 10 rejections, add increasing delay via close code
            // Client with BUG-061 fix stops at 10 retries for 4000-4099 codes
            // For clients ignoring close codes: server-side IP throttle handles it
            ws.send(JSON.stringify({
              type: "ERROR",
              error: "unauthorized_client",
              message: "App signature not authorized"
            }));
            return ws.close(4003, "Unauthorized client");
          } else {
            console.warn("[REGISTER] WARN — unauthorized signature:", clientSig, "from", msg.clientId, "(warn mode — not rejected)");
          }
        }
      }
      // Log signature presence for adoption monitoring (only first REGISTER per connection)
      if (allowedSigs) {
        console.log("[REGISTER] appSignature present:", !!msg.appSignature, "from", msg.clientId);
      }

      // Prüfen ob clientId bereits vergeben — allow reconnection by superseding old connection.
      // Fix HIGH-002 (2026-04-16): on supersede, drop the victim's stored FCM token so a
      // hijacker cannot inherit push-notification routing. The legitimate owner will
      // re-send their FCM token on the next REGISTER_FCM_TOKEN after reconnect.
      if (clientIds.has(msg.clientId)) {
        const existingConnId = clientIds.get(msg.clientId);
        if (existingConnId !== connId) {
          const oldClient = clients.get(existingConnId);
          if (oldClient) {
            console.log("[REGISTER] Superseding old connection for", msg.clientId, "(old connId:", existingConnId, ")");
            try { oldClient.ws.close(1000, "Superseded"); } catch(e) {}
          }
          if (fcmTokens.has(msg.clientId)) {
            fcmTokens.delete(msg.clientId);
            saveFcmTokens();
            console.log("[REGISTER] Cleared FCM token on supersede for", msg.clientId);
          }
        }
        clientIds.delete(msg.clientId);
      }

      // Registrieren
      const client = clients.get(connId);
      // Falls vorher schon eine andere clientId registriert war, alte entfernen
      if (client.clientId) {
        clientIds.delete(client.clientId);
        // Clean up old phone mapping for previous clientId — only if it still belongs to THIS client
        if (client.phoneNumber) {
          if (phoneNumbers.get(client.phoneNumber) === client.clientId || phoneNumbers.get(client.phoneNumber) === msg.clientId) {
            phoneNumbers.delete(client.phoneNumber);
            phoneHashes.delete(hashPhone(client.phoneNumber));
          }
        }
      }
      client.clientId = msg.clientId;
      clientIds.set(msg.clientId, connId);

      // Store phone number if provided
      const phone = normalizePhone(msg.phoneNumber);
      if (phone.length >= 4) {
        // Remove any OTHER phone that currently maps to this clientId (phone number changed)
        for (const [existingPhone, existingClientId] of phoneNumbers) {
          if (existingClientId === msg.clientId && existingPhone !== phone) {
            phoneNumbers.delete(existingPhone);
            phoneHashes.delete(hashPhone(existingPhone));
            break;
          }
        }

        // Check if this phone was previously registered with a DIFFERENT clientId
        // (e.g., app reinstall, data clear → new clientId for same phone)
        const oldClientId = phoneNumbers.get(phone);
        if (oldClientId && oldClientId !== msg.clientId) {
          console.log("[REGISTER] SecureID changed for phone-hash", hashPhone(phone), ":", oldClientId, "->", msg.clientId);
          // BUG-072: Removed broadcast-to-all-clients (amplification vector).
          // Contacts discover changed IDs on next BATCH_PHONE_LOOKUP (≤15s cycle).
          // Notify ONLY the old client (if still connected) so it knows it was replaced.
          const oldConnId = clientIds.get(oldClientId);
          const oldConn = oldConnId ? clients.get(oldConnId) : null;
          if (oldConn && oldConn.ws.readyState === WebSocket.OPEN) {
            try {
              oldConn.ws.send(JSON.stringify({
                type: "SECUREID_CHANGED",
                oldClientId: oldClientId,
                newClientId: msg.clientId
              }));
            } catch (e) {}
          }
          // Clean up old clientId's connection mapping if still active
          if (clientIds.has(oldClientId) && clientIds.get(oldClientId) !== connId) {
            const oldConnId = clientIds.get(oldClientId);
            const oldConn = clients.get(oldConnId);
            if (oldConn) {
              try { oldConn.ws.close(1000, "SecureID replaced"); } catch (e) {}
            }
          }
        }

        phoneNumbers.set(phone, msg.clientId);
        phoneHashes.set(hashPhone(phone), msg.clientId);
        client.phoneNumber = phone;
        console.log("[REGISTER] Phone:", hashPhone(phone), "->", msg.clientId);
      }

      console.log("[REGISTER]", msg.clientId, "->", connId);
      return ws.send(JSON.stringify({
        type: "REGISTERED",
        clientId: msg.clientId
      }));
    }

    // ===========================
    // CALL_INVITE — Anruf starten + an Empfänger weiterleiten
    // ===========================
    if (msg.type === "CALL_INVITE") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending CALL_INVITE"
        }));
      }

      if (!msg.to) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_to",
          message: "Field 'to' is required"
        }));
      }

      const sessionId = crypto.randomUUID();

      // Resolve target: try clientId first, then custom ID, then phone number
      let targetClientId = msg.to;
      if (!clientIds.has(targetClientId)) {
        // Try custom ID resolution
        const customDeviceId = customIds.resolve(targetClientId);
        if (customDeviceId) {
          console.log("[ROUTING] Custom ID resolved:", targetClientId, "->", customDeviceId);
          targetClientId = customDeviceId;
        } else {
          // Try phone number lookup
          const phoneLookup = phoneNumbers.get(normalizePhone(targetClientId));
          if (phoneLookup) {
            console.log("[ROUTING] Phone resolved: hash", hashPhone(targetClientId), "->", phoneLookup);
            targetClientId = phoneLookup;
          }
        }
      }

      // Check if peer is online
      if (clientIds.has(targetClientId)) {
        // Peer is online — normal flow
        routingTable.set(sessionId, {
          sessionId,
          from: myClientId,
          to: targetClientId,
          state: "INVITE",
          created: Date.now(),
          updated: Date.now()
        });

        console.log("[ROUTING] INVITE:", myClientId, "->", targetClientId, "session:", sessionId);

        ws.send(JSON.stringify({
          type: "CALL_INVITE_ACK",
          ok: true,
          sessionId,
          from: myClientId,
          to: targetClientId
        }));

        sendToClient(targetClientId, {
          type: "CALL_INVITE",
          sessionId,
          from: myClientId,
          to: targetClientId,
          pubKey: msg.pubKey,
          callerPhone: msg.callerPhone || ""
        });
      } else {
        // Peer is offline — try FCM push (use resolved targetClientId for token lookup)
        const fcmToken = fcmTokens.get(targetClientId) || fcmTokens.get(msg.to);
        if (fcmToken && fcm.isInitialized()) {
          routingTable.set(sessionId, {
            sessionId,
            from: myClientId,
            to: targetClientId,
            state: "INVITE_PENDING_PUSH",
            created: Date.now(),
            updated: Date.now()
          });

          console.log("[ROUTING] INVITE (offline, sending push):", myClientId, "->", targetClientId, "session:", sessionId);

          fcm.sendCallInvitePush(fcmToken, sessionId, myClientId);

          ws.send(JSON.stringify({
            type: "CALL_INVITE_ACK",
            ok: true,
            sessionId,
            from: myClientId,
            to: targetClientId,
            pushSent: true
          }));
        } else {
          return ws.send(JSON.stringify({
            type: "ERROR",
            error: "peer_not_found",
            message: `Client '${sanitize(msg.to)}' is not online`
          }));
        }
      }

      return;
    }

    // ===========================
    // CALL_ACCEPT — Anruf annehmen + an Caller weiterleiten
    // ===========================
    if (msg.type === "CALL_ACCEPT") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending CALL_ACCEPT"
        }));
      }

      const session = routingTable.get(msg.sessionId);
      if (!session) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      // Verify sender is the intended callee
      if (session.to !== myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_callee",
          message: "Only the intended callee can accept this call"
        }));
      }

      session.state = "ACTIVE";
      session.updated = Date.now();

      console.log("[ROUTING] ACCEPT:", msg.sessionId, "by", myClientId);

      // Bestätigung an Callee
      ws.send(JSON.stringify({
        type: "CALL_ACCEPT_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));

      // Weiterleiten an Caller
      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "CALL_ACCEPT",
          sessionId: msg.sessionId,
          from: myClientId,
          pubKey: msg.pubKey
        });
      }

      return;
    }

    // ===========================
    // CALL_BUSY — Callee is already in a call, forward to caller
    // ===========================
    if (msg.type === "CALL_BUSY") {
      const myClientId = getClientId(connId);
      const session = routingTable.get(msg.sessionId);
      if (session && session.to === myClientId) {
        const callerClientId = session.from;
        sendToClient(callerClientId, {
          type: "CALL_BUSY",
          sessionId: msg.sessionId,
          from: myClientId
        });
        routingTable.delete(msg.sessionId);
        console.log("[ROUTING] BUSY:", myClientId, "-> caller:", callerClientId, "session:", msg.sessionId);
      }
      return;
    }

    // ===========================
    // CALL_END — Anruf beenden + an Peer weiterleiten
    // ===========================
    if (msg.type === "CALL_END") {
      const myClientId = getClientId(connId);

      if (msg.sessionId && routingTable.has(msg.sessionId)) {
        const session = routingTable.get(msg.sessionId);
        // Verify sender is a participant
        if (session.from !== myClientId && session.to !== myClientId) {
          return ws.send(JSON.stringify({
            type: "ERROR",
            error: "not_participant",
            message: "Only call participants can end this call"
          }));
        }

        const peerClientId = getSessionPeer(msg.sessionId, myClientId);

        // Forward to peer BEFORE deleting session (prevents race condition)
        if (peerClientId) {
          sendToClient(peerClientId, {
            type: "CALL_END",
            sessionId: msg.sessionId,
            from: myClientId
          });
        }

        routingTable.delete(msg.sessionId);
        console.log("[ROUTING] END:", msg.sessionId, "by", myClientId);
      }

      return ws.send(JSON.stringify({
        type: "CALL_END_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // WEBRTC_OFFER — SDP Offer an Peer weiterleiten (BACKEND-02)
    // ===========================
    if (msg.type === "WEBRTC_OFFER") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      if (!msg.sdp) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_sdp",
          message: "Field 'sdp' is required for WEBRTC_OFFER"
        }));
      }

      if (typeof msg.sdp !== "string" || msg.sdp.length > 10000) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_sdp" }));
      }

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "WEBRTC_OFFER",
          sessionId: msg.sessionId,
          from: myClientId,
          sdp: msg.sdp
        });
        console.log("[WEBRTC] OFFER:", myClientId, "->", peerClientId);
      }

      return ws.send(JSON.stringify({
        type: "WEBRTC_OFFER_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // WEBRTC_ANSWER — SDP Answer an Peer weiterleiten (BACKEND-02)
    // ===========================
    if (msg.type === "WEBRTC_ANSWER") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      if (!msg.sdp) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_sdp",
          message: "Field 'sdp' is required for WEBRTC_ANSWER"
        }));
      }

      if (typeof msg.sdp !== "string" || msg.sdp.length > 10000) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_sdp" }));
      }

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "WEBRTC_ANSWER",
          sessionId: msg.sessionId,
          from: myClientId,
          sdp: msg.sdp
        });
        console.log("[WEBRTC] ANSWER:", myClientId, "->", peerClientId);
      }

      return ws.send(JSON.stringify({
        type: "WEBRTC_ANSWER_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // ICE_CANDIDATE — ICE-Kandidat an Peer weiterleiten (BACKEND-02)
    // ===========================
    if (msg.type === "ICE_CANDIDATE") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      if (!msg.candidate) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_candidate",
          message: "Field 'candidate' is required for ICE_CANDIDATE"
        }));
      }

      if (typeof msg.candidate !== "object" && typeof msg.candidate !== "string") {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_candidate" }));
      }

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, {
          type: "ICE_CANDIDATE",
          sessionId: msg.sessionId,
          from: myClientId,
          candidate: msg.candidate
        });
      }

      return ws.send(JSON.stringify({
        type: "ICE_CANDIDATE_ACK",
        ok: true,
        sessionId: msg.sessionId
      }));
    }

    // ===========================
    // GHOST_PREPARE — GhostNet Pre-Handshake (BACKEND-23)
    // ===========================
    if (msg.type === "GHOST_PREPARE") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending GHOST_PREPARE"
        }));
      }

      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "session_not_found"
        }));
      }

      console.log("[GHOST] PREPARE received for session:", msg.sessionId);

      const ghostNetId = crypto.randomUUID();
      const reply = {
        type: "GHOST_ACK",
        sessionId: msg.sessionId,
        ghostNetId,
        iceServers: ICE_SERVERS,
        relayHints: [
          { host: "relay1.securecall.local", port: 443 },
          { host: "relay2.securecall.local", port: 8443 }
        ]
      };

      ws.send(JSON.stringify(reply));
      console.log("[GHOST] ACK sent:", ghostNetId);
      return;
    }

    // ===========================
    // REGISTER_FCM_TOKEN — Store FCM token for push notifications
    // ===========================
    if (msg.type === "REGISTER_FCM_TOKEN") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_registered",
          message: "You must REGISTER before sending REGISTER_FCM_TOKEN"
        }));
      }

      if (!msg.fcmToken || typeof msg.fcmToken !== "string") {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_fcm_token"
        }));
      }

      fcmTokens.set(myClientId, msg.fcmToken);
      saveFcmTokens();
      console.log("[FCM] Token registered + persisted for:", myClientId);

      return ws.send(JSON.stringify({
        type: "REGISTER_FCM_TOKEN_ACK",
        ok: true
      }));
    }

    // ===========================
    // SUBSCRIPTION_VERIFY — Verify and store subscription
    // ===========================
    if (msg.type === "SUBSCRIPTION_VERIFY") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        ws.send(JSON.stringify({ type: "ERROR", error: "not_registered" }));
        return;
      }
      const { purchaseToken, productId } = msg;
      if (!purchaseToken || !productId) {
        ws.send(JSON.stringify({ type: "ERROR", message: "Missing purchaseToken or productId" }));
        return;
      }
      const result = subscriptions.verifySubscription(myClientId, purchaseToken, productId);
      ws.send(JSON.stringify({
        type: "SUBSCRIPTION_VERIFY_ACK",
        tier: result.tier,
        expiresAt: result.expiresAt
      }));
      console.log(`[SUBSCRIPTION] Verified: ${myClientId}, tier=${result.tier}, product=${productId}`);
      return;
    }

    // ===========================
    // PHONE_LOOKUP — Resolve phone number to clientId
    // FIX 8: Rate limited to 10 lookups/minute per client
    // Security: requires registration (H-06 fix)
    // ===========================
    if (msg.type === "PHONE_LOOKUP") {
      if (!getClientId(connId)) {
        return ws.send(JSON.stringify({type: "PHONE_LOOKUP_RESULT", error: "not_registered"}));
      }
      // Rate limit: 10 per 60 seconds per connection
      if (!clients.get(connId)._phoneLookups) clients.get(connId)._phoneLookups = [];
      const lookups = clients.get(connId)._phoneLookups;
      const now = Date.now();
      // Prune old entries
      while (lookups.length > 0 && now - lookups[0] > 60000) lookups.shift();
      if (lookups.length >= 10) {
        return ws.send(JSON.stringify({
          type: "PHONE_LOOKUP_RESULT",
          phoneNumber: msg.phoneNumber || "",
          clientId: null,
          online: false,
          error: "rate_limited"
        }));
      }
      lookups.push(now);

      if (!msg.phoneNumber || typeof msg.phoneNumber !== "string") {
        return ws.send(JSON.stringify({
          type: "PHONE_LOOKUP_RESULT",
          phoneNumber: "",
          clientId: null,
          online: false
        }));
      }

      const normalized = normalizePhone(msg.phoneNumber);
      const resolvedClientId = phoneNumbers.get(normalized) || null;
      const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;

      return ws.send(JSON.stringify({
        type: "PHONE_LOOKUP_RESULT",
        phoneNumber: msg.phoneNumber,
        clientId: resolvedClientId,
        online
      }));
    }

    // ===========================
    // BATCH_PHONE_LOOKUP — Resolve multiple phone numbers at once
    // Security: requires registration (H-06 fix)
    // ===========================
    if (msg.type === "BATCH_PHONE_LOOKUP") {
      if (!getClientId(connId)) {
        return ws.send(JSON.stringify({type: "BATCH_PHONE_LOOKUP_RESULT", results: [], error: "not_registered"}));
      }
      // Rate limit: 5 per 60 seconds per connection
      if (!clients.get(connId)._batchLookups) clients.get(connId)._batchLookups = [];
      const batchLookups = clients.get(connId)._batchLookups;
      const bNow = Date.now();
      while (batchLookups.length > 0 && bNow - batchLookups[0] > 60000) batchLookups.shift();
      if (batchLookups.length >= 5) {
        return ws.send(JSON.stringify({
          type: "BATCH_PHONE_LOOKUP_RESULT",
          results: [],
          error: "rate_limited"
        }));
      }
      batchLookups.push(bNow);

      // Hashed mode: client sends SHA-256 hashes instead of raw phone numbers
      if (Array.isArray(msg.hashes)) {
        const results = msg.hashes.slice(0, 200).map(hash => {
          const resolvedClientId = phoneHashes.get(hash) || null;
          const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
          return { hash, clientId: resolvedClientId, online };
        });
        return ws.send(JSON.stringify({
          type: "BATCH_PHONE_LOOKUP_RESULT",
          mode: "hashed",
          results
        }));
      }
      // Legacy mode: raw phone numbers
      const phoneList = Array.isArray(msg.phoneNumbers) ? msg.phoneNumbers : [];
      const results = phoneList.slice(0, 200).map(phone => {
        const normalized = normalizePhone(phone);
        const resolvedClientId = phoneNumbers.get(normalized) || null;
        const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
        return { phoneNumber: phone, clientId: resolvedClientId, online };
      });
      return ws.send(JSON.stringify({
        type: "BATCH_PHONE_LOOKUP_RESULT",
        results
      }));
    }

    // ===========================
    // ONLINE_STATUS_REQUEST — Simple online/offline check for phone numbers
    // ===========================
    if (msg.type === "ONLINE_STATUS_REQUEST") {
      if (!getClientId(connId)) {
        return ws.send(JSON.stringify({type: "ONLINE_STATUS_RESPONSE", statuses: [], error: "not_registered"}));
      }
      // Rate limit: 10 per 60 seconds per connection
      if (!clients.get(connId)._onlineStatusReqs) clients.get(connId)._onlineStatusReqs = [];
      const osReqs = clients.get(connId)._onlineStatusReqs;
      const osNow = Date.now();
      while (osReqs.length > 0 && osNow - osReqs[0] > 60000) osReqs.shift();
      if (osReqs.length >= 10) {
        return ws.send(JSON.stringify({
          type: "ONLINE_STATUS_RESPONSE",
          statuses: {},
          error: "rate_limited"
        }));
      }
      osReqs.push(osNow);

      const phones = Array.isArray(msg.phoneNumbers) ? msg.phoneNumbers.slice(0, 500) : [];
      const statuses = {};
      for (const phone of phones) {
        const normalized = normalizePhone(phone);
        const resolvedClientId = phoneNumbers.get(normalized);
        statuses[phone] = !!(resolvedClientId && clientIds.has(resolvedClientId));
      }
      return ws.send(JSON.stringify({ type: "ONLINE_STATUS_RESPONSE", statuses }));
    }

    // ===========================
    // ACTIVATE_CODE — Validate activation code and return tier
    // ===========================
    if (msg.type === "ACTIVATE_CODE") {
      const code = (msg.code || "").trim().toUpperCase();
      if (!code) {
        return ws.send(JSON.stringify({
          type: "ACTIVATE_CODE_RESULT",
          success: false,
          error: "missing_code"
        }));
      }

      // TODO-047: Block expired BETA codes — closes both FALLBACK and JSON paths
      const BLOCKED_CODES = ["BETA-PRO0-2026", "BETA-PREM-2026"];
      if (BLOCKED_CODES.includes(code)) {
        console.log("[ACTIVATION] Blocked expired BETA code:", code);
        return ws.send(JSON.stringify({
          type: "ACTIVATE_CODE_RESULT",
          success: false,
          error: "expired",
          message: "This beta code has expired. Thank you for testing!"
        }));
      }

      const entry = activationCodes.find(c => c.code === code);

      // Check gift codes if not found in activation codes
      if (!entry && giftCodes.has(code)) {
        const gift = giftCodes.get(code);
        if (gift.used) {
          return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "already_used" }));
        }
        if (new Date(gift.expires) < new Date()) {
          return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "expired" }));
        }
        gift.used = true;
        gift.usedBy = getClientId(connId);
        saveGiftCodes();
        const myClientId = getClientId(connId);
        console.log("[GIFT] Code redeemed:", code.substring(0, 4) + "****", "-> tier:", gift.tier, "by:", myClientId);
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: true, tier: gift.tier }));
      }

      if (!entry) {
        console.log("[ACTIVATION] Invalid code attempted:", code.substring(0, 4) + "****");
        return ws.send(JSON.stringify({
          type: "ACTIVATE_CODE_RESULT",
          success: false,
          error: "invalid"
        }));
      }

      const myClientId = getClientId(connId);
      const devices = Array.isArray(entry.usedBy) ? entry.usedBy : (entry.usedBy ? [entry.usedBy] : []);

      // Already activated on this device? Allow re-activation (idempotent)
      if (devices.includes(myClientId)) {
        console.log("[ACTIVATION] Code re-activated:", code.substring(0, 4) + "****", "by:", myClientId);
        return ws.send(JSON.stringify({
          type: "ACTIVATE_CODE_RESULT",
          success: true,
          tier: entry.tier,
          code: code,
          slot: devices.indexOf(myClientId) + 1,
          maxSlots: entry.maxUses
        }));
      }

      // All device slots used? Reject.
      if (devices.length >= entry.maxUses) {
        console.log("[ACTIVATION] Code exhausted:", code.substring(0, 4) + "****", "devices:", devices.length, "/", entry.maxUses, "attempted:", myClientId);
        return ws.send(JSON.stringify({
          type: "ACTIVATE_CODE_RESULT",
          success: false,
          error: "max_devices",
          message: `Code already used on ${entry.maxUses} devices`
        }));
      }

      // Success — register this device
      devices.push(myClientId);
      entry.usedBy = devices;
      entry.currentUses = devices.length;
      const slot = devices.length;
      console.log("[ACTIVATION] Code redeemed:", code.substring(0, 4) + "****", "-> tier:", entry.tier, "by:", myClientId, "slot:", slot + "/" + entry.maxUses);
      saveActivationCodes();

      return ws.send(JSON.stringify({
        type: "ACTIVATE_CODE_RESULT",
        success: true,
        tier: entry.tier,
        code: code,
        slot: slot,
        maxSlots: entry.maxUses
      }));
    }

    // ===========================
    // VERIFY_IFR_LOCK — Check IFR token lock status on Ethereum
    // ===========================
    if (msg.type === "VERIFY_IFR_LOCK") {
      const wallet = (msg.walletAddress || "").trim();
      if (!wallet || !wallet.match(/^0x[0-9a-fA-F]{40}$/)) {
        return ws.send(JSON.stringify({
          type: "IFR_LOCK_RESULT",
          success: false,
          error: "invalid_address"
        }));
      }

      const myClientId = getClientId(connId);

      // Check wallet→device mapping (manual entry: one wallet per device)
      const existing = walletMappings.find(w => w.wallet.toLowerCase() === wallet.toLowerCase());
      if (existing && existing.clientId !== myClientId) {
        return ws.send(JSON.stringify({
          type: "IFR_LOCK_RESULT",
          success: false,
          error: "wallet_bound",
          boundTo: existing.clientId.substring(0, 8) + "..."
        }));
      }

      console.log("[IFR] Verifying lock for wallet:", wallet, "client:", myClientId);

      verifyIfrLock(wallet).then(result => {
        if (result.success) {
          // Store/update wallet mapping
          const idx = walletMappings.findIndex(w => w.wallet.toLowerCase() === wallet.toLowerCase());
          if (idx >= 0) {
            walletMappings[idx].clientId = myClientId;
            walletMappings[idx].tier = result.tier;
            walletMappings[idx].lastVerified = Date.now();
          } else {
            walletMappings.push({ wallet: wallet.toLowerCase(), clientId: myClientId, tier: result.tier, lastVerified: Date.now() });
          }
          saveWalletMappings();
          console.log("[IFR] Lock verified:", wallet, "->", result.tier, "(", result.lockedAmount, "IFR)");
        }

        // H-07: Guard against closed WS after async operation
        try {
          if (ws.readyState === 1) { // WebSocket.OPEN
            ws.send(JSON.stringify({
              type: "IFR_LOCK_RESULT",
              success: result.success,
              tier: result.tier || "",
              lockedAmount: result.lockedAmount || "0",
              walletAddress: wallet,
              error: result.error || ""
            }));
          }
        } catch (_) {}
      }).catch(e => {
        console.error("[IFR] Verification error:", e.message);
        try {
          if (ws.readyState === 1) {
            ws.send(JSON.stringify({
              type: "IFR_LOCK_RESULT",
              success: false,
              error: "server_error"
            }));
          }
        } catch (_) {}
      });

      return;
    }

    // ===========================
    // DEREGISTER — Remove client from all registries (stealth-delete)
    // ===========================
    if (msg.type === "DEREGISTER") {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        ws.send(JSON.stringify({type: "ERROR", error: "not_registered", message: "Must be registered to deregister"}));
        return;
      }
      if (myClientId) {
        // Remove phone mappings
        for (const [phone, cid] of phoneNumbers) {
          if (cid === myClientId) {
            phoneNumbers.delete(phone);
            phoneHashes.delete(hashPhone(phone));
            console.log("[DEREGISTER] Removed phone mapping:", hashPhone(phone), "->", myClientId);
            break;
          }
        }
        // Remove clientId mapping
        if (clientIds.get(myClientId) === connId) {
          clientIds.delete(myClientId);
        }
        // Remove FCM token
        fcmTokens.delete(myClientId);
        // Clear client record
        const client = clients.get(connId);
        if (client) {
          client.clientId = null;
          client.phoneNumber = null;
        }
        console.log("[DEREGISTER] Client removed:", myClientId);
      }
      return ws.send(JSON.stringify({ type: "DEREGISTER_ACK", ok: true }));
    }

    // HEARTBEAT — client keepalive, reply so client's onMessage updates lastSeen
    if (msg.type === "HEARTBEAT") {
      return ws.send(JSON.stringify({ type: "HEARTBEAT_ACK" }));
    }

    // Fallback: unknown message type
    ws.send(JSON.stringify({
      type: "ERROR",
      error: "unknown_message_type",
      provided: msg.type
    }));
  });

  ws.on("close", () => {
    const client = clients.get(connId);
    const clientId = client ? client.clientId : null;
    const clientIp = client ? client.ip : null;

    console.log("[SIGNAL] disconnected:", connId, clientId ? `(${clientId})` : "");

    // Decrement per-IP connection count
    if (clientIp) {
      const count = ipConnections.get(clientIp) || 1;
      if (count <= 1) {
        ipConnections.delete(clientIp);
      } else {
        ipConnections.set(clientIp, count - 1);
      }
    }

    // Clean up rate limit bucket
    rateLimit.clear(connId);

    // Do NOT delete phoneNumbers/phoneHashes on disconnect.
    // Keep them so the user still appears as "registered but offline" (red dot)
    // in other users' contacts. The entries are re-added on reconnect via REGISTER.
    // Only removed when: (a) user registers a different phone number, or (b) server restarts.

    // Check if WE are still the active connection for this clientId BEFORE deleting.
    // When a connection is superseded (new connection registered same clientId),
    // the old close handler must NOT clean up sessions or delete the new mapping.
    // (Root cause of Bug #1: Call drops on WS reconnect, 2026-04-18.)
    const isActiveConnection = clientId && clientIds.get(clientId) === connId;

    if (isActiveConnection) {
      // Sessions aufräumen wo dieser Client beteiligt war
      for (const [sessionId, session] of routingTable) {
        if (session.from === clientId || session.to === clientId) {
          const peerId = session.from === clientId ? session.to : session.from;
          sendToClient(peerId, {
            type: "CALL_END",
            sessionId,
            reason: "peer_disconnected"
          });
          routingTable.delete(sessionId);
          console.log("[ROUTING] Session cleaned up (disconnect):", sessionId);
        }
      }
      clientIds.delete(clientId);
    } else if (clientId) {
      console.log("[ROUTING] Skip session cleanup — client superseded to new connection:", clientId);
    }
    clients.delete(connId);
  });
});

// --- Production Error Handling ---
process.on('unhandledRejection', (reason, promise) => {
  console.error('[ERROR] Unhandled Rejection at:', promise, 'reason:', reason);
});

process.on('uncaughtException', (error) => {
  console.error('[ERROR] Uncaught Exception:', error);
  process.exit(1);
});

// --- Emergency Broadcast System ---
const TEMPLATE_META = {
  1: { icon: "🔴", title: "CRITICAL: Do Not Use SecureCall" },
  2: { icon: "🟠", title: "Security Alert" },
  3: { icon: "🟡", title: "Critical Update Required" },
  4: { icon: "🔵", title: "Service Maintenance" },
  5: { icon: "⚫", title: "Stealth Protocol Activated" },
  6: { icon: "📻", title: "Emergency Broadcast" },
  7: { icon: "⚠️", title: "Network Compromise Warning" },
  8: { icon: "🟢", title: "All Clear" },
  9: { icon: "🔄", title: "Update Available" },
  10: { icon: "🧪", title: "Beta Update Available" },
  11: { icon: "📢", title: "Official Announcement" }
};

let lastBroadcast = {
  template_id: 8, icon: "🟢", title: "All Clear",
  body: "All systems operational. No active alerts.",
  timestamp: new Date().toISOString(), active: false
};

app.get("/status/last-broadcast", (req, res) => {
  res.json(lastBroadcast);
});

app.get("/status/live", (req, res) => {
  res.json({
    server: "online",
    uptime: Math.floor(process.uptime()),
    connectedClients: clients ? clients.size : 0,
    registeredIds: clientIds ? clientIds.size : 0,
    fcmTokens: fcmTokens ? fcmTokens.size : 0,
    timestamp: new Date().toISOString()
  });
});

app.post("/admin/broadcast", requireAdmin, (req, res) => {
  const { template_id } = req.body;
  if (!template_id || template_id < 1 || template_id > 11) {
    return res.status(400).json({ error: "invalid template_id (1-11)" });
  }

  // Broadcast to all connected WebSocket clients
  const msg = JSON.stringify({ type: "EMERGENCY_BROADCAST", template_id });
  let wsSent = 0;
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(msg);
      wsSent++;
    }
  });

  // Also send via FCM to all registered tokens
  let fcmSent = 0;
  if (fcm.isInitialized()) {
    for (const [clientId, token] of fcmTokens.entries()) {
      try {
        const admin = require("firebase-admin");
        admin.messaging().send({
          token,
          data: { type: "EMERGENCY_BROADCAST", template_id: String(template_id) },
          android: { priority: "high" }
        }).then(() => { fcmSent++; }).catch(() => {});
      } catch (_) {}
    }
  }

  const meta = TEMPLATE_META[template_id] || { icon: "📡", title: "Broadcast" };
  lastBroadcast = {
    template_id, icon: meta.icon, title: meta.title,
    body: "", timestamp: new Date().toISOString(),
    active: template_id !== 8
  };

  console.log(`[BROADCAST] Emergency template=${template_id} sent to ${wsSent} WS clients, ${fcmTokens.size} FCM targets`);
  res.json({ ok: true, ws_sent: wsSent, fcm_targets: fcmTokens.size });
});

// --- Gift Link System (admin-only) ---
const GIFT_CODES_FILE = path.join(__dirname, "..", "data", "gift_codes.json");
const giftCodes = new Map();

// Load gift codes from disk on startup
try {
  if (fs.existsSync(GIFT_CODES_FILE)) {
    const raw = JSON.parse(fs.readFileSync(GIFT_CODES_FILE, "utf8"));
    for (const [code, data] of Object.entries(raw)) {
      giftCodes.set(code, data);
    }
    console.log(`[GIFT] Loaded ${giftCodes.size} gift codes from disk`);
  }
} catch (e) {
  console.warn("[GIFT] Could not load gift_codes.json:", e.message);
}

function saveGiftCodes() {
  try {
    const obj = Object.fromEntries(giftCodes);
    writeJsonAtomic(GIFT_CODES_FILE, obj);
  } catch (e) {
    console.error("[GIFT] Failed to save gift_codes.json:", e.message);
  }
}

app.post("/admin/gift", requireAdmin, (req, res) => {
  const { tier, note } = req.body;
  if (!tier || !["pro", "premium"].includes(tier.toLowerCase())) {
    return res.status(400).json({ error: "tier must be 'pro' or 'premium'" });
  }

  const code = "GIFT-" + crypto.randomBytes(4).toString("hex").toUpperCase();
  const expires = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split("T")[0];

  giftCodes.set(code, {
    tier: tier.toUpperCase(),
    note: note || "",
    created: new Date().toISOString(),
    expires,
    used: false,
    usedBy: null
  });

  saveGiftCodes();
  console.log(`[GIFT] Created ${code.substring(0, 4)}**** → ${tier.toUpperCase()} (note: ${note || "none"})`);
  res.json({ code, tier: tier.toUpperCase(), expires, note: note || "" });
});

app.get("/admin/gifts", requireAdmin, (req, res) => {
  const gifts = [];
  for (const [code, data] of giftCodes.entries()) {
    gifts.push({ code, ...data });
  }
  res.json({ gifts });
});

app.delete("/admin/gift/:code", requireAdmin, (req, res) => {
  const code = req.params.code;
  if (!giftCodes.has(code)) {
    return res.status(404).json({ error: "gift code not found" });
  }
  giftCodes.delete(code);
  saveGiftCodes();
  res.json({ ok: true, deleted: code });
});

// --- Invite System ---
// BUG-077: Don't reveal whether a SecureID exists (user enumeration).
// Always return the same response regardless of existence.
app.get("/invite/:secureId", (req, res) => {
  const secureId = sanitize(req.params.secureId);
  res.json({ secureId, ok: true });
});

app.post("/invite/accepted", (req, res) => {
  const { inviterSecureId, newUserSecureId } = req.body;
  if (!inviterSecureId || !newUserSecureId) {
    return res.status(400).json({ error: "missing inviterSecureId or newUserSecureId" });
  }
  // Send FCM push to inviter if they have a token
  const fcmToken = fcmTokens.get(inviterSecureId);
  if (fcmToken && fcm.isInitialized()) {
    fcm.sendDataMessage(fcmToken, {
      type: "INVITE_ACCEPTED",
      newUserSecureId,
      message: newUserSecureId + " joined SecureCall and added you as a contact!"
    });
    console.log("[INVITE] Accepted notification sent to", inviterSecureId, "from", newUserSecureId);
  }
  // Also send via WebSocket if online
  sendToClient(inviterSecureId, {
    type: "INVITE_ACCEPTED",
    newUserSecureId,
    message: newUserSecureId + " joined SecureCall!"
  });
  res.json({ ok: true });
});

// --- Google Play Billing: Purchase Verification + Code Generation ---
app.post("/billing/verify-purchase", requireAdmin, async (req, res) => {
  const { purchase_token, product_id, package_name } = req.body;

  if (!purchase_token || !product_id || !package_name) {
    return res.status(400).json({ error: "missing fields: purchase_token, product_id, package_name" });
  }

  // Determine tier from product_id
  let tier = "premium";
  if (product_id.includes("pro") && !product_id.includes("premium")) {
    tier = "pro";
  }

  // Google Play Developer API verification
  // Requires GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64 env var (base64-encoded JSON key)
  const serviceAccountB64 = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64;
  if (serviceAccountB64) {
    try {
      const { google } = require("googleapis");
      const keyJson = JSON.parse(Buffer.from(serviceAccountB64, "base64").toString("utf8"));
      const auth = new google.auth.GoogleAuth({
        credentials: keyJson,
        scopes: ["https://www.googleapis.com/auth/androidpublisher"]
      });
      const androidpublisher = google.androidpublisher({ version: "v3", auth });

      // For one-time products (activation codes + lifetime), use products.get
      const result = await androidpublisher.purchases.products.get({
        packageName: package_name,
        productId: product_id,
        token: purchase_token
      });

      if (result.data.purchaseState !== 0) {
        return res.status(403).json({ error: "purchase_not_completed", state: result.data.purchaseState });
      }

      console.log("[BILLING] Purchase verified via Google API:", product_id, "state:", result.data.purchaseState);
    } catch (e) {
      console.error("[BILLING] Google API verification failed:", e.message);
      return res.status(500).json({ error: "verification_failed", detail: e.message });
    }
  } else {
    // No service account configured — accept in development, log warning
    console.warn("[BILLING] No GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64 set — skipping verification (dev mode)");
  }

  // Generate activation code
  const code = "PREM-" + crypto.randomBytes(4).toString("hex").toUpperCase();
  const expires = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000); // 1 year to redeem

  giftCodes.set(code, {
    tier,
    note: `Purchased via Google Play (${product_id})`,
    created: new Date().toISOString(),
    expires: expires.toISOString(),
    used: false,
    usedBy: null,
    purchaseToken: purchase_token
  });

  saveGiftCodes();
  console.log("[BILLING] Activation code generated:", code.substring(0, 4) + "****", "tier:", tier, "product:", product_id);

  res.json({
    code,
    tier,
    expires: expires.toISOString(),
    product_id
  });
});

// --- SIWE (Sign-In with Ethereum) — cryptographic wallet verification ---
const siweChallenges = new Map(); // nonce → { deviceId, message, createdAt }
const SIWE_TTL_MS = 5 * 60 * 1000; // 5 minutes

// Cleanup expired challenges + anti-spam trackers every 60s
setInterval(() => {
  const now = Date.now();
  for (const [nonce, data] of siweChallenges) {
    if (now - data.createdAt > SIWE_TTL_MS) siweChallenges.delete(nonce);
  }
  // Purge stale rejection tracker entries (>1h old)
  for (const [clientId, data] of rejectionTracker) {
    if (now - data.firstSeen > 3600000) rejectionTracker.delete(clientId);
  }
  // Purge stale IP attempt lists (>2min since last attempt)
  for (const [ip, attempts] of ipConnectionAttempts) {
    const recent = attempts.filter(t => now - t < 120000);
    if (recent.length === 0) ipConnectionAttempts.delete(ip);
    else ipConnectionAttempts.set(ip, recent);
  }
}, 60000);

app.get("/siwe/challenge", (req, res) => {
  const deviceId = (req.query.deviceId || "").trim();
  if (!deviceId) return res.status(400).json({ error: "missing_device_id" });

  const nonce = crypto.randomBytes(32).toString("hex");
  const issuedAt = new Date().toISOString();
  const message =
    "SecureCall wants you to verify your Ethereum wallet.\n\n" +
    "Device: " + deviceId + "\n" +
    "Nonce: " + nonce + "\n" +
    "Issued At: " + issuedAt + "\n\n" +
    "This request will expire in 5 minutes.";

  siweChallenges.set(nonce, { deviceId, message, createdAt: Date.now() });
  console.log("[SIWE] Challenge issued for", deviceId, "nonce:", nonce.substring(0, 12) + "...");
  res.json({ nonce, message });
});

app.post("/siwe/verify", async (req, res) => {
  const { walletAddress, signature, nonce, deviceId } = req.body;

  // Validate input
  if (!walletAddress || !signature || !nonce || !deviceId) {
    return res.status(400).json({ success: false, error: "missing_fields" });
  }
  if (!walletAddress.match(/^0x[0-9a-fA-F]{40}$/)) {
    return res.status(400).json({ success: false, error: "invalid_address" });
  }

  // Check nonce
  const challenge = siweChallenges.get(nonce);
  if (!challenge) {
    return res.json({ success: false, error: "invalid_nonce" });
  }
  if (challenge.deviceId !== deviceId) {
    return res.json({ success: false, error: "device_mismatch" });
  }
  if (Date.now() - challenge.createdAt > SIWE_TTL_MS) {
    siweChallenges.delete(nonce);
    return res.json({ success: false, error: "challenge_expired" });
  }

  // Invalidate nonce immediately (replay protection)
  siweChallenges.delete(nonce);

  // Verify signature via ethers.verifyMessage
  try {
    const recovered = ethers.verifyMessage(challenge.message, signature);
    if (recovered.toLowerCase() !== walletAddress.toLowerCase()) {
      console.log("[SIWE] Signature mismatch: recovered=" + recovered + " expected=" + walletAddress);
      return res.json({ success: false, error: "signature_invalid" });
    }
  } catch (e) {
    console.error("[SIWE] Signature verification error:", e.message);
    return res.json({ success: false, error: "signature_invalid" });
  }

  // Check wallet binding
  const existing = walletMappings.find(w => w.wallet.toLowerCase() === walletAddress.toLowerCase());
  if (existing && existing.clientId !== deviceId && existing.method === "walletconnect") {
    // Already SIWE-bound to another device — reject
    return res.json({ success: false, error: "wallet_bound", boundTo: existing.clientId.substring(0, 8) + "..." });
  }
  // If manual-bound to another device → SIWE overrides (verified > unverified)

  // Verify IFR balance
  let result;
  try {
    result = await verifyIfrLock(walletAddress);
  } catch (e) {
    console.error("[SIWE] Balance check error:", e.message);
    return res.json({ success: false, error: "balance_check_failed" });
  }

  // SIWE signature is valid — always bind wallet to device (even if insufficient IFR).
  // This way, when user buys IFR later, the 24h re-verify will pick up the new balance.
  const tier = result.success ? result.tier : "";
  const amount = result.lockedAmount || "0";

  const idx = walletMappings.findIndex(w => w.wallet.toLowerCase() === walletAddress.toLowerCase());
  const mapping = {
    wallet: walletAddress.toLowerCase(),
    clientId: deviceId,
    tier: tier,
    method: "walletconnect",
    lastVerified: Date.now(),
    verifiedAt: existing?.verifiedAt || Date.now()
  };
  if (idx >= 0) walletMappings[idx] = mapping;
  else walletMappings.push(mapping);
  saveWalletMappings();

  if (result.success) {
    console.log("[SIWE] Wallet verified:", walletAddress, "→", tier, "(", amount, "IFR) device:", deviceId);
    res.json({ success: true, tier: tier, lockedAmount: amount, walletBound: true });
  } else {
    console.log("[SIWE] Wallet bound (insufficient):", walletAddress, "(", amount, "IFR) device:", deviceId);
    res.json({ success: false, tier: "", lockedAmount: amount, error: "insufficient", walletBound: true });
  }
});

console.log("[SIWE] Endpoints ready: GET /siwe/challenge, POST /siwe/verify");

// --- Health Check Endpoint ---
app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    uptime: Math.round(process.uptime()),
    timestamp: new Date().toISOString()
  });
});

// --- Metrics Endpoint (admin only) ---
app.get("/metrics", requireAdmin, (req, res) => {
  const used = process.memoryUsage();
  res.json({
    memory: {
      rss: Math.round(used.rss / 1024 / 1024) + " MB",
      heapTotal: Math.round(used.heapTotal / 1024 / 1024) + " MB",
      heapUsed: Math.round(used.heapUsed / 1024 / 1024) + " MB"
    },
    uptime: Math.round(process.uptime()) + " seconds",
    activeConnections: wss.clients.size,
    registeredClients: clientIds.size,
    activeSessions: routingTable.size,
    fcmEnabled: fcm.isInitialized(),
    fcmTokensStored: fcmTokens.size
  });
});

// --- Stripe Payment Routes (disabled if STRIPE_SECRET_KEY not set) ---
try {
  const stripeHandler = require('./payments/stripe_handler');
  // Pass activationCodes reference so new codes from purchases are usable immediately
  stripeHandler.setupRoutes(app, activationCodes);
} catch (e) {
  console.warn("[STRIPE] Could not load stripe_handler:", e.message);
}

// --- Custom Call ID API ---
try {
  customIds.setupRoutes(app, requireAdmin);
} catch (e) {
  console.warn("[CUSTOM-ID] Could not load routes:", e.message);
}

// --- License Pricing API ---
const licenses = require('./licenses');

app.get('/licenses/status', (req, res) => {
  res.json(licenses.getStatus());
});

app.post('/admin/simulate-sale', requireAdmin, (req, res) => {
  const { tier, count } = req.body;
  if (!tier || !['pro_lifetime', 'premium_lifetime'].includes(tier)) {
    return res.status(400).json({ error: 'Invalid tier' });
  }
  const n = Math.min(count || 1, 20);
  for (let i = 0; i < n; i++) licenses.recordSale(tier);
  res.json({ ok: true, simulated: n, status: licenses.getStatus() });
});

app.post('/admin/reset-licenses', requireAdmin, (req, res) => {
  licenses.LICENSES.pro_lifetime.sold = 0;
  licenses.LICENSES.premium_lifetime.sold = 0;
  licenses.saveLicenses();
  console.log('[LICENSES] Reset to 0 by admin');
  res.json({ ok: true, status: licenses.getStatus() });
});

// Rate limit: 5 checkout requests per IP per 10 minutes
const checkoutRateLimits = new Map();
function checkoutRateLimit(req, res, next) {
  const ip = getClientIp(req);
  const now = Date.now();
  if (!checkoutRateLimits.has(ip)) checkoutRateLimits.set(ip, []);
  const attempts = checkoutRateLimits.get(ip);
  while (attempts.length > 0 && now - attempts[0] > 600000) attempts.shift();
  if (attempts.length >= 5) {
    return res.status(429).json({ error: "rate_limited", retry_after_seconds: 600 });
  }
  attempts.push(now);
  next();
}

app.post('/stripe/create-dynamic-checkout', checkoutRateLimit, async (req, res) => {
  const { tier } = req.body;
  if (!tier || !['pro_lifetime', 'premium_lifetime'].includes(tier)) {
    return res.status(400).json({ error: 'Invalid tier' });
  }
  const price = licenses.getCurrentPrice(tier);
  if (price === null) {
    return res.status(410).json({ error: 'Sold out' });
  }
  const secretKey = process.env.STRIPE_SECRET_KEY;
  if (!secretKey) {
    return res.status(503).json({ error: 'Stripe not configured' });
  }
  try {
    const stripe = require('stripe')(secretKey);
    const lic = licenses.LICENSES[tier];
    const session = await stripe.checkout.sessions.create({
      line_items: [{
        price_data: {
          currency: 'eur',
          product: lic.stripeProductId,
          unit_amount: price
        },
        quantity: 1
      }],
      mode: 'payment',
      success_url: 'https://stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}',
      cancel_url: 'https://stealthx.tech/#pricing',
      metadata: { tier, type: 'lifetime_dynamic' },
      payment_method_types: ['card', 'klarna', 'link']
    });
    res.json({ url: session.url, sessionId: session.id, price });
  } catch (err) {
    console.error('[LICENSES] Checkout error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// --- Start Server ---
const PORT = process.env.PORT || 8080;
server.listen(PORT, "0.0.0.0", () => {
  console.log(`[SIGNAL] Server running on port ${PORT}`);
  console.log(`[SIGNAL] WebSocket endpoint: ws://0.0.0.0:${PORT}/signal`);
  console.log(`[SIGNAL] Health check: http://0.0.0.0:${PORT}/health`);
});

// --- Graceful Shutdown ---
process.on('SIGTERM', () => {
  console.log('[SIGNAL] SIGTERM received, shutting down gracefully');
  // BUG-080: Close all WebSocket connections before shutting down
  for (const [connId, client] of clients) {
    try {
      client.ws.close(1001, "Server shutting down");
    } catch (e) {}
  }
  server.close(() => {
    console.log('[SIGNAL] Server closed');
    process.exit(0);
  });
  // Force exit after 10s if connections don't close cleanly
  setTimeout(() => {
    console.warn('[SIGNAL] Forcing exit after timeout');
    process.exit(1);
  }, 10000);
});
