# StealthX Suite Release Open Items - 2026-07-16

This file separates autonomously fixable repository work from external release gates.

## Fixed in this pass

- Public SecureCall copy no longer says the Google Play listing is still pending review.
- SecureCall public copy now references the current 1.0.45 release line instead of old v1.0.41/vC77 text.
- SecureChat and Chameleon public pages no longer present active IFR wallet verification or active Stripe discount checkout while launch gates remain open.
- SecureChat and Chameleon `ifr.html` pages now show a launch-gated status instead of live WalletConnect/Stripe controls.
- SecureChat sitemap refreshed with FAQ, IFR, user manual, protocol, Kaspa, relay, and build pages.
- Chameleon `robots.txt` and `sitemap.xml` added for Google and AI crawler discovery.

## Still open in this repo family

### SecureCall

- Retest S10 -> Tab S4 Free incoming-call accept flow on 1.0.45 to confirm the Free ad pause fix does not interrupt answer flow.
- Resolve S7 network blocker or document it as external if the device still has no validated Internet path.
- Finish the full call matrix: both directions, speaker, mute, end call, background/sleep, reconnect, onboarding, phone confirm, notifications.
- Optional but still recommended before final release clearance: Bluetooth/headset and GSM interruption checks.

### SecureChat

- Run full device/function QA after the public website wording is deployed.
- Build fresh AAB/APK only after device QA is green.
- Keep Android app wallet/IFR-free; IFR discount remains website-side and launch-gated.

### Chameleon

- Run full device/function QA after the public website wording is deployed.
- Build fresh AAB/APK only after device QA is green.
- Keep Android app wallet/IFR-free; IFR discount remains website-side and launch-gated.

## External Gates

- VLABS owns Stripe runtime key rotation, webhook/test checkout, activation email, and the full AADE/myDATA/e-timologio transfer path required for Greek fiscal compliance.
- Google Play Console and Google Search Console actions require console access: submit updated AABs, request indexing, and resubmit sitemaps after deployment.
- Public production sales should remain launch-gated until the VLABS finance path is verified end to end.
