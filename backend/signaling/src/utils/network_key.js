"use strict";

// Rate-limit key for a peer address: IPv4 as-is (IPv4-mapped IPv6 collapses to IPv4),
// IPv6 aggregated to its /64, because a single client usually controls a whole /64.
function networkKey(ip) {
  if (typeof ip !== "string" || ip.length === 0) return null;
  const mapped = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i.exec(ip);
  if (mapped) return mapped[1];
  if (!ip.includes(":")) return ip;
  const address = ip.toLowerCase().split("%")[0];
  const doubleColon = address.includes("::");
  const [head, tail = ""] = address.split("::");
  const left = head ? head.split(":") : [];
  let right = doubleColon && tail ? tail.split(":") : [];
  // An embedded dotted-quad tail occupies the last two hextets.
  if (right.length && right[right.length - 1].includes(".")) right = [...right.slice(0, -1), "0", "0"];
  const hextets = doubleColon
    ? [...left, ...Array(Math.max(0, 8 - left.length - right.length)).fill("0"), ...right]
    : left;
  if (hextets.length < 4) return address;
  return `${hextets.slice(0, 4).map(part => part.replace(/^0+(?=.)/, "")).join(":")}::/64`;
}

module.exports = { networkKey };
