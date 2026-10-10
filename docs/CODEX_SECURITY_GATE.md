# Codex Security Pre-Integration Gate
Owner directive recorded 2026-10-04. This is project-local; the frozen global Fleet workflow is unchanged.
1. Finish the bounded implementation and relevant tests, then plan one scoped Codex Security review of the exact immutable integration candidate.
2. Before scanning, verify access and obtain explicit authorization covering destination, transmitted code, exclusions and cost limit. This directive authorizes neither installation nor code transfer nor additional spending.
3. Never transmit secrets, wallet material or private operator information. Keep sensitive findings private; public records contain generic status only.
4. Compare findings with existing audits/tickets before creating bounded remediation tasks; avoid duplicate scans, tickets and implementation.
5. Verify fixes and record actual scope, full candidate commit, results, unresolved risks and integration decision. A changed candidate requires explicit coverage reconciliation, not an automatic repeated scan.
6. Codex Security is distinct from GitHub Security Audit workflows. A successful CI security job is not evidence of a Codex Security scan. Unavailable or unexecuted scans are NOT RUN, never PASS.
7. Tests, independent specialist reviews, contract/runtime verification and frontend gates remain required. No automatic merge, deployment or policy changes; the lead owns triage, documented exceptions and acceptance.

## Tracking Ticket SC-CODEX-SECURITY-GATE
Status: PLANNED / NOT RUN. Access: NOT VERIFIED. Destination, code scope, exclusions and cost limit: NOT AUTHORIZED. No installation, transfer or scan performed.
Coverage queue: combined PR133/135, PR119 commercial integration including the reviewed correction, PR120 identity integration, and PR136 settings-permission navigation. Each final integration commit must be frozen before its scoped authorization; existing isolated source/test commits are not a final combined candidate.
Next: lead records exact candidate and requests one bounded authorization; compare authorized scan results against existing STX audit/ticket register. Public completion statuses remain generic; private findings stay outside the public repository.

## 2026-10-10 - Scoped queue authorization update
Owner decision "Ja, mit Security-Scan" requests official Codex Security setup and execution for the #138/#137/#134/#136 integration queue. Admin squash/linear merges are authorized only in that order after actual scan acceptance and green exact heads; stop on any finding. This supersedes the earlier setup-not-authorized state, not the requirement to verify service destination, exact data scope/exclusions and cost before transmission. No additional spending is authorized.
Immutable combined preview: ed77a747a3c426d8380196d5f8814cefd3dadd5a (Draft #145), tree 621cb01141edfd171ead13286c8a80c5fc56d1e2, base f8c969b1436cb65c13a6c85e73abe179191245f1. Planned scope is the thirteen changed public source/test/config files, excluding inherited Bridge/Fleet documents and all secrets, signing/wallet material, private operator/customer/license data and history. Verify supported scope and EUR0 additional-cost limit before transfer; no full-account or unbounded repository grant.
Actual check: service installed=false; installation entry offered only, connection NOT VERIFIED, scan NOT RUN, no code transfer or main merge. PR #119/#120/#144/#143 and preview #145 are not authorized for merge by this decision. Remaining tests, specialist/runtime and release/readiness gates are unchanged.

## 2026-10-10 - Exact public transfer confirmation and CLI preflight
Owner explicitly confirms setup/run and code transfer of the four public Stealth PR diffs; no private repository scope or additional costs. The official @openai/codex-security CLI is another entry point to the same scanner, not a replacement CI audit. Verify stored ChatGPT service access; never silently fall back to API-key billing. The estimated --max-cost option is not a hard spending guarantee. Scan remains NOT RUN while installation/access and zero-additional-cost conditions are verified; no merge performed.

## 2026-10-10 - CLI installed and immutable scan admitted
Official CLI installation and isolated dry-run preflight passed. Actual authorized refs scope is the complete fifteen-file public diff (thirteen source/test/config and two already-public generic coordination files), not a claimed enforced thirteen-file filter. Private data remains excluded. One source-only scan uses explicit existing ChatGPT authentication without API fallback or additional purchases, private artifacts and verified task-local read/write isolation. Actual scan access/results remain pending; dry-run and CI are not scan acceptance. Any finding, incomplete run or paid requirement holds integration.

## 2026-10-10 - Interrupted actual run / supported continuation
The first isolated actual run exited2 during preflight at the lead's estimated usage ceiling. Coverage is INCOMPLETE; absence of a completed report is not a clean result. Supported workflow continuation preserves the immutable authorized public diff, existing included ChatGPT sign-in and private isolation; no additional fees or API billing authorized. Integration remains HOLD until a complete clean result and fresh exact-head checks. Original conditional four-PR order and all separate release gates remain unchanged.

## 2026-10-10 - Actual scan result and recovery gate
Status: INCOMPLETE / NOT ACCEPTED. Continuation was rejected by CLI output-directory and immutable request-binding validation before another analysis run; bounded retry threshold reached. Further scanner execution stopped for technical escalation. Owner setup/transfer/conditional integration authorization remains valid, not revoked or missing. Four exact heads and main are unchanged; all four CI rollups SUCCESS, no merges. Recovery must preserve scan scope, excluded private data and zero-extra-fee controls; partial output cannot satisfy the security gate. Private raw evidence remains outside public Git.
