# SecureCall Completion Programme

Task: SC-COMPLETION-PLAN-001, corrected under SC-COMPLETION-PLAN-002 · Author: Claude Code
(Worker A) · Date: 2026-09-24
Mode: planning/documentation only. No application code, no external write, no activation.
Gate state preserved by this document: `PRODUCT_READY=NO`, `FINANCE_READY=NO`.

Scope: SecureCall only. SecureChat and Chameleon are separate later workstreams; they appear
here only where a shared dependency blocks SecureCall (M0 repository serialization, M9
artifact/keystore hygiene).

---

## 1. Verified baseline (evidence, not assumption)

| Fact | Evidence |
| --- | --- |
| Remote `main` head is `e06d018417bae5be16bf6b89d0a1887586a99d3b` (`fix: restore SecureCall signaling startup (#81)`), preceded by #80/#79 | `git rev-parse origin/main` |
| `7ce4f01` (`docs: triage Brevo SMTP inactivity warning`) is an **unmerged task-branch commit**, not `main`; any plan step must resolve `main` from `origin/main`, never from a local worktree head | `git log --oneline origin/main` |
| PR #102 integrates the reviewed stack #82→#90→#94→#95→#96→#97→#98→#99→#100→#101 as 41 linear commits, 224 files, +15472/-2650 | `gh pr view 102` |
| PR #102 exact-head CI is fully green (Basic CI lint/signaling/Rust/Android, Instrumentation API 24 + API 36, Dependency Review, Secret Detection, Dependency Audit, Security Summary, CodeRabbit) at 2026-09-20 | `gh pr view 102 --json statusCheckRollup` |
| PR #102 is `MERGEABLE`, `OPEN`, not draft, but `reviewDecision=REVIEW_REQUIRED` with **zero** reviews | `gh pr view 102 --json reviews,reviewDecision` |
| Tree-equality proof against the reviewed stacked target `5f6398dd…` is claimed in the PR body (tree `0578362c…`, empty diff) — asserted, not re-verified in this task | PR #102 body |
| Audit register issue #84 is OPEN with 62 findings: 0 Critical / 8 High / 28 Medium / 20 Low / 6 Info; **no checkbox is ticked** | `gh issue view 84` |
| The audit reports themselves (`docs/community-audits/`) live in unmerged PR #83 and are absent from `main` | `ls docs/community-audits` → not found |
| PR #102's tree contains **neither** `docs/community-audits/*` (PR #83) **nor** `docs/audits/SECURECALL_DEVICE_UI_AUDIT_2026-09-18.md` (PR #85). #83 and #85 are **independent evidence PRs**, not superseded by #102, and must be integrated separately | PR #102 tree inspection |
| Tester Premium entitlement work is complete and verified inside PR #95 (device-bound P-256 key, server-side hash, atomic single-grant binding, fail-closed renewal; 25/25 synthetic, mypy clean, S10 API31 + Tab S4 API29 instrumentation 1/1) but unmerged | `gh pr view 95` |
| CI blocker issue #87: `android-actions/setup-android@v4.0.1` defaults `packages: tools`, which no longer exists → `Android Client` job fails on audit PRs #83/#85 | `gh issue view 87` |
| Device/UI defect issue #86 (narrow-phone dialer, bottom nav, clipped `+`, keyboard-obscured contact rows, Premium VPN settings expand) is open and explicitly separated from #84 | `gh issue view 86` |
| Brevo: delivery and keepalive both use `BREVO_API_KEY` over the HTTP API (`/v3/smtp/email`, `/v3/account`); **no active SMTP transport exists** in the repository | `.fleet/reports/SC-BREVO-001.md`, `verdict: ok` |
| Brevo credentials `Master Password` and `securecall-production` have no proven external owner; provider-side last-use metadata is unverified | `.fleet/PLAN.md` (milestone PARTIAL) |
| Finance is out of this repository: private VLABS operator owns it; `PRODUCT_READY` (repo) and `FINANCE_READY` (VLABS) are both required and both NO | BRIDGE.md 2026-09-04, 2026-09-06; `docs/GOOGLE_PLAY_BILLING_SETUP.md`; `docs/WIKI/FAQ.md:97` |
| Distribution split is non-negotiable: Play = `freeRelease`/`com.securecall.app.free`/`app-free-release.aab`, VPN-free; direct Premium = `premiumRelease -Pinternal`; direct Pro = `proRelease -Pinternal`, VPN-free | `AGENTS.md`, `docs/DISTRIBUTION_MATRIX.md` |
| 24 PRs are open: 10 stacked implementation PRs replayed into #102 (#82/#90/#94/#95/#96/#97/#98/#99/#100/#101), 2 independent evidence PRs (#83/#85), 3 Dependabot (#76/#77/#78), plus deferred docs PRs | `gh pr list` |

