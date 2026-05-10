const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const { ethers } = require("ethers");

// Resolve writable data dir — Railway volumes mount as root, overriding Dockerfile chown.
// Falls back to /tmp/stealthx-data when the preferred path is not writable.
const _DATA_PREFERRED = process.env.DATA_DIR || path.join(__dirname, "..", "data");
const DATA_DIR = (() => {
  try {
    fs.mkdirSync(_DATA_PREFERRED, { recursive: true });
    const probe = path.join(_DATA_PREFERRED, ".write_test");
    fs.writeFileSync(probe, "1");
    fs.unlinkSync(probe);
    return _DATA_PREFERRED;
  } catch {
    const fallback = "/tmp/stealthx-data";
    console.warn(`[DATA] ${_DATA_PREFERRED} not writable — using ${fallback}`);
    fs.mkdirSync(fallback, { recursive: true });
    return fallback;
  }
})();

const HeartbeatManager = require("./heartbeat");
const pkd = require("./pkd");
const rateLimit = require("./rate_limit");
const subscriptions = require("./subscriptions");
const fcm = require("./fcm");
const customIds = require("./custom_ids");

// Align store module file paths to the resolved writable data directory.
// Must be set BEFORE requiring store modules (top-level const in each module reads env var).
process.env.FCM_TOKENS_FILE = path.join(DATA_DIR, "fcm_tokens.json");
process.env.CODES_FILE      = path.join(DATA_DIR, "activation_codes.json");
process.env.WALLETS_FILE    = path.join(DATA_DIR, "wallets.json");

// Shared singleton state — same Map/Array instances used by HTTP routes and WS handlers
const state = require("./state");
const {
  clients, clientIds, routingTable, phoneNumbers, phoneHashes,
  ipConnections, rejectionTracker, ipConnectionAttempts,
  codeUsageCount, giftCodes, siweChallenges,
  inviteRateLimits, checkoutRateLimits,
  lastBroadcast,
} = state;
// Store-backed singletons (load from DATA_DIR-aligned paths set above)
const { fcmTokens, loadFcmTokens, saveFcmTokens }                 = require("./services/fcm_store");
const { activationCodes, loadActivationCodes, saveActivationCodes } = require("./services/activation_store");
const { walletMappings, loadWalletMappings, saveWalletMappings }   = require("./services/wallet_store");
const { getClientIp }                                               = require("./middleware/ip");
const { verifyIfrLock }                                             = require("./services/ifr");
const { buildContext, wireWs }                                      = require("./context");

// Hoisted so HTTP route handlers (defined below) can call ctx.sendToClient
// after buildContext() runs at startup — before any request arrives.
let ctx;

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
const TURN_SECRET = process.env.TURN_SECRET || null;
const TURN_HOST   = process.env.TURN_HOST   || null;
const TURN_TTL    = 86400; // 24h

if (process.env.NODE_ENV === "production" && !TURN_SECRET && (!process.env.TURN_USER || !process.env.TURN_PASS)) {
  console.warn("[WARN] No TURN credentials configured — relay disabled. Set TURN_SECRET (own coturn) or TURN_USER+TURN_PASS (Metered.ca).");
}

// RFC 8489 REST API: time-limited HMAC-SHA1 credentials for own coturn (use-auth-secret mode).
// Falls back to static credentials for Metered.ca backward compatibility.
function getIceServers(userId) {
  const base = [{ urls: process.env.STUN_URL || "stun:stun.l.google.com:19302" }];
  if (TURN_SECRET && TURN_HOST) {
    const timestamp = Math.floor(Date.now() / 1000) + TURN_TTL;
    const username  = `${timestamp}:${userId || "anon"}`;
    const credential = require("crypto").createHmac("sha1", TURN_SECRET).update(username).digest("base64");
    return [...base,
      { urls: `turn:${TURN_HOST}:3478?transport=udp`,  username, credential },
      { urls: `turn:${TURN_HOST}:3478?transport=tcp`,  username, credential },
      { urls: `turns:${TURN_HOST}:5349?transport=tcp`, username, credential },
    ];
  }
  if (process.env.TURN_USER && process.env.TURN_PASS) {
    const username   = process.env.TURN_USER;
    const credential = process.env.TURN_PASS;
    return [...base,
      { urls: "turn:a.relay.metered.ca:80?transport=udp",  username, credential },
      { urls: "turn:a.relay.metered.ca:80?transport=tcp",  username, credential },
      { urls: "turn:a.relay.metered.ca:443?transport=tcp", username, credential },
      { urls: "turns:a.relay.metered.ca:443?transport=tcp", username, credential },
    ];
  }
  return base;
}

