# SecureCall Ecosystem – Security Design Document

## 1. Zweck dieses Dokuments

Dieses Dokument beschreibt alle sicherheitsrelevanten Prinzipien,
Mechanismen, Architekturentscheidungen und Anforderungen des SecureCall Ecosystems.
Es dient als Master-Referenz für Entwickler, Auditoren und Architekten.

## 2. Sicherheitsziele (High-Level)

1. **Ende-zu-Ende Verschlüsselung**
   Kein Audio darf jemals unverschlüsselt das Gerät verlassen.

2. **Minimierung von Metadaten**
   Das System generiert so wenige Metadaten wie technisch möglich.
   Keine Logs, kein Tracking, keine Telemetrie.

3. **Zero-Retention Prinzip**
   Kryptografische Schlüssel werden so kurz wie möglich gehalten,
   niemals persistiert (außer Identity-Key unter sicheren Bedingungen).

4. **Defense in Depth**
   Mehrere Schichten schützen unabhängig voneinander:
   - Crypto
   - GhostNet Transport
   - Signaling
   - Security Monitor
   - OS-Härtung (Premium/OS)

5. **Fail-Safe Default**
   Bei Unsicherheit → blockieren, niemals schwach weiterlaufen.

6. **Transparenz & Auditierbarkeit**
   Code soll testbar, reproduzierbar und auditierbar sein.


3. Bedrohungsmodell

Das SecureCall Ecosystem berücksichtigt mehrere Angreiferklassen:

Lokale Angreifer auf dem Endgerät

Netzwerknahe Angreifer im Transportpfad

Infrastrukturnahe Angreifer auf Server-/Relay-Ebene

Funkzellen-/Baseband-Angreifer (IMSI-Catcher etc.)

OS-/Firmware-basierte Angriffe (insbesondere für GHOSTOS relevant)

3.1 Lokale Angreifer

Ziele:

Zugriff auf Klartext-Audio

Zugriff auf Schlüsselmaterial

Manipulation der App (Hooking, Code Injection)

Typische Werkzeuge:

Malware, Keylogger

Rootkits, Magisk-Module

Frida, Xposed, Emulatoren

Screen-Recording und UI-Capture

Abwehrmechanismen:

Security Monitor & Root-/Hooking-Erkennung

Verhindern von Screen-Recording

minimale Angriffsfläche in der UI

kein Debug-Build in Produktion

3.2 Netzwerknahe Angreifer

Ziele:

Mitschnitt von Audio

Man-in-the-Middle-Angriffe

Manipulation oder Replay von Paketen

Typische Szenarien:

bösartige WLANs

kompromittierte Router

staatliche/Carrier-basierte Überwachung

Abwehrmechanismen:

Ende-zu-Ende-Verschlüsselung

Integritätsschutz der Frames

keine Schlüssel oder Klartext im Backend

optional Multi-Hop-GhostNet-Routing

3.3 Infrastrukturnahe Angreifer

Ziele:

Kompromittierung von Signaling-Servern und Relays

Massenüberwachung von Metadaten

Blockieren oder Umleiten von Verbindungen

Abwehrmechanismen:

minimalistische Signaling-Logik

keine langfristigen Logs

Public-Key Directory ohne Klarnamen

Relays ohne Zugriff auf Schlüssel/Klartext

3.4 Funkzellen- und Baseband-Angreifer

Ziele:

Erkennen, wer mit wem kommuniziert

Erzwingen unsicherer Funkmodi

IMSI-/IMEI-Erfassung und Bewegungsprofile

Abwehrmechanismen (Premium/OS):

IMSI-Catcher-Detection (Anomalie-Erkennung)

Warnungen bei auffälligen Cell-IDs

eingeschränkte Funkprofile (z. B. LTE-only)

optional komplette Deaktivierung klassischer GSM-Telefonie


4. Krypto-Design & Schlüsselmanagement

Das SecureCall Ecosystem verwendet moderne, auditierbare Kryptographiemodule.
Die Core Crypto Engine (Rust) stellt alle Sicherheitsprimitive bereit.

4.1 Identitätsschlüssel (Identity Keys)

Typ: Ed25519 (Signatur) + X25519 (Schlüsselaustausch)

Erzeugung: lokal auf dem Gerät

Speicherung:

Free/Pro: verschlüsselt im App-Speicher

Premium/OS: im Hardware-TPM / Secure Element, falls verfügbar

Export: verboten

Backup: nicht vorgesehen (Sicherheitsrisiko)

4.2 Session-Setup

Jeder Call verwendet eine neue Session:

Start über X25519 Key Agreement

