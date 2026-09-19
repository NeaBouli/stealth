# StealthX / SecureCall - Master Status

> Snapshot: 2026-09-19 EEST  
> Scope: SecureCall product, Android distributions, signaling, public web/wiki/store surfaces,
> Google Play, security audits, device QA, direct sales, browser-only IFR holder discount and
> private VLABS finance coordination.  
> Current decision: **NOT PRODUCT_READY / NOT FINANCE_READY / SALES CLOSED**.

This is a public-safe engineering snapshot. It contains no secret, customer, wallet, tester,
provider, tax-account or signing-key value. Historical Bridge entries remain authoritative for
the exact execution evidence of earlier blocks.

## 1. Executive Status

SecureCall is not ready for unrestricted sale or a final production release yet. The strongest
foundations are already in place: Android targets API 36, Google Play Billing is on 8.2.1, the
Play/Direct VPN distribution split is enforced by build guards, Android contains no IFR or wallet
verification, direct-entitlement and revoke paths have been hardened fail-closed, and the
browser-only IFR proof candidate plus its exact VLABS offer contract have been accepted for
closed-gate pairing.

The remaining work is not one isolated bug. It is a controlled integration sequence:

1. integrate the reviewed entitlement base and its stacked security/privacy fixes;
2. close the unauthenticated identity and call-key-binding findings without breaking existing
   installations;
3. verify the TURN host configuration and finish the remaining audit waves;
4. correct narrow-phone UI defects and complete the real call/settings/device matrix;
5. complete VLABS signer/verifier pairing and canonical durable IFR checkout in non-production;
6. prove purchase, fulfilment, restore, refund, revoke, dispute and fiscal flows end to end;
7. freeze a new immutable release, build newly signed artifacts, rerun all release and physical
   device gates, then publish only after both readiness decisions are granted.

No current Desktop APK/AAB should be sold as the final fixed release. The newest local artifacts
were built before the September audit fixes and are evidence inputs, not the final sale candidate.

## 2. Authoritative Source and Working Model

### 2.1 Repository safety

- Public repository: `NeaBouli/stealth`.
- The canonical checkout at `/Users/gio/Desktop/repos/stealth` is intentionally not used for new
  changes. It is 10 commits ahead and 49 behind its remote tracking branch and contains a large
  set of pre-existing local changes, including unfinished tester-entitlement work.
- Current work therefore uses isolated worktrees and small stacked branches. No unrelated local
  file is reset, cleaned, overwritten or silently committed.
- Finance coordination is private in `NeaBouli/vlabs`; public files contain only neutral gate
  status and versioned public-safe contracts.

### 2.2 Current source stack

| Layer | Head | State | Purpose |
|---|---:|---|---|
| `main` | `e06d0184` at last clean baseline read | production baseline, behind active review stack | signaling startup restoration and prior merged work |
| PR #82 | `20018e0b` | open, mergeable, all exact-head checks green, normal review required | entitlement, fulfilment, refund/revoke and closed commerce gates |
| PR #90 | `97d667b1` | open, mergeable, stacked on #82, checks green | browser-only IFR ownership proof candidate; remains disabled |
| PR #91 | `a5111b8b` | open, mergeable, stacked on #82 | STX-02 public IP disclosure fix |
| PR #92 | `94221a4b` | open, mergeable, stacked on #82 | STX-23 certificate pin rotation/freshness policy |
| PR #93 | `f3d9708a` | open, mergeable, stacked on #82 | STX-29 removal of unconsented GA4 |
| Current product-truth branch | based on `97d667b1` | locally verified; publication/review pending | STX-22, STX-37 and STX-38 corrections |

The final release must contain all accepted layers on one reviewed immutable commit. Passing tests
on independent stacked heads is not a substitute for testing the final combined head.

## 3. Distribution Contract

SecureCall is permanently split into distinct artifacts:

| Channel | Variant | Package | Output | App-owned VPN |
|---|---|---|---|---|
| Google Play | `freeRelease` | `com.securecall.app.free` | Free AAB only | forbidden; external VPN detection/indicator only |
| Direct Pro | `proRelease -Pinternal` | `com.securecall.app.pro` | Pro APK | none |
| Direct Premium | `premiumRelease -Pinternal` | `com.securecall.app.premium` | Premium APK | optional local WireGuard after Android consent |

