# Final QA Checklist — SecureCall v0.2-beta

Complete pre-launch quality assurance checklist.
Mark each item with `[x]` when verified.

## 1. Build Tests

### Debug Builds
- [ ] FREE Debug APK builds without errors
- [ ] PRO Debug APK builds without errors
- [ ] PREMIUM Debug APK builds without errors

### Release Builds
- [ ] FREE Release AAB builds (with keystore)
- [ ] PRO Release AAB builds
- [ ] PREMIUM Release AAB builds
- [ ] R8/ProGuard mapping files generated
- [ ] APK size < 15 MB per flavor

## 2. Installation Tests

- [ ] FREE APK installs on Android 6.0 (API 23 — min SDK)
- [ ] FREE APK installs on Android 10 (API 29)
- [ ] FREE APK installs on Android 14 (API 34)
- [ ] PRO APK installs correctly
- [ ] PREMIUM APK installs correctly
- [ ] App icons correct per flavor (FREE/PRO/PREMIUM)
- [ ] App names correct ("SecureCall" / "SecureCall Pro" / "SecureCall Premium")

## 3. Security Tests — Anti-Recording Protection

### FLAG_SECURE
- [ ] Screenshot during call = black screen (PRO/PREMIUM)
- [ ] Screen recording during call = black frame (PRO/PREMIUM)
- [ ] FREE: Screenshot toggle OFF by default
- [ ] PRO: Screenshot toggle ON by default
- [ ] PREMIUM: Screenshot toggle forced ON, not changeable

### Screen Recording Detection
- [ ] FREE: Warning toast when recording detected
- [ ] PRO: Blocking dialog ("End Call" / "Continue")
- [ ] PREMIUM: Call terminates immediately

### Microphone Monitoring
- [ ] Detects when another app uses microphone
- [ ] Exclusive audio focus requested at call start
- [ ] Audio focus loss triggers warning

### Spy App Detection
- [ ] TeamViewer detected (install to test)
- [ ] AnyDesk detected (install to test)
- [ ] Known spy app in accessibility services → flagged

### Call Recording App Detection
- [ ] ACR Phone detected (install to test)
- [ ] Call recording app warning shown

### SecureCallMonitor
- [ ] Green lock: No threats
- [ ] Yellow lock: Warnings present
- [ ] Red lock: Critical threats
- [ ] Status updates in real-time during call

## 4. Security Infrastructure

- [ ] Root detection works (test on rooted device or emulator)
- [ ] Emulator detection works (PREMIUM blocks, others warn)
- [ ] Debugger detection works (PREMIUM terminates)
- [ ] Certificate pinning active (PRO/PREMIUM)
- [ ] Hardware keystore check (PREMIUM)

## 5. Encryption Tests

- [ ] Key exchange (X25519) completes successfully
- [ ] XChaCha20-Poly1305 encryption applied to audio frames
- [ ] Nonce uniqueness verified (no reuse)
- [ ] Perfect Forward Secrecy — each call uses unique session key
- [ ] Tampered frames detected and rejected
- [ ] Encryption indicator shows in CallActivity

## 6. Call Flow Tests

### Outgoing Call
- [ ] "New Call" button initiates call
- [ ] Connection state shows "Connecting..."
- [ ] State transitions to "Call Active"
- [ ] Call timer starts

### Incoming Call
- [ ] FCM push notification received
- [ ] Call screen opens from notification
- [ ] Call accept works

### During Call
- [ ] Audio works (both directions)
- [ ] Mute toggle works
- [ ] Speaker toggle works
- [ ] Audio quality acceptable

### End Call
- [ ] End call button stops call
- [ ] Timer stops
- [ ] Transport cleanup (no resource leaks)
- [ ] Returns to previous screen

## 7. Audio Quality Tests

- [ ] Audio clarity good (Opus 48kHz)
- [ ] No echo during call
- [ ] Latency acceptable (< 300ms)
- [ ] No audio dropouts during movement
- [ ] Works with Bluetooth headset
- [ ] Works with wired headphones
- [ ] Works with device speaker
- [ ] Works over WiFi
- [ ] Works over 4G/LTE
- [ ] Works over 5G