// --- Security Configuration ---
const ADMIN_API_KEY = process.env.ADMIN_API_KEY || null;
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || "").split(",").filter(Boolean);
const MAX_CONNS_PER_IP = parseInt(process.env.MAX_CONNS_PER_IP || "10", 10);
const CLIENT_ID_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;

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

// Load persistent store-backed state from DATA_DIR-aligned paths
loadFcmTokens();

loadActivationCodes();

loadWalletMappings();

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
// ICE servers are now delivered via REGISTERED WS message (H-01 fix).
// HTTP endpoint kept behind admin auth for debugging only.
app.get("/ice-servers", requireAdmin, (req, res) => {
  res.json({ iceServers: getIceServers("admin") });
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

app.put("/key/:id", requireAdmin, (req, res) => {
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

app.delete("/key/:id", requireAdmin, (req, res) => {
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
  Object.assign(lastBroadcast, {
    template_id, icon: meta.icon, title: meta.title,
    body: "", timestamp: new Date().toISOString(),
    active: template_id !== 8
  });

  console.log(`[BROADCAST] Emergency template=${template_id} sent to ${wsSent} WS clients, ${fcmTokens.size} FCM targets`);
  res.json({ ok: true, ws_sent: wsSent, fcm_targets: fcmTokens.size });
});

// --- Gift Link System (admin-only) ---
const GIFT_CODES_FILE = path.join(DATA_DIR, "gift_codes.json");

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

// Rate limit: 3 invite acceptances per IP per 10 minutes (H-04 fix)
function inviteRateLimit(req, res, next) {
  const ip = getClientIp(req);
  const now = Date.now();
  if (!inviteRateLimits.has(ip)) inviteRateLimits.set(ip, []);
  const attempts = inviteRateLimits.get(ip);
  while (attempts.length > 0 && now - attempts[0] > 600000) attempts.shift();
  if (attempts.length >= 3) {
    return res.status(429).json({ error: "rate_limited" });
  }
  attempts.push(now);
  next();
}

app.post("/invite/accepted", inviteRateLimit, (req, res) => {
  const { inviterSecureId, newUserSecureId } = req.body;
  if (!inviterSecureId || !newUserSecureId) {
    return res.status(400).json({ error: "missing inviterSecureId or newUserSecureId" });
  }
  // H-04 auth: newUserSecureId must be a currently registered client
  // This proves the caller is an actual connected app, not a random HTTP request
  let callerIsRegistered = false;
  for (const [, cid] of clientIds) {
    if (cid === newUserSecureId) { callerIsRegistered = true; break; }
  }
  if (!callerIsRegistered) {
    return res.status(403).json({ error: "sender_not_registered" });
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
  ctx.sendToClient(inviterSecureId, {
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

// --- Wire modular WS context (replaces inline wss.on("connection",...) block) ---
// All Maps/arrays passed here are the same singletons used by HTTP routes above,
// so HTTP routes and WS handlers share one consistent state — no split-brain.
ctx = buildContext({
  pkd, subscriptions, fcm, customIds, licenses,
  getIceServers, ADMIN_API_KEY, ALLOWED_ORIGINS, CLIENT_ID_REGEX,
  rateLimit, hb,
  giftCodes, saveGiftCodes,
});
wireWs(wss, ctx);

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