Ableitung eines einmaligen Root Keys

Etablierung eines Double-Ratchet oder Noise_NX / Noise_XX Protokolls

automatische Rotation der Schlüssel (Forward Secrecy)

Ein kompromittierter Session-Key darf:

keine vorherigen Audioframes entschlüsseln lassen (FS)

keine zukünftigen Frames gefährden (PCS)

4.3 Audioframe-Verschlüsselung

Für Audioframes wird ein AEAD-Verfahren eingesetzt:

XChaCha20-Poly1305

192-bit Nonce

256-bit Key

Nonce-Inkrement pro Frame (Overflow niemals erlaubt)

garantierter Integritätsschutz

4.4 Schlüssel-Lebenszyklen

Identity Keys → langfristig (1 Identität pro Gerät)

Session Keys → nur für einen Call gültig

Frame Keys → rotieren kontinuierlich

Keys werden mit zeroize/explicit_bzero aus dem RAM gelöscht

4.5 Zero-Retention Prinzip

Die App speichert niemals:

Session Keys

Audioframes

Metadaten der Verbindung

Netzwerkstatistiken (ausgenommen technische Metriken ohne Identität)

Nach Call-Ende:

alle Schlüssel werden überschrieben

alle Call-Daten werden verworfen

Speicher wird purged

4.6 Kryptographische Randbedingungen

alle Operationen constant-time

kein Fallback auf unsichere Modi

keine Nutzung hardwarebeschleunigter Funktionen, die Side-Channel-Leaks produzieren könnten

keine externe Abhängigkeit von Cloud-Diensten


5. Security Monitor & Hardening Layer

Der Security Monitor ist eine lokale Sicherheitskomponente, die den Gerätestatus
und die Laufzeitumgebung überwacht. Ziel ist es, Manipulationen und unsichere
Zustände zu erkennen und angemessen zu reagieren.

5.1 Root- und Jailbreak-Erkennung

Erkennung von Magisk, SuperSU, KingRoot

Prüfung auf manipulierte /system-Partitionen

Erkennung verdächtiger Binaries (su, busybox in atypischen Pfaden)

Prüfung auf Schreibzugriff in geschützten Bereichen

Policy:

Free: Warnung

Pro: optional Blockade

Premium/OS: Blockade verpflichtend

5.2 Hooking- und Debugger-Schutz

Erkennung von Frida-Servern & Frida-Gadgets

Erkennung von Xposed / LSPosed

Erkennung von aktiven ptrace/debugging-Sessions

Anti-Breakpoint-Checks (Timing, Debugger-Flags)

Schutz vor Code-Injection durch Prüfungen der App-Signatur

Reaktion:

Sitzung abbrechen

Warnmeldung

gesperrter Zustand bis Neustart

5.3 Emulator-Erkennung

Prüfung auf typische Emulator-Properties (ro.product.device, Google API Level Images)

Erkennung von fehlenden Sensoren

CPU-Architektur-Inkonsistenzen

ungewöhnliche Netzwerkkonfigurationen (10.0.2.x etc.)

Reaktion:

Free: Warnung

Pro: optional Block

Premium/OS: Block

5.4 Screen-Recording Detection

Überwachung „FLAG_SECURE“

Erkennung aktiver Screen-Capture-APIs

Erkennung von Hintergrund-Recording-Apps

automatische Sperrung des UI bei aktiver Aufnahme

5.5 Baseband- & Funkzellen-Checks (Premium/OS)

Erkennung von IMSI-Catchern über:

auffällige LAC/CID-Wechsel

ungewöhnlich starke Signalpegel

unverschlüsselte 2G-Downswitches

fehlende Netz-Authentifizierung

Warnung bei verdächtigen Basisstationen

Optionale automatische Deaktivierung des GSM-Moduls

5.6 Gerätestatus-Monitoring

USB-Debugging aktiviert? → Warnung/Blockade

Entwickleroptionen aktiv? → Warnung/Blockade

Screenshot-APIs aktiv? → Blockierung

externe Tastaturen/Controller → prüfen (Keylogging-Risiko)

5.7 Policy-basierte Reaktionen

Der Security Monitor meldet einen Zustand, die Policy Engine entscheidet, wie reagiert wird:

zulassen

warnen

blockieren

Call gar nicht starten

App beenden

Gerät isolieren (Premium/OS)


6. GhostNet Transport Layer

Der GhostNet Layer ist der verschlüsselte Transportkanal des SecureCall Ecosystems.
Er stellt sicher, dass Audioframes das Gerät ausschließlich verschlüsselt verlassen.

