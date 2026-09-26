id: SC-TESTER-PROMOTE-01
status: ok
worker: codex (solo mode)
branch: agent/codex/SC-UI-086-PR102
summary: Tester-license architecture section 8, private handoff promotion hop. Added exact-manifest-approved, address/code-free, atomic and idempotent registry promotion without deployment.
files: backend/signaling/scripts/promote_tester_staging.py; backend/signaling/scripts/test_promote_tester_staging.py; backend/signaling/scripts/TESTER_STAGING.md; docs/architecture/MAP.md
tests: private staging suite 30/30 PASS; backend tester protocol 3/3 PASS; Python compile PASS; git diff --check PASS; focused secret/private-value pattern check PASS; mypy NOT RUN because module is not installed locally
risks: Promotion proves exact local handoff approval, not remote operator identity, production import authorization or physical-device compatibility. Those remain separate gates.
security: Exact SHA-256, payload hashes, size, schema, count, deterministic grant binding, inactive state, ownership/permissions and atomic output are fail-closed. No raw code, email, secret, device data or provider action.
next: A private coordinator may run the documented command only after owner approval of the exact manifest. Do not deploy or send until the signed artifact, two-device test and bounded production approval pass.
