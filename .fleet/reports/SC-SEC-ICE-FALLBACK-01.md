id: SC-SEC-ICE-FALLBACK-01
status: ok
worker: codex (Solo Mode after claude -> kimi -> grok unavailable)
branch: agent/codex/SC-UI-086-PR102
summary: |
  Mapped and fixed the Android ICE hop REGISTERED -> IceServerFetcher ->
  WebRtcManager -> PeerConnection. Removed embedded public OpenRelay hosts and
  credentials. Direct mode retains configured first-party STUN; relay-only mode
  rejects initialization without dynamically supplied TURN credentials.
files:
  - client_android/app/src/main/java/com/securecall/app/net/IceServerFetcher.kt
  - client_android/app/src/main/java/com/securecall/app/net/WebRtcManager.kt
  - client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt
  - client_android/app/src/test/java/com/securecall/app/net/WebRtcRelayPolicyTest.kt
  - docs/architecture/MAP.md
tests: |
  Focused Free unit + Pro/Premium compile -> PASS (BUILD SUCCESSFUL)
  Complete :app:testFreeDebugUnitTest + :app:lintFreeDebug -> PASS
  Production-source OpenRelay marker scan -> PASS, zero matches
  git diff --check -> PASS
risks: Physical two-device call validation remains a release gate.
security: Embedded third-party TURN credentials removed; relay-only path now fails closed.
next: Independent security review when a fleet worker is available; root operator runtime rotation remains separate.