6.1 Architektur

GhostNet besteht aus drei Unterschichten:

Transport (WebRTC/QUIC)

Audio Engine (Opus)

Routing (Single-Hop / Multi-Hop)

6.2 Transport (WebRTC / QUIC)

GhostNet nutzt je nach Gerät und Produktlinie:

WebRTC mit DTLS für maximale Kompatibilität

oder QUIC mit eingebettetem AEAD Layer für Premium/OS

Eigenschaften:

NAT-Traversal via STUN/TURN

Congestion Control

Paketpriorisierung (Audio > Metadata)

Reconnect-Mechanismus bei Netzschwankungen

6.3 Audio Engine

Codec: Opus

Bitrate adaptiv (12–32 kbps)

geringstmögliche Latenz (20–40 ms)

FEC (Forward Error Correction) aktiviert

Jitter Buffer mit dynamischer Größe

6.4 Single-Hop GhostNet (Free/Pro)

P2P Verbindung oder Relay via TURN

Metadaten minimal gehalten

kein Zugriff der Relays auf Audio (alles verschlüsselt)

6.5 Multi-Hop Routing (Premium/OS)

Premium und OS unterstützen Multi-Hop GhostNet Routing:

Beispielkette:

Client A → Relay 1 → Relay 7 → Relay 3 → Client B

Jeder Hop sieht nur:

IP des vorherigen Relays

IP des nächsten Relays

KEINE Audioinhalte (E2E verschlüsselt)

Ziele:

Verschleierung des Ursprungs

Erschwerung von Traffic-Korrelation

Schutz vor staatlichen Überwachungsmaßnahmen

6.6 SilentCarrier Mode

Für Free/Pro/Premium verfügbar (OS optional deaktiviert):

Die App initiiert einen normalen GSM-Anruf

Mikrofonzugriff auf GSM wird geblockt

GSM-Call transportiert kein Audio

Der echte Audio-Call läuft über GhostNet

Ergebnis: äußerlich wirkt es wie ein normaler Telefonanruf

6.7 Transport-Sicherheitsregeln

kein Klartext-Audio außerhalb der App

kein Fallback auf unverschlüsselte Transportmodi

bei Transportfehlern → Call abbrechen, niemals auf „unsicher“ weiterlaufen

Frame-Limits & Nonce-Überwachung implementieren


7. Signaling, Identity & Key Directory

Das Signaling-System dient ausschließlich dazu,
zwei Geräte miteinander zu verbinden.
Es kennt keine Nutzerdaten und speichert keine Verbindungsmetadaten.

7.1 Identitätsmodell

Jedes Gerät besitzt:

Identity Key Pair (Ed25519/X25519)

erzeugt lokal

niemals exportiert

niemals auf Backend gespeichert

Optional: Stealth-Identitäten (Premium/OS)

mehrere Identitäten pro Gerät

rotierend

ideal für verdeckte Kommunikation

Das Backend kennt nur Public Keys, niemals Klarnamen oder Telefonnummern.

7.2 Registrierung (pseudonym)

Ein Client registriert sich wie folgt:

sendet Public Key

erhält eine zufällige, bedeutungslose User-ID

keine IP-Logs (nur ephemeral)

keine Zeitstempelpersistenz

Die Registrierung dient nur dazu:

erreichbar zu sein

den Public Key abrufbar zu machen

7.3 Signaling-Fluss

Der Signaling Server führt folgende Aufgaben aus:

Call-Invite an Ziel senden

Call-Antwort (Accept/Reject)

Austausch der ICE-Kandidaten

Meldung „Call beendet“

Wichtig:
Der Server kennt nie die Identität der Teilnehmer außerhalb eines Public Keys.

7.4 Transportmechanismen

REST für Registrierung / Public Key Lookup

WebSocket für Events:

invite

answer

candidate

end-call

Der Server speichert keine State-Daten länger als technisch notwendig.

7.5 Key Directory

Das Key Directory erlaubt:

Public Key von User X abrufen

Identität bestehend aus:

random user_id

public_key

Das Key Directory speichert nicht:

IP-Adressen

Zeitstempel

Kommunikationspartner

Geräteinformationen

7.6 Sicherheit des Signaling

Signaling selbst wird nicht vertraut.

Daher:

alle Inhalte werden signiert

identitätsbasierte Prüfung über Ed25519

Replay-Schutz

keine sensiblen Inhalte über Signaling senden


8. Policy Engine & Produktlinien-Profile

Die Policy Engine ist die zentrale Steuerkomponente des SecureCall Ecosystems.
Sie definiert, wie das System auf Risiken, Konfigurationen und Netzbedingungen reagiert.

