# SecureCall Bug Tracker

| ID | Description | Status | Severity | Fixed In |
|----|-------------|--------|----------|----------|
| BUG-001 | Online status dots inconsistent | FIXED | Medium | 9ef003c |
| BUG-002 | Save Contact disappears in <1s | FIXED | High | 5c4f9cd |
| BUG-003 | Contact deduplication broken | FIXED | High | c9c2bbd |
| BUG-004 | IFR wallet verify shows no token count | FIXED | High | c615a5b |
| BUG-005 | Block screenshots not working on all Activities/tiers | FIXED | Medium | 3597cc9 |
| BUG-006 | WireGuard VPN non-functional (no test config) | KNOWN STUB | Low | - |

## Fix Details

### BUG-001: Online status dots inconsistent (FIXED)
- Reduced STATUS_REFRESH_INTERVAL from 30s to 15s for faster dot transitions
- Previous fix (2c3c697) was reverted due to regression — it cleared cache on
  resume and WS disconnect, preventing dots from ever showing green
- New fix (9ef003c): single-line change, no cache clearing, no WS state clearing
- Verified: green→red within 15s on disconnect, red→green within 15s on reconnect

### BUG-002: Save Contact dialog disappears in <1s (FIXED)
- Proximity wake lock released BEFORE dialog caused Samsung screen state transitions
- Moved releaseProximitySensor() to dialog button handlers
- Added FLAG_KEEP_SCREEN_ON while dialog is visible
- Made isEnding and callback fields volatile for thread safety

### BUG-003: Contact deduplication broken (FIXED)
- ContactRepository.save() blindly appended without duplicate check
- Now checks by phoneOrId, secureId, and normalized phone before adding
- Post-call save dialog stores phone as primary key with SecureID as metadata
- Enables proper dedup with phone book contacts

### BUG-004: IFR wallet verify shows no token count (FIXED)
- Token balance was not persisted when verification returned "insufficient"
- Now stores balance even on failure so UI can display it
- Status shows "Wallet: 0x77e9...0A6e — 500 IFR held"
- Added specific error message for Ethereum RPC failures

### BUG-005: Block screenshots not working on all Activities/tiers (FIXED)
- SettingsActivity was missing FLAG_SECURE entirely
- Free tier had screenshot toggle disabled — now enabled as opt-in (default OFF)
- All 4 Activities now consistently apply FLAG_SECURE
