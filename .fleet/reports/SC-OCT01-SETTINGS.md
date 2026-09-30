id: SC-OCT01-SETTINGS
status: partial
worker: claude
branch: agent/claude/SC-OCT01-SETTINGS
summary: Module Settings, MAP hop 6 (SettingsFragment -> preferences.xml -> CollapsiblePreferenceCategory).
  Touch target: CollapsiblePreferenceCategory.onBindViewHolder sets itemView.minimumHeight to the existing
  @dimen/touch_target_min (48dp). Covers all collapsible headers, including the Premium VPN category (same class).
  Locale: DE strings added for settings_advanced/anti_recording/privacy_calls/anon_network. The hardcoded section titles
  Custom Call ID/Support Development/Diagnostics now use new EN+DE resources. No behavior, key or logic change.
files: client_android/app/src/main/java/com/securecall/app/ui/CollapsiblePreferenceCategory.kt,
  client_android/app/src/main/res/xml/preferences.xml, client_android/app/src/main/res/values/strings.xml,
  client_android/app/src/main/res/values-de/strings.xml, BRIDGE.md, .fleet/reports/SC-OCT01-SETTINGS.md
tests: df -> 4.2-4.5 GiB free (< 15 GiB gate): no Gradle, no APK, no unit/instrumentation suite run. No ADB (per brief).
  xmllint --noout on preferences.xml + both strings.xml -> OK.
  Python resource check: 11/11 preferences.xml section titles are @string refs, present in values + values-de, no duplicate names -> PASS.
  kotlinc CollapsiblePreferenceCategory.kt vs android-36 android.jar + preference 1.2.1 + recyclerview (stub R) -> compiles.
  Visual gate (frontend-visual-release-gate): NOT RUN. No new APK, FLAG_SECURE blocks screenshots, and the web viewports do not apply.
risks: Physical/visual revalidation pending. Unverified: header title vertical position inside the taller row
  (the category layout may keep the title near the top instead of centering it). Full aapt/lint merge not run.
  The DE copy ("Aufnahmeschutz", "Eigene Anruf-ID", "Entwicklung unterstuetzen", "Diagnose") has not had a product/copy review.
  Child preference titles hardcoded in EN (e.g. "Battery Optimization", "Check for Updates") stay unchanged: out of scope.
security: none. No FLAG_SECURE, permission, network or entitlement change. No secrets were touched.
next: With >=15 GiB free: ./gradlew :app:testFreeDebugUnitTest :app:testPremiumDebugUnitTest :app:lintFreeDebug, then build devtest APKs.
  Device cases: (1) Tab S4 Premium, DE locale: uiautomator dump of Settings, all 10+ section headers >=48dp high, labels all DE.
  (2) Same with the EN locale: all labels EN. (3) Expand/collapse each header: toggle still works and the arrow prefix is kept.
  (4) A21s Free (unlocked): the same hierarchy check. (5) Visual check of title alignment in the 48dp row (owner screenshot with FLAG_SECURE temporarily off only in devtest, if allowed).
