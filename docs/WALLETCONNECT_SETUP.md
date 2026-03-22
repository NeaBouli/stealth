# WalletConnect v2 Setup — SecureCall

## Project ID — MUST BE REGISTERED

The Project ID must be created at [cloud.reown.com](https://cloud.reown.com):

1. Go to cloud.reown.com → Sign up / Log in
2. Create New Project → Type: App
3. Copy the Project ID (hex string, e.g. `a1b2c3d4e5f6...`)
4. Replace `PROJECT_ID` in `WalletConnectManager.kt` line 27

**Current placeholder:** `83571cb4-8aa5-4b4b-bc0e-b9b098785fc7` (not yet registered — will return 403)

## App IDs to register in Reown dashboard
All three flavors:
- `com.securecall.app.free`
- `com.securecall.app.pro`
- `com.securecall.app.premium`

## How it works
1. User taps "Connect Wallet" in Settings → IFR Token Unlock
2. WalletConnect Sign protocol creates a pairing URI
3. URI opens in installed wallet app (MetaMask, Trust Wallet, etc.)
4. User approves connection in their wallet app
5. SecureCall reads the connected wallet address
6. Server verifies IFR token balance on-chain
7. If balance >= 1,000 IFR → PRO, >= 5,000 IFR → PREMIUM
8. WalletConnect-verified wallets get **permanent unlock** (no 30-day expiry)

## Deep Link
- Scheme: `securecall://wc`
- Used as redirect after wallet app approves the connection

## SDK
- `com.walletconnect:android-core:1.26.0`
- `com.walletconnect:sign:2.26.0`
- Relay: `wss://relay.walletconnect.com`

## Troubleshooting
- **403 Forbidden**: Project ID not registered at cloud.reown.com
- **No wallet app found**: User needs MetaMask or Trust Wallet installed
- **Connection timeout**: Check internet, relay may be temporarily down
