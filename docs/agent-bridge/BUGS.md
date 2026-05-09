# Codex Bug Findings

## 2026-05-09 14:00 PDT - Codex

### BUG-026 - eSIM Call Routing + Preferred Network

Status: OPEN, Android-Fix erforderlich. Kein Backend-Fix in `backend/signaling/src/server.js` noetig.

Findings:
- `docs/BUGS.md` beschreibt den Kern korrekt: `bindProcessToNetwork()` und OkHttp `socketFactory` sind fuer paralleles WiFi+Cellular/eSIM nicht verlaesslich genug, weil bestehende OkHttp/WebSocket-Sockets und DNS/Connection-Pool-Zustand wiederverwendet werden koennen.
- Android-Code bestaetigt das: `NetworkManager.bindToPreferredNetwork()` nutzt `requestNetwork()`, `bindProcessToNetwork()` und triggert `WebSocketService.forceReconnect()`. `HeartbeatClient.buildClient()` setzt zwar bei vorhandenem `boundNetwork` `socketFactory` und DNS auf dieses Network, aber das deckt nur neu gebaute OkHttp-Verbindungen ab.
- UI ist bereits ehrlich: `SettingsFragment.configureAnonymousNetwork()` deaktiviert `pref_esim_routing` und sagt "Coming Soon - requires VpnService-based traffic steering".
- `preferred_network_transport` bleibt aktiv, aber die Summary sagt korrekt, dass es nur beim Umschalten wie WiFi off -> Mobile effektiv ist.

Fix-Vorschlag:
1. Kurzfristig so lassen: eSIM-Routing nicht wieder aktivieren, solange nur `bindProcessToNetwork()` genutzt wird.
2. Mittelfristig Android-seitig einen eigenen `VpnService` fuer Traffic Steering bauen, aber nicht mit dem bestehenden WireGuard-VPN-Service vermischen. Android erlaubt nur einen aktiven VPN-Service pro User/Profile; ein zweiter StealthX-`VpnService` wuerde den WireGuard-Tunnel ersetzen.
3. Architektur: ein einziger StealthX-VPN-Orchestrator sollte Modi koennen:
   - WireGuard VPN aktiv: bestehender `GhostVpnService`/GoBackend bleibt alleiniger VPN-Owner.
   - eSIM Steering aktiv ohne WireGuard: eigener TUN-Service routet nur App-Ziele ueber die gewuenschte Network-Schnittstelle.
   - eSIM + WireGuard: nicht als zwei VPNs implementieren. Entweder WireGuard-Endpoint selbst ueber eSIM binden oder Feature-Kombination im UI sperren.
4. Implementierung ohne Breaking Change: bestehende Settings-Keys beibehalten, UI weiter deaktiviert lassen, neue interne Strategy-Schicht hinter `NetworkManager` einfuehren. Erst nach Device-Test auf S10/S7 freischalten.

Risiko:
- VpnService-basiertes Steering ist machbar, aber nicht "kleiner Patch". Es beruehrt Android-TUN-Routing, DNS, Akkuverhalten, Always-on/Kill-Switch und die bestehende WireGuard-Premium-Funktion.

### BUG-029 - Kein Audio nach Connected bei VPN+VPN

Status: FIXED — Commit `30c87fd` (2026-05-09). Manueller Retest auf S10/S7 ausstehend.

Findings:
- Backend stellt ICE-Server in `REGISTERED` bereit (`server.js` sendet `iceServers: ICE_SERVERS`). `ICE_SERVERS` enthaelt STUN plus TURN UDP :80, TURN TCP :80, TURN TCP :443 und TURNS TCP :443, sofern `TURN_USER` und `TURN_PASS` gesetzt sind.
- Android injiziert diese Server aus `REGISTERED` via `IceServerFetcher.injectFromRegistered()` und `WebRtcManager` nutzt sie beim PeerConnection-Start.
- `WebRtcManager` erlaubt `IceTransportsType.ALL`. Dadurch bevorzugt WebRTC bei erfolgreicher Kandidatenpruefung weiterhin host/srflx/UDP-Pfade, bevor Relay/TCP/TLS erzwungen wird.
- Audio laeuft nur ueber WebRTC DataChannel. `WebSocketService.sendBinary()` hat bewusst keinen WebSocket-Relay-Fallback. Wenn der DataChannel bei VPN+VPN nicht oeffnet oder nur scheinbar connected ist, gibt es folgerichtig keinen Audio-Fallback.
- Bestehender `GhostVpnService` routet per `ifaceBuilder.includeApplication(getPackageName())` die gesamte App durch WireGuard. Wenn beide Endgeraete diese App-VPNs nutzen, koennen UDP ICE und/oder Hairpin/MTU/NAT-Kombinationen scheitern, obwohl Signaling ueber WebSocket funktioniert.

Backend-Bewertung:
- Signaling-Weiterleitung fuer `WEBRTC_OFFER`, `WEBRTC_ANSWER` und `ICE_CANDIDATE` ist plausibel korrekt.
- Docker/Railway muss sicherstellen, dass `TURN_USER`/`TURN_PASS` gesetzt sind. Ohne diese Variablen liefert der Server nur STUN; bei VPN+VPN ist das fuer Audio sehr wahrscheinlich unzureichend.

Fix-Vorschlag:
1. Konfigurierbaren Relay-Modus einfuehren: Wenn StealthX-VPN aktiv ist oder ICE mehrfach fehlschlaegt, `WebRtcManager` mit `PeerConnection.IceTransportsType.RELAY` starten. Das erzwingt TURN statt host/srflx.
2. ICE-Server-Reihenfolge fuer VPN-Fallback anpassen: in diesem Modus `turns:...:443?transport=tcp` und `turn:...:443?transport=tcp` bevorzugen, UDP optional erst danach.
3. Fallback-Flow: Bei ICE `FAILED` oder DataChannel nicht `OPEN` nach Timeout den PeerConnection-Aufbau einmal mit Relay-only/TCP-443 neu starten, ohne den gesamten Call sofort zu beenden.
4. Diagnostics ergaenzen: in SecLog aktive Kandidaten mit Typ/Protokoll loggen, nicht nur IDs. Ziel: im Tester-Log klar sehen, ob `relay/tcp` genutzt wird oder host/srflx haengen bleibt.
5. Optional spaeter: eigener Medien-Relay-Fallback ueber WebSocket ist nur Notfalloption, weil Latenz und Serverlast steigen. Aktuell ist er bewusst entfernt.

### Dockerfile Review

Status: PASS.

Findings:
- `backend/signaling/Dockerfile` enthaelt `COPY data/ ./data/`.
- `backend/signaling/data/` existiert und enthaelt Seed-Dateien (`activation_codes.json`, `wallets.json`).
- `RUN chown -R securecall:securecall /app/data` macht die kopierten Seed-Daten fuer den Non-Root-User schreibbar.
- Railway-kompatibel, sofern Railway Root Directory auf `backend/signaling` steht oder der Service dieses Dockerfile direkt verwendet. Bei Dockerfile-Build mit diesem Context ist `COPY data/ ./data/` korrekt.

Empfehlung:
- Keine Dockerfile-Aenderung noetig.
- In Railway weiterhin Volume `/app/data` nutzen; beim gemounteten Volume ueberdeckt Railway die Image-Seed-Dateien zur Laufzeit, was fuer persistente Codes/Wallets gewollt ist.
