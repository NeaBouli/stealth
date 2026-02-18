# SecureCall v0.3 Feature Roadmap

**Target**: Phase 15 — User Experience & Social Features
**Status**: Planning

---

## Feature 1: Contact Invitation System
**Priority**: HIGH | **Effort**: Medium

Enable users to invite friends to SecureCall via multiple channels.

### Requirements
- SMS invitation with app download link
- Share via WhatsApp, Telegram, Email
- Dedicated "Invite Friends" screen accessible from contacts
- Invitation template:
  > "Hi! Join me on SecureCall for encrypted calls: https://play.google.com/store/apps/details?id=com.securecall.free"
- Track pending invitations
- Deep link handling for invitation acceptance

### Implementation
1. `InviteActivity.kt` — Share intent with pre-filled message
2. Contact list: "Invite" button next to contacts without SecureCall
3. Android Sharesheet integration for multi-app sharing
4. Optional: Referral tracking via backend

---

## Feature 2: Online Status & Push Notifications
**Priority**: HIGH | **Effort**: Large

Real-time presence system showing which contacts are online.

### Requirements
- Backend: User presence tracking (online/offline/last seen)
- WebSocket heartbeat for presence updates
- Push notification: "Max is now online"
- Contact list: Green dot indicator for online contacts
- Privacy setting: Hide own online status
- "Last seen" timestamp display

### Implementation
1. Backend: Presence service with WebSocket pub/sub
2. `PresenceManager.kt` — Manage local presence state
3. FCM notifications for contact online events
4. ContactAdapter: Online indicator dot (green/gray)
5. Settings: "Show Online Status" toggle

### Dependencies
- Signaling server presence module
- FCM backend integration

---

## Feature 3: Phone Dialer Integration
**Priority**: MEDIUM | **Effort**: Large

In-app dialer for entering phone numbers directly.

### Requirements
- Numeric keypad UI in the app
- Phone number input with country code
- Real-time search in contacts while typing
- DTMF tone feedback on key press
- Recent numbers quick-access

### Implementation
1. `DialerFragment.kt` — Numeric keypad UI
2. Add "Dialer" tab to bottom navigation (4th tab)
3. `PhoneNumberFormatter.kt` — Format and validate numbers
4. Integrate with ContactsFragment search
5. Launch CallActivity with entered number

### Future Consideration
- Native phone app integration via ConnectionService API
- Default dialer registration (requires user consent)

---

## Feature 4: Call History
**Priority**: HIGH | **Effort**: Medium

Persistent call log showing past calls with details.

### Requirements
- Store call records: contact, direction, duration, timestamp
- Group by date (Today, Yesterday, This Week, Older)
- Call type icons: incoming, outgoing, missed
- Retry call button on each entry
- Delete individual or all call history
- Encryption indicator per call

### Implementation
1. `CallRecord.kt` — Data model for call history entries
2. `CallHistoryRepository.kt` — SQLite/Room persistence
3. Update `CallsFragment.kt` — Load from repository
4. Update `CallActivity.java` — Save call record on end
5. Swipe-to-delete on call history items

### Data Model
```kotlin
data class CallRecord(
    val id: String,
    val contactName: String,
    val phoneNumber: String,
    val direction: CallDirection,  // INCOMING, OUTGOING, MISSED
    val duration: Long,            // seconds
    val timestamp: Long,
    val wasEncrypted: Boolean
)
```

---

## Priority Matrix

| Feature | Priority | Effort | Dependencies |
|---------|----------|--------|-------------|
| Contact Invitation | HIGH | Medium | Play Store listing |
| Online Status | HIGH | Large | Backend presence service |
| Call History | HIGH | Medium | None |
| Phone Dialer | MEDIUM | Large | None |

## Suggested Order
1. **Call History** — No backend dependency, improves UX immediately
2. **Contact Invitation** — Growth enabler, simple implementation
3. **Online Status** — Requires backend work, highest social impact
4. **Phone Dialer** — Nice-to-have, can be deferred to v0.4
