# SecureCall - Play Store Data Safety Form

This document reflects the Google Play package `com.securecall.app.free` candidate
`1.0.50-free` (version code 78017). Recheck it whenever advertising, diagnostics,
push, phone discovery, billing, entitlement, or SDK behavior changes. The separate
direct Premium APK is not a Google Play artifact and has its own VPN disclosure.

## Data Collection

| Question | Answer |
|----------|--------|
| Does the app collect or share user data? | Yes |
| Is collected data encrypted in transit? | Yes |
| Can users request deletion? | Yes - through in-app reset and the privacy-policy contact channel |

## Data Types To Declare

| Play data type | Collected | Shared | Required | Purpose |
|----------------|-----------|--------|----------|---------|
| Personal info - User IDs | Yes | No | Required | App functionality; security and fraud prevention |
| Personal info - Phone number | Yes | No* | Optional | Contact discovery, caller identification and app functionality |
| Contacts | Yes | No* | Optional | One-way phone hashes for user discovery; raw names and numbers remain local |
| App activity - App interactions | Yes** | Yes** | Optional in Free | Advertising |
| Device or other IDs | Yes** | Yes** | Optional in Free | Advertising; analytics are not used |
| App info and performance - Crash logs | Yes | Yes*** | Optional in Free, in-app opt-out | App functionality; diagnostics |
| Purchase history | Yes | Yes**** | Optional | App functionality; account management; fraud prevention |

`*` A user-supplied phone number is processed by the StealthX signaling service while
the user is registered. Bulk discovery sends one-way hashes. Explicit single-number
dial/lookup actions send the entered number for routing.

`**` Google AdMob processing applies only to the Free tier after UMP consent permits
an ad request. Confirm the exact declarations against the current Google Mobile Ads
SDK data-disclosure page before every submission.

`***` Firebase Crashlytics applies only to the Free tier and can be disabled in app
settings. Pro and Premium disable Crashlytics.

`****` Google Play processes purchase data. SecureCall receives product and entitlement
status, not payment-card or bank details.

## Data Not Collected

- Call audio or recordings
- Precise or approximate location
- Photos, videos, files, health, financial-account, or wallet data
- SMS or email content

## Processing Details

- Pseudonymous SecureIDs, connection/session identifiers and optional phone routing
  data are processed for signaling. No persistent server-side call history is kept.
- Call content is end-to-end encrypted. STUN/TURN providers may process network
  addresses required for WebRTC connectivity.
- FCM tokens are stored for incoming-call delivery.
- Raw contact names and the address book remain local. Discovery uses one-way hashes,
  except when the user explicitly dials or looks up a supplied phone number.
- Browser IFR holder verification and Stripe checkout are not part of the Android app.
- The Play artifact contains no `VpnService` and no WireGuard dependency.

## SDK Inventory Relevant To Data Safety

- Firebase Cloud Messaging
- Firebase Crashlytics (Free, optional)
- Google Mobile Ads SDK and UMP (Free)
- Google Play Billing Library 8.2.1 (Free Play package)
- WebRTC/STUN/TURN networking

## Privacy Policy

URL: https://stealthx.tech/privacy.html

Contact: kaspartisan@proton.me
