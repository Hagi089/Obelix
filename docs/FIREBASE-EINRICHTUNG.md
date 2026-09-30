# Firebase-Projekt einrichten (kostenlos, Region Deutschland)

Dauer: ca. 15 Minuten. Du brauchst ein Google-Konto. **Es wird kein Abrechnungskonto und keine Kreditkarte benötigt** – bitte an keiner Stelle auf den Blaze-Tarif upgraden.

## 1. Projekt anlegen
1. <https://console.firebase.google.com> öffnen → **Projekt erstellen**.
2. Name: `obelix` (oder frei wählbar).
3. **Google Analytics: deaktivieren** (Anforderung: kein Tracking).
4. Projekt erstellen. Oben links muss als Tarif **Spark** stehen.

## 2. Anmeldung (Authentication)
1. Menü **Build → Authentication → Jetzt starten**.
2. Reiter **Anmeldemethode** → **E-Mail/Passwort** → aktivieren (nur den ersten Schalter; „E-Mail-Link" aus lassen) → Speichern.
3. Reiter **Vorlagen** → Sprache der Vorlagen (Stiftsymbol unten) → **Deutsch**. Damit kommt die Mail „Passwort zurücksetzen" auf Deutsch.

## 3. Datenbank (Cloud Firestore) – Region Deutschland
1. Menü **Build → Firestore Database → Datenbank erstellen**.
2. Edition: **Standard**, Datenbank-ID `(default)` lassen.
3. Standort: **`europe-west3` (Frankfurt)**. ⚠ Der Standort kann später **nicht mehr geändert** werden.
4. Modus: **Produktionsmodus** (alles gesperrt). Die richtigen Sicherheitsregeln liefere ich in Phase 3.

**Nicht** einrichten: Storage, Functions, Hosting, Analytics, Crashlytics.

## 4. Android-App registrieren
1. Projektübersicht → **App hinzufügen → Android**.
2. Android-Paketname: **`de.hagi089.obelix`** (muss exakt stimmen).
3. App-Nickname: `OBELIX`. SHA-1: leer lassen (für E-Mail/Passwort nicht nötig).
4. **`google-services.json` herunterladen.** Die weiteren Schritte des Assistenten (Gradle-Einträge) kannst du überspringen – das ist schon im Projekt erledigt.

## 5. google-services.json ablegen
- In deinem lokalen Projekt nach `app/google-services.json` kopieren.
- Die Datei ist per `.gitignore` vom Commit ausgeschlossen und kommt **nicht** ins öffentliche Repository.
- Wenn ich (Claude) die App mit Firebase testen soll, hänge mir die Datei im Chat an. Sie enthält keine Passwörter; der Schutz der Daten erfolgt über die Sicherheitsregeln.

## 6. Kosten absichern
- Solange das Projekt im **Spark**-Tarif ist, kann nichts berechnet werden. Bei Überschreitung der Gratis-Kontingente wird der Dienst bis zum nächsten Tag bzw. Monat begrenzt, nicht berechnet.
- Falls die Konsole irgendwo ein Upgrade vorschlägt: ablehnen und mich fragen.

## Checkliste für die Rückmeldung
- [ ] Tarif Spark
- [ ] E-Mail/Passwort aktiv, Vorlagen auf Deutsch
- [ ] Firestore in `europe-west3`, Produktionsmodus
- [ ] Android-App `de.hagi089.obelix` registriert, `google-services.json` vorhanden
