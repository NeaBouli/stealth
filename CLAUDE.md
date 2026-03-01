# Project Handover -- SecureCall (StealthX Platform)

## Current Status

SecureCall is an end-to-end encrypted voice calling app for Android. The monorepo contains the Android client, a Node.js signaling backend (deployed on Railway), a Rust crypto engine, and supporting infrastructure.

**Sprint status:** All changes are **committed and pushed** to `origin/main`. Direct P2P audio transport is working via WebRTC DataChannel — Opus-encoded voice with E2E encryption, tested bidirectionally across all devices. Phone number → clientId resolution is live, tested across all 3 physical devices (S10, S7, Tab S4). Incoming calls now show over the lock screen and wake the device. Privacy-preserving contact verification is live — contacts with SecureCall show a green badge, using SHA-256 hashed phone lookups (server never sees raw numbers). Bug fix sprint complete: 7 UX/reliability bugs fixed (A2, D1, D2, A3, D3, C1, C2) — connection status indicator, pre-call health checks, missed call badges/notifications, save-contact-after-call dialog, and invite SMS with SecureCall ID.

**The 39 completed changes (all committed):**

1. **Contact auto-call fix** -- Removed `itemView.setOnClickListener` from `ContactAdapter.kt`. Only the phone icon (`btnCallContact`) now triggers calls; tapping the contact row does nothing.
2. **Messenger invite dialog** -- `DialerFragment.kt` now shows a 3-option dialog (Via Messenger, Share Link, Send SMS) when dialing an unknown number.
3. **Invite dialog bug fix** -- Removed `.setMessage()` from the invite AlertDialog (suppresses `setItems()`). Moved text into `.setTitle()`. *(Commit `a0b9872`)*
4. **Foreground background service** -- `WebSocketService` is now a foreground service with persistent notification.
5. **Red dial pad digits** -- `Widget.SecureCall.DialButton` style uses `@color/stealthx_red`.
6. **README transparency section** -- Third-Party Services table added to `README.md`.
7. **WebSocket reconnect-loop fix** -- Shared `OkHttpClient`, `isConnecting` guard, 30s max backoff.
8. **End-to-end call signaling** *(Commit `ac6f982`)* -- Full call flow wired: CALL_INVITE → IncomingCallActivity → CALL_ACCEPT → CallActivity with transport. See details below.
9. **End call button fix** *(Commit `bc1cc52`)* -- Removed `isCallActive` guard so end call works during ringing/connecting. Shows red end-call icon immediately for outgoing calls.
10. **Heartbeat timeout fix** *(Commit `c17bc14`)* -- Server sends `HEARTBEAT_ACK` response to client HEARTBEAT messages. Client updates `lastSeen` on successful heartbeat send. Filters `HEARTBEAT_ACK` from verbose logs. Connections are now stable (no more ~30s cycling).
11. **Session timeout increase** *(Commit `fe2b9fd`)* -- Server session timeout changed from 30s to 60s in `heartbeat.js`. Prevents ringing sessions from expiring before the callee can accept.
12. **IncomingCallActivity race condition fix** *(Commit `fe2b9fd`)* -- `onDestroy()` no longer clears `onCallEnded` callback when transitioning to CallActivity. Uses `accepted` flag to only clear on decline/caller-hangup.
13. **Heartbeat timeout increase** -- Client-side timeout changed from 15s to 30s.
14. **Real audio transport** *(Commit `228c30c`)* -- Wired peer-to-peer voice audio through the signaling WebSocket binary relay. See details below.
15. **Runtime RECORD_AUDIO permission** *(Commit `4fe8c77`)* -- CallActivity now requests microphone permission at runtime before starting audio capture. If permission is denied, shows a Toast and skips capture (call still connects, just no outgoing audio). Permission result handled in `onRequestPermissionsResult()`.
16. **endCall() idempotency guard** *(Commit `10fe6b0`)* -- Added `isEnding` boolean guard to prevent `endCall()` from firing multiple times. Also clears the `onCallError` callback after the first error to prevent repeated error-triggered endCall scheduling. Previously, server `rate_limited` errors would each schedule a separate `postDelayed(this::endCall, 3000)`, causing dozens of duplicate teardowns.
17. **E2E audio encryption** *(Commit `a97faa0`)* -- X25519 key exchange piggybacked on CALL_INVITE/CALL_ACCEPT signaling, session key derived via HKDF-SHA256, every Opus frame encrypted with XChaCha20-Poly1305 (40 bytes overhead: 24B nonce + 16B auth tag). Server forwards `pubKey` field transparently. Graceful fallback to unencrypted if native crypto unavailable. Key material zeroed on call end. See details below.
18. **Earpiece audio routing** *(Commit `0096e74`)* -- `GhostAudioPlayer` changed from `STREAM_MUSIC` to `STREAM_VOICE_CALL` for proper earpiece routing and voice call volume controls.
19. **Contact name resolution for incoming calls** *(Commit `0096e74`)* -- `IncomingCallActivity` looks up caller's `clientId` in `ContactRepository` by matching `phoneOrId`. Shows saved contact name on ringing screen and passes it through to `CallActivity`. Falls back to raw clientId if no contact matches.
20. **Jitter buffer** *(Commit `a6cd8c1`)* -- Wired `JitterBuffer` between OpusDecoder and GhostAudioPlayer. Decoded PCM frames are buffered (max 32, ShortArray) and drained by a dedicated playout thread at a steady 20ms rate. 60ms pre-buffer (3 frames) before playback starts. Writes silence on buffer underrun.
21. **WebRTC DataChannel P2P audio** *(Commit `27941e8`)* -- Added WebRTC PeerConnection with DataChannel for direct P2P audio transport, replacing server relay. After call accept, caller creates SDP offer with DataChannel (ordered=false, maxRetransmits=0), callee answers. ICE candidates trickle via existing signaling. Audio routes through DataChannel when open, automatic fallback to WebSocket relay if P2P fails. E2E encryption maintained. See details below.
22. **WebRTC signaling race condition fix** *(Commit `0f7284a`)* -- WEBRTC_OFFER and ICE_CANDIDATE messages arrive via WebSocket before `PeerConnectionFactory.init()` completes on the callee. Added pending queues (`pendingOffer`, `pendingAnswer`, `pendingIceCandidates`) in `WebRtcManager.kt` that buffer incoming signaling messages and drain them automatically once PeerConnection is created. Without this fix, the callee silently drops the offer and never creates an SDP answer, so the DataChannel never opens.
23. **Server rate-limit fix for P2P audio** *(Commit `dbad77c`)*
24. **CALL_END forwarding fix** *(Commit `0fbe701`)* -- Server's CALL_END handler deleted the session from `routingTable` BEFORE forwarding CALL_END to the peer. Moved `sendToClient()` before `routingTable.delete()` in `server.js`.
25. **Auto-hangup on peer disconnect** *(Commit `0fbe701`)* -- `WebRtcManager.kt`: Added `onPeerDisconnect` callback fired on ICE DISCONNECTED/FAILED and DataChannel CLOSED. `WebSocketService.kt`: Wired callback to invoke `_onCallEnded`. Calls now auto-end on both sides when either peer hangs up, even if the CALL_END message is delayed or lost. -- Binary audio frames (50fps Opus) were flooding the signaling rate limiter (40 msgs/10s), killing the DataChannel after ~2.5s. Three-part fix: (1) `server.js`: moved binary frame handling BEFORE `rateLimit.registerEvent()` so audio frames bypass the signaling rate limit; (2) `rate_limit.js`: added separate `registerBinaryEvent()` with 1000/10s limit (defense in depth against binary flooding); (3) `WebSocketService.kt`: removed WS binary fallback — `sendBinary()` returns `false` when DataChannel is not open instead of falling back to `client?.sendBinary()`. Audio only flows via P2P DataChannel now. Brief silence (~2-3s) during ICE negotiation is expected; UI shows "Connecting..." during this time.
26. **IncomingCallActivity auto-dismiss** *(Commit `a568bf3`)* -- When the caller cancels before the callee accepts, IncomingCallActivity now auto-dismisses. Three-layer approach: (1) static `activeInstance` reference so WebSocketService can call `dismissIfActive()` directly; (2) `onCallEnded` callback as backup; (3) identity-checked `onDestroy()` to only clear `activeInstance` if it's still this instance (prevents race condition with rapid back-to-back calls). Tested: Tab S4 → Emulator cancel, Emu → Tab S4 cancel, back-to-back cancels.
27. **Phone number → clientId resolution** *(Commit `e0c0784`)* -- Server-side phone number registry: clients send their phone number during REGISTER, server stores normalized phone → clientId mapping. New `PHONE_LOOKUP` message type resolves phone numbers to clientIds. `CALL_INVITE` handler has phone fallback resolution. `DialerFragment` and `ContactsFragment` use async `lookupPhone()` before deciding call vs invite dialog. Client reads device phone number via `TelephonyManager.getLine1Number()` (requires READ_PHONE_STATE/READ_PHONE_NUMBERS permissions). Tested bidirectionally: S7 (+4915203487046) → Tab S4 (+491752536807) and Tab S4 → S7.
28. **IncomingCallActivity lock screen fix** *(Commit `1aed31d`)* -- Added `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and `requestDismissKeyguard()` for Android 8.1+ (API 27+). Falls back to deprecated `FLAG_SHOW_WHEN_LOCKED`, `FLAG_DISMISS_KEYGUARD`, `FLAG_TURN_SCREEN_ON` for older versions. `FLAG_KEEP_SCREEN_ON` added unconditionally. Incoming calls now appear over the lock screen and wake the device. Tested: S10 → Tab S4 with Tab S4 screen locked — IncomingCallActivity appeared over lock screen.
29. **SHA-256 privacy-preserving contact verification** *(Commits `5ccba9f`, `e651a8d`)* -- BATCH_PHONE_LOOKUP now uses SHA-256 hashed phone numbers so the server never sees raw contact numbers. Server stores `phoneHashes` Map alongside `phoneNumbers` during REGISTER. Client hashes phone numbers locally via `MessageDigest("SHA-256")`, sends up to 200 hashes per batch. Server matches hashes against `phoneHashes` Map and returns results with `mode: "hashed"`. Contacts that are registered SecureCall users show a green badge (`badgeSecureCall`) in the contacts list. See details below.
30. **BATCH_PHONE_LOOKUP pagination** *(Commit `fd67ee1`)* -- `checkSecureCallMembers()` now chunks all contact hashes into sequential batches of 200 using `List.chunked(200)`. Batches are sent one at a time via recursive `sendBatch()` callback. Results accumulate across all batches; UI updates once after the last batch. Also fixed callback ordering bug in `WebSocketService.kt`: `_batchPhoneLookupCallback` was nullified after `invoke()`, killing the chain at batch 2. Fix: clear callback before invoking so the next batch's callback survives. S10 (1541 contacts): 8 batches in ~1.4s, now finds S7 + Tab S4. S7 (33 contacts): 1 batch, unchanged.
31. **Proximity wake lock fix** *(Commit `a720fff`)* -- `CallActivity.java`: `PROXIMITY_SCREEN_OFF_WAKE_LOCK` is now acquired immediately in `initProximitySensor()` (called from `onCreate`). The system automatically turns the screen off when the phone is held to the ear and back on when moved away. Removed the manual `SensorEventListener` approach which was unreliable — the wake lock handles proximity monitoring internally. Released in `endCall()` and `onDestroy()`. Tested on S10: logcat confirms `Proximity wake lock acquired` on call start and `Proximity wake lock released` on call end.
32. **Dialer cursor & delete fix** *(Commit `6ec004e`)* -- `DialerFragment.kt`: Backspace now deletes the character before the cursor position (`phoneDisplay.selectionStart`) instead of always deleting the last digit. Digit buttons insert at cursor position (`phoneNumber.insert(cursor, digit)`) instead of appending. `updateDisplay()` accepts a `cursorPos` parameter and calls `phoneDisplay.setSelection()` to restore cursor after `setText()`. Long-press backspace (clear all) unchanged. Tested on S10: typed `+1915231`, positioned cursor mid-number, backspace correctly deleted at cursor position.
33. **Incoming call caller phone display** *(Commit `92665a1`)* -- `IncomingCallActivity.kt`: Caller display now resolves contact name by both clientId and phone number. If no contact matches the clientId, tries matching the `callerPhone` field (from CALL_INVITE) against contacts' `phoneOrId` with normalized comparison. Falls back to phone number if no contact found, then raw clientId as last resort. Incoming caller screen now shows the caller's phone number or contact name instead of just the raw clientId.
34. **SecureCall ID copy fix** *(Commit `4f6efc8`)* -- `SettingsFragment.kt`: Fixed stale `clientId` closure in `onCreatePreferences`. The tap-to-copy handler now reads `client_id` fresh from SharedPreferences on each click instead of capturing it once at fragment creation time. Added `onResume()` to refresh the preference summary when returning to settings. Toast now shows the copied ID for verification.
35. **Connection status indicator & pre-call health checks** *(Commit `14ac6c9`)* -- `WebSocketService.kt`: Added `@Volatile var isConnected: Boolean` flag, set `true` in `onConnected()`, `false` in `onDisconnected()`/`onError()`/`handleHeartbeatTimeout()`. Added `statusCallbackOnline`/`statusCallbackOffline` callbacks. `MainActivity.java`: Toolbar subtitle shows green "Connected" or gray "Connecting..." based on WebSocket state via `wireConnectionStatusCallbacks()`. `DialerFragment.kt` and `ContactsFragment.kt`: Added pre-call health checks in all call initiation paths — if `ws.isConnected` is false, triggers `forceReconnect()` and shows "Reconnecting to server" toast instead of silently failing.
36. **Save contact dialog after phone-resolved call** *(Commit `a133856`)* -- `CallActivity.java`: After a call ends where the phone number was resolved to a clientId (via `originalPhone` intent extra), shows a "Save Contact" AlertDialog offering to save the contact with their SecureCall clientId. `shouldOfferContactSave()` checks: originalPhone is present, callContactId starts with `android-`, and contact not already saved. On save, creates a Contact with the clientId as `phoneOrId` so future calls connect directly without phone lookup. `DialerFragment.kt` and `ContactsFragment.kt` pass `originalPhone` extra through the intent chain.
37. **Invite SMS includes SecureCall ID** *(Commit `2019e06`)* -- `strings.xml`: Updated `dialer_invite_sms` and `dialer_invite_share` with `%1$s` format placeholder for SecureCall ID. `DialerFragment.kt`: Added `getMyClientId()` helper, formatted invite messages in `sendViaMessenger()`, `sendSmsInvite()`, `shareInviteLink()` with the local clientId. `ContactsFragment.kt`: Same fix in `showInviteDialog()`. Invite messages now include the sender's SecureCall ID so recipients can call back.
38. **Missed call notification with badge** *(Commit `ec9cceb`)* -- `IncomingCallActivity.kt`: Added `postMissedCallNotification()` called from `saveMissedCall()`. Creates `securecall_missed_calls` notification channel with `setShowBadge(true)`. Posts notification with caller name, missed call count via `setNumber()`, and tap-to-open PendingIntent. `CallHistoryRepository.kt`: Added `countMissed()` method. `strings.xml`: Added `missed_call_title` string. App launcher icon now shows badge count for missed calls (Samsung/Pixel launchers).
39. **In-app missed call badge & snackbar** *(Commit `2d89569`)* -- `MainActivity.java`: Added `onResume()` with `checkMissedCallBadge()` — counts missed calls since `last_calls_viewed` timestamp (SharedPreferences). Shows badge number on bottom nav "Calls" tab via `getOrCreateBadge()`. Shows Snackbar with "X missed call(s)" and "View" action if user is not on the Calls tab. Badge clears when user navigates to Calls tab (updates `last_calls_viewed` timestamp). `CallHistoryRepository.kt`: Added `countMissedSince(context, sinceTimestamp)` method.

**End-to-end call signaling details (change #8):**
- `WebSocketService.kt`: Added call signaling callbacks (`_onCallAccepted`, `_onCallEnded`, `_onCallError`), session tracking, incoming call activity launch, message parsing for CALL_INVITE/CALL_INVITE_ACK/CALL_ACCEPT/CALL_END/ERROR. Uses private backing fields with explicit setter methods for Java interop.
- `IncomingCallActivity.kt` (NEW): Ringing screen with accept/decline FABs. Accept sends CALL_ACCEPT + launches CallActivity. Decline sends CALL_END. Auto-closes if caller hangs up during ringing.
- `activity_incoming_call.xml` (NEW): Dark-themed layout with caller avatar, name, and accept/decline buttons.
- `CallActivity.java`: Three-way branching (fromNotification/isIncoming/outgoing). Outgoing sends CALL_INVITE, waits for CALL_ACCEPT to start transport. `endCall()` sends CALL_END signaling and clears all callbacks. Extracted `startTransportAndTimer()` helper.
- `AndroidManifest.xml`: Registered IncomingCallActivity with `showOnLockScreen` and `turnScreenOn`.
- `strings.xml`: Added `incoming_call_title`, `call_accept`, `call_decline`, `settings_client_id`.
- `preferences.xml` + `SettingsFragment.kt`: Tap-to-copy SecureCall ID in Account settings.

**Real audio transport details (change #14):**
- `HeartbeatClient.kt`: Added `onBinaryMessage(ByteArray)` to Listener interface, `sendBinary(ByteArray)` method, and `onMessage(WebSocket, ByteString)` override for binary WebSocket frames.
- `WebSocketService.kt`: Added `onBinaryMessage()` handler that decodes Opus → PCM via `OpusDecoder` and plays via `GhostAudioPlayer`. Added `sendBinary()` passthrough and `stopAudioPlayback()` cleanup. Audio pipeline is lazy-initialized on first binary frame received.
- `AudioCapturePlaceholder.java`: Redirected audio output from `GhostNetWebSocketClient` (separate unused WebSocket) to `WebSocketService.sendBinary()` (signaling WebSocket with server binary relay).
- `CallActivity.java`: Replaced stubbed `GhostNetTransport` with real `AudioCapturePlaceholder`. Audio capture starts when call becomes active (2s after connect). Mute button stops/starts capture. Audio cleanup in `endCall()` and `onDestroy()`.
- **Audio flow:** Mic → AudioRecord (48kHz mono) → OpusEncoder (32kbps, native JNI) → WebSocket binary → Server `forwardBinaryToPeer()` → Peer WebSocket → OpusDecoder (native JNI) → JitterBuffer (60ms prefill, 20ms playout) → GhostAudioPlayer (AudioTrack) → Earpiece
- **No server changes needed** -- `server.js` already relays binary frames between active session peers.

**E2E audio encryption details (change #17):**
- `server.js`: Added `pubKey: msg.pubKey` to forwarded CALL_INVITE and CALL_ACCEPT objects (2 lines). Server never reads the key — just passes it through.
- `WebSocketService.kt`: Added crypto state fields (`localPrivKey`, `remotePubKey`, `sessionKey`). `sendCallInvite()` generates X25519 keypair and includes pubKey in JSON. CALL_INVITE handler extracts and stores caller's public key. `sendCallAccept()` generates keypair, derives session key (callee), includes pubKey. CALL_ACCEPT handler extracts callee's pubKey and derives session key (caller). `sendBinary()` encrypts with `CoreCrypto.encrypt()` before sending. `onBinaryMessage()` decrypts with `CoreCrypto.decrypt()` before Opus decode (drops frame on decrypt failure). `clearSession()` zeroes all key material via `ByteArray.fill(0)`.
- **Key exchange flow:** Caller sends CALL_INVITE with X25519 pubKey → Callee stores it, generates own keypair, derives session key from privB+pubA, sends CALL_ACCEPT with pubKey → Caller derives session key from privA+pubB. Both sides have identical session keys before any audio flows.
- **Fallback:** If `CoreCrypto.isNativeAvailable()` returns false, audio is sent/received unencrypted (log warning).

**WebRTC DataChannel P2P details (change #21):**
- `build.gradle`: Added `io.github.webrtc-sdk:android:125.6422.07` dependency.
- `proguard-rules.pro`: Added WebRTC keep/dontwarn rules.
- `SecureCallApplication.kt`: Added `PeerConnectionFactory.initialize()` in `onCreate()` for early native library loading.
- `WebRtcManager.kt` (NEW): Manages PeerConnection + DataChannel lifecycle. Constructor takes callbacks for local SDP, local ICE candidate, and received data. Uses ICE servers from BuildConfig (STUN + TURN + TURNS). DataChannel config: `ordered=false, maxRetransmits=0` (unreliable, lowest latency for real-time audio). Caller creates DataChannel + SDP offer, callee receives DataChannel via `onDataChannel` callback + creates SDP answer. Pending queues (`pendingOffer`, `pendingAnswer`, `pendingIceCandidates`) buffer signaling messages that arrive before `init()` completes; drained automatically after PeerConnection creation.
- `WebSocketService.kt`: Added `webRtcManager` field. `sendBinary()` routes through DataChannel when `isDataChannelOpen`, falls back to WebSocket relay. Added `startWebRtc()` (called after CALL_ACCEPT), `sendWebRtcSdp()`, `sendIceCandidate()`. Handles incoming WEBRTC_OFFER/WEBRTC_ANSWER/ICE_CANDIDATE signaling messages. DataChannel received data feeds into existing `onBinaryMessage()` decrypt→decode pipeline. `clearSession()` tears down WebRTC.
- **No server changes:** Server already relays WEBRTC_OFFER (lines 553-597), WEBRTC_ANSWER (lines 602-646), and ICE_CANDIDATE (lines 651-694).
- **Audio flow with P2P:** Mic → OpusEncoder → E2E encrypt → DataChannel P2P → peer decrypt → OpusDecode → JitterBuffer → GhostAudioPlayer (earpiece). Falls back to WebSocket relay transparently if DataChannel is not open.

**SHA-256 privacy-preserving contact verification details (change #29):**
- `server.js`: Added `crypto` require, `phoneHashes` Map (SHA256(normalized_phone) → clientId), and `hashPhone()` helper. REGISTER handler stores hash alongside raw phone in all paths (registration, re-registration cleanup, phone change). BATCH_PHONE_LOOKUP handler: if `msg.hashes` array exists, looks up against `phoneHashes` Map and returns `{ hash, clientId, online }` with `mode: "hashed"`. Legacy `msg.phoneNumbers` path preserved as fallback. Disconnect cleanup also removes from `phoneHashes`.
- `ContactsFragment.kt`: Added `sha256()` helper (Java `MessageDigest`). `checkSecureCallMembers()` hashes each phone number with `sha256(normalized)`, builds `hashToPhone` reverse lookup map, sends up to 200 hashes via `ws.batchPhoneLookup(batch)`. On response, maps registered hashes back to phone numbers for `registeredPhones` Set. Limited to 200 per batch to stay under server's 64KB `maxPayload`.
- `WebSocketService.kt`: `batchPhoneLookup()` sends `"hashes"` field instead of `"phoneNumbers"`. BATCH_PHONE_LOOKUP_RESULT handler checks `mode == "hashed"` and returns `hash` field; legacy mode still returns `phoneNumber`.
- `ContactAdapter.kt`: Already shows green `badgeSecureCall` ImageView for contacts whose phone numbers are in the `registeredPhones` Set. No changes needed.
- **Privacy guarantee:** Server never receives raw phone numbers during contact discovery. Only SHA-256 hashes are transmitted. Server stores hashes at REGISTER time and matches incoming hashes against stored hashes.
- **Batch size limit:** Client sends max 200 hashes per request. Server also slices to 200. On large contact lists (1500+), only the first 200 contacts are checked. This prevents WebSocket disconnects from payloads exceeding 64KB maxPayload.

**Testing:** APK tested on 4 devices: S10 (`RF8N313QMFL`, `android-f90e7cf6`), S7 (`ce10160adc00152604`, `android-bc0f46cc`), Tab S4 (`ce12182c68644439037e`, `android-725b46bc`), and Pixel 5 emulator (`emulator-5554`, `android-33068922`). Full bidirectional call signaling, audio, E2E encryption, and auto-hangup verified across all device pairs:
- **S10 → Emulator:** CALL_INVITE → IncomingCallActivity → Accept → CALL_ACCEPT → CallActivity with timer → End call from S10 → Emulator receives CALL_END and closes
- **Emulator → S10:** CALL_INVITE → IncomingCallActivity on S10 → Accept → CALL_ACCEPT → CallActivity with timer → End call from emulator → S10 receives CALL_END and closes
- **Bidirectional audio:** Both devices show `AUDIO_CAPTURE: Capture thread started` (mic recording) and `AUDIO_PLAYER: write(): wrote=960 samples` (receiving peer audio). Full pipeline: OpusEncoder init → capture thread → binary WebSocket send → remote decrypt → OpusDecode → JitterBuffer (60ms prefill) → playout thread (20ms) → AudioTrack (earpiece).
- **End call works in all states:** ringing, connecting, and active. Both caller and callee can end.
- **Remote hangup:** When one side ends the call, the other side receives CALL_END and automatically closes CallActivity.
- **Heartbeat stability:** Connections remain stable for 65+ seconds with zero timeouts or reconnects.
- **Error handling:** `peer_not_found` (target offline), `peer_disconnected` (caller dropped), `session_not_found` (stale session) all handled correctly
- **Runtime permission:** RECORD_AUDIO is now requested at runtime when the call goes active. Tested: permission revoked → call started → dialog appeared → granted → audio capture started immediately.
- **endCall() guard:** Verified `endCall()` fires exactly once in all scenarios: error (peer_not_found), rate limiting (multiple server errors), and normal call flow. Proximity sensor does NOT trigger endCall — it only manages the wake lock.
- **E2E encryption:** Full key exchange verified — caller generates X25519 keypair and includes pubKey in CALL_INVITE, callee stores it and derives session key on accept, caller derives matching session key from CALL_ACCEPT pubKey. Both sides log `E2E session key derived`. Bidirectional encrypted audio: all Opus frames encrypted/decrypted successfully (zero `E2E decrypt failed` errors). Tested with native crypto on both arm64-v8a (S10) and x86_64 (emulator).
- **Contact name resolution:** S10 incoming call screen shows saved contact name "Emulator" (resolved from `android-33068922` via ContactRepository) instead of raw clientId. Name is also passed through to CallActivity.
- **Jitter buffer:** Both devices: `Jitter playout thread started, prefill=3` → `Jitter prefill reached, starting playout` → `AUDIO_PLAYER: write(): wrote=960 samples` at steady 20ms intervals. Playout thread (separate from WS reader thread) drains buffer smoothly.
- **Earpiece routing:** AudioTrack initialized with `STREAM_VOICE_CALL` (confirmed by `prepare(): done`). Audio routes to earpiece with voice call volume controls.
- **WebRTC P2P DataChannel:** Full P2P connection established between emulator and S10. Caller (emulator): `PeerConnection created` → `DataChannel 'audio' created` → `SDP created: OFFER` → ICE candidates gathered (host + srflx) → `WEBRTC_ANSWER` received → `ICE connection state: CONNECTED` → `DataChannel state: OPEN`. Callee (S10): `PeerConnection created` → `WEBRTC_OFFER` received → `SDP created: ANSWER` → ICE candidates gathered → `ICE connection state: CONNECTED` → `DataChannel received (callee): audio` → `DataChannel state: OPEN`. Audio frames flowing P2P through DataChannel with E2E encryption, bypassing server relay.
- **WebRTC pending queue fix:** Verified callee correctly queues WEBRTC_OFFER + ICE candidates when they arrive before PeerConnection init completes: `Queuing remote offer (PeerConnection not ready)` → `PeerConnection created` → `Draining 6 pending ICE candidates` → SDP answer created → ICE CONNECTED → DataChannel OPEN. Both S10 (caller) and emulator (callee) establish P2P successfully.
- **S10 → Emulator call (rate-limit fix verified):** Full 2+ minute call with DataChannel OPEN the entire duration. Zero `rate_limited` errors. S10 (caller) sent CALL_INVITE → Emulator accepted → WebRTC: DataChannel + SDP offer/answer exchange → ICE CONNECTED → DataChannel OPEN → P2P audio flowing at steady 20ms intervals for entire call. Binary audio frames correctly exempted from signaling rate limit on server. No WS relay fallback triggered. Clean teardown on call end.
- **4-device cross-call testing (Commit `0fbe701`):** Tested calls across S7↔Tab S4, S10↔S7, Emu↔S10, S10↔Emu, S7↔Emu. All calls: P2P DataChannel OPEN, bidirectional audio, clean auto-hangup on both sides via `onPeerDisconnect` callback. Belt-and-suspenders approach: P2P disconnect fires first (ICE DISCONNECTED → `onPeerDisconnect`), server CALL_END arrives as confirmation.
- **7-test comprehensive suite (Tab S4 + Emulator):**
  1. **Tab→Emu accept+end from Tab:** PASSED. 42s call, P2P audio, clean auto-hangup on both sides.
  2. **Emu→Tab accept+end from Emu:** PASSED. 128s call, P2P audio, clean auto-hangup.
  3. **Tab→Emu decline from Emu:** PASSED. Emulator declined, CALL_END sent, missed call saved (0s). Tablet received CALL_END.
  4. **Emu→Tab decline from Tab:** PASSED. Tablet declined, CALL_END sent and received.
  5. **Background call notification:** PASSED. App backgrounded on tablet, foreground service running, WebSocket received CALL_INVITE, IncomingCallActivity launched, notification shown with title "Incoming Secure Call" from "Emulator" (importance=HIGH, fullscreenIntent set). Note: fullscreenIntent doesn't bring activity to foreground over launcher on unlocked Samsung tablets — shows as notification instead (known Android behavior).
  6. **Call history:** PASSED. Both devices: 10+ entries with correct OUTGOING/INCOMING/MISSED types, contact names, durations, and `encrypted=true`.
  7. **Unknown number invite dialog:** PASSED. Dialed random number on tablet, dialog appeared: "Invite 22378 to SecureCall" with options "Via Messenger", "Share Link", "Send SMS".
- **IncomingCallActivity auto-dismiss (Commit `a568bf3`):** Caller cancels → callee's IncomingCallActivity auto-dismisses within 1s. Tested: Tab S4 → Emu cancel (PASSED, logs show "Caller cancelled call — auto-dismissing"), Emu → Tab S4 cancel (PASSED), back-to-back cancel (Call 1 dismissed, Call 2 dismissed, no lifecycle race).
- **Phone number → clientId resolution (Commit `e0c0784`, server fix `036e838`):** Full 6/6 cross-device phone lookup matrix tested on 3 physical devices after server phone registry overwrite fix. All devices registered with user-confirmed phone numbers:
  - S10 registered `+4915231794100` → `android-f90e7cf6`. S7 registered `+4915203487046` → `android-bc0f46cc`. Tab S4 registered `+491752536807` → `android-725b46bc`.
  - **S10 → S7:** Dialed `+4915203487046` on S10. PHONE_LOOKUP returned `clientId=android-bc0f46cc, online=true`. S7 showed IncomingCallActivity. PASSED.
  - **S10 → Tab S4:** Dialed `+491752536807` on S10. PHONE_LOOKUP returned `clientId=android-725b46bc, online=true`. Tab S4 showed IncomingCallActivity. PASSED.
  - **S7 → Tab S4:** Dialed `+491752536807` on S7. PHONE_LOOKUP returned `clientId=android-725b46bc, online=true`. Tab S4 showed IncomingCallActivity. PASSED.
  - **Tab S4 → S7:** Dialed `+4915203487046` on Tab S4. PHONE_LOOKUP returned `clientId=android-bc0f46cc, online=true`. S7 showed IncomingCallActivity. Contact resolved as "Partner Karte 100 Altes Samsung". PASSED.
  - **Tab S4 → S10:** Dialed `+4915231794100` on Tab S4. PHONE_LOOKUP returned `clientId=android-f90e7cf6, online=true`. S10 showed IncomingCallActivity. Contact resolved as "GREGOR MARINOW". PASSED.
  - **S7 → S10:** Dialed `+4915231794100` on S7. PHONE_LOOKUP returned `clientId=android-f90e7cf6, online=true`. S10 showed IncomingCallActivity. Contact resolved as "CHEF". PASSED.
  - **6/6 directions verified.** Server phone registry overwrite bug fixed — multiple devices re-registering no longer corrupt each other's phone mappings. Permissions granted via `adb shell pm grant` for testing.
- **IncomingCallActivity lock screen fix (Commit `1aed31d`):** Tab S4 screen locked with pattern lock. S10 called Tab S4's phone number. Before fix: IncomingCallActivity launched behind keyguard, `Surface is not valid`, invisible to user. After fix: IncomingCallActivity appeared over the lock screen with "Incoming Secure Call" from the caller. PASSED. Device screen turned on and stayed on (`FLAG_KEEP_SCREEN_ON`).
- **SHA-256 privacy-preserving contact verification (Commits `5ccba9f`, `e651a8d`):** Tested on all 3 physical devices (S10 Premium, S7 Free, Tab S4 Pro). All devices send SHA-256 hashes (not raw phone numbers) via BATCH_PHONE_LOOKUP. Server responds with `mode: "hashed"` results.
  - **S7 (33 contacts):** Sent 33 hashes → server returned 1 registered match: hash `914437a2...` resolved to `clientId=android-f90e7cf6` (S10), `online=true`. Green badge (`badgeSecureCall`) visible next to "CHEF" contact (+49 1523 1794100). PASSED.
  - **S10 (1500+ contacts, pre-pagination):** Sent 200 hashes (batch limit) → `mode=hashed`, 0 registered. S7/Tab S4 phone numbers fall outside the first 200 alphabetically-sorted contacts. Hashed mode working correctly, batch limit preventing payload overflow. PASSED.
  - **Tab S4 (1500+ contacts, pre-pagination):** Sent 200 hashes → `mode=hashed`, 0 registered. Same alphabetical ordering limitation as S10. PASSED.
  - **Audio leak test (S10→S7):** S10 called S7 (+4915203487046), S7 rang without answering, S10 hung up. Clean teardown on both sides: S10 `killAllAudio()` x3, S7 `dismissIfActive()` + `Vibration cancelled`. No lingering audio resources. PASSED.
- **BATCH_PHONE_LOOKUP pagination (Commit `fd67ee1`):** Tested on S10 (Premium) and S7 (Free) with updated sequential batch pagination.
  - **S10 (1541 contacts):** 8 batches sent sequentially (7×200 + 1×141) in ~1.4s. **2 registered found** — S7 (`android-bc0f46cc`) + Tab S4 (`android-725b46bc`). Previously 0 registered when limited to first 200. Green badge visible next to "Partner Karte 100 Altes Samsung" (+49 1520 3487046). PASSED.
  - **S7 (33 contacts):** 1 batch of 33 hashes, 1 registered (S10). Identical to pre-pagination behavior. PASSED.
  - **Callback ordering fix verified:** All 8 batches on S10 complete without stalling. Before fix, chain died after batch 2 because `_batchPhoneLookupCallback` was nullified immediately after `invoke()`. PASSED.

## Architecture Decisions

- **Monorepo structure:** Android client, backend, Rust crypto, website, and docs all live in one repo. The Android project root is `client_android/`.
- **Three-tier product flavors:** `free` (billing, limited features), `pro` (unlimited, cert pinning, root detection), `premium` (max security, hardware keystore, all detections). Each has its own `applicationIdSuffix` and `buildConfigField` flags. The `free` debug variant is used for development and testing.
- **Rust crypto via JNI:** All cryptographic operations (XChaCha20-Poly1305, X25519, HKDF-SHA256) run in a native Rust library (`core_crypto/`) accessed via JNI through a C++ CMake bridge (`src/main/cpp/CMakeLists.txt`). No Java crypto APIs are used.
- **GhostNet transport protocol:** Custom encrypted voice transport layer in `com.securecall.app.ghostnet` with its own handshake, session management, frame serialization. The old `GhostNetTransport` class was stubbed (no real network). Real audio now bypasses it entirely, using the signaling WebSocket's binary relay instead.
- **WebSocket signaling:** `HeartbeatClient` manages the OkHttp WebSocket connection with keepalive pings (8s app-level HEARTBEAT + 5s OkHttp native ping) and exponential backoff reconnect (1s -> 30s max). `WebSocketService` wraps it as a foreground service and handles message routing (call signaling, key exchange, subscription verification, GHOST protocol). Heartbeat timeout is 30s. Server responds with `HEARTBEAT_ACK` to keep client `lastSeen` fresh. Client also updates `lastSeen` on successful send (writable socket = alive).
- **Single reconnect owner:** Only `HeartbeatClient.onFailure()` triggers reconnect. `WebSocketService.onError()` notifies callbacks but does NOT reconnect. This prevents the double-reconnect exponential explosion bug.
- **Server session/client timeout:** 60s (in `heartbeat.js`). Server sends native `ws.ping()` every 5s, tracks `lastSeen` via pong and message handlers. Sessions and clients that don't communicate for 60s are terminated.
- **Call signaling flow:** Outgoing: CallActivity sends CALL_INVITE via WebSocketService → server routes to target → target's WebSocketService launches IncomingCallActivity → user accepts → CALL_ACCEPT sent back → caller's CallActivity starts audio capture. End call: either side sends CALL_END → server forwards and cleans up session → remote side receives CALL_END and auto-closes. All callbacks use private backing fields (`_onCallAccepted`, etc.) with explicit setter methods to avoid Kotlin/Java JVM signature clashes. IncomingCallActivity uses an `accepted` flag to prevent clearing `onCallEnded` when transitioning to CallActivity.
- **Audio transport via WebRTC DataChannel (P2P only):** After call is accepted, both sides establish a WebRTC PeerConnection with a DataChannel (`ordered=false, maxRetransmits=0`). `WebRtcManager` handles SDP offer/answer exchange and ICE candidate trickling via the existing signaling WebSocket. Once the DataChannel opens, `sendBinary()` routes encrypted Opus frames directly P2P, bypassing the server. If the DataChannel is not open yet, audio frames are silently dropped (no WS relay fallback — this prevents binary frames from flooding the signaling rate limiter). Brief silence (~2-3s) during ICE negotiation while UI shows "Connecting...". ICE servers: Google STUN + Metered.ca TURN/TURNS from BuildConfig. Receiving side: DataChannel `onMessage` → `WebSocketService.onBinaryMessage()` → decrypt → `OpusDecoder.decode()` → `JitterBuffer.push()` → playout thread → `GhostAudioPlayer.write()`. All audio frames are E2E encrypted with XChaCha20-Poly1305.
- **Jitter buffer:** `JitterBuffer` (singleton, synchronized) buffers decoded PCM `ShortArray` frames (max 32). A dedicated `jitter-playout` thread drains one frame every 20ms (matching Opus 960-sample frame duration at 48kHz). Pre-buffers 3 frames (60ms) before starting playback. On underrun, writes 960 zero samples (silence) to maintain steady output. Thread and buffer are cleared in `stopAudioPlayback()`.
- **E2E audio encryption:** X25519 public keys are piggybacked on existing CALL_INVITE/CALL_ACCEPT messages (no extra round-trips). Both sides derive a shared session key via X25519 DH + HKDF-SHA256 using `CoreCrypto.deriveSessionKey()`. Every Opus frame is encrypted with `CoreCrypto.encrypt(sessionKey, data)` → [nonce(24B)|ciphertext|tag(16B)] before sending, and decrypted with `CoreCrypto.decrypt()` on receive. Session keys are derived before any audio flows. Key material (`localPrivKey`, `sessionKey`) is zeroed via `ByteArray.fill(0)` in `clearSession()`. Graceful fallback: if `CoreCrypto.isNativeAvailable()` is false, audio passes through unencrypted.
- **endCall() idempotency:** `endCall()` uses an `isEnding` boolean guard to ensure it runs at most once per call session. The `onCallError` callback is cleared after first invocation (`ws.setOnCallError(null)`) to prevent server error floods (e.g., `rate_limited`) from scheduling multiple delayed `endCall()` calls.
- **Proximity wake lock:** `CallActivity` acquires `PROXIMITY_SCREEN_OFF_WAKE_LOCK` immediately in `onCreate()` via `initProximitySensor()`. The system automatically turns the screen off when the proximity sensor detects near (phone held to ear) and back on when far. No manual `SensorEventListener` needed — the wake lock handles it internally. Released in `endCall()` and `onDestroy()` via `releaseProximitySensor()`.
- **Kotlin/Java interop:** CallActivity is Java, WebSocketService is Kotlin. Kotlin `var` properties auto-generate getters/setters that clash with explicit methods of the same name. Solution: private backing fields (`_fieldName`) with explicit public setter methods. Java lambdas for Kotlin `(String) -> Unit` must return `kotlin.Unit.INSTANCE`.
- **Phone number → clientId resolution:** Server maintains an in-memory `phoneNumbers` Map (normalized phone → clientId). Clients send their phone number (via `TelephonyManager.getLine1Number()`) in the REGISTER message. `PHONE_LOOKUP { phoneNumber }` → `PHONE_LOOKUP_RESULT { phoneNumber, clientId, online }` resolves phone numbers. `CALL_INVITE` handler has a fallback: if `msg.to` isn't a known clientId, it tries `phoneNumbers.get(normalizePhone(msg.to))`. Phone entries are cleaned up on disconnect. `normalizePhone()` strips non-digit characters except leading `+`.
- **Privacy-preserving contact verification (BATCH_PHONE_LOOKUP):** Server maintains a parallel `phoneHashes` Map (SHA256(normalized_phone) → clientId) alongside `phoneNumbers`. During REGISTER, the server stores `hashPhone(phone)` → clientId. Client hashes each contact's phone number locally using Java `MessageDigest("SHA-256")` and sends hashes in sequential batches of 200 (server max per request). `checkSecureCallMembers()` uses `List.chunked(200)` and a recursive `sendBatch()` callback to send all batches sequentially, accumulating results across batches. UI updates once via `finalizeResults()` after the last batch. Server matches against `phoneHashes` and returns `mode: "hashed"` results. The `registeredPhones` Set in `ContactsFragment` drives the green `badgeSecureCall` badge in `ContactAdapter`. Server never sees raw phone numbers during contact discovery. Edge cases: `isAdded` check before each batch (fragment lifecycle), `WebSocketService.instance` null check (WS disconnect applies partial results), failed sends advance to next batch via existing error callback.
- **Feature flags via BuildConfig:** Tier-specific behavior is controlled by `BuildConfig` fields set in `build.gradle` per flavor, accessed at runtime through `FeatureProvider` interface and `FeatureProviderRegistry` singleton.
- **Firebase disabled:** Firebase initialization is disabled via manifest (`FirebaseInitProvider` set to `enabled="false"`). Crashlytics and Analytics collection are both disabled. FCM push notifications won't work until Firebase is properly configured with real credentials.

## Project Structure

```
stealth/                              # Monorepo root
├── CLAUDE.md                         # THIS FILE
├── README.md                         # Project README (modified: transparency section added)
├── LICENSE                           # Source-available license
├── SECURITY.md                       # Security disclosure policy
├── CHANGELOG.md
├── logo.png
├── securecall-release-key.jks        # Release signing keystore
│
├── client_android/                   # Android app (Kotlin)
│   ├── app/
│   │   ├── build.gradle              # Flavors: free/pro/premium, deps (incl. WebRTC), server URLs
│   │   └── src/main/
│   │       ├── AndroidManifest.xml   # MODIFIED: foreground service, IncomingCallActivity, phone permissions
│   │       ├── cpp/CMakeLists.txt    # JNI bridge to Rust crypto
│   │       ├── java/com/securecall/app/
│   │       │   ├── MainActivity.java               # MODIFIED: connection status callbacks, missed call badge/snackbar, phone permission UI
│   │       │   ├── CallActivity.java          # MODIFIED: signaling, audio, runtime permission, endCall guard, save-contact dialog
│   │       │   ├── IncomingCallActivity.kt    # NEW: incoming call ringing screen (accepted flag fix, contact name, lock screen flags, missed call notification)
│   │       │   ├── SecureCallApplication.kt
│   │       │   ├── net/
│   │       │   │   ├── HeartbeatClient.kt      # MODIFIED: reconnect fix, lastSeen on send, binary WS support
│   │       │   │   ├── WebSocketService.kt     # MODIFIED: foreground, call signaling, audio, E2E encryption, WebRTC, phone registry, hashed batch lookup
│   │       │   │   ├── WebRtcManager.kt        # NEW: WebRTC PeerConnection + DataChannel for P2P audio
│   │       │   │   └── signal/                 # Call & key exchange message builders
│   │       │   ├── ui/
│   │       │   │   ├── CallsFragment.kt
│   │       │   │   ├── ContactsFragment.kt     # MODIFIED: async phone lookup, SHA-256 hashed batch contact verification, pre-call health, invite with ID
│   │       │   │   ├── DialerFragment.kt       # MODIFIED: messenger invite, T9 search, invite dialog fix, phone lookup, pre-call health, cursor fix, invite with ID
│   │       │   │   ├── SettingsFragment.kt     # MODIFIED: background service toggle, clientId display, fresh-read copy fix
│   │       │   │   └── adapter/
│   │       │   │       └── ContactAdapter.kt   # MODIFIED: removed row click
│   │       │   ├── config/                     # FeatureProvider, FeatureProviderRegistry
│   │       │   ├── audio/                      # AudioCapturePlaceholder (MODIFIED: sends via WS), Opus codec
│   │       │   │   └── jitter/JitterBuffer.kt # MODIFIED: ShortArray PCM buffer with playout support
│   │       │   ├── ghostnet/media/playback/
│   │       │   │   └── GhostAudioPlayer.kt   # MODIFIED: STREAM_VOICE_CALL
│   │       │   ├── billing/                    # Subscription tiers, licensing
│   │       │   ├── call/                       # CallController
│   │       │   ├── crypto/                     # EphemeralKeyProvider
│   │       │   ├── data/                       # ContactRepository, CallHistoryRepository (MODIFIED: countMissed, countMissedSince)
│   │       │   ├── ghostnet/                   # Encrypted transport protocol
│   │       │   ├── security/                   # Anti-recording, root detection
│   │       │   ├── fcm/                        # FCM push handler
│   │       │   └── vpn/                        # GhostVpnService
│   │       └── res/
│   │           ├── layout/activity_incoming_call.xml  # NEW: incoming call layout
│   │           ├── values/strings.xml          # MODIFIED: call signaling strings
│   │           ├── values/styles.xml           # MODIFIED: red dial pad
│   │           └── xml/preferences.xml         # MODIFIED: background service, clientId pref
│   └── gradlew                                 # Build: ./gradlew assembleFreeDebug
│
├── backend/                          # Node.js signaling server (Railway)
│   └── signaling/src/
│       ├── server.js                 # MODIFIED: HEARTBEAT_ACK, pubKey forwarding, binary before rate limit, phone registry + PHONE_LOOKUP, phoneHashes + hashed BATCH_PHONE_LOOKUP
│       ├── rate_limit.js             # MODIFIED: separate binary rate limit (1000/10s)
│       └── heartbeat.js              # MODIFIED: session timeout 30s→60s
├── core_crypto/                      # Rust crypto library (XChaCha20, X25519, HKDF)
├── docs/                             # Security audit, architecture, wiki pages
├── website/                          # GitHub Pages (neabouli.github.io/stealth)
├── deploy/                           # Deployment scripts
├── deployment/                       # Kubernetes/infrastructure configs
├── marketing/                        # Marketing assets
├── native/                           # Native code modules
├── rom_ghostos/                      # Custom ROM project
└── tools/                            # Test scripts
```

### Key Server URLs (hardcoded in build.gradle for both debug and release)

| Service | URL |
|---------|-----|
| WebSocket Signaling | `wss://protective-healing-production.up.railway.app/signal` |
| STUN | `stun:stun.l.google.com:19302` |
| TURN | `turn:a.relay.metered.ca:443?transport=tcp` |

