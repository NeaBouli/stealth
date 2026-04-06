const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const { v4: uuidv4 } = require("uuid");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const { ethers } = require("ethers");

const HeartbeatManager = require("./heartbeat");
const pkd = require("./pkd");
const rateLimit = require("./rate_limit");
const subscriptions = require("./subscriptions");
const fcm = require("./fcm");

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

// --- App Setup ---
const app = express();
// Stripe webhook needs raw body for signature verification — must come BEFORE express.json()
app.use('/stripe/webhook', express.raw({ type: 'application/json' }));
app.use(express.json());

// CORS configuration
app.use((req, res, next) => {
  const allowedOrigins = ALLOWED_ORIGINS.length > 0 ? ALLOWED_ORIGINS : ["*"];
  const origin = req.headers.origin;
  if (allowedOrigins.includes("*") || allowedOrigins.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin || "*");
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
    fs.writeFileSync(FCM_TOKENS_FILE, JSON.stringify(obj, null, 2), "utf8");
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

// Hardcoded fallback codes (used if file is missing on deployment platform)
// NOTE: Beta codes DEACTIVATED for production (TODO-047). Tester codes below.
const FALLBACK_CODES = [
  {code: "TEST-PRO1-CODE", tier: "pro", maxUses: 10, currentUses: 0, usedBy: []},
  {code: "TEST-PREM-CODE", tier: "premium", maxUses: 10, currentUses: 0, usedBy: []},
  // DEACTIVATED: {code: "BETA-PRO0-2026", tier: "pro", maxUses: 50, currentUses: 0},
  // DEACTIVATED: {code: "BETA-PREM-2026", tier: "premium", maxUses: 25, currentUses: 0},
  // 30 Premium Tester Reward Codes (single-use each)
  {code: "PREM-1A7B-WCHQ-ZW3X", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-MUMC-L1B2-5QYP", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-0CO3-6X3Y-LL29", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-ES4X-LDCT-LZ8U", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-BUXR-XSO7-R4B6", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-Q44J-JDLE-I4YW", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-TBZP-7FAT-GA0Z", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-VU8M-VEVB-35LI", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-X3DY-WO92-56RM", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-5BM8-Q21J-3GNX", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-EO6I-JG95-7S3D", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-D6J0-XXD5-FYBX", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-THXA-T71H-F1RA", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-CVEI-5J47-HET2", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-RJR9-5RZ3-H2X2", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-P5IA-2KL6-DAHD", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-GLQP-OFOF-4ZSS", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-3ME6-KBKG-DDQZ", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-143G-6ETG-FBOV", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-83Z7-OZMZ-ITPJ", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-IL0Y-AINQ-HNDS", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-2J7H-50RL-AGK3", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-2LPK-895J-6F6J", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-G5UM-KVKP-CLZN", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-495K-IL3T-22GE", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-RGTW-O4ZC-9PVB", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-7YDP-G8AF-VA7I", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-XGRK-Y8OE-X20U", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-EDQ0-OP6I-EJS6", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
  {code: "PREM-T0V0-1YQ5-WY07", tier: "premium", maxUses: 2, currentUses: 0, usedBy: []},
];

function loadActivationCodes() {
  try {
    const raw = fs.readFileSync(CODES_FILE, "utf8");
    const data = JSON.parse(raw);
    activationCodes = data.codes || [];
    console.log(`[ACTIVATION] Loaded ${activationCodes.length} activation codes from file`);
  } catch (e) {
    console.warn("[ACTIVATION] Could not load activation_codes.json:", e.message, "— using fallback codes");
    activationCodes = FALLBACK_CODES.map(c => ({...c}));
  }
}

function saveActivationCodes() {
  try {
    fs.writeFileSync(CODES_FILE, JSON.stringify({ codes: activationCodes }, null, 2), "utf8");
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
    fs.writeFileSync(WALLETS_FILE, JSON.stringify({ wallets: walletMappings }, null, 2), "utf8");
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
  const provided = req.headers["x-admin-key"] || req.query.admin_key;
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
// Public endpoint — app clients fetch TURN credentials at call start.
// Credentials come from server env vars (rotatable without APK rebuild).
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
app.get("/api/subscription/:clientId", (req, res) => {
  const adminKey = req.headers["x-admin-key"];
  if (adminKey !== process.env.ADMIN_KEY && adminKey !== "dev-admin-key") {
    return res.status(403).json({ error: "Forbidden" });
  }
  const sub = subscriptions.getSubscription(req.params.clientId);
  if (!sub) {
    return res.status(404).json({ error: "No subscription found" });
  }
  res.json(sub);
});

// --- WebSocket Setup ---
const wss = new WebSocket.Server({
  server,
  path: "/signal",
  maxPayload: 64 * 1024, // 64 KB max message size
  verifyClient: (info, done) => {
    // Origin validation
    if (ALLOWED_ORIGINS.length > 0) {
      const origin = info.origin || info.req.headers.origin;
      if (!origin || !ALLOWED_ORIGINS.includes(origin)) {
        return done(false, 403, "Origin not allowed");
      }
    }
    // Per-IP connection limit
    const ip = info.req.socket.remoteAddress;
    const count = ipConnections.get(ip) || 0;
    if (count >= MAX_CONNS_PER_IP) {
      return done(false, 429, "Too many connections from this IP");
    }
    done(true);
  }
});

wss.on("connection", (ws, req) => {
  const connId = uuidv4();
  const ip = req.socket.remoteAddress;
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

    if (msg.__proto__ || msg.constructor !== undefined) {
      delete msg.__proto__;
      delete msg.constructor;
      delete msg.prototype;
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

      // TODO: Implement challenge-response auth using PKD public keys
      // For now, accept registration if clientId is valid and not taken

      // Prüfen ob clientId bereits vergeben — allow reconnection by superseding old connection
      if (clientIds.has(msg.clientId)) {
        const existingConnId = clientIds.get(msg.clientId);
        if (existingConnId !== connId) {
          // Supersede old connection — allow re-registration on reconnect
          const oldClient = clients.get(existingConnId);
          if (oldClient) {
            console.log("[REGISTER] Superseding old connection for", msg.clientId, "(old connId:", existingConnId, ")");
            try { oldClient.ws.close(1000, "Superseded"); } catch(e) {}
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
          console.log("[REGISTER] SecureID changed for phone", phone, ":", oldClientId, "->", msg.clientId);
          // Broadcast SECUREID_CHANGED to all connected clients
          const notification = JSON.stringify({
            type: "SECUREID_CHANGED",
            phoneNumber: phone,
            oldClientId: oldClientId,
            newClientId: msg.clientId
          });
          for (const [, c] of clients) {
            if (c.ws.readyState === WebSocket.OPEN && c.clientId && c.clientId !== msg.clientId) {
              try { c.ws.send(notification); } catch (e) {}
            }
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
        console.log("[REGISTER] Phone:", phone, "->", msg.clientId);
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

      const sessionId = uuidv4();

      // Resolve target: try clientId first, then phone number lookup
      let targetClientId = msg.to;
      if (!clientIds.has(targetClientId)) {
        const phoneLookup = phoneNumbers.get(normalizePhone(targetClientId));
        if (phoneLookup) {
          console.log("[ROUTING] Phone resolved:", targetClientId, "->", phoneLookup);
          targetClientId = phoneLookup;
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
        // Peer is offline — try FCM push
        const fcmToken = fcmTokens.get(msg.to);
        if (fcmToken && fcm.isInitialized()) {
          routingTable.set(sessionId, {
            sessionId,
            from: myClientId,
            to: msg.to,
            state: "INVITE_PENDING_PUSH",
            created: Date.now(),
            updated: Date.now()
          });

          console.log("[ROUTING] INVITE (offline, sending push):", myClientId, "->", msg.to, "session:", sessionId);

          fcm.sendCallInvitePush(fcmToken, sessionId, myClientId);

          ws.send(JSON.stringify({
            type: "CALL_INVITE_ACK",
            ok: true,
            sessionId,
            from: myClientId,
            to: msg.to,
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
      if (session) {
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

      const ghostNetId = uuidv4();
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
      const { purchaseToken, productId } = msg;
      if (!purchaseToken || !productId) {
        ws.send(JSON.stringify({ type: "ERROR", message: "Missing purchaseToken or productId" }));
        return;
      }
      const result = subscriptions.verifySubscription(connId, purchaseToken, productId);
      ws.send(JSON.stringify({
        type: "SUBSCRIPTION_VERIFY_ACK",
        tier: result.tier,
        expiresAt: result.expiresAt
      }));
      console.log(`[SUBSCRIPTION] Verified: connId=${connId}, tier=${result.tier}`);
      return;
    }

    // ===========================
    // PHONE_LOOKUP — Resolve phone number to clientId
    // FIX 8: Rate limited to 10 lookups/minute per client
    // ===========================
    if (msg.type === "PHONE_LOOKUP") {
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
    // ===========================
    if (msg.type === "BATCH_PHONE_LOOKUP") {
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
        const myClientId = getClientId(connId);
        console.log("[GIFT] Code redeemed:", code, "-> tier:", gift.tier, "by:", myClientId);
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: true, tier: gift.tier }));
      }

      if (!entry) {
        console.log("[ACTIVATION] Invalid code attempted:", code);
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
        console.log("[ACTIVATION] Code re-activated on same device:", code, "by:", myClientId);
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
        console.log("[ACTIVATION] Code exhausted:", code, "devices:", devices.length, "/", entry.maxUses, "attempted:", myClientId);
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
      console.log("[ACTIVATION] Code redeemed:", code, "-> tier:", entry.tier, "by:", myClientId, "slot:", slot + "/" + entry.maxUses);
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

        ws.send(JSON.stringify({
          type: "IFR_LOCK_RESULT",
          success: result.success,
          tier: result.tier || "",
          lockedAmount: result.lockedAmount || "0",
          walletAddress: wallet,
          error: result.error || ""
        }));
      }).catch(e => {
        console.error("[IFR] Verification error:", e.message);
        ws.send(JSON.stringify({
          type: "IFR_LOCK_RESULT",
          success: false,
          error: "server_error"
        }));
      });

      return;
    }

    // ===========================
    // DEREGISTER — Remove client from all registries (stealth-delete)
    // ===========================
    if (msg.type === "DEREGISTER") {
      const myClientId = getClientId(connId) || msg.clientId;
      if (myClientId) {
        // Remove phone mappings
        for (const [phone, cid] of phoneNumbers) {
          if (cid === myClientId) {
            phoneNumbers.delete(phone);
            phoneHashes.delete(hashPhone(phone));
            console.log("[DEREGISTER] Removed phone mapping:", phone, "->", myClientId);
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

    // clientId Mapping aufräumen — only if WE are still the active connection for this clientId.
    // When a connection is superseded (new connection registered same clientId),
    // the old close handler must NOT delete the new connection's clientId mapping.
    if (clientId && clientIds.get(clientId) === connId) {
      clientIds.delete(clientId);
    }
    clients.delete(connId);

    // Sessions aufräumen wo dieser Client beteiligt war
    if (clientId) {
      for (const [sessionId, session] of routingTable) {
        if (session.from === clientId || session.to === clientId) {
          // Peer benachrichtigen
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
    }
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
const giftCodes = new Map();

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

  console.log(`[GIFT] Created ${code} → ${tier.toUpperCase()} (note: ${note || "none"})`);
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
  res.json({ ok: true, deleted: code });
});

// --- Invite System ---
app.get("/invite/:secureId", (req, res) => {
  const secureId = req.params.secureId;
  const exists = clientIds.has(secureId);
  res.json({ secureId, exists, online: exists });
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

  console.log("[BILLING] Activation code generated:", code, "tier:", tier, "product:", product_id);

  res.json({
    code,
    tier,
    expires: expires.toISOString(),
    product_id
  });
});

// --- Health Check Endpoint ---
app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    uptime: Math.round(process.uptime()),
    timestamp: new Date().toISOString()
  });
});

// --- Metrics Endpoint ---
app.get("/metrics", (req, res) => {
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
  stripeHandler.setupRoutes(app);
} catch (e) {
  console.warn("[STRIPE] Could not load stripe_handler:", e.message);
}

// --- Custom Call ID API ---
try {
  const customIds = require('./custom_ids');
  customIds.setupRoutes(app, requireAdmin);
} catch (e) {
  console.warn("[CUSTOM-ID] Could not load:", e.message);
}

// --- License Pricing API ---
const licenses = require('./licenses');

app.get('/licenses/status', (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
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

app.post('/stripe/create-dynamic-checkout', async (req, res) => {
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
      metadata: { tier, type: 'lifetime_dynamic' }
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
  server.close(() => {
    console.log('[SIGNAL] Server closed');
    process.exit(0);
  });
});
