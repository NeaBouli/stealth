# <TASK-ID> – <Kurztitel des Tasks>

## 1. Ziel des Tasks
Kurze, klare Erklärung des technischen Ziels.
Warum existiert der Task? Was soll erreicht werden?

## 2. Ergebnis / Deliverables
- Was wird am Ende erwartet?
- Welche Dateien entstehen?
- Welche Module werden erweitert?

Beispiele:
- neue Klasse in client_android/
- neue Funktion in core_crypto/
- API-Erweiterung im backend/

## 3. Arbeitsschritte
Schrittweise, klar gegliederte Liste:
1. …
2. …
3. …

Jeder Schritt muss nachvollziehbar, testbar und reproduzierbar sein.

## 4. Akzeptanzkriterien
- funktional messbare Kriterien
- Verhalten unter bestimmten Bedingungen
- Performance-Ziele (z. B. Latenz)
- Security-Standards

Beispiel:
- encrypt → decrypt liefert ByteGenauigkeit
- Warnung bei Debug-Mode aktiv

## 5. Tests
Welche Tests sollen laufen?
- Unit Tests
- Integration Tests
- Security Tests
- ggf. Netzwerk-Simulation

## 6. Abhängigkeiten
Welche Tasks/Module müssen vorher fertig sein?

Format:
- CRYPTO-01 muss abgeschlossen sein
- BACKEND-01 Signaling Server muss laufen

## 7. Risiken & Hinweise
- bekannte Stolpersteine
- technische Grenzfälle
- sicherheitskritische Punkte

## 8. Dokumentation
Was muss in die Doku eingepflegt werden?

Beispiele:
- README-Abschnitt erweitern
- new API: docs/backend/api.md
- neue Parameter im Security Modell

