# SC-SEC-ICE-FALLBACK-01

## Objective

Remove the embedded public OpenRelay TURN fallback from SecureCall Android and make the
REGISTERED WebSocket payload the only source of TURN credentials.

## Architecture boundary

Extend `docs/architecture/MAP.md` with the ICE lane and change only this hop:
`REGISTERED payload -> IceServerFetcher -> WebRtcManager -> PeerConnection`.

## Allowed files

- `docs/architecture/MAP.md`
- `client_android/app/src/main/java/com/securecall/app/net/IceServerFetcher.kt`
- `client_android/app/src/main/java/com/securecall/app/net/WebRtcManager.kt`
- `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
- focused tests under `client_android/app/src/test/java/com/securecall/app/net/`
- `.fleet/reports/SC-SEC-ICE-FALLBACK-01.md`

## Acceptance criteria

1. No public TURN host, username, or password remains in Android production sources.
2. Dynamic TURN credentials come only from the authenticated REGISTERED message cache.
3. A normal call may use the configured first-party STUN URL when TURN is unavailable.
4. Relay-only mode fails closed when no dynamically supplied TURN server exists.
5. WebSocketService does not create/send an offer after failed ICE initialization.
6. Focused unit tests cover fallback selection and relay-only fail-closed behavior.
7. Relevant Android unit tests, compilation, and `git diff --check` pass.

## Guardrails

- Do not change signaling protocol, cryptography, UI, billing, VPN, or backend behavior.
- Do not add another public relay provider or embedded credential.
- Do not weaken existing tests.
- No merge to main, deployment, secret access, or provider action.

