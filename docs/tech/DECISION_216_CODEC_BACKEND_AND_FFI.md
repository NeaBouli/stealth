# DECISION 216 — Codec Backend (Rust vs libopus) & FFI-Plan

## 1. Ziel dieses Dokuments

Dieses Dokument legt fest:

- welchen technischen Weg wir für den Audio-Codec (Opus) einschlagen,
- wie die FFI-Grenze (Kotlin ↔ native) strukturiert wird,
- wie Rust / C / libopus zusammenspielen können,
- und welche Implikationen das für Build, Sicherheit und Wartbarkeit hat.

Es ist eine ARCHITEKTUR-ENTSCHEIDUNG, kein Implementierungs-HowTo.


## 2. Anforderungen an das Codec-Backend

### 2.1 Funktionale Anforderungen

- Dekodierung von Opus-Audioframes in 16-bit PCM (48 kHz, mono).
- Latenzarm (Echtzeit-Telefonie).
- Robust gegen fehlerhafte / korrupte Frames.
- Geeignet für Mobile (Android, später ggf. andere Plattformen).

### 2.2 Nicht-funktionale Anforderungen

- **Sicherheitsfokus**: Kein „unsicheres C-Gefrickel“ im App-Kontext.
- **Minimaler Attack-Surface**: So wenig native Code wie möglich.
- **Wartbarkeit**: Clear Schnittstellen, getestet, isoliert.
- **Portabilität**: Ideal auch außerhalb von Android wiederverwendbar (z. B. Desktop, Server).


## 3. Optionen: Rust vs libopus (pur C) vs Hybrid

### 3.1 Option A – reines libopus (C, direkt via JNI)

**Vorteile:**
- battle-tested, weit verbreitet
- sehr performant
- Beispiele & Referenzimplementierungen existieren

**Nachteile:**
- Memory-Sicherheit liegt vollständig beim Entwickler (C)
- Fehler in JNI/Buffer-Handling → Abstürze / Exploits möglich
- weniger flexible Wiederverwendbarkeit in Nicht-Java-Umgebungen

### 3.2 Option B – Rust-Codec (pure Rust, ohne libopus)

**Vorteile:**
- Memory-sicher (Borrow-Checker)
- Moderne Toolchain, gute Testbarkeit
- Einheitliche Sprache für Crypto + Codec möglich

**Nachteile:**
- pure Rust-Opus-Implementierungen sind nicht so gereift wie libopus
- Entwicklungsaufwand deutlich höher
- Eventuell schlechtere Performance / Kompatibilität zu Referenz-Implementierung

### 3.3 Option C – Hybrid: Rust-Fassade + libopus darunter

**Vorteile:**
- libopus als stabiler, jahrelang getesteter Kern
- Rust als „Safety-Hülle“ um C-Funktionen
- JNI spricht NUR mit Rust → C bleibt hinter sicherer FFI versteckt
- Rust kann zusätzliche Checks / Bounds / Sanitisierung durchführen
- Besserer Kompromiss aus Sicherheit und Reifegrad

**Nachteile:**
- Komplexere Toolchain (Rust + C + NDK)
- Build-System aufwendiger (Gradle + Cargo + CMake)


## 4. Entscheidung

**Wir wählen Option C: Hybrid (Rust-Fassade + libopus-Kern).**

Begründung:

- libopus ist der de-facto Standard für hochwertige, effiziente Opus-Decodierung.
- Rust als Fassade minimiert die Risiken im Umgang mit rohen Pointern / Buffern.
- Die JNI-Schicht wird sehr schmal gehalten und spricht nur mit Rust-Funktionen.
- Diese Architektur passt zur generellen Sicherheits-Philosophie von SecureCall.


## 5. Geplante FFI-Schichten

ASCII-Übersicht:

Kotlin/Java (Android)
    |
    |  JNI (NativeOpus.nativeDecode / nativeInit / nativeRelease)
    v
C++ Wrapper (native-lib.cpp, minimal)
    |
    v
Rust Fassade (crate `securecall_opus`)
    |
    v
libopus (C-Bibliothek)


### 5.1 Kotlin/Java → JNI