## Open TODOs

1. ~~**Redeploy Railway signaling server**~~ -- DONE.
2. ~~**Verify connection**~~ -- DONE.
3. ~~**Feature testing on S10**~~ -- DONE.
4. ~~**Commit all changes**~~ -- DONE.
5. ~~**End-to-end call signaling**~~ -- DONE. Full bidirectional call flow tested.
6. ~~**End call button fix**~~ -- DONE. Button works in all states.
7. ~~**Heartbeat/ping-pong fix**~~ -- DONE. Server sends `HEARTBEAT_ACK`, client updates `lastSeen` on send. Connections stable 65+ seconds.
8. ~~**Session timeout fix**~~ -- DONE. Increased to 60s. IncomingCallActivity race condition fixed.
9. ~~**Railway redeploy**~~ -- DONE. Auto-deployed via GitHub push.
10. ~~**Real audio transport**~~ -- DONE. Opus-encoded audio relayed via signaling WebSocket binary frames. Tested bidirectionally with live mic audio.
11. ~~**Runtime RECORD_AUDIO permission.**~~ DONE. CallActivity requests RECORD_AUDIO at runtime before starting audio capture. Commit `4fe8c77`.
12. ~~**Contact name resolution.**~~ DONE. IncomingCallActivity resolves caller clientId via ContactRepository. Commit `0096e74`.
13. ~~**E2E audio encryption.**~~ DONE. X25519 key exchange + XChaCha20-Poly1305 per-frame encryption. Commit `a97faa0`.
14. **Firebase setup.** Configure real Firebase credentials to enable FCM push for incoming calls when app is not running.
15. **TURN credential rotation.** Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch from server at runtime.
16. ~~**Direct P2P audio (WebRTC).**~~ DONE. WebRTC DataChannel (P2P only, no WS relay fallback). Commit `27941e8`. Pending queue fix `0f7284a`. Rate-limit fix `dbad77c`.
17. ~~**Audio stream type.**~~ DONE. GhostAudioPlayer changed to `STREAM_VOICE_CALL`. Commit `0096e74`.
18. ~~**Jitter buffer.**~~ DONE. JitterBuffer wired between OpusDecoder and GhostAudioPlayer with 60ms prefill playout thread. Commit `a6cd8c1`.
19. ~~**IncomingCallActivity auto-dismiss.**~~ DONE. Static activeInstance + dismissIfActive() + identity-checked onDestroy(). Commit `a568bf3`.
20. ~~**Phone number → clientId resolution.**~~ DONE. Server phone registry, PHONE_LOOKUP handler, client sends phone in REGISTER, DialerFragment/ContactsFragment async lookup. Commit `e0c0784`. Tested bidirectionally S7↔Tab S4, S10→S7, S10→Tab S4.
21. ~~**IncomingCallActivity over lock screen.**~~ DONE. Added show-when-locked, turn-screen-on, dismiss-keyguard, keep-screen-on flags. Commit `1aed31d`. Tested: S10 → locked Tab S4, IncomingCallActivity appeared over lock screen.
22. ~~**Runtime phone number permission UI.**~~ DONE. `MainActivity.java` requests `READ_PHONE_NUMBERS` (Android 11+) or `READ_PHONE_STATE` (older) at runtime via `requestPhoneNumberPermission()`. On first launch (no `confirmed_phone_number` in prefs), shows "Confirm Your Phone Number" dialog with SIM number pre-filled. User can Confirm (saves to SharedPreferences, re-registers with server) or Skip. Permissions were previously granted via adb for testing, which bypassed the dialog.
23. **Automated tests.** Add unit/integration tests for the new features (crypto, jitter buffer, WebRTC signaling, phone lookup).
24. ~~**Privacy-preserving contact verification.**~~ DONE. SHA-256 hashed BATCH_PHONE_LOOKUP. Server stores phoneHashes alongside phoneNumbers. Client hashes locally, sends up to 200 per batch. Green badge for registered contacts. Commits `5ccba9f`, `e651a8d`. Tested on S7 (1 match found — S10), S10, Tab S4.
25. ~~**BATCH_PHONE_LOOKUP pagination.**~~ DONE. Sequential batches of 200 via recursive `sendBatch()`. All contacts now checked regardless of list size. Callback ordering fix in WebSocketService. Commit `fd67ee1`. Tested: S10 (1541 contacts, 8 batches, 2 registered found), S7 (33 contacts, 1 batch, unchanged).

