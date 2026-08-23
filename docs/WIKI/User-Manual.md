> **CLASSIFICATION: RESTRICTED** | **DOCUMENT: SC-OPS** | **DIVISION: StealthX // SecureCall**

---

# OPERATOR MANUAL

---
#### ████ ESTABLISHING COMMS ████
---

### Starting a Call
1. Open SecureCall
2. Select a contact from your list
3. Tap the **Call** button
4. Wait for the connection (1-2 seconds)
5. The **lock icon** indicates encryption status:
   - **Green** — Call is encrypted, no threats detected
   - **Yellow** — Potential threat detected (recording app, etc.)
   - **Red** — Active threat detected

---
#### ████ ACTIVE CHANNEL OPERATIONS ████
---

- **Speaker** — Toggle between earpiece and loudspeaker
- **Mute** — Mute your microphone
- **End Call** — Hang up

### Call Duration
| Tier | Limit |
|------|-------|
| Free | 15 minutes max |
| Pro | Unlimited |
| Premium | Unlimited |

---
#### ████ SECURITY COUNTERMEASURES ████
---

### Screenshot Protection (FLAG_SECURE)
- The call screen cannot be captured by screenshots or screen recording
- **Free:** Optional (Settings)
- **Pro:** Enabled by default
- **Premium:** Always enforced

### Screen Recording Detection
- SecureCall detects if another app is recording your screen
- **Pro:** Shows a warning dialog
- **Premium:** Automatically terminates the call

### Spy App Detection
- Scans for known surveillance and call-recording apps
- Checks accessibility services for suspicious activity
- **Pro:** Alerts you to threats
- **Premium:** Blocks threats and terminates if critical

### Security Status
During a call, the security status bar shows:
- **Green Lock** — All clear, no threats
- **Yellow Warning** — Non-critical threat detected (e.g., accessibility service)
- **Red Alert** — Critical threat detected (e.g., active screen recording)

---
#### ████ CONFIGURATION ████
---

### Audio Quality
- **Standard** (Free tier) — Good quality for most connections
- **HD Opus 48kHz** (Pro/Premium) — Crystal-clear audio quality

### Anti-Recording Protection
- **Screen capture detection** — Detect screen recording apps
- **Microphone monitoring** — Detect other apps using the mic
- **Spy app detection** — Scan for surveillance apps

### Privacy
- **Crash reports** — Anonymous crash data (Free tier only, opt-out available)
- **Delete all data** — Erase all local data (keys, contacts, settings)

### Network and VPNs
- A green status LED appears only while SecureCall uses Android's active VPN route.
- The Google Play edition contains no built-in VPN service and remains compatible with a VPN managed by Android or another trusted app.
- The direct Premium APK can run an optional WireGuard tunnel after Android VPN consent. Only SecureCall is included in that tunnel, its configuration remains local, and its private key is encrypted with Android Keystore.
- SecureCall automatically uses TURN relay when a direct WebRTC path is unavailable.
- If a VPN blocks calling or registration, allow SecureCall in that provider's settings.

---
#### ████ CONTACT REGISTRY ████
---

### Adding a Contact
1. Tap **"+"** in the contact list
2. Enter the contact's SecureCall ID
3. Optionally add a display name
4. Tap **Save**

### Contact Limits
| Tier | Contacts |
|------|----------|
| Free | 10 max |
| Pro | Unlimited |
| Premium | Unlimited |

---
#### ████ UPGRADING ████
---

### Free → Pro (€3.49/month)
1. Open Settings → Subscription
2. Tap "Upgrade to Pro"
3. Complete purchase via Google Play

### Pro → Premium (€4.99/month)
1. Open Settings → Subscription
2. Tap "Upgrade to Premium"
3. Complete purchase via Google Play

### Cancel Subscription
1. Google Play → Subscriptions
2. Select SecureCall
3. Tap "Cancel subscription"
4. Features remain active until end of billing period

---

> DOCUMENT END // CLASSIFICATION: RESTRICTED

[← Return to Operations Center](Home.md)
