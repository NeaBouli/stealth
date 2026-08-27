# StealthX Android Suite — Audit
> Scope: SecureCall, SecureChat, Chameleon Android clients; signaling checkout/contact paths; public sales, download, privacy and wiki pages  ·  Date: 2026-08-27  ·  Result: 1 FAIL / 6 WARN / 12 PASS

## Summary
The three Android codebases compile against and target API 36, pass their repository test and lint gates, and produce signed release candidates with verified package names, versions and certificates. Wallet and IFR verification are absent from all Android clients; IFR holder verification is browser-only and requires a signed proof of wallet ownership. Every signable SecureChat and Chameleon release variant now requires a server-signed entitlement instead of embedding paid access. Online sales must not launch until the approved Stripe checkout and Greek VAT/AADE/myDATA/e-timologio integration is complete and tested. Remaining non-payment gates are external publication/review work and physical multi-device coverage, not hidden code-build failures.

## Findings by domain
### Payments and Greek tax reporting — FAIL
- **[BLOCKING] Online checkout and statutory reporting remain intentionally on standby** — `backend/signaling/src/server.js`, `website/js/ifr-checkout.js`, `docs/PRICING.md`
  - What: The product owner explicitly deferred Stripe production checkout and Greek VAT/AADE/myDATA/e-timologio activation until the required account data arrives.
  - Path: A customer purchase cannot be declared launch-ready until payment success, invoice/tax classification and the required Greek fiscal transmission are tested end to end.
  - Fix: Complete the separately authorized VLABS payment/tax block, run success/failure/refund/idempotency tests, and record production evidence without secrets.

### Android build and policy — PASS
- **[LOW] API 36 and current billing baseline verified** — `client_android/app/build.gradle`, `client_android/gradle/libs.versions.toml`
  - What: SecureCall targets API 36 and uses Billing 8.2.1; SecureChat and Chameleon target API 36 and do not expose Play billing flows.
  - Path: Full Gradle checks and release builds completed successfully for all three repositories.
  - Fix: Keep these checks in CI for every release candidate.

### Wallet and IFR app boundary — PASS
- **[LOW] Android clients are wallet-free** — `client_android/app/src`, `securechat/app/src`, `chameleon/app/src`
  - What: Repository guards reject WalletConnect, SIWE and IFR unlock mechanisms in Android application code.
  - Path: The guards passed in SecureCall and Chameleon; SecureChat's equivalent source checks and full Gradle gate passed.
  - Fix: Preserve the guard tasks and browser-only product rule.

### Paid tier authorization — PASS
- **[LOW] Signable SecureChat and Chameleon builds cannot embed paid access** — `app/build.gradle.kts`, `build.gradle.kts`, `shared/src/main/java/com/stealthx/shared/DevTierOverride.kt`
  - What: Release, internal, Free, Pro and Elite compatibility variants all keep tier overrides disabled; only debug/screenshot builds can force a tier.
  - Path: New verification tasks fail the build if a signable variant enables the override. Generated BuildConfig values and signed APKs were rebuilt after the correction.
  - Fix: Distribute one base APK per product and issue server-signed, device-bound activation credentials after checkout.

### Activation transport pins — PASS
- **[LOW] Live activation host certificate chain matches the clients** — `data/src/main/java/com/stealthx/data/activation/ActivationCodeClient.kt`, `data/src/main/java/com/stealthx/data/exchange/ContactExchangeManager.kt`
  - What: The live leaf certificate is valid through October 2026; current Let's Encrypt intermediate and root backup pins are present in both SecureChat and Chameleon.
  - Path: The live chain and all configured SPKI pins were calculated and compared on 2026-08-27.
  - Fix: Keep certificate-chain verification in every release gate and rotate overlapping pins before certificate authority changes.

