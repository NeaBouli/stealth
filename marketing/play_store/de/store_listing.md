# Play Store Listing - SecureCall (Deutsch)

Diese Datei ist eine redaktionelle Ansicht. Die kanonische deutsche Store-Kopie
liegt in `marketing/play_store_de.txt`; Preis-, Sicherheits- und Releaseangaben
muessen vor jeder Einreichung mit diesem Kandidaten und der Distribution Matrix
abgeglichen werden.

## App-Name (max. 30 Zeichen)

```text
SecureCall
```

## Kurzbeschreibung (max. 80 Zeichen)

```text
Ende-zu-Ende-verschluesselte 1:1-Sprachanrufe ueber eine SecureID.
```

## Vollstaendige Beschreibung

```text
SecureCall ermoeglicht Ende-zu-Ende-verschluesselte 1:1-Sprachanrufe ueber eine SecureID. Fuer den SecureID-Anrufpfad ist kein herkoemmliches Benutzerkonto erforderlich.

SICHERHEITSDESIGN
* XChaCha20-Poly1305 schuetzt Anwendungs-Medienframes.
* X25519 und HKDF-SHA256 leiten fuer jeden Anruf separates Schluesselmaterial ab.
* Das Schluesselmaterial wird nach dem Anruf verworfen.
* Die Kryptografie-Engine ist in Rust implementiert.

SecureCall implementiert derzeit kein Double Ratchet, keinen authentifizierten Identitaets-Schluesselaustausch und keine Post-Compromise-Sicherheit. Quellcode, Auditstatus und offene Grenzen sind oeffentlich pruefbar.

ANRUFE
* Opus-Audio-Codec
* WLAN und mobile Daten
* TURN-Relay-Fallback, wenn keine direkte WebRTC-Verbindung moeglich ist
* Benachrichtigungen fuer eingehende Anrufe ueber Firebase Cloud Messaging
* Optionale lokale Kontakte zur Anzeige von Namen

DATENSCHUTZ
* Kein Upload der Kontaktliste
* Keine Speicherung von Anrufinhalten durch den Signalisierungsdienst
* Routing- und Zustellmetadaten werden wie in der Datenschutzrichtlinie beschrieben verarbeitet.
* Die Google-Play-Version enthaelt keinen app-eigenen VPN-Dienst. Sie kann dem aktiven Android-Netzwerk einschliesslich eines extern verwalteten VPNs folgen.

Die aktuelle Android-App enthaelt weder WalletConnect noch eine IFR-Token-Freischaltung. Direkte kostenpflichtige Angebote und der geplante IFR-Halterrabatt werden ausschliesslich ausserhalb der Android-App angeboten und bleiben geschlossen, bis Produkt- und Finanzfreigabe fuer dieselbe Version vorliegen.

Datenschutz: https://stealthx.tech/privacy.html
Sicherheit: https://stealthx.tech/security.html
Quellcode und Auditstatus: https://github.com/NeaBouli/stealth
```

## Release Notes

Die aktuelle kanonische Fassung liegt in
`marketing/play_store/de/release_notes.txt`.
