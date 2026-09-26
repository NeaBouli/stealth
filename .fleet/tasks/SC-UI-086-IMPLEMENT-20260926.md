# SC-UI-086-IMPLEMENT-20260926 — Phone dialer and navigation UI correction

Worker: Claude Code
Mode: implementation

Use architecture-map first. Implement issue #86 only: prevent keyboard/contact-match overlap on
narrow phones, restore the visible `+` affordance on key 0, keep dial targets >=48dp, preserve
bottom-navigation labels or an equally accessible compact representation, and make the Premium VPN
settings section expand/collapse consistently. Preserve tablet behavior and flavor separation.

Follow `frontend-visual-release-gate`: focused tests, responsive screenshots and measured assertions
for 320dp phone and tablet. Use attached S10/Tab S4 only if free; do not disturb another app/test.
No release/deploy. Write the standard report `.fleet/reports/SC-UI-086-IMPLEMENT-20260926.md`.
