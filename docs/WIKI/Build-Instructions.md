# Build Instructions

> **Important: Source-Available License**
>
> This code is published for **transparency and security auditing** purposes.
> Building, distributing, or commercial use is **NOT** permitted.
> Download the official app from Google Play only.
>
> See the [LICENSE](https://github.com/NeaBouli/stealth/blob/main/LICENSE) for full terms.

## For Security Researchers Only

The following instructions are provided so that security researchers can verify
the cryptographic implementation matches the published claims.

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| JDK | 17+ | `brew install openjdk@17` / [Download](https://adoptium.net/) |
| Android SDK | API 33+ | Via Android Studio |
| Android NDK | r25+ | Via Android Studio SDK Manager |
| Rust | 1.70+ | `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \| sh` |
| Node.js | 18+ | `brew install node@18` / [Download](https://nodejs.org/) |

### Rust Android Targets

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
rustup target add i686-linux-android
```

### Building the Rust Crypto Engine

```bash
cd core_crypto
cargo build --release
cargo test --release
```

### Building the Android App (Debug)

```bash
cd client_android
./gradlew assembleFreeDebug
```

The debug APK will be at:
`app/build/outputs/apk/free/debug/app-free-debug.apk`

### Running the Signaling Server (Local)

```bash
cd backend/signaling
npm install
npm start
```

Server starts on `http://localhost:8080`. Health check: `http://localhost:8080/health`

### Running Tests

```bash
# Rust tests
cd core_crypto
cargo test

# Android instrumented tests (requires connected device)
cd client_android
./gradlew connectedFreeDebugAndroidTest
```

---

[← Back to Home](Home.md)
