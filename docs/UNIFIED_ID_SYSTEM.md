# StealthX Unified ID System

## Konzept
Eine ID fuer alle StealthX Produkte.
Wer deine sx_ID kennt, kann dich auf allen Kanaelen erreichen.

## Format
sx_[9 Zeichen Base58]
Beispiel: sx_a7Kx9mPq2

## Custom Handle (Pro/Elite)
@username (3-20 Zeichen)
Wird statt der sx_ID angezeigt wo immer moeglich.

## Generierung
- Deterministisch aus Ed25519 Public Key (SHA-256, Base58, 9 Chars)
- Einmalig pro Geraet — beim ersten Start einer StealthX App
- Kein Server noetig — rein lokal
- Gleiche ID in SecureCall und SecureChat

## Cross-App Verwendung
| App | Verwendung der ID |
|-----|-------------------|
| SecureCall | Anrufe empfangen unter sx_ID |
| SecureChat | Nachrichten empfangen unter sx_ID |

## Kontakte
Ein Kontakt mit sx_ID ist automatisch in beiden Apps erreichbar
— sofern er die jeweilige App installiert hat.

## Datenschutz
- ID wird lokal in EncryptedSharedPreferences gespeichert
- Kein zentrales Register
- Kaspa kann optional als oeffentliches Verzeichnis dienen (Phase 2)
