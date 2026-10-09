# StealthX Project Completion Plan

Owner directive: 2026-10-10. Lead: Codex. Scope: SecureCall, SecureChat and Chameleon.
SecureCall is the first release milestone; it must not wait for unrelated product features.
This plan extends, rather than restarts, the existing audit, implementation and QA work.

## Target

Finish the implemented product scope, resolve or explicitly disposition every open finding,
validate the complete supported feature and license lifecycle, publish consistent verified
artifacts and documentation, and reach separately approved sales activation for each product.
Completion means evidenced functioning releases and sales, not merely a successful build,
Google Play approval, an uploaded artifact, or a visible purchase button.

## Milestones

| ID | Outcome | Acceptance |
| --- | --- | --- |
| M1 | Complete current-state ledger | Existing audit registers, issues, PRs, QA, release and distribution evidence reconciled; each unresolved item has an owner, bounded next task and evidence requirement. No duplicate findings or repeated accepted work. |
| M2 | Safe integration candidate | Reviewed fixes integrated in normal dependency order; exact-head CI and applicable independent security gates satisfied. Preserve unrelated changes. |
| M3 | Entitlements and upgrades | Existing legitimate rights survive updates through authoritative proofs; valid activation, renewal, restore, offline behavior, refund/dispute revocation, replay/concurrency and persistence verified. Package identity alone never proves a purchase. |
| M4 | Complete product QA | Supported settings/features and negative/error paths verified across Free and paid plans, supported Android versions, compact/tablet layouts, background/reconnect and real call/audio cases. Record skipped/unavailable cases rather than count them as passes. |
| M5 | Immutable release artifacts | Fresh signed APK/AAB candidates, unique version codes, signatures, checksums, mapping and artifact inspections verified against the exact tested source. Preserve the Play/direct VPN split. |
| M6 | Public distribution consistency | Download, landing, FAQ, Wiki, GitHub and store links/copy match released artifacts and actually supported behavior. Responsive visual and navigation gates satisfied, followed by authorized live readback. |
| M7 | Web-only IFR offer | All supported product sales surfaces use verified wallet ownership and eligibility through the existing approved adapter. No address-only qualification and no wallet/IFR mechanism inside Android. No arbitrary redemption cap for eligible holders; eligibility and offer terms remain versioned. |
| M8 | Commercial lifecycle | Authorized isolated tests prove checkout, fulfillment, restore, refund/dispute and entitlement withdrawal, including unavailable services, invalid proofs and retries. Failed or unfinished products stay closed. |
| M9 | Product acceptance | PRODUCT_READY granted only for each tested immutable product/offer candidate; no blanket approval for the whole suite. |
| M10 | Separate financial acceptance | Financial coordination occurs separately through the private VLABS operator. Matching explicit FINANCE_READY is required; another project's approval is not transferable. |
| M11 | Authorized launch | Normal merge/release gates and separately authorized publishing, deployment and sales activation completed for the matching approved candidate; post-publication functional/visual verification and rollback recorded. |

## Initial Unresolved Release Gates

- Current dependency correction and combined privacy candidate: PR138/PR137 share the same verified lockfile; do not implement it again. Normal integration remains pending.
- PR134 native reproducibility, PR136 full-screen startup, PR119 commercial candidate, PR139 lifecycle amendments and preserved PR120 identity work require their remaining exact-candidate gates. Historical green checks are not approval of a later combined head.
- Android34+ startup is exclusively assigned to the existing Claude terminal. Paid lifecycle, legitimate legacy-right recovery and complete call/audio acceptance remain open.
- Complete STX, SCT and CHA audit registers and additive device/UI findings remain acceptance inputs. Do not assume the latest PR closes every item.
- Final signed artifacts, release-version reconciliation and all public distribution endpoints remain to be validated after integration, not rebuilt speculatively.
- Web offer and commercial runtime acceptance remain incomplete. Purchase controls remain closed until matching product and separate financial acceptance and release authorization.
- Controlled personal tester entitlement preparation/delivery remains a separate private workflow; no production activation or recipient communication is implied by this plan.
- Codex Security: NOT RUN. Installation, transmitted scope/destination, exclusions and cost approval remain separate prerequisites; GitHub security jobs are not a substitute.
- Server credential rotation remains deferred by the owner. A required infrastructure action must have an owner and exact approval, not be hidden as completed.

## Work Allocation

- Codex: scope, architecture, private operator coordination, security/integration review, exact-head acceptance, publishing and release judgment.
- Existing Claude terminal: the assigned API34+ runtime block only; no concurrent device agents.
- Separate Claude worker: bounded public web offer-route tracing, independent of devices and entitlement implementation.
- Kimi: bounded large-context implementation/review blocks when available; do not reconnect during a confirmed quota limit or repeat an accepted task.
- Grok/JEV: only eligible narrow auxiliary tasks under existing data-transfer rules; neither grants financial, security or release approval.

## Execution Rules

Advance one bounded milestone block at a time. Reuse accepted evidence where source and scope
are unchanged; rerun the full relevant chain after integration changes the candidate.
External dependencies block only the affected action. Continue independent authorized work.
Keep the append-only Bridges and local `.fleet/PLAN.md` current with actual results and blockers.
Do not relax tests, silently bypass review, mark unavailable checks PASS, or activate incomplete
products. Frozen global Fleet instructions remain unchanged.
