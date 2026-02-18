# SecureCall Release Keystore Information

## Keystore Details

| Field              | Value                                      |
|--------------------|-------------------------------------------|
| **File**           | `securecall-release-key.jks`              |
| **Alias**          | `securecall`                               |
| **Algorithm**      | RSA 2048-bit                               |
| **Validity**       | 10,000 days (~27 years)                    |
| **CN**             | SecureCall                                 |
| **OU**             | Engineering                                |
| **O**              | StealthX                                   |
| **C**              | DE                                         |

## Generate Keystore

```bash
keytool -genkey -v \
  -keystore securecall-release-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias securecall
```

When prompted, enter:
- **Keystore password**: Use a strong, unique password (min 16 chars)
- **Key password**: Use a different strong password
- **CN**: SecureCall
- **OU**: Engineering
- **O**: StealthX
- **L**: Your city
- **ST**: Your state
- **C**: DE

## Storage

- **NEVER** commit the keystore file to version control
- Store the keystore in a secure location (e.g., encrypted USB drive, password manager)
- Keep a backup in a separate secure location
- The `.gitignore` already excludes `*.jks` and `*.keystore`

## Recovery

If the keystore is lost, you **cannot** update the app on Google Play.
Google Play App Signing can mitigate this — see [Play App Signing](https://developer.android.com/studio/publish/app-signing#app-signing-google-play).

> **WARNING**: Passwords are intentionally NOT stored in this file.
> Store them securely in a password manager or secret vault.
