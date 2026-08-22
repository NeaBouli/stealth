# SecureCall — User Manual

**Version 1.0.33 · StealthX Platform**

---

## What Is SecureCall?

SecureCall is an end-to-end encrypted voice call app built for people who need genuine communication privacy. Every call is encrypted in transit using military-grade cryptography. The server never stores calls, never logs metadata, and never has access to your voice data. Your identity is a randomly generated ID — no phone number required.

---

## How It Works

When you make a call, SecureCall establishes a direct encrypted audio channel between two devices using WebRTC with hardware-backed key exchange. The signaling server (which coordinates the connection) only sees encrypted session tokens — never your voice. Once the call is established, audio travels peer-to-peer. If a direct connection cannot be established, a TURN relay is used, but audio remains end-to-end encrypted and the relay cannot read it.

Your SecureCall ID is your identity on the network. It is randomly generated on your device and never tied to your phone number, email, or real name.

---

## Tier Overview

SecureCall is available in three tiers. Your effective tier is the highest of: your build flavor, any active subscription, or any active activation code.

| Feature | Free | Pro | Premium |
|---|---|---|---|
| Call duration | 15 min per call | Unlimited | Unlimited |
| Saved contacts | 10 max | Unlimited | Unlimited |
| Ads | Yes | No | No |
| Anti-recording protection | Off | Toggleable | Forced on |
| Multi-device support | No | Yes | Yes |
| Custom Call ID | No | No | Yes |
| Screen capture detection | No | No | Yes |
| Diagnostic logs | No | Yes | Yes |
| Hardware keystore required | No | No | Yes |

---

## First-Time Setup

### Step 1 — Onboarding

On first launch you will see a four-page introduction. Swipe through or tap **Next** to proceed. Tap **Skip** to go directly to the app.

### Step 2 — Phone Number (Optional)

After onboarding, a dialog asks you to confirm your phone number. This allows other SecureCall users to find you by your regular phone number. Your SIM number is suggested automatically but you can change it or tap **Skip** to register without a phone number. Your SecureCall ID works regardless.

### Step 3 — Notification Permission (Android 13+)

SecureCall asks for notification permission. Grant it. Without it, incoming call notifications will not appear and you will miss calls when the app is in the background.

### Step 4 — Battery Optimization

SecureCall will ask you to disable battery optimization for the app. Tap **Allow** on the system dialog. Without this exemption, Android may kill the background connection and you will miss incoming calls. On Samsung devices you may see additional guidance for One UI's aggressive battery management — follow the on-screen steps.

---

## Main Navigation

The app has four tabs at the bottom:

**Calls** — Your recent call history. Tap any entry to call that person back. Long-press an entry for options: save as contact, block, or delete from history.

**Contacts** — Your saved SecureCall contacts. Tap a contact to call them. Use the search bar to filter by name.

**Dialer** — Enter a SecureCall ID or phone number manually. Your own SecureCall ID is displayed here. Use the invite buttons to send your ID via SMS, messenger, or a share link.

**Settings** — All configuration options (detailed below).

The toolbar shows your connection status in real time:
- **Green** — Connected and ready to receive calls
- **Yellow** — Reconnecting
- **Red** — Disconnected

Tap the connection icon in the toolbar to manually disconnect or reconnect.

---

## Settings — Complete Reference

### Account

**Current Plan**
Displays your active tier (Free, Pro, or Premium). Read-only.

**Upgrade to Pro / Upgrade to Premium**
Opens the in-app purchase screen. Available on Free and Pro tiers respectively. Not shown on Premium.

**Activation Code**
Enter a redeemable code provided by StealthX. Type the code in the field and tap **Activate**. On success the app restarts and applies your new tier. On failure you will see one of these errors:
- *Invalid code* — the code does not exist
- *Code exhausted* — all device slots for this code are used
- *Max devices reached* — your account has reached the device limit
- *Connection timeout* — check your internet connection and retry

**Your SecureCall ID**
Your unique identifier on the network. Tap to copy it. Share this ID with anyone you want to call or receive calls from. If you have a Custom Call ID active, that is shown here instead.

**Phone Number**
The phone number associated with your account. Other users can call you using this number if they also use SecureCall. Change it here and the app re-registers with the server automatically.

**Custom Call ID** *(Premium only)*

A human-readable alias for your SecureCall ID (3–30 characters). Once activated on this device it cannot be transferred without your password.

- *Current ID* — shows your active custom ID or "Not set"
- *Custom ID input* — type your desired ID (lowercase only)
- *Password* — minimum 8 characters. Required to transfer the ID to another device. This password cannot be recovered. Store it safely.
- **Activate** — registers this ID on the current device. A confirmation dialog appears before activation.
- **Transfer** — enter the password to move an existing custom ID from a different device to this one.
- **Buy Custom ID** — opens the pricing page at stealthx.tech

---

### Security

**Certificate Pinning**
Shows whether certificate pinning is active. Read-only — determined by your tier.

**Device Attestation**
Shows whether device attestation (hardware integrity verification) is required. Read-only — determined by your tier.

**Hardware Keystore**
Shows whether encryption keys are required to be hardware-backed. Read-only — determined by your tier.

---

### Anti-Recording Protection

These settings control protections against call recording and screen capture during calls.

**Block Screenshots**
Prevents the system from capturing your screen while SecureCall is in the foreground. On Free tier this is locked off. On Pro it is on by default and can be toggled. On Premium it is forced on and cannot be disabled.

**Exclusive Microphone Access**
Prevents other apps from accessing the microphone while a call is active. On Free tier this is locked off. On Pro it is on by default and can be toggled. On Premium it is forced on.