### Unresolved contradictions (must be closed, not narrated away)

1. **Runtime health vs. merge readiness.** BRIDGE 2026-09-06 records that the SecureCall Test
   runtime returns no application response; BRIDGE 2026-09-07 records a local startup fix that was
   never confirmed deployed. Green CI does not prove a live runtime. → M2.
2. **Reviewed vs. approved.** PR #102 claims Kimi K3 reviewed the application-code parent and Sol
   verified the stack, yet GitHub records zero reviews and requires one. Fleet review and GitHub
   approval are two distinct requirements: Kimi can supply the first, but branch protection needs
   an approving review from a trusted GitHub account other than `NeaBouli`, and no such
   collaborator exists today. → M1.
3. **Audit "fixed" vs. register state.** #96/#97/#98/#99/#100/#101 and #90/#93/#94 implement
   remediations for STX-01/02/21/22/23/29/37/38 and others, but every box in #84 is unticked, and
   #83 (the evidence base) is unmerged. The true remaining-finding count is unknown. → M3.
4. **Play listing status.** STX-42 says the Play listing is verified LIVE while homepage and
   `llms.txt` say "in review"; the repo simultaneously asserts sales are closed. Live listing +
   closed sales is a legitimate state but must be stated once, consistently. → M8.
5. **Pricing.** STX-38: app UI €49 vs. website €25 vs. BRIDGE 2026-07-12 "consistent EUR 1/2/5"
   for Custom-ID and "25 EUR activation-code catalog price". Three price surfaces, no single
   source of truth. PR #94 claims a fix; unverified on `main`. → M3/M7.
6. **Version head.** STX-40 reports four changelogs with four heads; BRIDGE records 1.0.45, then
   1.0.48 (78016). The shipping version identifier is ambiguous. → M9.
7. **Brevo necessity.** The repository needs `BREVO_API_KEY` (HTTP) and provably not SMTP, yet two
   SMTP credentials are warned as inactive with no identified owner. Repo evidence cannot close
   this; only provider metadata can. → M6.
8. **Certificate pinning.** STX-23: pin set expired 2026-08-14. PR #92 is superseded by #96 inside
   #102; whether a non-expired policy actually ships is unverified. → M3, hard release blocker.

---

## 2. Milestones

Ordering is strict for M0–M3; M4–M6 may run in parallel with each other once M2 is green.
Every milestone lists module/hop so the diff boundary is fixed before the first changed line.

### M0 — Repository serialization and single-writer lock
- **Module/hop:** fleet coordination → `.fleet/` → GitHub PR queue.
- **Input:** the 24 open PRs; PR #102 as the single integration vehicle for the *implementation*
  stack only.
- **Owner:** Codex (sole merge authority).
- **Files / surface:** `.fleet/PLAN.md`, GitHub PR metadata only. No repository code.
- **Acceptance (two phases — annotate now, close later):**
  1. *Now:* the 10 stacked implementation PRs #82/#90/#94/#95/#96/#97/#98/#99/#100/#101 each carry a
     traceability comment naming #102. **They stay open.** Nothing is closed at this point.
  2. *Only after #102 has merged and its `main` tree and CI are verified (M1 acceptance 6 and 7
     passed):* those same 10 PRs are closed, each closing comment citing the verified merge SHA.
- **Never closed under M0:** evidence PRs **#83** (`docs/community-audits/*`) and **#85**
  (`docs/audits/SECURECALL_DEVICE_UI_AUDIT_2026-09-18.md`) — their content is absent from #102 and
  needs its own integration (see M3/M5) — and the Dependabot PRs #76/#77/#78, which carry
  dependency changes #102 does not contain.
