# Codex Findings — Aktuelle Session

Datum: 2026-05-04 (fortlaufend)
Archiv: `CODEX_FINDINGS_ARCHIVE_20260504.md`

## Aktueller Fix-Status (alle CC-Commits verified)

| ID | Finding | Status | CC-Commit |
|----|---------|--------|-----------|
| C-01 | Hardcoded activation codes | VERIFIED_FIXED | `21b0957` |
| C-02 | Wildcard CORS | VERIFIED_FIXED | `edc6dc7` |
| C-03 | Stripe webhook optional | VERIFIED_FIXED | `21b0957` |
| H-01 | /ice-servers public | VERIFIED_FIXED | `385386a` |
| H-02 | /metrics public | VERIFIED_FIXED | `edc6dc7` |
| H-03 | DEREGISTER spoofing | VERIFIED_FIXED | `edc6dc7` |
| H-04 | /invite/accepted auth | NEEDS_RECHECK | `9be8df9` |
| H-05 | Checkout rate limit | VERIFIED_FIXED | `cbbbcd6` |
| H-06 | Phone lookup no auth | VERIFIED_FIXED | `21b0957` |
| H-07 | Codes in logs | VERIFIED_FIXED | `cf741a0` |
| H-08 | JSON injection | VERIFIED_FIXED | `1b39f9b` |
| H-09 | Cert pinning claims | VERIFIED_FIXED (claims) | `b64ee25` |
| M-01 | PKD PUT/DELETE auth | VERIFIED_FIXED | `281320f` |
| L-01 | "open source" text | VERIFIED_FIXED | `c15b955` |
| L-02 | og-image GPL | VERIFIED_FIXED | `0b64d09` |
| P-01 | Privacy claim drift | NEEDS_RECHECK | `0ca084e` |

## Offene Punkte (kein Fix noetig, Monitoring/Entscheidung)

- **H-09 echtes Pinning**: Bewusst als "planned" gefuehrt. Braucht OkHttpClient-Factory.
- **Dependabot**: `uuid` (medium) + `@tootallnate/once` (low) transitiv via firebase-admin. Kein nicht-breaking Fix-Pfad.
- **Hybrid-Migration**: MIGRATION_PLAN.md liegt vor. Ausfuehrung braucht Gio-Entscheidung.
- **UpdateChecker Tests**: 8 Unit Tests erstellt in `0ca084e`. Codex soll verifizieren.

## Codex: Bitte re-verifizieren

1. `9be8df9` — H-04 HMAC invite token (single-use, 1h TTL, inviterSecureId match)
2. `0ca084e` — P-01 Privacy Claims (README + privacy.html) + UpdateChecker Tests
