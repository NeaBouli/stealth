# SC-AUDIT-RECONCILE-01 — public audit reconciliation

Status: `ok` (candidate classification; no issue mutation)
Worker: `codex` in Solo Mode after required probes failed for Kimi, Claude and Grok
Candidate: PR [#102](https://github.com/NeaBouli/stealth/pull/102) plus branch
`agent/codex/SC-UI-086-PR102` through `1042af5`

## Rules

- `fixed` means patch and test evidence exists in the candidate, not that `main` is remediated.
- Issue #84 checkboxes remain open until the exact candidate is reviewed, CI-green and merged.
- `runtime-deferred` means repository remediation exists but the owner deferred runtime rotation.
- `partial` is never proposed for checkbox closure.

## Result

| ID | Class | Evidence / remaining work |
|---|---|---|
| STX-01 | fixed | Authenticated identity registration and calls in [#97](https://github.com/NeaBouli/stealth/pull/97). |
| STX-02 | fixed | Public status IP buckets removed and contract-tested in [#96](https://github.com/NeaBouli/stealth/pull/96). |
| STX-03 | runtime-deferred | Secret-free TURN template and tests in [#98](https://github.com/NeaBouli/stealth/pull/98); production rotation deferred by owner. |
| STX-04 | open | Phone enumeration and contact-hash design not remediated by the reviewed stack. |
| STX-05 | open | General gift-code entropy and per-code throttling remain outside tester-license work. |
| STX-06 | partial | Custom-ID purchase surface is default-closed in [#82](https://github.com/NeaBouli/stealth/pull/82); password-model remediation is not evidenced. |
| STX-07 | open | A single verified proxy/rate-limit identity contract is not evidenced. |
| STX-08 | fixed | PKD count/TTL bounds and registration limiter in [#99](https://github.com/NeaBouli/stealth/pull/99). |
| STX-09 | open | Tracked/baked runtime JSON data remains unremediated. |
| STX-10 | fixed | Deployment scripts no longer print generated credentials; CI guard in [#100](https://github.com/NeaBouli/stealth/pull/100). |
| STX-11 | open | Legacy re-attachable payment fallback removal is not evidenced. |
| STX-12 | open | Android root-enforcement claims/behavior are not remediated. |
| STX-13 | fixed | Embedded public TURN hosts/credentials removed in commit `4cde4e5`; full unit/lint/compile/instrumentation evidence recorded. |
| STX-14 | fixed | Central constant-time admin auth and bounded throttling in [#101](https://github.com/NeaBouli/stealth/pull/101). |
| STX-15 | open | Fail-closed WebSocket Origin configuration is not evidenced. |
| STX-16 | open | Caller-controlled lock-screen/FCM metadata policy remains open. |
| STX-17 | partial | PKD/admin limiter state is bounded in #99/#101; remaining limiter/sold-code retention is not proven fixed. |
| STX-18 | open | `/tmp` data-directory fallback remains open. |
| STX-19 | open | Stub/dev artifacts near live paths remain open. |
| STX-20 | open | Header/fingerprinting/status hardening remains open. |
| STX-21 | fixed | Signed transcript binding, key confirmation and media gating in [#97](https://github.com/NeaBouli/stealth/pull/97). |
| STX-22 | fixed | False Double-Ratchet/PFS claims replaced by implemented per-call model in [#94](https://github.com/NeaBouli/stealth/pull/94). |
| STX-23 | fixed | Current controlled certificate rollover pins and expiry gates in [#96](https://github.com/NeaBouli/stealth/pull/96). |
| STX-24 | open | Media replay/AAD/direction-separation design remains open. |
| STX-25 | open | JNI/Java key-material zeroization remains open. |
| STX-26 | open | Mock GhostNet crypto removal/isolation is not evidenced. |
| STX-27 | open | FFI panic/zeroization/AAD hardening remains open. |
| STX-28 | open | Core-crypto README/KAT/fuzz follow-up remains open. |
| STX-29 | fixed | Unconsented GA loading removed from HTML surfaces and contract-tested in [#96](https://github.com/NeaBouli/stealth/pull/96). |
| STX-30 | open | Private vulnerability-reporting channel remains open. |
| STX-31 | open | Invite referrer/deep-link/localStorage defects remain open. |
| STX-32 | open | Third-party payment/wallet script integrity remains open. |
| STX-33 | open | Legacy return forwarder retirement is not evidenced. |
| STX-34 | open | Dynamic price escalation in the legacy license path remains open. |
| STX-35 | open | Payment-success deep-link/support contract remains open. |
| STX-36 | superseded | Audit already classifies the listed public identifiers as harmless; no remediation required. |
| STX-37 | fixed | Removed-feature GhostNet marketing corrected in [#94](https://github.com/NeaBouli/stealth/pull/94). |
| STX-38 | fixed | Android/direct-channel product and price truth aligned and regression-tested in [#94](https://github.com/NeaBouli/stealth/pull/94). |
| STX-39 | open | Independent-audit claim correction is not fully evidenced. |
| STX-40 | partial | Major docs were aligned in #94; all four changelog heads were not proven reconciled. |
| STX-41 | fixed | Play/Fastlane feature, license and pricing copy aligned with regression coverage in [#94](https://github.com/NeaBouli/stealth/pull/94). |
| STX-42 | fixed | Homepage/llms Play-status product truth aligned in [#94](https://github.com/NeaBouli/stealth/pull/94). |
| STX-43 | open | Nonexistent issue references and known-issue contradictions remain open. |
| STX-44 | open | Railway/Hetzner hosting-documentation drift remains open. |
| STX-45 | open | F-Droid/GPL remnants remain open. |
| STX-46 | fixed | Absolute marketing/security claims replaced with bounded implementation truth in [#94](https://github.com/NeaBouli/stealth/pull/94). |
| STX-47 | open | Contact/disclosure channel consolidation remains open. |
| STX-48 | partial | Current product copy improved in #94; all historical version identifiers are not reconciled. |
| STX-49 | open | Removed WalletConnect store screenshot remains open. |
| STX-50 | partial | Broad docs refresh in #94; the complete stale-doc cluster is not proven closed. |
| STX-51 | open | HTML/Markdown/wiki publication drift remains open. |
| STX-52 | partial | GA was removed in #96; privacy-policy convergence is not proven complete. |
| STX-53 | open | Stale audit score/page remains open. |
| STX-54 | open | Cosmetic/factual cleanup cluster remains open. |
| STX-55 | open | License-versus-verifiability policy remains open. |
| STX-56 | fixed | `llms.txt` product/status/hosting/contact truth updated and covered in [#94](https://github.com/NeaBouli/stealth/pull/94). |
| STX-57 | open | Digital-product structured-data correction remains open. |
| STX-58 | open | Sitemap membership/lastmod hygiene remains open. |
| STX-59 | open | Robots/transactional-page/AI-crawler policy remains open. |
| STX-60 | open | Pages-host security headers remain open. |
| STX-61 | open | Wrong/inert redirects configuration remains open. |
| STX-62 | open | hreflang/404 invite-router tracking behavior remains open. |

Counts: `fixed=16`, `runtime-deferred=1`, `partial=6`, `superseded=1`, `open=38`; total `62`.

## Issue action after integration

After the exact candidate reaches `main`, check only STX-01, 02, 08, 10, 13, 14, 21, 22,
23, 29, 37, 38, 41, 42, 46 and 56. Close STX-36 with a no-action rationale. Keep STX-03
open and labelled runtime-deferred until the coordinated maintenance window is complete. Keep all
partial/open findings unchecked. Before editing the issue, rerun exact-main CI and verify the final
tree contains every cited patch.

Tests: public PR metadata/evidence review; all 62 IDs counted exactly once; `git diff --check` pending.
Risks: classifications for documentation clusters are intentionally conservative.
Security: no new non-public finding or exploit detail was published.