## 8. UI/UX Tests

- [ ] Material Design 3 looks professional
- [ ] Dark mode renders correctly
- [ ] Light mode renders correctly
- [ ] Animations smooth (no jank)
- [ ] Bottom navigation works (Calls/Contacts/Settings)
- [ ] FAB button visible and functional
- [ ] Onboarding shows on first launch
- [ ] Onboarding does NOT show on subsequent launches
- [ ] Settings screen displays all options correctly
- [ ] Security settings reflect correct tier behavior

## 9. Tier-Specific Tests

### FREE Tier
- [ ] 15-minute call limit enforced
- [ ] Max 10 contacts enforced
- [ ] Upgrade button visible in Settings
- [ ] Crashlytics reporting enabled
- [ ] Debug features hidden in release build

### PRO Tier
- [ ] Unlimited call duration
- [ ] Unlimited contacts
- [ ] No upgrade button
- [ ] Crashlytics disabled
- [ ] Certificate pinning active

### PREMIUM Tier
- [ ] Maximum security enforcement (TERMINATE)
- [ ] Screen capture blocked (hard)
- [ ] Hardware keystore required
- [ ] Aggressive key rotation enabled
- [ ] Emulator detection blocks app launch
- [ ] All security toggles forced ON

## 10. Performance Tests

- [ ] Cold app start < 3 seconds
- [ ] RAM usage < 150 MB idle
- [ ] RAM usage < 300 MB during call
- [ ] Battery drain < 5% per hour of calling
- [ ] No memory leak after 10 consecutive calls
- [ ] UI remains responsive during call

## 11. Crash & Edge Case Tests

- [ ] Force-stop during call → no crash on restart
- [ ] Screen rotation during call → state preserved
- [ ] Airplane mode toggle → reconnect attempt
- [ ] WiFi → cellular handoff during call
- [ ] Very slow connection (3G) → call degrades gracefully
- [ ] Background → foreground → call still active
- [ ] Kill app process → peer receives disconnect
- [ ] Low battery warning during call → no crash
- [ ] Incoming phone call during SecureCall → handled

## 12. In-App-Purchase Tests (Sandbox)

- [ ] Google Play Billing dialog opens
- [ ] Test purchase completes (sandbox)
- [ ] Subscription tier recognized after purchase
- [ ] Features unlock (FREE → PRO)
- [ ] Restore purchases works
- [ ] Subscription expiry handled

## 13. Backend / Server Tests

- [ ] Signaling server health: `curl https://signal.stealthx.app/`
- [ ] WebSocket connects: `wscat -c wss://signal.stealthx.app/signal`
- [ ] STUN server responds
- [ ] TURN server allocates relay (test behind NAT)
- [ ] SSL certificate valid (no warnings)
- [ ] Server handles 10 simultaneous connections
- [ ] Rate limiting works (40 msg/10s)
- [ ] Admin API protected by key

## 14. Privacy & Compliance

- [ ] Privacy Policy accessible at stealthx.app/privacy
- [ ] Privacy Policy matches actual data practices
- [ ] GDPR data deletion option works
- [ ] FREE: Crashlytics opt-out toggle works
- [ ] No hardcoded test credentials in release
- [ ] No debug logs in release build (PREMIUM: ERROR_ONLY)
- [ ] No sensitive data in plain text

## 15. Play Store Readiness

- [ ] App icon 512x512 PNG prepared
- [ ] Feature graphic 1024x500 prepared
- [ ] 2-8 phone screenshots prepared
- [ ] Store listing texts finalized (DE + EN)
- [ ] Release notes written
- [ ] Content rating questionnaire ready
- [ ] Privacy Policy URL works
- [ ] Contact channel monitored (https://github.com/NeaBouli/stealth/issues)

---

## Sign-Off

| Role | Name | Date | Status |
|------|------|------|--------|
| Developer | | | |
| QA Tester | | | |
| Security Review | | | |

**Release Decision**: [ ] GO / [ ] NO-GO

**Version**: `0.2-beta` (versionCode `2`)
