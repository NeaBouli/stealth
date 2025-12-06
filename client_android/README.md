# SecureCall Android Client

Dies ist das Android-Hauptprojekt des SecureCall Ecosystems.

## Ziel von ANDROID-01
Ein Dummy-App-Skelett mit:
- drei Screens (Home, Call, Settings)
- einfache Navigation
- lauffähigem Gradle-Build ohne Funktionalität
- korrekt gesetzten Permissions (RECORD_AUDIO, INTERNET)
- verschlankter, modularer Projektstruktur

Das Projekt wird schrittweise über ANDROID-01, ANDROID-02, ANDROID-03 aufgebaut.


## Android inbound audio debug

The Android inbound audio beep test and the end-to-end media pipeline
(MediaRouterInboundStub → AudioPlaybackStub → AudioTrack) are documented in:

- \`docs/tech/ANDROID_INBOUND_AUDIO_DEBUG.md\`

If your decoder produces 16-bit mono 48 kHz PCM, you can feed it directly into:

```java
byte[] pcm = ...; // decoded audio
com.securecall.app.ghostnet.media.MediaRouterInboundStub.handleDecodedPcm(pcm);
On the maintainer's test device (RF8N313QMFL) the audio will be played immediately,
and logcat will show the MEDIA_ROUTER_INBOUND / AUDIO_PLAYBACK_STUB path.
