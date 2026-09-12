# SecureCall Development Candidate - 2026-09-13

Status: DRAFT / NOT RELEASE READY.

Source checkpoint: `618f7cb3e51f6972b2790f118d4c0f8b446b4e80`.
Branch: `codex/vlabs-sales-integration-20260905`.

## Scope

This candidate adds strict direct-license verification and local lifecycle
handling, correlates activation responses, enforces runtime feature checks and
keeps unavailable purchasing controls closed. Distribution remains separate:
Play Free and Direct Pro have no built-in VPN; Direct Premium retains its
optional VPN runtime. No release or deployment is part of this draft.

## Existing Verification

Developer checks completed on September 8 against these source bytes:

- Unit tests: Free 197, Pro 197, Premium 204 passed.
- Offline API30 and API36 instrumentation: each Free 23, Pro 23, Premium 24
  passed; 140 instrumented tests across the two API levels.
- All debug variant builds/Lint and billing/distribution guards passed.
- Complete backend suite and three closed web-control tests passed.
- Later September 8 recheck: backend/web tests passed freshly; Gradle completed
  with 18 executed and 119 up-to-date tasks. Unit/Lint evidence was cached.

These are historical checks, not September 13 reruns. All counts concern
SecureCall variants, not SecureChat or Chameleon. Runtime test configuration
does not enable paid plans; injected proof tests are not paid-plan acceptance.

## Review and Remaining Gates

Earlier Kimi contributions and reviews were integrated by the lead. The last
additional Kimi review attempts failed at startup with a local watcher error;
no extra independent approval is claimed. Normal source review and CI remain
required. Existing published artifacts are unchanged.

Still open: approved compatibility/recovery behavior, unlocked feature matrix,
real communication/media tests, final physical acceptance, signed release
validation and integration with current main. Do not enable controls or label
the candidate complete on the strength of build/unit success alone.

Financial coordination is handled separately by the VLABS operator.