Permanent consequences:

- A Play entitlement can unlock application features inside the Play package, but can never add
  the separately distributed Premium APK's WireGuard runtime.
- Pro/Premium APKs must never be uploaded to the SecureCall Play listing.
- Direct Premium may keep its VPN implementation; Google Play Free and Direct Pro must remain free
  of `VpnService`, WireGuard dependencies, native WireGuard libraries and dynamic VPN installers.
- Android must remain free of WalletConnect, SIWE and IFR entitlement logic. The IFR benefit is a
  browser purchase discount only.

## 4. Current Android Candidate

The current reviewed source candidate declares:

- version name `1.0.50`;
- version code `78017`;
- compile/target SDK 36;
- Google Play Billing `8.2.1`;
- `BILLING_ENABLED=false` in all current variants;
- `ACTIVATION_CODE_ENABLED=false` in all current variants.

These flags are intentionally closed. They must not be enabled merely because source tests pass.
The final integrated release may need a new Play-unique version code after the Console is checked;
`78017` must not be assumed unused without that check.

### 4.1 Existing local artifacts

The presale directory contains previously signed `1.0.50 / 78017` candidates for Free AAB and
Free/Pro/Premium APKs, built on 2026-08-27. They predate the September security, privacy, pinning and
content corrections. They are useful only for comparison and must be rebuilt after final
integration. Desktop `SecureCall-LATEST.aab` currently points to an older `1.0.49 / 78016` build.

### 4.2 Required final artifact checks

- exact package, version, target SDK, signing certificate and SHA-256 for every artifact;
- archive scan proving Play Free and Direct Pro contain no VPN service/library;
- archive scan proving Direct Premium contains only the intended Premium VPN runtime;
- R8/minification, 16 KB native-page compatibility and ABI inspection;
- API 24 and API 36 instrumentation plus Free/Pro/Premium unit/lint/build matrix;
- repository guards for no Android IFR/wallet code and closed billing gates;
- install/update/restore behavior on real devices;
- final website and download assets must match those exact hashes.

## 5. VLABS and IFR Holder Discount

### 5.1 Accepted closed-gate offer contract

The private VLABS operator accepted the following exact contract under request
`SECURECALL-VLABS-INPUT-01`:

| Field | Pro | Premium |
|---|---|---|
| Product ID | `stealthx-securecall-pro-lifetime` | `stealthx-securecall-premium-lifetime` |
| Catalog | `stealthx-lifetime-v1` | `stealthx-lifetime-v1` |
| Full-price offer | `securecall-pro-eur-1500-lifetime-v1` | `securecall-premium-eur-2500-lifetime-v1` |
| IFR offer | `securecall-pro-ifr50-eur-750-lifetime-v1` | `securecall-premium-ifr50-eur-1250-lifetime-v1` |
| Release binding | `securecall-android-1.0.50-vc78017-api36` | same |
| Full price | EUR 15.00 | EUR 25.00 |
| IFR-holder price | EUR 7.50 | EUR 12.50 |
| Discount | 5000 basis points | 5000 basis points |

Business rule: every wallet with a positive verified IFR balance is eligible. There is no token
threshold and no lifetime redemption cap. Each ownership proof is single-use and expires exactly
five minutes after issuance.

### 5.2 Privacy and security boundary

Only a domain-separated SHA-256 proof digest, proof version, eligibility fact, exact immutable
product/offer/release/price tuple and eligibility expiry may cross into payment evidence. Raw wallet
address, signature, nonce, message and balance must not be stored in Stripe metadata or finance
records.

The current source proves browser wallet ownership and rejects wrong domain, chain, account,
signature, expired proof, replay, zero balance and tuple drift. It remains closed by
`data-ifr-enabled=false`. The process-local challenge store is test evidence only and is not an
accepted multi-instance production store.

### 5.3 Remaining VLABS-owned work

