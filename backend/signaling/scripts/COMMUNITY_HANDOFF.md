# Community coordinator handoff - 2026-09-13

This supersedes the earlier instruction that required the community coordinator
to obtain a complete server gift inventory or a reference/signing key.

## Scope owned by community coordinator

1. Reuse existing private review and source records. No rescan of unrelated lists.
2. Preserve the exact general tester list, including the owner's row. Expected
   list total is23; external intended delivery candidates22. Counts are expectations,
   not permission to add/remove people. No automatic approval or other-list import.
3. Prepare a separate private intake directory (owner-only mode700, outside Git).
   Do not chmod original files recursively or alter backup/encryption settings.
4. CSV input must have email,list headers and exact list label with Greek beta:
   SecureCall beta-test (beta rendered as U+03B2). If the private source schema differs,
   adapt a private copy using a CSV parser, following the reviewed source mappings;
   do not edit the original or print its rows. Unclear field mapping is a real question.
5. Run the synthetic tests in TESTER_STAGING.md, then the documented --intake-only
   command. This mode needs neither gift inventory nor reference pepper.
6. Keep the resulting recipient-intake.json private on this host. Return source
   commit, test result, selected count, codes_generated=0 and status only. Do not
   attach addresses, code mappings or private file contents to public Git or chat.
7. After the lead supplies the reviewed current staging/export commit and confirms
   the private inventory input, the coordinator may run the documented preparation
   and draft-export commands. Keep all three resulting files private. Draft rows
   remain `draft_do_not_send`; do not send a code until the lead returns the exact
   signed APK identity and an explicit ready-to-deliver authorization.

## Lead deliverables still outstanding

- Evidence-based reconciliation against existing gifts; no fabricated empty inventory.
- Device-bound activation with verified signed entitlement, repeat/replay/concurrency
  and recovery handling, preservation of commercial and Play licenses.
- Tested Direct Premium artifact, exact version/package/signature/checksum and
  two-device acceptance. No old or untested APK substitution.
- Approved private code-delivery storage/channel, controlled license import,
  rollback plan and specific production/dispatch approval.

## Things NOT delegated

No server, signing, API, Play, finance, migration, activation or email changes.
No dummy or provisional codes sent to customers. Intake COMPLETE does not mean
license feature COMPLETE. Do not rerun the earlier code-preparation command while
its lead-side prerequisites are missing. FileVault being off is not proof that
every alternative storage approach is unsuitable, but intake does not certify
code-storage security. Real codes remain prohibited until that is reviewed.

Customer communication and usable code delivery remain pending the lead's
explicit ready-to-deliver handoff; no automatic schedule or promise is implied.