- **Depends on:** nothing for phase 1; M1 for phase 2.
- **Rollback:** reopen any PR closed in error; no code is touched.
- **Stop:** if any stacked implementation PR contains a commit absent from #102's 41-commit replay —
  then the replay is incomplete and M1 must not proceed. Closing an evidence or Dependabot PR as
  "superseded" is itself a stop condition: it destroys content that never landed.

### M1 — PR #102 integration
- **Module/hop:** whole repository → `main`.
- **Input:** PR #102 head `5f70b16`; reviewed target `5f6398dd…`; tree `0578362c…`.
- **Owner:** Codex merges. **Fleet review: Kimi** (large-context, whole-stack) — Kimi can and must
  deliver the substantive security/stack review as `.fleet/reports/KIMI-102-REVIEW.md`.
- **Branch protection is a separate, unmet requirement.** A fleet report does **not** satisfy it.
  M1 additionally needs an approving GitHub review from a trusted GitHub account **other than
  `NeaBouli`**. No such collaborator exists today, so M1 is blocked on a human/account decision by
  Gio (add a trusted reviewer account) — not on Kimi's throughput. This blocker must be resolved
  openly; it is never to be worked around.
- **Files / surface:** 224 files; `main` branch protection.
- **Acceptance (all required, in order):**
  1. `git diff 5f6398dd699e589e750f3a12def2dd2ed98d8c56 5f70b16` → empty output;
  2. `git rev-list --count --merges main..5f70b16` → `0`; `git rev-list --count main..5f70b16` → `41`;
  3. Kimi's fleet review is delivered and its verdict is not "requesting changes";
  4. `gh pr view 102 --json reviewDecision` → `APPROVED`, from a trusted GitHub account that is
     **not** `NeaBouli` and not the PR author — obtained without relaxing branch protection;
  5. exact-head CI re-run green after any rebase;
  6. post-merge: `git rev-parse main^{tree}` equals `0578362ce3c5b49ef0672eb116a03cd6471ee40d`;
  7. post-merge full local chain: `cd backend/signaling && npm ci && npm test`,
     `cd core_crypto && cargo test && cargo clippy -- -D warnings`,
     `cd client_android && ./gradlew --no-daemon testFreeDebugUnitTest lint`.
- **Depends on:** M0.
- **Rollback:** `main` is linear; revert the merge commit range and restore the pre-merge tag. Tag
  `pre-102-<sha>` **before** merging.
- **Stop:** any non-empty tree diff, any admin bypass, any protection change, any self-approval, or
  any attempt to record a fleet report as if it were the GitHub approval.

### M2 — Runtime truth for signaling
- **Module/hop:** signaling → `backend/signaling/server.js` startup → `/health` → deployed host.
- **Input:** BRIDGE 2026-09-06 (no application response) and 2026-09-07 (local startup fix, three
  consecutive process-level passes). Post-M1 `main`.
- **Owner:** Codex (only agent with deployment authority). Claude may prepare read-only diagnosis.
- **Files / surface:** no code change expected; deployment host configuration; `docs/RAILWAY_*`
  vs. Hetzner drift (STX-44).
- **Acceptance:** `npm test` green on `main` including the process-level startup test; then a
  recorded live `/health` 200 with uptime from the intended host; then `/status/live` proven to
  contain no client IP (STX-02 regression check).
- **Depends on:** M1.
- **Rollback:** redeploy the previous known-good revision; the startup fix is import-only.
- **Stop:** if the live host cannot be identified unambiguously (Railway vs. Hetzner), stop and
  resolve hosting ownership first — do not deploy to a guessed target.

### M3 — Audit remediation closure (#84)
- **Module/hop:** per finding; the register is the map. Signaling (`backend/signaling/ws/handlers/`,
  `server.js`, `middleware/`), crypto (`core_crypto/src/`), Android
  (`client_android/app/src/`), deploy (`deploy/`), website (`website/`).
- **Input:** issue #84's 62 findings; the merged implementations from #102; PR #83 reports.
- **Owner:** Codex triages and ticks; **Kimi** performs the large-context sweep (see §5); Claude
  fixes bounded residuals; Grok takes single-file Low/Info items.
