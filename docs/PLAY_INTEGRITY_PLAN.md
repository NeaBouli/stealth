# Play Integrity Plan - SecureCall

Date: 2026-07-01
Status: Planning complete, implementation not started
Package scope: `com.securecall.app.free` public Play app, with internal `pro` and `premium` test flavors kept non-public unless explicitly built with `-Pinternal`.

## Goal

Use Play Integrity as a backend risk signal, not as an app-start kill switch. The first release should collect and log verdicts around abuse-sensitive actions while keeping normal calling, onboarding, and offline app usage available.

## Source Baseline

- Google Play Integrity API overview: verifies that server requests come from the genuine app on a genuine, certified Android device.
- Standard requests: suitable for app-to-server interactions and supported on Android 5.0+.
- Classic requests: useful for high-value actions that need explicit nonce binding and replay/tamper protection.
- Optional verdicts: app access risk and Play Protect can be enabled in Play Console after linking a Cloud project.
- Current Android dependency from Google docs: `com.google.android.play:integrity:1.6.0`.
- Official references checked on 2026-07-01:
  - https://developer.android.com/google/play/integrity/overview
  - https://developer.android.com/google/play/integrity/setup
  - https://developer.android.com/google/play/integrity/standard
  - https://developer.android.com/google/play/integrity/classic
  - https://developer.android.com/google/play/integrity/verdicts

## Phase 1 - Signal Only

Protected actions:
- Activation-code redeem.
- Upgrade/license refresh.
- Stripe/Billing entitlement sync.
- Suspicious rapid REGISTER/reconnect loops.

Client behavior:
- Request an integrity token only when one of the protected actions starts.
- Do not request on every app launch.
- Cache only minimal local state such as last request time and transient failure reason.
- If Play services, network, or token generation fails, continue the user action and send `integrityUnavailable=true` to backend telemetry.

Backend behavior:
- Generate a short-lived nonce or request hash for the protected action.
- Decode/verify the token server-side through Google Play.
- Store summarized verdict fields with timestamp, package name, account/device/app verdicts, and action name.
- Never log raw tokens.
- Treat missing or weak verdicts as risk flags, not automatic denial.

## Phase 2 - Policy Gates

Candidate soft gates:
- Add review/step-up logging for `appRecognitionVerdict` not matching the Play-installed app.
- Flag devices without acceptable device integrity during activation-code redeem.
- Flag high recent-device-activity levels before repeated checkout or activation attempts.
- Use app access risk only for warning/step-up on payment or activation flows, not for calls.

Hard denial should require a separate Gio sign-off and observed telemetry. Do not block app startup, contacts, existing active calls, or emergency call cleanup paths based on Play Integrity.

## Backend Contract Draft

`POST /integrity/challenge`

Request:
```json
{
  "action": "activation_redeem",
  "clientId": "android-...",
  "packageName": "com.securecall.app.free"
}
```

Response:
```json
{
  "challengeId": "uuid",
  "nonce": "base64url-16-to-500-chars",
  "expiresAt": "ISO-8601"
}
```

`POST /integrity/verify`

Request:
```json
{
  "challengeId": "uuid",
  "integrityToken": "signed-token",
  "actionPayloadHash": "sha256-base64url"
}
```

Response:
```json
{
  "decision": "allow",
  "risk": "low",
  "signals": {
    "app": "recognized",
    "device": "meets_device_integrity",
    "playProtect": "not_evaluated",
    "appAccessRisk": "not_evaluated"
  }
}
```

## Implementation Tasks

- Link the SecureCall Play app to a Google Cloud project in Play Console.
- Enable optional Play Protect and App Access Risk verdicts only after deciding whether the added latency is acceptable.
- Add the Play Integrity library to `client_android/app/build.gradle`.
- Add a small client wrapper for challenge, token request, and verify calls.
- Add backend challenge storage with single-use nonce expiry.
- Add backend token verification and summarized telemetry.
- Add tests for nonce replay, wrong package, expired challenge, missing token, and Google API failure.
- Add release monitoring before any enforcement.

## Rollback

Keep all Phase 1 behavior behind backend config:
- `PLAY_INTEGRITY_ENABLED=false` disables challenge generation and verification.
- `PLAY_INTEGRITY_ENFORCEMENT=log` is the initial and default mode.
- `PLAY_INTEGRITY_ENFORCEMENT=deny` must not be used until telemetry is reviewed.
