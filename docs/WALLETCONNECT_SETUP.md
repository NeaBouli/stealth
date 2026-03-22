# WalletConnect v2 Setup — SecureCall

## Project ID
```
83571cb4-8aa5-4b4b-bc0e-b9b098785fc7
```

Registered at [cloud.reown.com](https://cloud.reown.com) (formerly cloud.walletconnect.com).

## App IDs to register
All three flavors need to be registered in the Reown dashboard:
- `com.securecall.app.free`
- `com.securecall.app.pro`
- `com.securecall.app.premium`

## How it works
1. User taps "Connect Wallet" in Settings → IFR Token Unlock
2. Reown AppKit modal opens (QR code or deep link to MetaMask/Trust Wallet)
3. User approves connection in their wallet app
4. SecureCall reads the connected wallet address
5. Server verifies IFR token balance on-chain
6. If balance >= 1,000 IFR → PRO, >= 5,000 IFR → PREMIUM
7. WalletConnect-verified wallets get **permanent unlock** (no 30-day expiry)

## Deep Link
- Scheme: `securecall://wc`
- Used as redirect after wallet app approves the connection

## SDK
- Reown AppKit BOM 1.7.7
- Relay: `wss://relay.walletconnect.com`

## Metadata
```
name: SecureCall
description: Encrypted P2P calling app — verify IFR token holdings
url: https://github.com/NeaBouli/stealth
icon: https://raw.githubusercontent.com/NeaBouli/stealth/main/website/icon.png
```
