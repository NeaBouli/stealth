# SecureCall Audit Intake and Remediation Backlog

Date: 2026-09-19

Status: CATALOG ONLY - NO REMEDIATION APPLIED

## Purpose

This document turns the September 2026 audit material into bounded work packages without
changing application, backend, workflow, deployment, or production behavior. It is the
planning gate for later remediation. GitHub issue #84 remains the canonical finding register.

The current source baseline is `e06d018417bae5be16bf6b89d0a1887586a99d3b`.

## Inputs and provenance

| Input | Exact revision | Intake result |
| --- | --- | --- |
| Consolidated SecureCall audit PDF, 2026-09-15 | SHA-256 `7dc9d7521d0103bbf7952848acd687bb13a392b3a5084b1e7df8a0b6b4219537` | 30 pages read and sampled visually; source for STX-01 through STX-62 |
| GitHub PR #83 | `2672781ca5d920e692ac3040f55cc85873f0a1ae` | Report-only; open, mergeable, blocked by review and Android CI |
| GitHub issue #84 | Open | Canonical 62-finding register: 0 critical, 8 high, 28 medium, 20 low, 6 informational |
| GitHub PR #85 | `d609e97874021dd8b3be1fb756b5d4d16a980ab3` | Report-only; open, mergeable, blocked; requires corrections before merge |
| Owner device reports, 2026-09-19 | S10 | Keyboard overlap, clipped `+` label, and non-collapsible Premium VPN settings |

## Evidence labels

- `SOURCE-CONFIRMED`: present on the exact current source baseline.
- `LIVE-CONFIRMED`: rechecked against a public endpoint on 2026-09-19.
- `DEVICE-MEASURED`: measured in PR #85 against the named 1.0.48 artifact.
- `DEVICE-REPORTED`: reported by the owner and source-consistent, but not reproduced in this intake.
- `CONDITIONAL`: requires host, runtime, or deployment verification.
- `REVALIDATE`: report evidence or proposed cause is incomplete, stale, or ambiguous.

## CI and quota diagnosis

