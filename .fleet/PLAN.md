# SecureCall completion plan

Current milestone: release/security remediation before final physical-device and sales gates.

1. `SC-SEC-ICE-FALLBACK-01` — remove public TURN fallback; status: SOURCE GREEN, independent
   worker review queued because all workers were unavailable.
2. External root operator — rotate first-party TURN/Admin runtime secrets using the prepared
   redacted runbook; status: BLOCKED BY TECHNICAL ROOT ACCESS. Read-only recheck: health 200,
   unauth admin 401, services/listeners active, rotation not yet applied, watchdog still broken.
3. Railway — revoke/reissue only if an active consumer is proven; status: NO MATCHING ACTIVE
   TOKEN OR CONSUMER FOUND. Current CLI is authenticated but the SecureCall checkout is not linked,
   so no unrelated account/workspace token is changed on inference.
4. VLABS IFR checkout PR #208 — rebase and test after npm toolchain recovery; status: BLOCKED
   after three reproducible npm CLI failures.
5. Physical S10/S4 release matrix and tester-license binding — status: WAITING FOR DEVICES and
   production signer/registry provisioning. Local API-35 instrumentation: 30 tests, zero failures,
   one conditional live-chain skip.
6. Release/Play/store activation — status: CLOSED until PRODUCT_READY and FINANCE_READY match
   the immutable release tuple.
