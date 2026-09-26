# SC-UI-086 implementation report

Status: `ok`
Worker: Claude Code (partial implementation), Codex (integration, correction, tests)
Security: `none`
Risks: physical S10 and Tab S4 confirmation remains open

## Scope

Architecture lane: `MainActivity` shell and bottom navigation -> `DialerFragment` dial pad,
contact suggestions and IME -> Premium `VpnFeature` settings category. The bounded map is in
`docs/architecture/MAP.md`.

## Result

- Bottom navigation always renders all four labels.
- Dial keys retain 48 dp minimum touch targets; the zero key renders an unclipped `+` hint.
- The redundant 96 dp dialer bottom reservation was removed without allowing the call button to
  cross the content boundary.
- `MainActivity` uses `adjustResize`; the fragment keeps a visible-frame fallback for edge-to-edge
  devices. A synthetic contact-result row is asserted fully above the IME.
- The Premium VPN settings section uses the same collapsible category as peer sections.

## Verification

- PASS: `testFreeDebugUnitTest`.
- PASS: Free, Pro and Premium debug Kotlin compilation.
- PASS: Free and Premium debug APK assembly.
- PASS: `lintFreeDebug`.
- PASS: full Free instrumentation suite, 25/25 on API 35 at 320x640 dp.
- PASS: focused UI suite, 3/3 at 320x640 dp.
- PASS: focused UI suite, 3/3 at 720x1280 dp.
- PASS: `git diff --check`.
- Visual evidence: `.fleet/evidence/SC-UI-086/phone-calls-320x640.png` and
  `.fleet/evidence/SC-UI-086/phone-dialer-320x640.png`; labels, ad separation, `+`, dial keys and
  call controls are visible without overlap.

An initial 1600x2560 / 320 dpi profile exceeded the emulator's physical surface and caused two
Espresso root-focus failures; the same tablet assertions passed on the native 1080x1920 surface at
240 dpi (720x1280 dp). This was test infrastructure, not an application failure.

## Remaining gate

Repeat the visible dialer/IME/contact-list and collapsible Premium VPN settings checks on physical
S10 and Tab S4 before a release artifact is declared ready. No release artifact was produced here.
