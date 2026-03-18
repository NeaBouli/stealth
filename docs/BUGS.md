# SecureCall Bug Tracker

| ID | Description | Status | Severity | Fixed In |
|----|-------------|--------|----------|----------|
| BUG-001 | Online status dots inconsistent | FIXED | Medium | 2c3c697 |
| BUG-002 | Save Contact disappears in <1s | FIXED | High | 5c4f9cd |
| BUG-003 | Contact deduplication broken | FIXED | High | c9c2bbd |
| BUG-004 | IFR wallet verify shows no token count | FIXED | High | c615a5b |
| BUG-005 | Block screenshots not working on all Activities/tiers | FIXED | Medium | 3597cc9 |
| BUG-006 | WireGuard VPN non-functional (no test config) | KNOWN STUB | Low | - |

## Fix Details

### BUG-001: Online status dots inconsistent (FIXED)
- Stale cached online state shown on resume — cleared on tab visit
- Reduced refresh interval from 30s to 15s
- Online state updated atomically to prevent adapter inconsistency
- Dots cleared when WebSocket disconnected

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