Jede Produktlinie erhält ein eigenes Policy-Profil:

Free (GhostTalk Basic)

Pro (GhostShield Advanced)

Premium (PhantomLine Elite)

OS (GHOSTOS BlackRoot)

8.1 Policy-Struktur

Eine Policy besteht aus:

Netzanforderungen

Geräteanforderungen

Root-/Debugverhalten

erlaubte Transportmodi

zulässige UI-Funktionen

Aktivierung von Sicherheitsfeatures

Reaktionen bei Risiken

Beispiel (vereinfacht):

{
  "require_secure_wlan": true,
  "allow_mobile_data": false,
  "allow_rooted_device": false,
  "require_vpn": false,
  "enable_imsi_detection": false,
  "max_ghostnet_hops": 1
}

8.2 Free Policy – GhostTalk Basic

WLAN empfohlen, aber nicht erzwungen

Root-Geräte erlaubt → nur Warnung

keine VPN-Firewall

GhostNet: Single-Hop

SilentCarrier Mode verfügbar

keine IMSI-Detection

minimale Härtung

darf parallel mit anderen Apps laufen

8.3 Pro Policy – GhostShield Advanced

WLAN sicher → bevorzugt

Mobile Daten erlaubt

Root optional blockierbar

VPN-Firewall aktiv (App-lokales VPN)

GhostNet: Single-Hop, optimiert

SilentCarrier Mode aktiv

stärkere Antimanipulation

Zero-Retention Key Management erzwungen

Anti-Debug / Anti-Emulator verpflichtend

8.4 Premium Policy – PhantomLine Elite

WLAN oder „dediziertes Mobilnetzprofil“

kein Betrieb auf Root-Geräten

Device-Owner Modus Pflicht

App-Whitelist aktiv

GhostNet: Multi-Hop

IMSI-Catcher Detection aktiv

Stealth-UI verfügbar

alle Hintergrundprozesse blockiert

Funkprofile eingeschränkt (LTE-only möglich)

SilentCarrier Mode aktiv

8.5 OS Policy – GHOSTOS BlackRoot

vollständige Kontrolle über das Gerät

kein Root möglich (gehärteter Kernel)

alle Fremd-Apps blockiert

keine Browser/WebView-Komponenten

Kernel-Level Firewall

GhostNet Multi-Hop tief im System integriert

Mikrofon/Kamera exklusiv für SecureCall

strikteste Funkprofilkontrolle

keine Hintergrundprozesse

RAM-Purging nach jeder Session

8.6 Policy Engine Auswertung

Die Policy Engine gibt Entscheidungen zurück:

ALLOW (Vorgang unkritisch)

WARN (Benutzer muss Risiko bestätigen)

BLOCK (Vorgang wird abgebrochen)

HARD_BLOCK (App/OS beendet Vorgang sofort)

Diese Entscheidungen werden vom Security Monitor umgesetzt
und gelten global in der App.


9. OS-Level Security – GHOSTOS BlackRoot

GHOSTOS BlackRoot ist ein gehärtetes Spezialbetriebssystem auf Android-Basis
für militärische und hochkritische Einsätze.
Es folgt dem Prinzip „Maximum Security – Minimum Surface“.

9.1 Kernprinzipien

keine Fremd-Apps

kein Browser, kein WebView

keine Google-Services

keine Hintergrundprozesse

keine Telemetrie

Mikrofon & Kamera exklusiv für SecureCall

RAM-Purging nach jeder Sitzung

systemweite Firewall

reproduzierbare Builds

keinerlei Debug-Schnittstellen

9.2 Kernel-Hardening

Der Kernel wird modifiziert und gehärtet:

deaktivierte Debug-Funktionen

gehärtete SELinux-Policies (enforcing)

deaktiviertes ptrace

restriktive Syscall-Whitelists

restriktive seccomp-Konfiguration

restriktive cgroups für Ressourcenmanagement

deaktivierter ungenutzter Kernel-Code

9.3 System-Level Einschränkungen

kein Play Store

kein Package Installer

keine Drittanbieter-Apps

keine Hintergrunddienste außer SecureCall

keine Synchronisationsdienste

kein Filesystem-Zugriff für User-Apps

9.4 Sichere Systempartition

read-only Systempartition

Verified Boot aktiv

kein Bootloader-Unlock möglich

Recovery nur mit Signatur

kein ADB, kein Fastboot (außer im Manufacturing Mode)

9.5 Netzwerk- und Funkprofilkontrolle

systemweite Firewall (iptables/nftables)

