module.exports = {
  clients: new Map(),
  clientIds: new Map(),
  routingTable: new Map(),
  phoneNumbers: new Map(),
  phoneHashes: new Map(),
  ipConnections: new Map(),
  rejectionTracker: new Map(),
  ipConnectionAttempts: new Map(),
  inviteRateLimits: new Map(),
  checkoutRateLimits: new Map(),
  activationCodes: [],
  codeUsageCount: new Map(),
  giftCodes: new Map(),
  walletMappings: [],
  siweChallenges: new Map(),
  lastBroadcast: {
    template_id: 8,
    icon: "All Clear",
    title: "All Clear",
    body: "All systems operational. No active alerts.",
    timestamp: new Date().toISOString(),
    active: false
  }
};