- **Files / surface:** `docs/community-audits/*` is **not** in #102 and must land on `main` through
  a separate integration of PR #83 so the register links resolve. Likewise
  `docs/audits/SECURECALL_DEVICE_UI_AUDIT_2026-09-18.md` from PR #85 (feeds M5). Both are evidence,
  not duplicates; neither may be closed as superseded.
- **Acceptance:** each of the 62 boxes is ticked with either a commit SHA or an explicit `wontfix`
  reason. Hard gates that must be *fixed*, never `wontfix`:
  STX-01 (unauthenticated REGISTER), STX-02 (IP exposure), STX-03 (coturn literal secret),
  STX-21 (unauthenticated key exchange, `HKDF salt=None`), STX-22 (Double-Ratchet claim),
  STX-23 (pin set expired 2026-08-14), STX-29 (consentless GA4), STX-37 (wiki sells removed
  GhostNet), STX-38 (pricing contradiction).
  Evidence commands: `cd backend/signaling && npm test`; `cd core_crypto && cargo test && cargo clippy -- -D warnings`;
  `grep -rn "expiration" client_android/app/src/main/res/xml/network_security_config.xml`;
  `grep -rn "gtag\|googletagmanager" website/` → no unconsented hit.
- **Depends on:** M1 (M2 in parallel).
- **Rollback:** per-finding revert; findings are independent commits.
- **Stop:** any High finding that cannot be fixed without a new architecture decision → escalate to
  Gio, do not ship a partial mitigation as "fixed".

### M4 — Tester Premium entitlements
- **Module/hop:** entitlement → tester-license grant → device-bound key possession
  (`backend/signaling` payment/entitlement path + Premium Android first-use flow).
- **Input:** PR #95, already merged via #102. Nothing is to be rebuilt.
- **Owner:** Codex (registry is private and operator-owned).
- **Files / surface:** the private tester registry and the exporter's
  `draft_do_not_send_or_activate` output — **outside** this repository.
- **Acceptance:** post-M1 re-run of the 25/25 synthetic tester tests and the signaling suite on
  `main`; then exactly one real device-bound grant is issued to one tester and verified as
  non-transferable (a second device with the same code is refused); revocation fails renewal
  closed before challenge issuance.
- **Depends on:** M1, M2 (grant needs a live signaling host).
- **Rollback:** revoke the grant; the binding is atomic and single-active by construction.
- **Stop:** do not mass-issue, do not email codes, do not leave draft state, until M6 disposes of
  the email path and the operator authorizes delivery.

### M5 — Emulator and device acceptance
- **Module/hop:** CI → `.github/workflows/ci-basic.yml` + `android-instrumentation.yml` → Android
  client UI (`client_android/app/src/main/res/`, `MainActivity`).
- **Input:** issue #87 (setup-android `packages: tools`), issue #86 (UI defects), PR #85 device
  measurements.
- **Owner:** **Grok** for #87 (two-line, single-scope workflow input override); **Claude** for #86
  (measure from source first — the 48dp assumption in #86 is explicitly flagged as the wrong
  diagnosis).
- **Files / surface:** the two workflow files; bottom navigation, dial pad, contact-match rows,
  settings rows, Premium VPN settings section.
- **Acceptance:** `Android Client`, `Instrumented Tests API 24`, `Instrumented Tests API 36` green
  on `main` head; then physical re-test on S10 (narrow 320dp), S7 and Tab S4 with the clipped `+`,
  keyboard-obscured contact rows and the Premium VPN expand/collapse recorded as PASS in BRIDGE.
- **Depends on:** M1. #87 may start immediately and independently.
- **Rollback:** workflow input revert; UI changes are resource-only.
- **Stop:** S7 call-matrix stays blocked pending validated S7 internet — record as blocked, do not
  substitute an emulator result for a device result.

### M6 — Brevo disposition
- **Module/hop:** email → Stripe/VLABS fulfilment → `sendActivationCode` → Brevo HTTP
  `/v3/smtp/email` (`BREVO_API_KEY`); separate CI hop `tools/brevo_keepalive.sh` → `/v3/account`.