erlaubte Verbindungen:

SecureCall Signaling

GhostNet Relays

alle anderen IP-Verbindungen blockiert

Funkprofile:

LTE-only möglich

2G/GSM deaktivierbar

automatische Blockade bei verdächtigen Cell-IDs

9.6 Ressourcen-Isolation

Mikrofon und Kamera exklusiv durch SecureCall-App

keinerlei API-Zugriff für andere Prozesse

keine Screenshot- oder Screen-Recording-Funktion im OS

restriktiver Zugriff auf Sensoren (Accelerometer, Gyro optional deaktiviert)

9.7 RAM Purge & Geheimnisverwaltung

Nach jedem Call:

komplette Speicherbäume werden geleert

Zeroize für kryptografische Strukturen

Beenden aller Dienste

Neustart optional erzwingbar (Enterprise Option)

9.8 Device-Owner Mode

SecureCall ist System-App + Device-Owner

keine Deinstallation möglich

keine App-Hinzufügungen möglich

Policy durchsetzt systemweite Regeln

9.9 Build-Chain & Transparenz

deterministische Builds

reproducible build scripts

Abgleich von Hashes für OTA-Updates

signierte Systemimages

interner Audit-Prozess


10. Integration, API-Grenzen & Sicherheitsarchitektur

Dieses Kapitel beschreibt die Interaktion der Module und definiert,
welche Sicherheitsgarantien jede Schicht gibt und wo explizite Grenzen liegen.

10.1 API-Grenzen & Verantwortlichkeiten

Jede Komponente hat strikt definierte Zuständigkeiten:

Crypto Engine (Rust)

erzeugt & verwaltet Schlüssel

verschlüsselt & entschlüsselt Audioframes

führt zeroize durch

hat keinen Netzwerkzugriff

Android Client

bildet UI, Call-Control, Fehlerhandling

setzt Policies um

kommuniziert mit Signaling & GhostNet

speichert keine sensiblen Daten

Signaling Backend

kennt nur Public Keys & temporäre Session-Daten

keine Logs, keine Telemetrie

dient ausschließlich zur Verbindung, nicht zum Routing

GhostNet Relays

leiten ausschließlich verschlüsselte Frames weiter

kennen weder Identität noch Ziel des Nutzers

speichern keine Metadaten

GHOSTOS BlackRoot

erzwingt OS-Policies

verhindert Manipulation

kontrolliert Funkprofile und Ressourcen

10.2 Integrationsgrenzen (Security Boundaries)

Crypto Engine ↔ Android App

Austausch nur verschlüsselter Payloads

App erhält niemals private Schlüssel im Klartext

Kommunikation über FFI ist limitiert & geprüft

Android App ↔ Backend

Backend wird nicht vertraut

Signale werden signiert/verifiziert

GhostNet ↔ Backend

Relays können keine Frame-Inhalte einsehen

nur Weiterleitung, kein Zugriff auf Session Keys

OS ↔ SecureCall App

OS schützt App

App schützt Daten

App kontrolliert Mikrofon/Kamera exklusiv

10.3 Vertrauensmodell

Die Vertrauenskette ist wie folgt definiert:

Crypto Engine
höchste Vertrauensebene

SecureCall App
vertraut Crypto, aber nicht dem OS

OS (BlackRoot)
vertraut App, schützt Hardware & Funk

GhostNet Relays
untrusted

Signaling Backend
untrusted

10.4 Minimale Metadaten

Das System garantiert:

keine Speicherung von IP-Adressen

keine Speicherung von Kommunikationspartnern

keine Persistenz von Zeitstempeln

keine Geräteinformationen

keine Analyse durch Relays

10.5 Fehler-Handling & Fail-Safe

Bei Unsicherheiten gilt:

niemals in unsicheren Zustand wechseln

keine Fallbacks auf unverschlüsselte Kanäle

lieber Call abbrechen als schwach weitermachen

Benutzer informieren (außer OS-Policy verlangt Silent Block)

10.6 Auditierbarkeit

deterministische Builds

nachvollziehbare Release-Chains

interner Code-Review-Prozess

Security-Tests verpflichtend

Premium/OS: externe Audits erforderlich

10.7 Zusammenfassung der Sicherheitsarchitektur

Die Architektur stellt sicher:

Klartext verlässt das Gerät niemals.

Keine Komponente vertraut blind einer anderen.

Metadaten werden minimiert bis zur Grenze der technischen Machbarkeit.

OS-Härtung schützt Hardware, App schützt Kryptografie.

Backend ist austauschbar & untrusted by design.

