"use strict";

const crypto = require("crypto");
const net = require("net");

// Privacy-safe representation of a client IP for LOG OUTPUT ONLY.
// Rate limiting, connection counting and abuse buckets keep using the real IP
// (middleware/ip.js); only what is written to stdout/log files is reduced:
//   - IPv4 (incl. IPv4-mapped IPv6) -> a.b.c.0/24
//   - IPv6                          -> first 48 bits, e.g. 2001:db8:1::/48
// With a pepper (LOG_IP_PEPPER, else ID_HASH_PEPPER) a short domain-separated
// HMAC tag is appended so operators can correlate lines of the same client
// without the full address being recoverable from the log.

function expandIpv6(address) {
  const [head, tail = ""] = address.split("::");
  const headParts = head ? head.split(":") : [];
  const tailParts = tail ? tail.split(":") : [];
  const missing = 8 - headParts.length - tailParts.length;
  if (!address.includes("::") && headParts.length !== 8) return null;
  if (missing < 0) return null;
  return [...headParts, ...Array(missing).fill("0"), ...tailParts].map((part) => part || "0");
}

function truncateIp(ip) {
  if (typeof ip !== "string" || ip.length === 0) return "unknown";
  let address = ip.trim();
  const zone = address.indexOf("%");
  if (zone !== -1) address = address.slice(0, zone);

  const mapped = address.match(/^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i);
  if (mapped) address = mapped[1];

  if (net.isIPv4(address)) {
    const [a, b, c] = address.split(".");
    return `${a}.${b}.${c}.0/24`;
  }
  if (net.isIPv6(address)) {
    const parts = expandIpv6(address.toLowerCase());
    if (!parts) return "unknown";
    const prefix = parts.slice(0, 3).map((part) => part.replace(/^0+(?=.)/, ""));
    return `${prefix.join(":")}::/48`;
  }
  return "unknown";
}

function logIp(ip, env = process.env) {
  const reduced = truncateIp(ip);
  const pepper = env.LOG_IP_PEPPER || env.ID_HASH_PEPPER;
  if (!pepper || reduced === "unknown") return reduced;
  const tag = crypto.createHmac("sha256", pepper).update(`log-ip:${String(ip).trim()}`).digest("hex").slice(0, 10);
  return `${reduced}#${tag}`;
}

module.exports = { logIp, truncateIp };