- **Input:** `.fleet/reports/SC-BREVO-001.md` (repo side closed), provider metadata (open).
- **Owner:** Codex only — provider console access is operator-level.
- **Files / surface:** Brevo console, read-only: Settings → SMTP & API → SMTP. Redacted status and
  last-used metadata only.
- **Acceptance:** one written disposition per credential — retain-and-validate / replace via a
  separately approved change / allow expiry — each citing provider last-use plus the repository
  proof that no SMTP transport exists. `bash -n tools/brevo_keepalive.sh` and the scheduled
  keepalive run stay green throughout.
- **Depends on:** nothing. Blocks M4 delivery and M10.
- **Rollback:** none needed — allowing expiry is reversible by creating a new credential; deletion
  is not performed.
- **Stop:** if any external system is found to own `securecall-production`, stop and hand back to
  Gio. No rotation, no deletion, no test send, no credential readout, ever.

### M7 — VLABS / IFR / Stripe finance gates
- **Module/hop:** commerce → public repo boundary → private VLABS operator.
- **Input:** BRIDGE 2026-09-04 / 2026-09-06; `docs/GOOGLE_PLAY_BILLING_SETUP.md`;
  `docs/WIKI/FAQ.md:97`; the closed browser-only IFR checkout gate (`website/js/ifr-checkout.js`).
- **Owner:** Gio + private VLABS operator. Codex records only the repository-side `PRODUCT_READY`
  evidence. **No fleet agent may set `FINANCE_READY`.**
- **Files / surface:** `NeaBouli/vlabs` → `docs/finance-integrations/projects/securecall.md`
  (private, not this repo). This repository carries a pointer only.
- **Acceptance:** repo side — M1–M6 and M8–M9 green, one canonical price on every surface (closes
  STX-38/STX-34), Custom-ID and Play refund/revocation paths re-tested post-merge
  (`npm test` payment + RTDN suites). VLABS side — a project-specific payment, delivery and
  reversal test recorded privately. Both recorded before either flag changes.
- **Depends on:** M1–M6, M8, M9.
- **Rollback:** both flags return to `NO` and Checkout closes; the IFR gate is default-off.
- **Stop:** no tax/provider/customer/invoice/recipient value is ever written to this repository.
  `node --test website/js/ifr-checkout.test.cjs` must keep proving the closed-checkout gate.

### M8 — Public content and claim truth
- **Module/hop:** website/wiki → `website/`, `docs/WIKI/`, `fastlane/metadata/`, `store_assets/`,
  `llms.txt`, `robots.txt`, `sitemap.xml`.
- **Input:** STX-22/29/30/37/38/39/40/41/42/43/44/45/46/47/48/49/50/51/52/53/56/57/58/59/60/61/62.
- **Owner:** **Claude** (single writer for `website/` and `docs/WIKI/` — see §3).
- **Acceptance:** one statement of Play status across homepage, `llms.txt` and wiki; one price; no
  "audited"/"Double Ratchet"/GhostNet claim unsupported by shipped code; `SECURITY.md` names a
  private channel; no unconsented analytics (`grep -rn "gtag\|googletagmanager" website/`);
  `yamllint .` green; `node --check website/js/ifr-checkout.js` green.
- **Depends on:** M3 (the code truth must exist before copy may claim it).
- **Rollback:** content-only revert.
- **Stop:** never describe a feature as shipped before the corresponding M3 finding is ticked.

### M9 — Artifacts and version identity
- **Module/hop:** build → `client_android/app/build.gradle` → `freeRelease` / `proRelease` /
  `premiumRelease` → signed AAB/APK.
- **Input:** STX-40/48 version drift; the 1.0.45 → 1.0.48 (78016) history; `AGENTS.md` split rules.
- **Owner:** Codex (holds signing). Claude may prepare the version bump diff.
- **Acceptance:** one versionCode/versionName across `build.gradle`, all four changelogs and the
  wiki; `./gradlew --no-daemon -Pinternal assembleRelease bundleRelease` BUILD SUCCESSFUL with
  `verifyNoAppIfrWalletCode`; the Free/Pro VPN policy guards and the Premium runtime guard PASS;
  `aapt dump badging` confirms each package id; SHA-256 recorded per artifact in BRIDGE.
