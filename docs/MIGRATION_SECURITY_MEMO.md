# MIGRATION SECURITY MEMO
## Projekt: StealthX / SecureCall
## Datum: 14.04.2026

### Was wurde gefixt
- KRITISCH: Admin-Backdoor "dev-admin-key" aus server.js entfernt
- KRITISCH: TURN-Credentials aus Docs durch Platzhalter ersetzt
- KRITISCH: Git-History bereinigt (BFG) — alte TURN-Credentials komplett entfernt
- .gitignore: .env zum Root hinzugefuegt
- .gitleaks.toml erstellt (Stripe, TURN, API Keys)
- .github/workflows/security-audit.yml erstellt
- TURN-Credential Docs auf Metered.ca Dashboard Referenz aktualisiert

### Bei Migration beachten
- [ ] TURN_USER und TURN_PASS in Railway SOFORT aktualisieren (alte Creds noch aktiv!)
- [ ] Neue TURN-Credentials: f5b688ee5718f674474b72f4 / +bIkbDSs07ne+JFZ
- [ ] Nach Railway-Update verifizieren: curl /ice-servers muss neue Creds zeigen
- [ ] Admin-Backdoor wurde entfernt — neues Deployment auf Railway noetig
- [ ] ADMIN_API_KEY in .env.local muss gesetzt sein (kein Fallback mehr)
- [ ] Stripe Keys nur ueber Railway ENV-Vars, nie im Code

### Benoetigte ENV-Variablen
- NODE_ENV
- PORT
- STUN_URL
- TURN_URL
- TURN_USER
- TURN_PASS
- ADMIN_API_KEY
- STRIPE_SECRET_KEY (falls Payments aktiv)
- STRIPE_WEBHOOK_SECRET
- RESEND_API_KEY (E-Mail)
- BREVO_API_KEY (E-Mail Fallback)
- FIREBASE_SERVICE_ACCOUNT_KEY (Push Notifications)

### Was NIE auf den Server darf
- securecall-release-key.jks (Android Signing Key)
- .env.local (lokale Entwickler-Secrets)
- activation_codes.json
- sxslot-*.json

### Migrations-Reihenfolge
1. Railway ENV-Vars aktualisieren (TURN_USER, TURN_PASS — neue Credentials!)
2. Railway Redeploy abwarten (~2 Min)
3. Verify: curl /health → 200 OK
4. Verify: curl /ice-servers → neue TURN-Credentials
5. Test-Call zwischen zwei Geraeten (TURN Relay verifizieren)
6. Stripe Webhook Endpoint verifizieren
