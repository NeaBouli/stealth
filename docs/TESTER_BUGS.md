# Tester Bug Reports

> Tester emails are stored anonymized:
> Only first + last character visible (e.g. g.......i@gmail.com).
> Full emails are NOT stored in the repo.

| # | Bug | Severity | Tester | Device | Status |
|---|-----|----------|--------|--------|--------|
| TB-001 | — | — | — | — | — |
| TB-002 | Upgrade Screen: bad formatting + wrong prices | HIGH | All Testers | S10/S7/Tab | FIXED |
| TB-003 | "Peer not found" when calling | CRITICAL | Tester | All | FIXED — user-friendly message |
| TB-004 | Phone number not saved after onboarding | HIGH | Tester | All | FIXED — reads confirmed_phone_number |
| TB-005 | Android navigation bar overlaps app footer | HIGH | Tester | Samsung S21+ | FIXED — WindowInsets padding |
| TB-006 | App does not detect that contact has app installed | HIGH | Tester | All | FIXED — "Try calling anyway" option |
| TB-007 | SMS invite link points to wrong store | MEDIUM | Tester | All | FIXED — stealthx.tech |
| TB-008 | Support Development section not collapsible | LOW | Tester | All | FIXED — initialExpandedChildrenCount=0 |
| TB-009 | Ad banner not collapsible | LOW | Tester | Free | WONTFIX — AdMob standard behavior |
| TB-010 | No invite system — testers cannot add each other as contacts | HIGH | Tester | All | FIXED — Deep Link Invite v3.5 |
| TB-011 | WalletConnect hangs at Connecting when user cancels | HIGH | Tester | S10/All | FIXED — 30s timeout |
| TB-012 | Support Development accordion opens but does not close | MEDIUM | Tester | All | FIXED — toggle button |
| TB-013 | Ad banner completely invisible — AdMob not loading | HIGH | Tester | Free | FIXED — container VISIBLE |
| TB-014 | FCM tokens lost on server redeploy | CRITICAL | Dev | Backend | FIXED — persistent file storage |
| TB-015 | FLAG_SECURE toggle not locked on Free tier | HIGH | Tester | Free | FIXED — disabled + PRO label |
| TB-016 | FLAG_SECURE ignores toggle state on Pro | HIGH | Tester | Pro | FIXED — tier-based logic |
| TB-017 | Update button opens stealthx.tech instead of Play Store | MEDIUM | Tester | All | FIXED — always Play Store |
| TB-018 | App disconnects on network switch (WiFi→Mobile→eSIM) | HIGH | G........e | X Cover 7, A16 | OPEN |
| TB-019 | Incoming calls not received when app closed | CRITICAL | G........e | A17, Xiaomi Redmi 15C | OPEN |
| TB-020 | Call drops immediately after connecting | CRITICAL | G........e | Huawei P30, Motorola Edge 60 | OPEN |
| TB-021 | Ad banner visible during active call | HIGH | G........e | X Cover 7 | OPEN |
| TB-022 | Contact shows phone number instead of phonebook name | MEDIUM | G........e | A16, A17 | OPEN |
| TB-023 | All Settings sections expanded | LOW | G........e | All | OPEN |
| TB-024 | No disconnect button next to connection status | MEDIUM | G........e | All | OPEN |
| TB-025 | Label "Anonymous Network" instead of "Network" | LOW | G........e | All | OPEN |
| TB-026 | "New Call" button visible in all tabs | MEDIUM | G........e | All | OPEN |
| TB-027 | "Report a Bug" does nothing / opens wrong page | MEDIUM | G........e | All | OPEN |
| TB-028 | "Check for Updates" opens Play Store on APK install | MEDIUM | G........e | Xiaomi Redmi 15C | OPEN |
| TB-029 | IFR Token section not at the bottom | LOW | G........e | All | OPEN |
| TB-030 | Emergency Delete not at the top | LOW | G........e | All | OPEN |
| TB-031 | eSIM status stays "connected" after network switch | MEDIUM | G........e | Samsung A16 | OPEN |
| TB-032 | No diagnostic export (SecLog CSV) | LOW | G........e | All | OPEN |
| TB-033 | Random disconnects on network switch | HIGH | G........e | Huawei P30, Motorola Edge 60 | OPEN |
| TB-034 | Phone number normalization broken (+49/0049) | HIGH | G........e | All | OPEN |

## Beta Testers (anonymized)

| # | Tester | Status |
|---|--------|--------|
| 1 | B................1@gmail.com | Invited |
| 2 | H................1@gmail.com | Invited |
| 3 | a..............s@gmail.com | Invited |
| 4 | d................3@gmail.com | Invited |
| 5 | e................2@gmail.com | Invited |
| 6 | g..............i@gmail.com | Invited |
| 7 | l............a@gmail.com | Invited |
| 8 | n......n@gmail.com | Invited |
| 9 | p............a@gmail.com | Invited |
| 10 | r......s@gmail.com | Invited |
| 11 | z......r@gmail.com | Invited |
| 12 | u..........m@gmail.com | Invited |
| 13 | r..........1@gmail.com | Invited |
| 14 | b..........r@googlemail.com | Invited |
| 15 | c...........4@gmail.com | Invited |

**Total testers: 15/15**

## How to report
Testers report bugs via email to kaspartisan@proton.me or via GitHub Issues.
Format: Device, Android version, steps to reproduce, screenshot if possible.