- **Depends on:** M3, M5, M8.
- **Rollback:** rebuild from the previous tag; artifacts are reproducible from source.
- **Stop:** any WireGuard/VPN symbol found in the `freeRelease` or `proRelease` artifact aborts the
  release outright (`AGENTS.md` §Non-Negotiable).

### M10 — Distribution: Play and direct
- **Module/hop:** release → Play Console (`app-free-release.aab` only) and direct APK hosting.
- **Input:** M9 artifacts; `docs/DISTRIBUTION_MATRIX.md`; `docs/PLAY_STORE_UPLOAD_CHECKLIST.md`.
- **Owner:** Gio (Play account) with Codex preparing; no agent uploads autonomously.
- **Acceptance:** Play receives only `app-free-release.aab`
  (`com.securecall.app.free`); direct Premium (`com.securecall.app.premium`) and direct Pro
  (`com.securecall.app.pro`) are published only outside Play; website download copy states the
  distinction; the Play listing status matches M8 copy exactly.
- **Depends on:** M9, M8.
- **Rollback:** halt Play rollout; withdraw the direct APK link.
- **Stop:** uploading a Pro/Premium artifact to the Play listing, or implying that a Play
  entitlement converts the Play package into the Premium APK, is a hard abort.

### M11 — `PRODUCT_READY=YES` then sales activation
- **Module/hop:** gate → repository `PRODUCT_READY` record → VLABS `FINANCE_READY` → Checkout.
- **Input:** all prior milestone evidence.
- **Owner:** Gio decides. Codex records. No worker may activate.
- **Acceptance:** M1–M10 each closed with named evidence; issue #84 fully ticked; a live signed
  synthetic test recorded by the private operator; both flags flipped in that order, in separate
  recorded steps, never in one action.
- **Depends on:** everything.
- **Rollback:** close Checkout, reset both flags to `NO`, revoke issued entitlements.
- **Stop:** **until M11 completes, this document and every artifact keep `PRODUCT_READY=NO` and
  `FINANCE_READY=NO`.**

---

## 3. One-writer file ownership

Exactly one agent may modify each surface at a time. Cross-surface reads are always allowed.