Tracking issue: [#87](https://github.com/NeaBouli/stealth/issues/87).

The failed `Android Client` checks on PR #83 and PR #85 are not GitHub Actions
rate-limit failures. The runners start normally and fail before repository Gradle code runs.
The pinned `android-actions/setup-android@v4.0.1` action defaults its `packages` input to
`tools platform-tools`; the obsolete SDK package `tools` is no longer available to
`sdkmanager`. Both Android workflows invoke the action without overriding that default.

Read-only API checks showed normal API capacity at intake time. The exact paid Actions-minute
balance was not available to the current token, but it is not needed to diagnose these runs:
the jobs were allocated runners and executed until the SDK package error.

Later fix candidate, not applied here:

1. Set `packages: platform-tools` on both setup action invocations.
2. Keep the explicit API 36, Build Tools 36, CMake, and NDK installation step.
3. Run Basic CI and Android Instrumentation on the exact candidate head.
4. Require the Android jobs to reach Gradle before considering the infrastructure gate closed.

## PR disposition

### PR #83

Do not merge yet. The report set is useful and most sampled high-severity claims match the
current source, but the branch has no approving review and its Android check is red for the
infrastructure reason above. Before merge:

1. Repair or override the Android SDK bootstrap and rerun CI.
2. Recount STX-29 against the exact website tree and state the counting method.
3. Keep STX-03 explicitly conditional until the running coturn configuration is inspected.
4. Mark every live observation with its observation date.
5. Obtain an independent review of finding accuracy and disclosure safety.

### PR #85

Do not merge unchanged. It correctly separates a shipped Premium 1.0.48 APK from source
baseline `e06d018`, but some conclusions cross that boundary:

- A3's device measurement may be valid, but its stated source cause is not. Current source
  already contains `android:minHeight=64dp` in `Widget.SecureCall.DialButton`, introduced
  before the audited baseline. The real issue is likely GridLayout measurement, clipping, or
  artifact/source drift. The proposed `minHeight=48dp` patch would also reduce the existing
  source floor.
- A1's public TLS chain measurement was reproduced on 2026-09-19: the server presents the
  YR2 / Root YR hierarchy. The remediation still needs an Android/OkHttp chain-cleaning test;
  a trust anchor may be added to the cleaned chain even when it is not server-sent. Do not
  copy the proposed pins into production without that verification and a rotation design.
- A2, A4, and A5 remain useful device findings, but must be retested on a build produced from
  the exact fix revision, including Free, Pro, and Premium.
- A6 remains informational and should not be mixed with the small-screen correctness patch.

Required PR #85 correction: distinguish `artifact observation`, `current source fact`, and
`hypothesis` in every finding, then rerun CI and independent review.

## Finding coverage map

Every STX identifier is assigned exactly once below. Detailed descriptions stay in issue #84
and PR #83; this document defines execution boundaries and dependencies.

| Work package | Findings | Gate and intended result |
| --- | --- | --- |
| `SEC-IDENTITY-01` | STX-01, STX-02, STX-03, STX-04, STX-05, STX-06, STX-07, STX-08 | Authenticated identity lifecycle, bounded registration and lookup behavior, migration and abuse tests |
| `SEC-RUNTIME-02` | STX-09, STX-10, STX-11, STX-12, STX-13, STX-14, STX-15, STX-16, STX-17, STX-18, STX-19, STX-20 | Fail-closed runtime data, secret-safe tooling, TURN policy, admin/origin/rate-limit hardening, truthful enforcement behavior |
| `CRYPTO-PROTOCOL-03` | STX-21, STX-22, STX-23, STX-24, STX-25, STX-26, STX-27, STX-28 | Authenticated transcript-bound protocol, accurate claims, rotation-safe pinning, replay/AAD/direction design, key-lifecycle and FFI hardening |
| `WEB-PRIVACY-04` | STX-29, STX-30, STX-31, STX-32, STX-33, STX-34, STX-35, STX-36 | Consent/privacy-safe web surfaces, private reporting route, hardened invite/return/payment surfaces, disclosed pricing behavior |
| `CONTENT-DISTRIBUTION-05` | STX-37, STX-38, STX-39, STX-40, STX-41, STX-42, STX-43, STX-44, STX-45, STX-46, STX-47, STX-48, STX-49, STX-50, STX-51, STX-52, STX-53, STX-54, STX-55 | One canonical product/pricing/release/security truth propagated to website, wiki, store metadata, docs, and release assets |
| `DISCOVERY-METADATA-06` | STX-56, STX-57, STX-58, STX-59, STX-60, STX-61, STX-62 | Correct machine-readable product facts, structured data, sitemap/robots, headers, redirects, and language metadata |

## Added device and UI tickets

Tracking issue: [#86](https://github.com/NeaBouli/stealth/issues/86).

| Ticket | Evidence | Scope | Acceptance criteria |
| --- | --- | --- | --- |
| `SC-UX-01` | A2 `DEVICE-MEASURED` | Bottom navigation labels and touch height on narrow phones | Four labels remain visible and readable; all targets are at least 48dp at 320dp width, both themes, EN/DE |
| `SC-UX-02` | A3 `DEVICE-MEASURED`, cause `REVALIDATE` | Dial-pad sizing and layout measurement | Twelve keys measure at least 48x48dp on exact-source builds; no reduction of the existing 64dp source floor without evidence |
| `SC-UX-03` | A4 `DEVICE-MEASURED` | Settings row touch targets | Every selectable row measures at least 48dp at default and large font scales |
| `SC-UX-04` | A5 `SOURCE-CONFIRMED` | Small-screen, tablet, and landscape resource strategy | Explicit responsive constraints exist; 320dp phone, 360dp phone, sw600dp tablet, and landscape smoke tests pass |
| `SC-REL-05` | A6 `REVALIDATE` | Release logging and direct-APK ABI policy | Release logging policy is verified; direct APK size/ABI decision is documented and tested separately |
| `SC-UX-06` | Owner report, `DEVICE-REPORTED`; `adjustPan` is `SOURCE-CONFIRMED` | Keyboard obscures dialer contact matches | With keyboard open on S10-class viewport, complete match rows stay visible and tappable; backspace/dial controls do not overlap |
| `SC-UX-07` | Owner report, `DEVICE-REPORTED`; two-line weighted label is `SOURCE-CONFIRMED` | `+` under key 0 is clipped or invisible | `0` and `+` remain legible at default and large font scales; long-press behavior is tested if retained |
| `SC-UX-08` | Owner report and `SOURCE-CONFIRMED` | Premium standalone VPN category does not collapse | Direct-Premium VPN section uses the same accessible expand/collapse contract as peer sections and preserves child visibility state |

## Priority and dependency order

### Gate 0 - make evidence trustworthy

1. `CI-INFRA-01`: remove the obsolete SDK package request and prove CI reaches Gradle.
2. Correct PR #85's A3 source statement and A1 validation boundary.
3. Complete the PR #83 recount/conditional labels and independent review.
4. Merge report-only PRs only after the corrected exact heads are green and approved.

### Wave 1 - immediate exposure and availability

1. STX-02: remove raw connection identifiers from the public status surface.
2. STX-01 and STX-21: design and migrate registration plus key exchange together. Fixing
   only one does not establish authenticated calls.
3. STX-23/A1: produce a rotation-safe certificate trust design, then test the actual cleaned
   chain on API 24 and API 36 before release.
4. STX-03: inspect the running coturn configuration; remediate only from verified runtime
   evidence.

### Wave 2 - truthful security and privacy surface

1. STX-22: remove unsupported Double Ratchet claims until an implementation is verified.
2. STX-29: remove analytics or implement valid consent and query redaction; align privacy copy.
3. STX-37: synchronize the live GitHub wiki with the canonical current product model.
4. STX-38: bind every displayed price to one approved catalog/version contract.

### Wave 3 - remaining security and protocol packages

Execute the rest of `SEC-IDENTITY-01`, `SEC-RUNTIME-02`, and `CRYPTO-PROTOCOL-03` in
small dependency-aware changes. Auth, crypto, secrets, deployment, and migration work each
require a separately bounded authorization and rollback plan.

### Wave 4 - Android small-screen and settings quality

Implement `SC-UX-01` through `SC-UX-08` as one UI program with separate commits for layout,
settings, and automated viewport checks. Validate Free on a phone-size emulator, Pro on a
tablet-size emulator, and Premium on both before physical-device acceptance.

### Wave 5 - public content and discovery cleanup

Complete `WEB-PRIVACY-04`, `CONTENT-DISTRIBUTION-05`, and `DISCOVERY-METADATA-06` from a
single canonical facts file or generated source where practical. Run link, fragment, schema,
sitemap, privacy-copy, and release-version drift checks before publication.

## Required test matrix for later implementation

- Signaling unit/integration tests and abuse/concurrency tests for every identity change.
- Rust tests, strict Clippy, known-answer tests, protocol migration tests, and independent
  crypto review for protocol changes.
- Android unit, lint, Free/Pro/Premium compile and release policy guards.
- Emulator viewports: 320x640, 360x800, and sw600dp; portrait and landscape; default and
  enlarged font; keyboard shown and hidden; EN and DE.
- Physical acceptance at the end on S7, S10, and Tab S4 using artifacts built from one exact
  revision. Artifact version, package, signature, and SHA-256 must be recorded.
- Website syntax, local-link, fragment, structured-data, sitemap, consent, responsive, and
  live post-deploy checks.
- No production deployment, secret change, payment activation, or store publication is part
  of this intake.

## Agent split and token discipline

Work proceeds in bounded blocks, not as one open-ended autonomous goal:

1. One work package or at most three tightly coupled findings per block.
2. Each block has an explicit definition of done, tests, bridge update, and target stop.
3. Sol owns scope, architecture, security decisions, integration, external writes, and final
   verification.
4. Kimi K3 is reserved for a non-overlapping independent review after 17:45 EEST and, after
   owner approval to start fixes, one clearly bounded large implementation slice at a time.
5. Kimi receives no secrets and performs no production, deployment, IAM, payment, or external
   write action. Sol reviews every resulting diff and reruns the complete relevant tests.
6. Small mechanical work may be delegated separately only when its handoff costs less than
   doing it directly; no two agents work on the same files or acceptance criteria in parallel.

## Current stop state

- Audit material: ingested and normalized.
- Source remediation: not started by instruction.
- PR #83: hold for CI, corrections, and review.
- PR #85: hold for report correction, CI, and review.
- Issue #84: remains the canonical 62-finding register.
- Next permitted action before implementation: publish this catalog, link the bounded tickets,
  and obtain the independent catalog review when Kimi becomes available.
