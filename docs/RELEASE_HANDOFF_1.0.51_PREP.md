# SecureCall 1.0.51 — Release Preparation Handoff (PREPARATION ONLY)

Status: **NOT release-ready.** Nothing here is built, signed, uploaded or approved.
Prepared 2026-10-10 under SC-RC151-PREP-20261010. `PRODUCT_READY` and the matching separate
VLABS `FINANCE_READY` are **not granted**. No sales are active and no codes have been issued.

## 1. Version metadata (release metadata -> flavor artifact hop)

| Field | Value | Source |
|---|---|---|
| versionName | `1.0.51` | `client_android/app/build.gradle` `defaultConfig` |
| base versionCode | `78018` | same; previous `78017` (1.0.50 candidate) |
| Per-flavor versionCode offsets | none (flavors only add `versionNameSuffix` -free/-pro/-premium) | `productFlavors` |
| AAB versionCode (Play) | `78018` | `buildingAppBundle` skips ABI rewrite |
| Split APK versionCodes (direct) | arm64-v8a `78018001`, armeabi-v7a `78018002`, x86_64 `78018003`, universal `78018009` | ABI rule in `androidComponents` |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 (unchanged) | `build.gradle` |
| Packages | Play `com.securecall.app.free`; direct `.pro`, `.premium` | `docs/DISTRIBUTION_MATRIX.md` |
| Base (PROVISIONAL) | `3f3fa0550b7544535b3cb239fe1bc89105c71a17` (frozen PR119 branch) | integration base |

### Version-code inventory (read-only, 2026-10-10)

- All local branches/remote-tracking refs: highest `versionCode` is `78017` (1.0.50 candidate);
  older values 78013–78016.
- Local artifact filenames (names only): highest `vc78016` (1.0.49 AAB); direct APK highest 1.0.48 / `vc78015`.
- Public GitHub releases (NeaBouli/stealth): latest `v1.0.48`; no 1.0.49/1.0.50/1.0.51 release.
- `78018` is therefore strictly greater and unused in this inventory.
- **Google Play-wide uniqueness: UNVERIFIED.** The current Play Console version history was not
  accessible. The owner must confirm in Play Console (App bundle explorer / Release history) that
  `78018` was never uploaded on any track before upload; if used, bump and re-run all gates.

### Deliberately NOT changed (blocker for lead decision)

`ENTITLEMENT_RELEASE_ID` is still `securecall-android-1.0.50-vc78017-api36`. It is an entitlement
binding duplicated in `build.gradle`, three Android unit tests, one instrumentation test and
`backend/signaling/src/payments/vlabs_fulfillment.js`. Changing it is payment/entitlement scope
and was excluded. Before any direct Pro/Premium APK or sale: decide whether 1.0.51 keeps the
1.0.50 ID or all of those places move together under a finance-reviewed change.
The Play freeRelease AAB keeps `BILLING_ENABLED=false`; paid activation still needs its own verified binding and gates. No upload decision is made here.
Public website/README strings still say "candidate v1.0.50"; update only after a release decision.

## 2. Release notes (PROPOSED — depend on the final integrated candidate; edit before use)

Final reviewed queue 138/137/134/136/119/139/120 must be integrated, then rebuilt and retested.
Do **not** mention pending migration (PR120), real-call or paid-entitlement tests as complete.
Do **not** advertise Double Ratchet, automatic VPN or active sales/paid unlock.

EN (proposed):
```
SecureCall 1.0.51
- Maintenance and security hardening updates
- Dependency and build updates
- The Google Play edition contains no app-owned VPN service; VPN connections managed by Android or another provider are still detected
```

DE (vorgeschlagen):
```
SecureCall 1.0.51
- Wartungs- und Sicherheitsverbesserungen
- Aktualisierte Abhaengigkeiten und Build-Anpassungen
- Die Google-Play-Version enthaelt keinen app-eigenen VPN-Dienst; von Android oder einem anderen Anbieter verwaltete VPN-Verbindungen werden weiterhin erkannt
```

## 3. Play checklist (later owner/operator steps; none performed)

