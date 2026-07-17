# Entitlement signing key provisioning

The SecureCall signaling server signs short-lived paid-product entitlements for
SecureCall, SecureChat and Chameleon. The Ed25519 private key is a runtime
credential. It must never be stored in Git, an Android package, a Bridge file,
logs, screenshots or support messages.

## Offline generation

Run on the private runtime host from `backend/signaling`. Both output paths must
be absolute and outside the repository. The private parent directory must grant
no group or world access, and neither output file may already exist.

```bash
install -d -m 700 /private/runtime/secrets
install -d -m 755 /private/runtime/public
npm run entitlement:keygen -- \
  --private-file=/private/runtime/secrets/entitlement-private.pem \
  --public-file=/private/runtime/public/entitlement-public.txt \
  --json
```

The command performs an in-memory Ed25519 sign/verify proof, writes the PKCS#8
private key with mode `0600`, writes the raw unpadded 32-byte base64url public
key separately, and reports only paths plus a public fingerprint. Existing
files are never overwritten and a partial write removes both newly created
files.

## Runtime and client boundary

- Load the complete private PEM into the server-only
  `ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM` secret through the private deployment
  provider. Do not put it in a shell command, source file or Android build.
- Supply the public file value as
  `STEALTHX_ENTITLEMENT_PUBLIC_KEY_BASE64` to both SecureChat and Chameleon
  release builds. It is public verification material, not a signing secret.
- Compare the generator fingerprint with the public key used by both client
  build pipelines before creating release artifacts.
- Keep sales and provider gates closed while running one synthetic cross-repo
  issue/verify/revoke test. A server without the private key and a client
  without the public key must both fail closed.

## Rotation

Generate a new pair into new filenames. Build and test clients with the new
public key before changing the server signer. Because clients currently trust
one key, rotation is a coordinated release and must not overwrite the active
pair in place. Retain the previous private key only for the approved rollback
window in the private secret store, then destroy it under the operator policy.