| Surface | Sole writer | Note |
| --- | --- | --- |
| `main`, all merges, tags, releases | Codex | Release gate. No worker merges. |
| `.fleet/PLAN.md`, `.fleet/tasks/*` | Codex | Workers write only their own report file. |
| `.fleet/reports/<id>.md` | the assigned worker | One file per task id. |
| `BRIDGE.md` | Codex | Append-only. Workers request entries via their report. |
| `backend/signaling/**` | Codex (M2) | Kimi's M3 sweep is read-only; never two writers in one window. |
| `core_crypto/**` | writer assigned per finding by Codex; **not** the reviewer | Implementer and security reviewer are always different agents (§5). Kimi's first crypto assignment is read-only. |
| `client_android/app/src/**` | Claude (M5 UI) | Excludes `build.gradle`. |
| `client_android/app/build.gradle` | Codex (M9) | Version + signing. |
| `.github/workflows/**` | Grok (M5 #87 only) | Single bounded override. |
| `website/**`, `docs/WIKI/**`, `llms.txt`, `robots.txt` | Claude (M8) | Content truth. |
| `deploy/**`, `tools/brevo_keepalive.sh` | Codex (M3 STX-03/10, M6) | Secret-adjacent. |
| Brevo console, Play Console, Stripe, VLABS repo | Codex / Gio | Never a worker. |

**Safe parallel sets** (no shared writer, no shared file):
- Set A: M5/#87 (Grok, workflows) ‖ M6 (Codex, provider console) ‖ M8 prep (Claude, website).
- Set B: M3 crypto review (Kimi, read-only over `core_crypto/`) ‖ M5 UI (Claude, Android resources).
- Never parallel: M1 with anything; M2 with M3 signaling work; M9 with M3 or M5.

---

## 4. Duplication guards

- PR #95's tester work is **done**. M4 verifies and issues; it does not reimplement.
- PRs #89/#91/#92/#93 are already contained in #96; do not re-fix STX-02/23/29 separately.
- The Brevo repository inventory is **closed** (`SC-BREVO-001`, `verdict: ok`). M6 adds only
  provider metadata.
- The signaling startup import fix is in `main` (`e06d018`). M2 verifies deployment, not the fix.
- Issue #86's own text corrects the 48dp assumption; measure from source before changing a dimen.

---

## 5. Kimi assignments for tomorrow (large context) and mandatory security review

1. **KIMI-102-REVIEW** — whole-stack review of PR #102 (224 files, 41 commits). Verify the replay
   is faithful: every commit from #82/#90/#94/#95/#96/#97/#98/#99/#100/#101 present, no extra
   change smuggled in, tree equality independently recomputed. Deliver the result as
   `.fleet/reports/KIMI-102-REVIEW.md`. This Fleet review blocks M1 but does not satisfy the
   separate GitHub branch-protection approval.
2. **KIMI-STX-SWEEP** — map all 62 findings of #84 against the post-#102 tree; output a table
   `finding → fixed-by-commit | still-open | wontfix-candidate`. Read-only, no edits. Feeds M3.
3. **KIMI-SEC-CRYPTO (mandatory security review, read-only)** — `core_crypto/src/ffi/mod.rs`
   and `session/mod.rs` against STX-21 (unauthenticated key exchange, `HKDF salt=None`), STX-22
   (Double-Ratchet claim vs. implementation), STX-24 (replay/AAD/direction separation), STX-25
   (unzeroized key copies), STX-27 (no `catch_unwind`). **No edits** — findings and proposed diffs
   only. It may not be silently replaced by another worker; any handover is recorded under `risks`.

**Crypto separation-of-duties rule (binding for all of M3):** the agent that implements a crypto
change is never the agent that security-reviews it. No agent reviews its own crypto change. Because
Kimi holds the read-only crypto review above, residual crypto *writes* may be assigned to Kimi only
later, and only once Codex has named a different agent as the reviewer for those specific changes.
Until Codex makes that assignment, crypto residuals stay unassigned rather than defaulting to the
reviewer.
4. **KIMI-ENTITLEMENT-REVIEW** — re-review the merged tester-license path (device binding
   atomicity, fail-closed renewal, revocation) because it touches auth and payments. Blocks M4.

---

## 6. Next executable batch (no new credentials, no production access)

Startable immediately, in parallel, by three different agents:

1. **Kimi → KIMI-102-REVIEW** and **KIMI-STX-SWEEP**. Read-only on the PR branch. Unblocks M1 and
   supplies the true remaining-findings count for M3.
2. **Grok → SC-CI-087**: set `packages: platform-tools` for `android-actions/setup-android` in
   `.github/workflows/ci-basic.yml` and `android-instrumentation.yml`. Keep the explicit API 36 /
   Build Tools 36 / CMake / NDK installs. Acceptance: `yamllint .` green and the `Android Client`
   job reaching Gradle. No Gradle, source, signing or release change.
3. **Claude → SC-UI-086-MEASURE**: read-only measurement pass over the Android UI defects in #86
   (bottom-nav label mode and minimum height, dial-pad row weights and the `0\n+` label,
   `adjustPan` vs. contact-match rows, settings row touch targets, Premium VPN section
   expand/collapse). Output an exact-source measurement table plus the minimal proposed diff — no
   resource edit until the measurement is reviewed.
4. **Codex → M0**: annotate the ten stacked implementation PRs now. Close them only after #102 is
   merged and the resulting `main` tree and CI are verified. Preserve #83, #85 and Dependabot PRs.

Explicitly **not** in this batch: any merge, deploy, Brevo console access, Play upload, artifact
build, tester code issuance, email send, or flag change.

**Separate exact-authorization gate.** Each of the following is its own gate, requested and granted
individually in writing by Gio for that one action, and is never carried by this plan, by a
milestone being "green", or by a prior approval of a similar action:
- issuing a real tester entitlement grant to a real device (M4);
- any provider-console action at Brevo, Stripe, Play or VLABS, including read-only console access
  (M6, M7, M10);
- any deployment or redeploy of the signaling host (M2);
- any sales-state mutation: `PRODUCT_READY`, `FINANCE_READY`, Checkout open/close (M7, M11).

A granted gate authorizes exactly the one named action, once. It does not generalize.

---

`PRODUCT_READY=NO` · `FINANCE_READY=NO` — unchanged by this document.