Channel separation (must not be mixed):

- **Play:** `freeRelease`, `app-free-release.aab`, package `com.securecall.app.free`, no app-owned VPN service, `BILLING_ENABLED=false`.
- **Direct (not Play):** `-Pinternal` Pro/Premium APKs (`.pro`, `.premium`); Premium only has optional WireGuard.

Fill in at the final candidate (all blank until built and verified):

- [ ] Final commit hash: `________` (integrated queue 138/137/134/136/119/139/120 on reviewed base)
- [ ] versionName `1.0.51`, versionCode `78018` read from the built AAB (not from source)
- [ ] Package `com.securecall.app.free`; targetSdk `36`
- [ ] AAB signing certificate SHA-256 matches the current Play **upload certificate**, not the separately managed Google app-signing certificate; do not change either key
- [ ] AAB SHA-256: `________`
- [ ] R8 mapping: `client_android/app/build/outputs/mapping/freeRelease/mapping.txt` archived per version, uploaded to Play and Crashlytics
- [ ] Policy: `verifyFreeReleaseVpnPolicy` PASS; AAB has zero `VpnService`, `wireguard`, `libwg` entries
- [ ] Billing: `verifyFreeReleaseBillingClosed` PASS (PR119 AGP 9 host-test guard intact); all Play products inactive
- [ ] Data Safety re-verified against this exact AAB (no reuse of historical answers)
- [ ] Latest Play code-history confirmation: versionCode `78018` unused on every track
- [ ] All-track VPN cleanup: no earlier VpnService-bearing artifact remains active on any track (Internal/Closed/Open/Production)
- [ ] Track and rollout percentage: UNDECIDED; record separately approved destination and percentage before submission
- [ ] Later upload clicks: Play Console > SecureCall > approved testing track (or separately authorized Production) > Create new release > upload verified `app-free-release.aab` > paste reviewed EN/DE notes > Save. Review and submit/start rollout only under the exact track-specific approval; no track or rollout is selected here.

Gates required on the FINAL candidate (all NOT RUN here):

- [ ] Android: `./gradlew --no-daemon --max-workers=1 -Pinternal testFreeDebugUnitTest testPremiumDebugUnitTest testFreeReleaseUnitTest verifyFreeReleaseBillingClosed verifyNoVpnServiceSource verifyFreeReleaseVpnPolicy verifyProReleaseVpnPolicy verifyPremiumReleaseVpnRuntime lintFreeRelease assembleFreeRelease bundleFreeRelease assembleProRelease assemblePremiumRelease`
- [ ] Backend: signaling full `npm test` (tester/TURN/backend suites), lint
- [ ] Native: Rust `cargo test`/clippy, NDK/Opus JNI build
- [ ] Instrumentation and physical-device/runtime smoke (existing terminal owns all devices/emulators)
- [ ] Artifact inspection of Free AAB / Pro APK / Premium APK per `docs/DISTRIBUTION_MATRIX.md`
- [ ] Open: original Crashlytics action attribution, versioned R8 mapping and fatal-crash monitoring
- [ ] Open: PR120 migration, real-call test and paid-entitlement test remain pending

Commercial activation requires matching `PRODUCT_READY` and `FINANCE_READY` plus explicit release approval; neither is granted. An isolated, noncommercial test upload does not itself require sales authorization, but still requires its own approved track, verified code/signing/policy and applicable security/test acceptance. Any applicable red or unverified gate blocks the corresponding action. This preparation document authorizes no upload, rollout or sale.

## 4. Rollback and post-release checks (later, separately authorized)
- Preserve the last reviewed source/artifacts and versioned mapping. Stop a failing rollout through the authorized operator; Android cannot install a lower versionCode as a normal update. An approved forward-fix uses a new higher code and full gates; do not revert license records or rotate keys as a rollback shortcut.
- Recheck exact installed package/version, API34+ startup and incoming calls, distribution/policy guards and fatal events against this version's mapping. Keep old1.0.48 events separate. Sales/license checks require the approved isolated evidence and matching operator gate before activation.
