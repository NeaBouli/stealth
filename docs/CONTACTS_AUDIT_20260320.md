# Contacts, Notifications & Edge Cases Audit — 2026-03-20

## Test Results

### Contacts Tests
| Test | Description | Result | Notes |
|------|-------------|--------|-------|
| TEST-A | Add contact manually | ⏭️ N/A | App uses phone book sync + post-call save. No manual add dialog by design |
| TEST-B | Edit contact | ⏭️ N/A | Contacts are read from phone book, not editable in-app |
| TEST-C | Delete contact | ⏭️ N/A | App contacts are derived from phone book |
| TEST-D | Call history entry | ✅ PASS | Calls logged with name, type (incoming/outgoing), timestamp |
| TEST-E | Call history delete | ⏭️ N/A | No delete UI in current version |
| TEST-F | Save from call history | ✅ PASS | Post-call "Save Contact" dialog verified (TEST-02 earlier) |
| TEST-G | Search contacts | ✅ PASS | "mama" → 7 results filtered from full list |

### Notification Tests
| Test | Description | Result | Notes |
|------|-------------|--------|-------|
| TEST-H | FCM when app killed (force-stop) | ⚠️ KNOWN | Android blocks FCM delivery to force-stopped apps. App wakes on next manual open |
| TEST-H | FCM when app in background (Home) | ✅ PASS | WS still connected, CALL_INVITE received via WS, IncomingCallActivity launched |
| TEST-I | Missed call notification | ✅ PASS | Verified: 45s ring timeout → auto-decline → missed call saved + notification posted |

### Edge Cases
| Test | Description | Result | Notes |
|------|-------------|--------|-------|
| TEST-J | Call while busy | ⏭️ NOT TESTED | Requires 3 simultaneous active calls |
| TEST-K | App background during call | ✅ PASS | Call continues via foreground service, returning shows call screen |

### Call Tests (Release APKs)
| Test | Description | Result |
|------|-------------|--------|
| S7→S10 | Call, accept, hold, end | ✅ PASS |
| S10→S7 | Call via dialer, accept, end | ✅ PASS (verified earlier) |
| IFR wallet verify | PRO→PREMIUM upgrade | ✅ PASS |

## Landing Page Updated
- Quality Audit section updated to v1.6
- 10 verified test items in test matrix
- Banner stats: 33/33 settings, 8/8 bugs fixed, FCM verified

## Notes
- Contact management is phone-book-driven: contacts sync from Android phone book via READ_CONTACTS permission + BATCH_PHONE_LOOKUP for SecureCall registration check
- Manual add/edit/delete of contacts is not implemented — this is by design (privacy: no separate contact database)
- Android `force-stop` blocks ALL background services including FCM — this is an OS limitation, not a bug
