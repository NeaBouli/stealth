id: SC-SEC-RUNTIME-VERIFY-20260927
status: partial
worker: codex
branch: agent/codex/SC-UI-086-PR102
summary: |
  Repeated the authorized read-only production verification through the restricted
  fleet account. Signaling health and the unauthenticated admin boundary are correct;
  signaling and coturn are running and TURN listeners are present. Process start times
  prove the authorized credential rotation has not yet been executed.
files: documentation only
tests: |
  Public health -> HTTP 200
  Unauthenticated admin route -> HTTP 401
  Recent signaling error classification (5,000 error-log lines) -> 0 matches
  Recent watchdog sample (5,000 lines) -> 5,000 permission-denied matches
  Railway OAuth identity -> reachable; SecureCall checkout has no linked Railway project
risks: |
  Root-owned TURN/Admin secrets remain unrotated. The watchdog remains broken. Railway
  token metadata cannot be safely mapped by the unlinked CLI context; revoking an
  unidentified account/workspace token could interrupt an unrelated project.
security: Authorized runtime rotation remains required; no secret value was read or emitted.
next: Root operator executes the prepared atomic rotation/restart/rollback runbook. Gio identifies the exact Railway token in Dashboard metadata; revoke it and issue no replacement unless a live consumer is proven.