**Detect Screen Recording**
Detects if another app is recording your screen and alerts you. On Free tier this is locked off. On Pro it is on by default and can be toggled. On Premium it is forced on.

*Security Level display* — shows a summary of your current protection level: "Basic — warnings only" (Free), "High — critical threats blocked" (Pro), or "Maximum — all protections enforced" (Premium).

---

### Privacy & Call Settings

**Background Connection**
Keep the SecureCall service running in the background so you receive incoming calls even when the app is minimized. This is on by default. Disabling it means you can only receive calls while the app is actively open on screen. Recommended: **On**.

**Battery Optimization Status**
Shows whether battery optimization is disabled for SecureCall. If it shows ⚠️ *Restricted*, tap to open the system dialog and allow unrestricted battery usage. SecureCall re-asks every 7 days if this has not been granted. Reliable call reception requires this to be unrestricted.

**Save Call History**
Store your recent calls locally on the device. On by default. Disable if you prefer no local call log.

---

### Appearance

**Dark Mode**
Three options: *System Default*, *Light*, or *Dark*. The selection applies immediately and is saved across sessions.

---

### Network & eSIM *(Pro and Premium)*

**Setup eSIM**
Opens Android's built-in eSIM management settings so you can add a carrier profile. Disabled if your device has no eSIM hardware.

**Active Network**
Read-only display of your current network connection (WiFi or Mobile) and binding status.

**Preferred Network**
Choose Default, WiFi, or Mobile. SecureCall will prefer this network type when it is available. Only active when switching between networks.

**VPN routing**
A green status LED appears only while SecureCall uses Android's active VPN route. The Google Play edition contains no VPN service and relies on a VPN managed by Android or another trusted app. The direct Premium APK can additionally run an optional, app-only WireGuard tunnel after Android VPN consent; its configuration remains local and its private key is encrypted with Android Keystore. If a VPN blocks signaling or WebRTC traffic, allow SecureCall in the provider's settings.

---

### IFR Holder Discount *(planned)*

The current SecureCall app does not include WalletConnect or in-app IFR tier unlocking.

IFR holder benefits are planned as a web checkout flow:

1. Open the StealthX web checkout.
2. Verify an eligible wallet in the browser.
3. The backend checks IFR balance read-only on Ethereum Mainnet.
4. Eligible holders receive a Stripe discount, currently planned at 50%.
5. SecureCall unlocks through the normal activation-code or license path.

This keeps wallet state out of the Android app and avoids fragile app-return links from wallet browsers.

---

### Diagnostics *(Pro and Premium)*

**Enable Diagnostic Logs**
Collect detailed logs of WebSocket events, FCM, calls, and network changes. Off by default. Enable only when troubleshooting.

**Export Logs as CSV**
Export all collected log entries as a CSV file and share it via the system share sheet. The button shows the current entry count.

**Clear Logs**
Delete all locally stored diagnostic log entries.

---

### About & Support

| Item | Description |
|---|---|
| Check for Updates | Check the current update channel or open it |
| GitHub | Source code repository |
| Documentation | Full documentation wiki |
| User Manual | This manual |
| Report a Bug | Bug report form |
| Privacy Policy | Data handling policy |
| Terms of Service | Usage terms |
| Open Source Licenses | Third-party license notices |
| Version | App version |
| Build Number | Internal build number |

**Support Development**
Ethereum, Bitcoin, and Solana donation addresses are shown here. Tap any address to copy it. The IFR Token link opens ifrunit.tech.

---

### Emergency Delete

Tap the Emergency Delete area **five times within five seconds** to trigger an immediate, irreversible wipe. There is no confirmation dialog.

- Tap 3: single vibration
- Tap 4: double vibration
- Tap 5: 150ms vibration and instant wipe

The wipe deletes all contacts, call history, encryption keys, settings, and wallet data. The app closes. This action cannot be undone.

---

## Deep Links & Invites

SecureCall supports the following deep link formats for adding contacts and sharing your ID:

- `https://stealthx.tech/invite/{secureId}` — adds the contact and optionally starts a call
- `securecall://add-contact?id=xxx&name=xxx` — adds a contact directly
- `securecall://custom-id?id=xxx&token=xxx` — activates a Custom Call ID

---

## Permissions Reference

| Permission | Purpose |
|---|---|
| Microphone | Capture audio during calls |
| Notifications | Incoming call alerts |
| Contacts (read) | Resolve contact names in call history |
| Phone state / numbers | Suggest your SIM number at onboarding |
| Battery optimization exempt | Keep background connection alive |
| Boot completed | Auto-restart connection after reboot |
| Full screen intent | Show incoming call screen over lock screen |

---

## Troubleshooting

**I miss incoming calls**
Open Settings → Privacy & Call Settings → Battery Optimization Status. If it shows Restricted, tap it and grant unrestricted battery usage. Also ensure Background Connection is turned on.

**Call quality is poor**
Check your network connection. If you are on mobile data, try switching to WiFi. If you are on WiFi, ensure you are not behind a restrictive firewall. The app uses a STUN/TURN relay as fallback for NAT traversal.

**The connection status stays red**
Your device cannot reach the SecureCall server. Check your internet connection. If the device uses an external VPN, verify that it allows SecureCall traffic.

**My tier shows Free after entering an activation code**
Activation codes restart the app automatically. Wait for the restart to complete. If the tier still shows Free, check the code for typos.

**The IFR verification shows insufficient balance**
Ensure the verified wallet holds enough IFR on Ethereum Mainnet. Visit ifrunit.tech or Uniswap if you need more tokens.