## Known Issues

1. ~~**Railway server 429 block**~~ -- RESOLVED.
2. ~~**Heartbeat timeout cycling**~~ -- RESOLVED. Server sends `HEARTBEAT_ACK`, client updates `lastSeen` on send. Connections stable 65+ seconds. Server-side fix needs Railway redeploy but client-side fix works independently.
3. **Firebase disabled.** Firebase initialization is disabled in the manifest (placeholder credentials). FCM push notifications for incoming calls will not work. Crashlytics and Analytics are also disabled.
4. **Emulator instability.** The Pixel 5 AVD (Android 16 API 36) shows frequent "System UI isn't responding" dialogs during testing. `eth0` often stays DOWN after boot — fix with `su 0 ndc network create 100 && ndc network interface add 100 eth0 && ndc network default set 100` or `su 0 ip link set eth0 down && ip link set eth0 up` then add IP/route manually. App-related testing should prioritize the physical S10 device.
5. **TURN credentials in source.** The Metered.ca TURN username and password are hardcoded in `build.gradle`. These should be rotated and fetched from the server at runtime.
6. **Release keystore in repo.** `securecall-release-key.jks` is in the repo root. Passwords are read from environment variables, but the keystore file itself is committed.
7. **No automated tests.** Unit test dependencies are configured but no tests were added for the new changes.
8a. ~~**Server rate-limiting kills DataChannel.**~~ RESOLVED. Root cause was binary audio frames (50fps Opus) flooding the signaling rate limit, not ICE candidates. Fixed in commit `dbad77c`: binary frames exempted from signaling rate limit on server, separate binary rate limit added (1000/10s), WS relay fallback removed from client. DataChannel now stays OPEN for entire call duration.
8. ~~**No runtime mic permission request.**~~ RESOLVED. CallActivity now requests RECORD_AUDIO at runtime. Commit `4fe8c77`.
9. **Android AlertDialog gotcha.** `setMessage()` and `setItems()` are mutually exclusive -- `setMessage` suppresses the item list. Fixed in commit `a0b9872`.
10. **Kotlin/Java interop gotcha.** Kotlin `var` properties auto-generate getters/setters that clash with explicit methods of the same name. Use private backing fields + explicit methods. Java lambdas for Kotlin function types must return `kotlin.Unit.INSTANCE`.
11. **Activity lifecycle race condition.** `IncomingCallActivity.onDestroy()` runs after `CallActivity.onCreate()` when accepting a call. Any callbacks set in `onDestroy()` to `null` will overwrite what `CallActivity.onCreate()` just set. Fixed with `accepted` flag guard.
12. ~~**IncomingCallActivity doesn't auto-dismiss on caller cancel.**~~ RESOLVED. Static `activeInstance` + `dismissIfActive()` + identity-checked `onDestroy()`. Commit `a568bf3`.
13. **Background fullscreenIntent on Samsung.** On unlocked Samsung devices, `fullScreenIntent` notifications don't bring the activity to the foreground over the launcher — they show as a heads-up/status bar notification instead. The IncomingCallActivity IS launched and in the task stack, but the user must tap the notification to bring it forward. This is a known Android/Samsung restriction for background activity launches.
14. **Phone number unavailable on some SIMs.** `TelephonyManager.getLine1Number()` returns null on SIMs that don't store the phone number. User-confirmed phone number (via dialog on first launch) is now the primary source, stored in `confirmed_phone_number` SharedPreference. All 3 physical devices now have confirmed numbers. Runtime permission request UI exists in `MainActivity.java` (`requestPhoneNumberPermission()` + `promptForPhoneNumber()`).
15. **Dialer T9 suggestions shift button positions.** On S7 (Galaxy S7, Android 8), the contactSuggestions RecyclerView appearing/changing height as digits are typed shifts the dial pad button positions. Tapping buttons at static coordinates can hit the wrong button. Not a functional bug (users tap visually) but complicates automated UI testing via adb.
16. ~~**BATCH_PHONE_LOOKUP callback ordering.**~~ RESOLVED. `_batchPhoneLookupCallback` was nullified after `invoke()`, so if the callback set a new callback for the next batch, it was immediately killed. Fixed by clearing before invoking: `val cb = _batchPhoneLookupCallback; _batchPhoneLookupCallback = null; cb?.invoke(registered)`. Commit `fd67ee1`.