### IFR checkout authorization — PASS
- **[LOW] Wallet address ownership is proven before discount eligibility** — `backend/signaling/src/services/ifr.js`, `backend/signaling/src/server.js`, `website/js/ifr-checkout.js`
  - What: Browser checkout requires a signed challenge and a positive Ethereum-mainnet IFR balance; pasted addresses do not grant eligibility.
  - Path: Backend tests, handler tests and the new holder eligibility tests passed.
  - Fix: Re-run against the production RPC and Stripe test mode during the payment block.

### Contact privacy — PASS
- **[LOW] Periodic discovery no longer sends raw phone numbers** — `client_android/app/src/main/java/com/securecall/app/data`, `backend/signaling/src/server.js`
  - What: Client and server use matching normalized phone hashes, including `00` to `+` normalization.
  - Path: Backend test suite and Android unit tests passed.
  - Fix: Monitor discovery mismatch metrics without logging phone numbers.

### SecureCall call termination — WARN
- **[HIGH] Updated reason-aware disconnect behavior is not yet deployed to signaling production** — `backend/signaling/src/server.js`, `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
  - What: The server now forwards the termination reason and Android delays teardown only for an explicit peer disconnect.
  - Path: Local backend and Android tests pass, but production behavior remains unchanged until a separately authorized signaling deployment.
  - Fix: Deploy in a controlled signaling release, then run two-device hang-up, network-loss and reconnect tests.

### SecureCall Play policy — WARN
- **[HIGH] Google review remains an external release gate** — `client_android/app/src/free`, `client_android/app/build.gradle`
  - What: Free/Pro Play artifacts contain no `VpnService`; Premium direct APK retains the separately documented VPN runtime. The accepted Play replacement remains subject to Google review and active-track cleanup.
  - Path: VPN policy guards passed and signed artifacts were inspected, but repository tests cannot complete Google's review.
  - Fix: Confirm all active Play tracks contain only compliant artifacts and wait for policy approval before production declaration.

### Consent and crash reporting — WARN
- **[MEDIUM] AdMob UMP requires console-side message configuration** — `client_android/app/src/free`, `docs/PLAY_STORE_DATA_SAFETY.md`
  - What: The app gates ad requests on UMP consent and provides privacy options; EEA message configuration is external.
  - Path: Code and lint checks pass, but a missing or unpublished UMP message can still block compliant ad serving.
  - Fix: Verify the AdMob privacy message, regions and consent test devices in the AdMob console.

### SecureChat release readiness — WARN
- **[MEDIUM] Closed-test and physical messaging matrix are incomplete** — `app/build.gradle.kts`, `docs/PLAY_STORE_DATA_SAFETY.md`
  - What: Version 15 is signed and build-clean; one-device S10 install/start passed before the device disconnected.
  - Path: S7 and S4 were reserved by the Woizz developer, so cross-device send/receive, background and notification coverage was not rerun in this block.
  - Fix: Complete the three-device matrix and Google's tester-duration requirement before public production.

### Chameleon feature boundary — WARN
- **[HIGH] Cross-device overlay and messenger remain launch-gated** — `index.html`, `docs/user-manual.md`, `features/overlay`, `features/messenger`
  - What: The UI and documentation correctly mark these capabilities unavailable until authenticated pairing and interoperability are proven.
  - Path: Local unit/build/lint gates pass, but no permitted two-device run was available and the S10 disconnected before Chameleon smoke installation.
  - Fix: Finish authenticated pairing, then run two-device overlay/messenger and lifecycle tests before advertising those capabilities as available.

### Public pages and downloads — WARN
- **[MEDIUM] New pages and release aliases are prepared but not yet published** — `website/index.html`, `website/download.html`, `securechat/index.html`, `chameleon/index.html`
  - What: Local browser checks show current versions, stable `latest/download` links and no horizontal overflow.
  - Path: Until the branches merge, sites deploy and matching GitHub Release assets exist, public visitors still see the prior release state.
  - Fix: Merge through normal review, publish the three release asset sets, deploy the sites, and verify every public URL with HTTP 200.

### Signed artifacts — PASS
- **[LOW] Candidate identity and integrity verified** — `/Users/gio/Desktop/aab apk/presale-2026-08-27`
  - What: AAB/APK package names, version codes, API 36 metadata, release certificates and SHA-256 hashes were checked.
  - Path: SecureCall 1.0.50/78017, SecureChat 0.1.11/15 and Chameleon 0.1.13/14 were built from the audited worktrees.
  - Fix: Publish only these checksummed candidates after PR merge.

### SecureCall backend tests — PASS
- **[LOW] Signaling and checkout tests are green** — `backend/signaling/test`, `backend/signaling/src`
  - What: Context smoke, handlers, subscription/WebRTC, activation, payment and IFR tests passed; production dependency audit reports zero vulnerabilities.
  - Path: Syntax checks and the full test suite completed after legacy Android SIWE handlers were removed.
  - Fix: Retain the tests as deployment gates.

### SecureCall native media — PASS
- **[LOW] Native tests and strict lint are green** — `client/`, `client_android/app/src/main/cpp`
  - What: Rust unit/E2E tests and strict Clippy completed successfully; Android native libraries built for release ABIs.
  - Path: 28 unit tests, 6 E2E tests and `clippy -D warnings` passed.
  - Fix: Add physical Bluetooth/GSM interruption coverage when devices are available.

### SecureChat build matrix — PASS
- **[LOW] Full modular gate and signed variants succeeded** — `app`, `data`, `domain`, `presentation`, `security`, `features`
  - What: The original 1,305-task gate passed; the entitlement-hardening rerun passed 1,467 tasks, followed by signed base, Free, Pro and Elite compatibility artifacts with no embedded tier.
  - Path: Unit tests, module checks, Release Lint and debug assembly completed successfully.
  - Fix: Address Gradle 9 deprecations before the toolchain upgrade.

### Chameleon build matrix — PASS
- **[LOW] Full modular gate and signed variants succeeded** — `app`, `core`, `data`, `domain`, `presentation`, `security`, `features`
  - What: The original 1,448-task gate passed; the entitlement and pin-hardening rerun passed 1,615 tasks after clearing generated build output to resolve a full-disk interruption.
  - Path: Unit tests, module checks, Release Lint, IFR/wallet and release-tier guards, signed base AAB/APK plus compatibility APKs completed successfully.
  - Fix: Address deprecated Android APIs and Gradle 9 warnings in a separate maintenance block.

### Public privacy statements — PASS
- **[LOW] App and web data boundaries are documented** — `docs/PLAY_STORE_DATA_SAFETY.md`, `privacy.html`, `website/privacy.html`
  - What: Contact discovery, diagnostics, ads, wallet-free Android behavior and browser-only purchase verification are described consistently.
  - Path: Repository text scans and local browser checks found no contradictory in-app IFR unlock claim.
  - Fix: Reconcile the final text with Play Data Safety forms at submission time.

### Secret handling — PASS
- **[LOW] Release credentials remain outside Git** — `.gitignore`, local keystores, system keychain
  - What: Private signing material and passwords were not added to worktrees, Bridges or commits.
  - Path: Release builds consumed local ignored keystores and environment variables; only certificate fingerprints and artifact hashes are recorded.
  - Fix: Back up keys in the approved secure offline location.

## Priority matrix
### 🔴 BLOCKING
1. Complete and verify Stripe plus Greek VAT/AADE/myDATA/e-timologio before online sales activation.

### 🟠 HIGH
1. Deploy and physically verify the SecureCall reason-aware disconnect change.
2. Obtain Google approval for the VPN-free Play artifact and clean all active tracks.
3. Keep Chameleon cross-device overlay/messenger disabled until authenticated two-device tests pass.

### 🟡 MEDIUM
1. Configure and verify the AdMob UMP console message.
2. Complete SecureChat and Chameleon physical multi-device matrices and closed-test duration.
3. Merge, publish GitHub release assets and deploy all updated public pages.

### 🟢 LOW
1. Resolve Gradle 9 and deprecated Android API warnings in a maintenance release.
