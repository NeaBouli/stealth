# SecureCall TODO Tracker

| ID | Task | Status | Priority | Notes |
|----|------|--------|----------|-------|
| TODO-001 | FCM push notifications | DONE | High | Client ✅ Server ✅ Verified: force-stopped S10 woken by FCM, IncomingCallActivity launched |
| TODO-002 | WalletConnect v2 integration | OPEN | High | UI ready, SDK not integrated |
| TODO-003 | TURN credential rotation | OPEN | Medium | Metered.ca API, /ice-servers endpoint exists |
| TODO-004 | Release APKs all 3 flavors | OPEN | High | Keystore exists, CI/CD needed |
| TODO-005 | Play Store listing | OPEN | High | Checklist at docs/PLAY_STORE_CHECKLIST.md |
| TODO-006 | Activation code premium unlock | VERIFIED | High | Fully implemented and working |
| TODO-007 | Wire real encryption (replace mock handshake) | OPEN | Critical | GhostNet uses placeholder crypto in debug |
| TODO-008 | HKDF key derivation (CRYPTO-03) | OPEN | High | Uses random bytes instead of HKDF-SHA256 |
| TODO-009 | Samsung background Activity launch | OPEN | Medium | fullScreenIntent unreliable on Samsung locked screen |
| TODO-010 | Self-hosted TURN (coturn) | OPEN | Medium | Config exists in deployment/coturn_config/ |

## Completed

- TODO-006: Activation code feature verified working. UI in SettingsFragment, backend validates codes, tier persisted and applied on restart. Re-verified 2026-03-19: TEST-PRO1-CODE on Tab S4 Free → PRO upgrade confirmed via TierManager log.
