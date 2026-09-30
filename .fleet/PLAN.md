# SecureCall completion plan

Current milestone: release/security remediation before final physical-device and sales gates.

## October 1 active block

- Original SC-OCT01-DEVICE-QA timed out after APK assembly, with no physical-test report. Its automatic fallback was stopped to prevent duplicate device work; evidence recovered in SC-OCT01-QA-RECOVER.
- Physical smoke COMPLETE/PARTIAL: 57 passes, zero failures, two conditional skips; tablet dialer/navigation measurements passed. Phone UI is keyguard-blocked; full calls/audio/license matrix remains open.
- Settings follow-up: source patch delivered for 48dp category headers and EN/DE section resources; static checks passed, physical/visual revalidation remains blocked by storage and phone lockscreen.
- Integration: SC-OCT01-INTEGRATION-CHECK COMPLETE via Claude fallback; no source conflict, approval required, known scanner false positive assigned separately. Kimi's external probe reports token_limited.
- Dependency: SC-OCT01-DEPENDENCY COMPLETE via Claude fallback; four-line patch integrated as 34c6283. Lead full backend tests/audit passed; hosted Dependency Audit and Secret Detection passed on the exact source head.
- Scanner: SC-OCT01-SCANNER COMPLETE; Claude 89ff909 integrated as 694f5d6. Lead config diff review and 16/16 synthetic regressions passed with CI scanner version. Historical real findings remain unchanged and owner-deferred.
- Codex: PR102 advanced normally to ff5d99f; assess exact-head CI, integrate reports and record gates. Required approving review remains open.
- Two physical devices are connected (Tab S4 and Galaxy A21s). No sales/finance gate is inferred.

1. `SC-SEC-ICE-FALLBACK-01` — remove public TURN fallback; status: SOURCE GREEN and independently
   reviewed by Claude in `SC-PREOCT-INDEPENDENT-REVIEW-01` with no blocking finding.
2. Runtime credential rotation — owner-deferred until all projects are complete. Preserve the
   prepared TURN/Admin/Railway runbooks; do not rotate or repeatedly treat this as an immediate
   project gate. Read-only recheck: health 200, unauth admin 401, services/listeners active;
   watchdog repair remains part of the later coordinated maintenance window.
4. VLABS IFR checkout PR #208 — rebase and test after npm toolchain recovery; status: BLOCKED
   after three reproducible npm CLI failures.
5. Full physical call/audio/license matrix — PARTIAL. Galaxy A21s/Tab S4 instrumentation 57 pass,
   0 fail, 2 conditional pin-chain skips; full matrix and authorized private signer/registry
   provisioning remain open. Phone UI requires owner unlock; final builds need >=15 GiB free.
6. Release/Play/store activation — status: CLOSED until PRODUCT_READY and FINANCE_READY match
   the immutable release tuple.

## Work before the 2026-10-01 hosted-CI reset

1. Reconcile public audit issue #84 against the already-green PR stack and the current integration
   branch; status: COMPLETE in `SC-AUDIT-RECONCILE-01` (`16 fixed`, `1 runtime-deferred`,
   `6 partial`, `1 superseded`, `38 open`). Issue mutation waits for exact candidate merge.
2. Prepare one coherent PR update from `agent/codex/SC-UI-086-PR102`, including the UI, tester
   promotion and ICE fixes; status: PREPARED in `SC-PR102-PREP-01`. Current merge-tree is clean
   and PR #102 can be fast-forwarded once on/after 2026-10-01 without force-push.
3. Independent security review: COMPLETE. Claude verified tester promotion (30/30), Python
   lint/typecheck and ICE policy (6/6); no vulnerability or private-data/credential leak found.
4. Do not build final signed release artifacts before integration, physical QA and immutable
   PRODUCT_READY/FINANCE_READY release coordinates are complete.

JEV public-status prioritization selected `audit-reconciliation`; no private VLABS context was
sent to the external decision service.
