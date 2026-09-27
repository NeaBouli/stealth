# SecureCall completion plan

Current milestone: release/security remediation before final physical-device and sales gates.

1. `SC-SEC-ICE-FALLBACK-01` — remove public TURN fallback; status: SOURCE GREEN, independent
   worker review queued because all workers were unavailable.
2. Runtime credential rotation — owner-deferred until all projects are complete. Preserve the
   prepared TURN/Admin/Railway runbooks; do not rotate or repeatedly treat this as an immediate
   project gate. Read-only recheck: health 200, unauth admin 401, services/listeners active;
   watchdog repair remains part of the later coordinated maintenance window.
4. VLABS IFR checkout PR #208 — rebase and test after npm toolchain recovery; status: BLOCKED
   after three reproducible npm CLI failures.
5. Physical S10/S4 release matrix and tester-license binding — status: WAITING FOR DEVICES and
   production signer/registry provisioning. Local API-35 instrumentation: 30 tests, zero failures,
   one conditional live-chain skip.
6. Release/Play/store activation — status: CLOSED until PRODUCT_READY and FINANCE_READY match
   the immutable release tuple.
