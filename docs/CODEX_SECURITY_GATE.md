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
