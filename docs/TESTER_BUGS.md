# Tester Bug Reports

> Tester-Emails werden anonymisiert gespeichert:
> Nur erster + letzter Buchstabe sichtbar (z.B. g.......i@gmail.com).
> Vollstaendige Emails werden NICHT im Repo gespeichert.

| # | Bug | Severity | Tester | Geraet | Status |
|---|-----|----------|--------|--------|--------|
| TB-001 | — | — | — | — | — |
| TB-002 | Upgrade Screen: schlechte Formatierung + falsche Preise | HIGH | Alle Tester | S10/S7/Tab | FIXED |
| TB-003 | "Peer not found" beim Anruf | CRITICAL | Tester | Alle | FIXED — user-friendly message |
| TB-004 | Telefonnummer nach Onboarding nicht gespeichert | HIGH | Tester | Alle | FIXED — reads confirmed_phone_number |
| TB-005 | Android Navigationsleiste ueberlappt App-Footer | HIGH | Tester | Samsung S21+ | FIXED — WindowInsets padding |
| TB-006 | App erkennt nicht dass Kontakt App installiert hat | HIGH | Tester | Alle | FIXED — "Try calling anyway" option |
| TB-007 | SMS Einladungslink zeigt auf falschen Store | MEDIUM | Tester | Alle | FIXED — stealthx.tech |
| TB-008 | Support Development Section nicht einklappbar | LOW | Tester | Alle | FIXED — initialExpandedChildrenCount=0 |
| TB-009 | Werbebanner nicht einklappbar | LOW | Tester | Free | WONTFIX — AdMob standard behavior |
| TB-010 | Kein Invite System — Tester koennen sich nicht gegenseitig als Kontakt hinzufuegen | HIGH | Tester | Alle | FIXED — Deep Link Invite v3.5 |
| TB-011 | WalletConnect haengt bei Connecting wenn User abbricht | HIGH | Tester | S10/Alle | FIXED — 30s timeout |
| TB-012 | Support Development Accordion oeffnet sich aber schliesst nicht | MEDIUM | Tester | Alle | FIXED — toggle button |
| TB-013 | Werbebanner komplett unsichtbar — AdMob laedt nicht | HIGH | Tester | Free | FIXED — container VISIBLE |
| TB-014 | FCM Tokens gehen bei Server-Redeploy verloren | CRITICAL | Dev | Backend | FIXED — persistent file storage |
| TB-015 | FLAG_SECURE Toggle Free Tier nicht gesperrt | HIGH | Tester | Free | FIXED — disabled + PRO label |
| TB-016 | FLAG_SECURE ignoriert Toggle-Zustand auf Pro | HIGH | Tester | Pro | FIXED — tier-based logic |
| TB-017 | Update Button oeffnet stealthx.tech statt Play Store | MEDIUM | Tester | Alle | FIXED — always Play Store |

## Beta Tester (anonymisiert)

| # | Tester | Status |
|---|--------|--------|
| 1 | B................1@gmail.com | Eingeladen |
| 2 | H................1@gmail.com | Eingeladen |
| 3 | a..............s@gmail.com | Eingeladen |
| 4 | d................3@gmail.com | Eingeladen |
| 5 | e................2@gmail.com | Eingeladen |
| 6 | g..............i@gmail.com | Eingeladen |
| 7 | l............a@gmail.com | Eingeladen |
| 8 | n......n@gmail.com | Eingeladen |
| 9 | p............a@gmail.com | Eingeladen |
| 10 | r......s@gmail.com | Eingeladen |
| 11 | z......r@gmail.com | Eingeladen |
| 12 | u..........m@gmail.com | Eingeladen |
| 13 | r..........1@gmail.com | Eingeladen |
| 14 | b..........r@googlemail.com | Eingeladen |

**Tester gesamt: 14/14**

## How to report
Tester melden Bugs per Email an kaspartisan@proton.me oder via GitHub Issues.
Format: Geraet, Android-Version, Schritte zum Reproduzieren, Screenshot falls moeglich.
