# Stealth Agent Instructions

These rules extend `/Users/gio/AGENTS.md` for work inside this repository.

## Coordination

- Read `/Users/gio/BRIDGE.md`, this file, `BRIDGE.md`, and relevant files under `docs/agent-bridge/` before material work.
- Keep `BRIDGE.md` append-only and current after meaningful checks, fixes, releases, or decisions.
- Do not overwrite or clean unrelated dirty files, especially existing `docs/agent-bridge/*` changes.
- Never write secrets, full API keys, passwords, private keys, or unredacted production credentials to code, logs, commits, or bridge files.

## Agent Role Split

The main agent remains responsible for architecture, security, product decisions, release judgment, final verification, commits, pushes, and bridge updates.

Use project subagents when they reduce noise or isolate small work:

- `spark_worker`: small scoped patches, UI corrections, targeted searches, and focused local checks.
- `terra_analyst`: read-heavy repository exploration, log triage, larger code summaries, and test-output analysis.

Delegate only independent or clearly bounded work. Wait for subagent results, review their output yourself, and run the final integration checks in the main thread.

Do not delegate cross-repo release decisions, security-sensitive changes, pricing/tax logic, or unclear requirements without first narrowing the scope.

## Non-Negotiable SecureCall Distribution Split

Treat SecureCall as two separate distribution channels on every future change, build, test,
documentation update and release. Read `docs/DISTRIBUTION_MATRIX.md` before changing Android
flavors, VPN/network code, download links, release automation or store metadata.

1. **Google Play product:** `freeRelease`, package `com.securecall.app.free`, uploaded only as
   `app-free-release.aab`. It must never contain an app-owned `VpnService`, WireGuard dependency,
   native WireGuard library, VPN configuration UI, plugin/downloader or code that installs VPN
   functionality. It may detect and visibly follow a device-managed external VPN. Google Play
   billing or an activation code may unlock Pro/Premium application features inside this package,
   but that entitlement never adds the direct APK's WireGuard runtime.
2. **Direct Premium product:** `premiumRelease` built with `-Pinternal`, package
   `com.securecall.app.premium`, distributed only as the official direct APK. It may contain the
   optional app-managed WireGuard service, Android VPN consent and local provider configuration.
3. **Direct Pro product:** `proRelease` built with `-Pinternal`, package
   `com.securecall.app.pro`. It remains free of built-in VPN/WireGuard code.
4. Never upload a Pro/Premium artifact to the SecureCall Google Play listing. Never replace the
   direct Premium APK with the Play artifact or imply that a Play entitlement converts the Play
   package into the separately signed Premium APK.
5. Every Android release must run the Free/Pro VPN policy guards and Premium runtime guard, inspect
   the final artifacts, and verify website/download copy still explains the distinction.
6. A change is not release-complete until both applicable paths were considered and their real
   tests were recorded in the append-only Bridge. Shared code must be tested against Free and
   Premium; VPN-specific code must remain in `src/premium`.
