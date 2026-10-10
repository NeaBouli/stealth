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
