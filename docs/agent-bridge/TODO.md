# Stealth TODO

## Priority 1

- [ ] Dependabot-/Security-Warnungen pruefen:
  - GitHub meldete beim Push am 2026-05-03: 6 Vulnerabilities.
  - Schweregrade: 1 critical, 1 high, 2 moderate, 2 low.
  - Naechster Schritt: GitHub Dependabot Details lesen, betroffene Pakete identifizieren, Fix-Risiko gegen Play/APK/F-Droid-Rollout abwaegen.
  - Offene Alerts laut GitHub API:
    - Critical: `protobufjs < 7.5.5`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-xq3m-2v4x-88gg`.
    - High: `path-to-regexp < 0.1.13`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-37ch-88jc-xwx2`.
    - Medium: `uuid < 14.0.0`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-w5hq-g745-h8pq`.
    - Medium: `fast-xml-parser < 5.7.0`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-gh4j-gqv2-49f6`.
    - Low: `rand >= 0.7.0, < 0.8.6`, Manifest `core_crypto/Cargo.lock`, GHSA `GHSA-cq8v-f236-94qc`.
    - Low: `@tootallnate/once < 3.0.1`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-vpq2-c234-7xj6`.
  - Lokaler Fix-Stand:
    - `backend/signaling/package-lock.json` aktualisiert.
    - `core_crypto/Cargo.lock` aktualisiert: `rand 0.8.5 -> 0.8.6`.
    - Kritische und hohe npm-Audit-Treffer lokal beseitigt: `npm audit --audit-level=high` exit `0`.
    - Rust Tests erfolgreich: `cargo test --locked` mit 34 Tests passed.
    - Backend Syntaxcheck erfolgreich: `node --check src/server.js`.
  - Rest-Risiko:
    - `npm audit` meldet weiterhin moderate/low transitive Risiken, hauptsaechlich `uuid`, `firebase-admin`/Google Cloud-Transitives, `resend`/`svix`, `@tootallnate/once`.
    - `npm audit fix --force` wuerde Breaking Changes/Downgrades vorschlagen (`firebase-admin@10.1.0`, `resend@6.1.3`, `uuid@14.0.0`). Nicht automatisch anwenden, weil Backend-/FCM-/Mail-Flows rolloutkritisch sind.

## Done / Monitoring

- [x] Website-Lizenz-/Branding-Texte live pruefen:
  - Website-Texte wurden angepasst.
  - GitHub Pages Deploy erfolgreich.
  - `stealthx.tech` live verifiziert.

## Priority 2

- [ ] README-/Download-Statusdrift pruefen:
  - Play Store Beta, APK, F-Droid und Website-Links sollen auf denselben aktuellen Stand zeigen.

## Priority 3

- [ ] ICE/TURN Endpoint `/ice-servers` separat sicherheitsauditieren.
- [ ] Backend-Monolith `backend/signaling/src/server.js` schrittweise modularisieren.
- [ ] Privacy-Metadaten-Claims gegen FCM/TURN/Signaling Realitaet pruefen.
