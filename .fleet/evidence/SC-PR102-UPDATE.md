## October 1 update for PR #102

Additional reviewed candidate work after the original exact tree:

- fixes narrow-phone dialer/IME/navigation layout and Premium settings collapse behavior;
- adds fail-closed private tester-registry promotion tooling without real recipient data;
- removes embedded public TURN hosts/credentials and requires dynamic TURN for relay-only mode;
- records the 62-finding audit reconciliation without prematurely closing issue #84.

Verification before the queued update:

- Free unit suite and lint: PASS;
- Pro/Premium Kotlin compilation: PASS;
- API-35 Free instrumentation: 30 tests, 0 failed, 1 conditional live-chain skip;
- public TURN marker scan and `git diff --check`: PASS;
- current candidate is 0 behind / 49 ahead of `origin/main`, merge-tree clean;
- `integrate/securecall-linear-stack-20260920` is an ancestor of the candidate, so the update is
  a normal fast-forward and does not require force-push.

Gates remain unchanged: independent approving review, exact-head hosted CI, physical S10/S4
two-device acceptance, matching PRODUCT_READY/FINANCE_READY, then release artifacts and stores.
No deployment, provider/payment mutation, runtime rotation or sales activation is included.