## Explicit Non-Goals

- Backend changes are minimal: `HEARTBEAT_ACK` response, `pubKey` forwarding, binary-before-rate-limit reorder, phone number registry + `PHONE_LOOKUP` handler, `phoneHashes` Map + hashed `BATCH_PHONE_LOOKUP` mode in `server.js`; separate binary rate limit in `rate_limit.js`; session timeout increase in `heartbeat.js`. No structural or architectural backend changes.
- No Rust crypto changes. `core_crypto/` is stable and untouched.
- Both `free` and `premium` debug variants are built and tested. Premium is used for 3-device physical testing (S10, S7, Tab S4).
- No CI/CD pipeline changes. GitHub Actions workflows exist but are not being modified.
- No new automated tests for this sprint. Testing is manual on physical device.
- No Firebase configuration. Push notifications remain non-functional.
- No localization work. The S10 displays German via system locale; no translation files are being added.

## Code Conventions

- **Language:** Kotlin for all app code. XML for Android resources. Rust for crypto engine.
- **Build:** `./gradlew assembleFreeDebug` or `./gradlew assemblePremiumDebug` from `client_android/` directory. APK output: `app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk`.
- **Package naming:** `com.securecall.app.<feature>` (e.g., `net`, `ui`, `audio`, `config`, `ghostnet`).
- **Variable/function naming:** camelCase. Classes: PascalCase. Resource IDs: `snake_case` (e.g., `pref_background_service`, `btnCallContact`).
- **Preference keys:** Prefixed with `pref_` (e.g., `pref_dark_mode`, `pref_block_screenshots`, `pref_background_service`).
- **Log tags:** Short descriptive tags -- `WS_SERVICE` for WebSocketService, `HB` for HeartbeatClient, `INCOMING_CALL` for IncomingCallActivity, `CallActivity` for CallActivity.
- **Comments:** Older code has German comments (e.g., `// BACKEND-22: Heartbeat Ueberwachung`). New code uses English. Ticket references like `BACKEND-22`, `PATCH 201` appear throughout.
- **Error handling:** Non-critical failures use `catch (_: Exception) {}`. Critical errors use `Log.e(TAG, message, throwable)`.
- **adb on this machine:** Not in PATH. Full path required: `/Users/gio/Library/Android/sdk/platform-tools/adb`. Emulator: `~/Library/Android/sdk/emulator/emulator`. AVD name: `Pixel_5`.
- **S10 serial:** `RF8N313QMFL`. ClientId: `android-f90e7cf6`. Phone: `+4915231794100`.
- **S7 serial:** `ce10160adc00152604`. ClientId: `android-bc0f46cc`. Phone: `+4915203487046`.
- **Tab S4 serial:** `ce12182c68644439037e`. ClientId: `android-725b46bc`. Phone: `+491752536807`. Landscape mode (2560x1492).
- **Emulator:** `emulator-5554`. ClientId: `android-33068922` (changes on wipe).
- **Package name (free):** `com.securecall.app.free`. **Package name (premium):** `com.securecall.app.premium`.
- **App launch command:** `adb -s <serial> shell am start -n com.securecall.app.{free|premium}/com.securecall.app.MainActivity`
- **Contacts storage:** SharedPreferences file `securecall_contacts` with key `contacts_json` (JSON array). Fields: `id`, `name`, `phoneOrId`, `createdAt`, `isPhoneContact`.