- isolated non-production entitlement signer and public-verifier handoff for exact PR #82 source;
- synthetic issue, refresh, restore, transfer, duplicate, expiry and revoke matrix;
- durable multi-instance single-use IFR challenge and consumption in the canonical VLABS checkout;
- exact accepted offer-tuple enforcement;
- Stripe Test lifecycle including failed/abandoned checkout and idempotent replay;
- Elorus Demo and later approved Greek VAT/AADE/myDATA/e-timologio evidence;
- refund, mandatory remedy, dispute, revoke and accounting lifecycle;
- version-bound `FINANCE_READY` decision.

SecureCall must not enable the legacy direct Stripe route or build a parallel durable checkout.

### 5.4 Remaining SecureCall-owned work

- integrate and freeze the exact entitlement and IFR source;
- provide the approved public verifier in final release configuration without embedding a private
  key;
- keep all Android wallet/IFR guards green;
- provide final signed artifact hashes and device evidence;
- obtain `PRODUCT_READY` for the same immutable product/release tuple used by VLABS;
- keep all buttons/routes closed until both decisions and a separate activation authorization.

## 6. Google Play and Store Compliance

### 6.1 Already implemented in source

- target API 36;
- Billing Library 8.2.1;
- Play Free AAB contains no app-owned VPN service by design and build policy;
- Play and Direct product distinction documented;
- wallet/IFR verification absent from Android;
- billing and activation controls fail closed.

### 6.2 Still requiring Console or external confirmation

- confirm every active Play track no longer serves the rejected `78013` artifact with `VpnService`;
- confirm the VPN-policy case is cleared for the compliant replacement;
- confirm the highest used version code before selecting the final upload code;
- confirm Android developer/app/signing-key verification requirements due 2026-09-30;
- configure and test Play license testers, product IDs, purchase, restore, cancel, refund and RTDN;
- configure authenticated Pub/Sub/OIDC RTDN and least-privilege service account;
- verify App Content, Data Safety, ads declaration, target audience, content rating and privacy URL;
- verify AdMob UMP message/regions/test devices outside the repository;
- replace the obsolete WalletConnect/IFR store screenshot;
- update exact release notes/screenshots only after the final build exists.

Google Play approval does not grant VLABS `FINANCE_READY`, and VLABS direct-channel approval does
not grant Play Billing readiness. They are separate sales contracts.

## 7. Security Audit Intake

### 7.1 Audit identity

- Consolidated report: 30 pages, dated 2026-09-15.
- Local PDF SHA-256:
  `7dc9d7521d0103bbf7952848acd687bb13a392b3a5084b1e7df8a0b6b4219537`.
- Audit PR #83 head `2672781c`; open, mergeable, independent review required.
- Umbrella issue #84 catalogs 62 findings: 0 Critical, 8 High, 28 Medium, 20 Low,
  6 Informational.
- Triage PR #88 head `7f6a40bc`; open and mergeable. Its Android failure is from the old runner
  setup inherited by that branch, not from the documentation-only diff; it still needs a clean
  integration/recheck.
- Device/UI audit PR #85 head `d609e978`; open and mergeable; report-only.

### 7.2 High findings

| ID | Current status | Required closure |
|---|---|---|
| STX-01 unauthenticated WebSocket registration | open; coupled design queued with STX-21 | compatible per-install identity proof, reclaim/recovery model, migration, FCM and active-session takeover tests |
| STX-02 public IP disclosure | local fix green in PR #91 | integrate, deploy under separate approval, redacted live endpoint recheck |
| STX-03 TURN secret/config risk | unverified on host | inspect effective host config without printing secret; render secret correctly; test relay credentials and abuse limits |
| STX-21 unauthenticated key exchange | open; coupled with STX-01 | authenticated identity/key binding, downgrade/replay/MITM tests and safe migration |
| STX-22 false Double Ratchet/PFS claims | local product-truth fix and regression guard green | publish, obtain normal review, integrate and verify live/public surfaces |
| STX-29 GA4 without consent | local fix green in PR #93 | integrate, deploy and confirm zero analytics requests on all live pages |
| STX-37 public wiki advertises removed GhostNet | local product-truth sources corrected | publish reviewed website/GitHub wiki sources and verify all linked live pages |
| STX-38 inconsistent/scarcity pricing | local product-truth fix and regression guard green | integrate accepted VLABS tuple; keep checkout closed until both readiness gates pass |

