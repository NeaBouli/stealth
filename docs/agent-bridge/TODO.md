# Stealth TODO

## Priority 1

- [ ] Dependabot-/Security-Warnungen pruefen:
  - GitHub meldete beim Push am 2026-05-03: 6 Vulnerabilities.
  - Schweregrade: 1 critical, 1 high, 2 moderate, 2 low.
  - Naechster Schritt: GitHub Dependabot Details lesen, betroffene Pakete identifizieren, Fix-Risiko gegen Play/APK/F-Droid-Rollout abwaegen.

## Priority 2

- [ ] Website-Lizenz-/Branding-Texte live pruefen:
  - Lokale Texte wurden angepasst.
  - Falls Website aus diesem Repo deployed wird, nach Push/Deploy `stealthx.tech` pruefen.

- [ ] README-/Download-Statusdrift pruefen:
  - Play Store Beta, APK, F-Droid und Website-Links sollen auf denselben aktuellen Stand zeigen.

## Priority 3

- [ ] ICE/TURN Endpoint `/ice-servers` separat sicherheitsauditieren.
- [ ] Backend-Monolith `backend/signaling/src/server.js` schrittweise modularisieren.
- [ ] Privacy-Metadaten-Claims gegen FCM/TURN/Signaling Realitaet pruefen.