## Next Immediate Step

All core features are complete, committed, and tested across 4 devices (S10, S7, Tab S4, Emulator). 39 changes shipped including full E2E encrypted P2P voice calls, phone number resolution, auto-dismiss, lock screen incoming calls, proximity wake lock, dialer cursor fix, privacy-preserving contact verification with paginated batch lookup, and a 7-bug UX/reliability fix sprint (connection status, pre-call health checks, save-contact-after-call, invite SMS with ID, missed call notifications with badge, in-app missed call snackbar). Git history has been cleaned (test screenshots purged via `git filter-repo`). Next priorities:

1. ~~**Runtime phone permission UI**~~ -- DONE. Already implemented in `MainActivity.java`: `requestPhoneNumberPermission()` + `promptForPhoneNumber()` dialog. Permissions were granted via adb during testing, bypassing the runtime dialog.
2. ~~**Fix lock screen incoming calls**~~ -- DONE. IncomingCallActivity now shows over lock screen. Commit `1aed31d`.
3. ~~**Privacy-preserving contact verification**~~ -- DONE. SHA-256 hashed BATCH_PHONE_LOOKUP with green badge. Commits `5ccba9f`, `e651a8d`.
4. ~~**BATCH_PHONE_LOOKUP pagination**~~ -- DONE. Sequential batches of 200, all contacts checked. Commit `fd67ee1`.
5. ~~**Bug fix sprint (A2, D1, D2, A3, D3, C1, C2)**~~ -- DONE. 7 bugs fixed: incoming caller phone display, settings ID copy, connection status indicator, save-contact dialog, invite SMS with ID, missed call notification, in-app missed call badge. Commits `92665a1` through `2d89569`.
6. **Firebase setup** -- Configure real Firebase credentials to enable FCM push for incoming calls when app is not running.
7. **TURN credential rotation** -- Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch from server at runtime.
8. **Automated tests** -- Add unit/integration tests for the new features (crypto, jitter buffer, WebRTC signaling, phone lookup).