### 7.3 Medium findings still open unless explicitly listed above

- STX-04 phone discovery enumeration and unsalted number hashes;
- STX-05 gift-code entropy and per-code attempt limiting;
- STX-06 Custom-ID password/pepper/transfer controls;
- STX-07 proxy/IP limiter identity; PR #82 contains substantial hardening, final audit closure
  still requires integrated verification;
- STX-08 unauthenticated unbounded public-key registration;
- STX-09 tracked/baked runtime data files;
- STX-10 deployment script secret output;
- STX-11 obsolete unsafe payment implementation and setup path;
- STX-12 tier security behavior differs from the published matrix;
- STX-13 public OpenRelay fallback;
- STX-23 expired/stale pin set: local fix green in PR #92, final integrated artifact pending;
- STX-24 media replay/AAD/direction separation;
- STX-25 key-material copy zeroization;
- STX-26 dormant mock GhostNet crypto layer;
- STX-30 private security reporting channel;
- STX-31 invite/deep-link/local-storage defects;
- STX-32 third-party JavaScript integrity/dependency policy on wallet/payment paths;
- STX-33 obsolete live return/intent forwarding surface;
- STX-39 audit-claim accuracy;
- STX-40 changelog/version-source drift;
- STX-41 fastlane listing drift;
- STX-42 live Play status contradiction;
- STX-43 non-existent issue references and stale known issues;
- STX-44 hosting/provider documentation drift;
- STX-45 F-Droid/GPL remnants;
- STX-56 `llms.txt` factual drift;
- STX-57 incorrect digital-product JSON-LD return/shipping schema.

### 7.4 Low and informational findings still tracked

- STX-14 admin comparison/route/throttling consistency;
- STX-15 WebSocket Origin default behavior;
- STX-16 caller-controlled metadata/FCM disclosure;
- STX-17 limiter/store map lifecycle;
- STX-18 data-directory fallback;
- STX-19 stub/dev artifacts near live paths;
- STX-20 fingerprinting and header hygiene;
- STX-27 FFI panic/zeroization/AAD hardening;
- STX-28 core-crypto README, KAT and fuzz coverage;
- STX-34 dynamic price escalation code;
- STX-35 payment-success/deep-link/support contradiction;
- STX-36 verified public non-secret identifiers;
- STX-46 absolute marketing claims;
- STX-47 contact/disclosure fragmentation;
- STX-48 version-identifier drift;
- STX-49 removed WalletConnect screenshot;
- STX-50 stale documentation cluster;
- STX-51 triple-wiki/manual-publish drift;
- STX-52 divergent privacy policies;
- STX-53 stale/self-scored audit page;
- STX-54 cosmetic/fake-heartbeat copy cluster;
- STX-55 source-available license versus audit-build contradiction;
- STX-58 sitemap hygiene;
- STX-59 crawler/robots/transactional-page policy;
- STX-60 missing static-host security headers;
- STX-61 wrong/inert redirects file;
- STX-62 hreflang and 404/invite-router coherence.

No checkbox in issue #84 should be marked complete until the fix is integrated and its required
runtime/live verification has passed.

## 8. Device and UI Audit

The 2026-09-18 device audit reproduced phone-only layout failures that tablet testing hid:

- on the 320 dp S10 width, bottom-navigation labels collapse to 0 x 0 while the Tab S4 shows all
  four labels;
- narrow-phone bottom navigation is about 47 dp high versus 80 dp on the tablet;
- S10 dial buttons were measured around 91 x 42 dp, below the 48 dp touch-target minimum;
- contact suggestions below the number input are partially obscured by the keyboard;
- the `+` secondary glyph on the `0` key is not visible enough;
- there are no screen-width or landscape layout qualifiers, so one weighted layout serves phone
  and tablet extremes;
- the VPN settings section does not follow the same collapse/expand behavior as the other settings
  groups.

Required fix and validation:

- add responsive constraints/qualifiers without changing dial semantics;
- force a stable bottom-navigation label policy and usable touch targets;
- apply IME/inset-aware scrolling so matched contacts remain visible;
- restore the `+` glyph and accessibility labels;
- normalize settings section expansion behavior;
- run 320 dp phone, normal phone, tablet, portrait, landscape and large-font/TalkBack checks;
- preserve `FLAG_SECURE`; use hierarchy measurements and approved local visual inspection rather
  than weakening screenshot protection.

