verdict: ok
reviewer: self (Solo Mode)

Reviewed the complete bounded diff after green tests. No public relay credential or host remains
in Android production sources. TURN is accepted only from the existing authenticated REGISTERED
cache; no new protocol or credential source was introduced. Direct mode has a non-secret
first-party STUN fallback. Relay-only initialization and retry both reject missing/STUN-only
server lists before offer creation. WebSocketService tears down the call and notifies the peer
instead of continuing with an unusable PeerConnection. No UI, crypto, billing, backend, provider,
deployment, or runtime configuration changed. Independent review remains queued because all fleet
workers were unavailable at the task boundary.

