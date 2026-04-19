# StealthX Unified ID System

## Concept
One ID for all StealthX products.
Anyone who knows your sx_ID can reach you on all channels.

## Format
sx_[9 characters Base58]
Example: sx_a7Kx9mPq2

## Custom Handle (Pro/Elite)
@username (3-20 characters)
Displayed instead of the sx_ID wherever possible.

## Generation
- Deterministic from Ed25519 Public Key (SHA-256, Base58, 9 chars)
- One-time per device — on first launch of a StealthX app
- No server required — purely local
- Same ID in SecureCall and SecureChat

## Cross-App Usage
| App | ID Usage |
|-----|----------|
| SecureCall | Receive calls under sx_ID |
| SecureChat | Receive messages under sx_ID |

## Contacts
A contact with an sx_ID is automatically reachable in both apps
— provided they have the respective app installed.

## Privacy
- ID is stored locally in EncryptedSharedPreferences
- No central registry
- Kaspa can optionally serve as a public directory (Phase 2)