The reported speaker toggle/audio-route defect, S10 phone-confirm behavior, reconnect/disconnect
handling, ads covering controls and background incoming-call behavior must remain in the final
physical matrix even where earlier patches exist. Historical fixes are not final proof for the new
combined release.

## 9. Real Device and Emulator Status

### 9.1 Verified in the current September blocks

- certificate pin policy and live chain test passed on isolated API 24 and API 36 emulators;
- relevant Android unit, lint, instrumentation compile and APK assembly gates passed for the pin
  fix;
- earlier full `1.0.50 / 78017` build matrices passed before the new audit fixes.

### 9.2 Current hardware availability

- Only Tab S4 (`SM-T835`) is currently visible to ADB.
- S7 and S10 are not currently connected.
- The connected device has not been modified by the September security blocks.

### 9.3 Final physical SecureCall matrix

Run all directions and relevant tier combinations after one immutable build is installed:

- registration, reconnect and network loss/recovery;
- outgoing/incoming call, accept, decline, caller/callee hang-up;
- loudspeaker on/off, mute/unmute and audio-route persistence;
- background, locked-screen, doze and cold-start incoming calls;
- Wi-Fi/mobile/external-VPN transitions and degraded/no-network recovery;
- FCM notification action handling and duplicate/stale notification cleanup;
- contact discovery, matched suggestions and keyboard/inset behavior;
- onboarding and confirmed phone-number persistence;
- Free ads without occluding navigation or call controls;
- settings for every exposed option, persistence after restart and tier enforcement;
- Play Free, Direct Pro and Direct Premium distribution-specific behavior;
- Premium local VPN consent/start/stop only in the direct Premium APK;
- update over prior signed version and entitlement restore;
- Bluetooth/headset and GSM interruption when suitable hardware/SIM conditions are available.

## 10. Personal Tester Entitlements

An older developer prepared uncommitted tester-entitlement code in the dirty canonical checkout.
No real code was generated, activated or sent. That work is not part of the reviewed PR #82 stack
and must not be committed wholesale from the old checkout.

Safe continuation requires:

- salvage only source changes that remain valid against the final entitlement architecture;
- independent security review of physical-device evidence, concurrency and restore behavior;
- synthetic tests for first bind, same-device restore, other-device reject, copied app-data/client
  ID replay, simultaneous first activation, persistence failure and server restart;
- keep recipients, raw codes and device bindings in an approved private store outside public Git;
- reconcile existing gift entitlements before generating any new ones;
- obtain an explicit redacted production plan before writing the production license store;
- two real-device validation before any email handoff.

This is a separate controlled tester-distribution task. It does not make the commercial release
ready and must not weaken customer entitlement rules.

## 11. CI and Dependency State

The earlier GitHub Actions allowance/rate-limit interruption is not active now: the September PR
heads have received real hosted runs. Do not rerun successful workflows unnecessarily.

Open dependency work:

- PR #89 fixes the signaling `qs` moderate advisories and is green but still needs normal review;
- Dependabot PRs #76, #77 and #78 remain open and need compatibility review; #78 currently has
  Android failures and must not be merged blind;
- dependency changes must be integrated before the final full backend/Android release run.

Audit-document PRs #83, #85 and #88 inherit old Android setup failures. Their documentation is not
proven wrong by that infrastructure failure, but the branches still require a clean rebase/retarget
and exact-head checks before merge.

## 12. Public Website, Wiki and Download State

The current repository candidate explains the Play/Direct VPN split, keeps IFR checkout disabled,
and has a locally green product-truth correction for current security, GhostNet and pricing
surfaces. That correction is not authoritative until it is published, reviewed, integrated and
verified on the live public surfaces.

Remaining publication work:

- integrate and deploy STX-29 before claiming analytics-free live pages;
- publish corrected `docs/WIKI` content to the actual GitHub Wiki, not only the website copy;
- integrate the reviewed product-truth correction and finish the remaining sitemap, download,
  version-source and structured-data audit items;
