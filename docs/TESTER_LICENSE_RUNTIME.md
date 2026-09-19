# Tester License Runtime

Status: local implementation candidate, NOT release-ready or activated. No codes
or recipient records belong in this repository. Community communications remain
with the community coordinator; this document is not a delivery authorization.

## Separation and defaults

- Direct Premium only. Play Free and Direct Pro cannot enable tester licensing.
- The commercial activation and billing switches remain false. A separate opt-in
  `testerLicensesEnabled` Gradle property requires internal flavors and an explicit
  raw public Ed25519 verifier via `testerEntitlementPublicKey`.
- The server is disabled unless `SECURECALL_TESTER_LICENSE_ENABLED` is exactly
  `true`, with valid operator-provisioned `SECURECALL_TESTER_SIGNER_FILE` and
  `SECURECALL_TESTER_REGISTRY_FILE`. No configuration is set by this source change.
- Misconfiguration disables tester activation without breaking call signaling.
  Signers, private registry records and raw codes must never enter Git or logs.

## Activation and renewal

Registered sessions use dedicated BEGIN/COMPLETE challenge exchanges. The server
takes the subject from the connection, not a message claim. A P-256 proof from a
previously approved hardware key is required. The client checks challenge domain,
subject, device key and request correlation before signing. It only accepts a
correctly signed `sct1` proof through the regular entitlement store.

An active lifetime grant can issue successive 30-day signed leases. Renewal needs
fresh key possession and rechecks the grant, enrolled key and stored binding.
Old leases can refresh for seven days after expiry but never grant access after
expiry. Offline access is bounded by the current lease; revocation cannot instantly
invalidate an offline proof. After the grace window, same-device code activation
is required. Multiple valid leases do not extend access beyond their signed expiry.

Bindings survive runtime restarts. Lost keys, reinstalls that remove Keystore
entries, identity changes and device transfers must not silently clear bindings.
Recovery requires an independently reviewed owner decision; no automatic transfer
or reset endpoint exists. Local hardware checks alone are NOT remote attestation.

## Operator storage contract

Use existing private owner-only files (0600) in owner-only directories (0700),
outside Git and without symlink ancestors. Permissions do not protect against the
same OS account, privileged access or unprotected backups. Approved protected
storage, full inventory reconciliation and recipient approval remain prerequisites.

Run one registry instance per process and retain a WebSocket on that process for
both challenge steps. Pending challenges are intentionally lost on restart and
must be retried. All writers must use the same locking protocol; do not edit a live
registry concurrently. The registry persists through atomic replacement and fsync.
If a process crashes while holding the lock, activation fails closed. Recovery is
an operator action: first prove all writers are stopped, preserve the registry,
inspect the abandoned lock, and only then approve scoped lock removal. Never
auto-delete a lock by age or during an active writer. No recovery action is run by
this implementation.

## Remaining release gates

1. Implement and validate hardware enrollment against trusted attestation evidence,
   challenge, package and signer. Never accept client-supplied `hardware-verified`.
2. Reconcile the private gift inventory and provision dedicated signer, approved
   enrollment and inactive gift records. The runtime has no public import endpoint.
3. Build and inspect the configured signed Direct Premium APK. Check activation,
   reinstall/recovery, updates, offline expiry and revocation on two real devices.
   Every subsequent Direct Premium update must preserve the approved public
   verifier and tester feature configuration while the gift program is active;
   publishing a default-disabled candidate is not a valid gift-user update.
4. Submit the redacted recipient count, exact artifact identity, test evidence and
   rollback plan for the separately required production/delivery approval.

## Local checks

`npm test` includes the dedicated synthetic registry, protocol and transport suite.
Android billing unit tests include client correlation, challenge-domain rejection,
proof validation, timeout, concurrency, device-loss and all-flavor verifier checks.
Synthetic preapproved keys test registry behavior only, not real enrollment.

The maintained upstream reference for server-side certificate-chain validation
is [Android Key Attestation Verifier](https://github.com/android/keyattestation).
It is not yet integrated here. A local Keystore check must not substitute for
trusted chain, fresh revocation information and application/challenge validation.