- Einstiegspunkt in `NativeOpus.kt`:

  - `nativeInit(sampleRate, channels): Long` → gibt einen "Decoder-Handle" zurück.
  - `nativeDecode(handle, data: ByteArray): ShortArray`
  - `nativeRelease(handle: Long)`

- Diese API bleibt stabil, unabhängig von der dahinterliegenden nativen Implementierung.


### 5.2 JNI → C++ Stub (native-lib.cpp)

- Verantwortlichkeiten:

  - Parametervalidierung (Minimal – robust gegen `null` / length 0).
  - Weiterleitung der Daten/Handles an Rust-Funktionen.
  - Rückgabe von `jshortArray` an die JVM.

- KEINE Opus-Logik direkt im C++.
- KEINE manuellen Pufferberechnungen außer minimaler Bridge-Logik.


### 5.3 C++ → Rust (extern "C" Fn-Signaturen)

Rust-Seite (geplant):

```rust
#[no_mangle]
pub extern "C" fn sc_opus_init(sample_rate: i32, channels: i32) -> i64 {
    // erstellt einen Decoder-Context, verwaltet im Rust-Heap
}

#[no_mangle]
pub extern "C" fn sc_opus_decode(
    handle: i64,
    data_ptr: *const u8,
    data_len: i32,
    out_pcm_ptr: *mut i16,
    out_pcm_capacity: i32,
) -> i32 {
    // dekodiert einen Frame in PCM
}

#[no_mangle]
pub extern "C" fn sc_opus_release(handle: i64) {
    // gibt den Decoder frei
}
C++ ruft diese Funktionen auf, Rust verwaltet den Zustand.

5.4 Rust → libopus (C)
Rust-Crate bindet libopus via FFI:

rust
Code kopieren
extern "C" {
    fn opus_decoder_create(fs: i32, channels: i32, error: *mut i32) -> *mut c_void;
    fn opus_decode(
        st: *mut c_void,
        data: *const u8,
        len: i32,
        pcm: *mut i16,
        frame_size: i32,
        decode_fec: i32,
    ) -> i32;
    fn opus_decoder_destroy(st: *mut c_void);
}
Rust kapselt:

Allocation / Deallocation des opus_decoder.

Fehlercodes (Opus-Returnwerte) in Rust-Result-Typen.

Längenchecks bevor PCM-Puffer beschrieben werden.

6. Sicherheitsüberlegungen
JNI-Schicht bleibt extrem dünn, minimiert Risiko in C++.

Rust übernimmt die zentrale Pufferlogik → Memory Safety.

libopus ist seit Jahren im Einsatz und gut auf Schwachstellen geprüft.

Dekodierung läuft in strikt kontrollierten Buffern:

Max. Framegröße

definierte Samplingrate

kein unkontrolliertes Allokieren im JNI-Umfeld.

7. Auswirkungen auf das Build-System
Android:

Gradle baut weiterhin die App.

CMake verwaltet native-lib.cpp und das Rust-Shared-Objekt.

Rust wird via Cargo gebaut und als statische oder dynamische Library eingebunden.

Repository:

Neues Verzeichnis z. B. native/securecall_opus/ für das Rust-Crate.

CI-Pipeline muss Rust + NDK installieren.

Getrennte Test-Suites:

Rust-Tests (Codec)

Android-Tests (Integration)

8. Roadmap für Implementation (High-Level)
Rust-Crate securecall_opus anlegen.

FFI-Signaturen in Rust definieren (sc_opus_*).

libopus-FFI in Rust implementieren.

C++-Bridge in native-lib.cpp → Aufrufe nach Rust.

JNI-Funktionen mit Rust-Bridge verbinden.

Integration mit NativeOpus.kt (anstatt Stub).

End-to-End Test:

encoded Testframes → ShortArray → AudioTrack.

Fehlerhandling (kaputte Frames, leere Pakete…).

9. Fazit
Codec-Backend-Entscheidung:

Wir nutzen libopus als bewährten Kern,

umgeben von einer Rust-Fassade,

angesteuert via minimaler JNI-C++-Bridge.

Damit bleibt das System:

sicher,

testbar,

erweiterbar,

und kompatibel mit der langfristigen Sicherheitsstrategie von SecureCall.