- replace obsolete WalletConnect media;
- generate final download assets and checksums from the final signed build;
- update GitHub Release and every `latest/download` link atomically;
- verify all public links, fragments, responsive layouts, HTTP status, canonical metadata and
  actual downloaded package/hash after deployment.

The website must describe the IFR discount as a browser purchase step and must never imply an
in-app wallet connection or IFR tier unlock.

## 13. Backend and Production Runtime Gates

Before production deployment:

- merge the reviewed source sequence and create one exact release commit;
- complete STX-01/STX-21 identity/key-binding design and tests;
- verify effective coturn configuration and rotate only if evidence requires it;
- deploy signaling through the controlled runbook with health, registration and rollback checks;
- confirm `/status/live` no longer leaks IPs;
- verify rate limits with the real trusted-proxy topology;
- verify certificate pin compatibility from the final signed app;
- run call and entitlement matrices against the deployed candidate;
- keep secrets out of logs, bridges, artifacts and screenshots.

No production deployment, restart, provider mutation, key provisioning, database migration or
sales activation is part of this status document.

## 14. Recommended Integration Order

1. Obtain normal review and integrate PR #82.
2. Rebase/retarget and integrate PR #89 dependency repair.
3. Retarget PRs #90, #91, #92 and #93 to the verified base, resolve overlaps, and rerun exact-head
   CI for each accepted combined revision.
4. Finish and review the STX-22/STX-37/STX-38 product-truth branch.
5. Complete the coupled STX-01/STX-21 design, migration plan, implementation and adversarial tests.
6. Perform the conditional STX-03 host verification and remediation if confirmed.
7. Work through remaining audit waves in dependency order, including current-store/documentation
   truth and removal/quarantine of dead unsafe code.
8. Implement and test the phone UI/settings fixes from PR #85.
9. Complete VLABS isolated signer/public-verifier pairing and canonical IFR/Stripe/fiscal E2E.
10. Freeze a new immutable release and choose a Console-confirmed unused version code.
11. Build and inspect newly signed Free AAB plus Free/Pro/Premium APKs.
12. Run emulator and three-device physical matrices.
13. Grant `PRODUCT_READY` only from exact evidence; receive matching VLABS `FINANCE_READY`.
14. Under a separate activation authorization, upload/publish/deploy the exact approved artifacts
    and perform live post-release checks.

## 15. Ownership and External Dependencies

| Owner | Work |
|---|---|
| Codex Sol | scope, architecture, security decisions, integration, full tests, Bridge, release judgment and external actions |
| Kimi K3 | bounded larger implementation and deep review blocks; no independent deploy/publish/payment action |
| Claude Code | small mechanical edits or focused checks only |
| VLABS Finance Operator | canonical checkout, signer pairing, Stripe/Elorus/fiscal lifecycle and `FINANCE_READY` |
| Gio | independent PR approval path, physical-device availability, exact production/release approvals and required business/accounting inputs |
| Google/AdMob | policy review, Play track state, developer verification and consent-console configuration |

## 16. Definition of Complete

SecureCall is complete and sellable only when all of the following are simultaneously true for one
immutable source and product tuple:

- no unresolved launch-blocking security finding;
- normal review and exact-head CI green after final integration;
- complete backend, Android, native, web and policy tests green;
- new signed artifacts built, inspected and checksummed;
- physical device matrix green on S10, S7 and Tab S4, with limitations explicitly recorded;
- Play Free/Direct Pro/Direct Premium distribution split verified in final archives;
- Play Console policy, API level, billing, listing and track state accepted;
- website/wiki/store/GitHub claims and downloads match final artifacts;
- browser-only IFR flow uses the accepted durable VLABS checkout and exact offer contract;
- purchase, restore, refund, revoke, dispute and fiscal evidence passes in approved environments;
- exact-version `PRODUCT_READY=YES` and `FINANCE_READY=YES` are both recorded;
- a separate release/activation approval names the same immutable version;
- post-deploy live verification passes and rollback remains available.

Until then, the correct public and operational state is:

`PRODUCT_READY=NO / FINANCE_READY=NO / CHECKOUT CLOSED / SALES CLOSED`.
