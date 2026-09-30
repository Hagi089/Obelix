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
4. Modus: **Produktionsmodus** (alles gesperrt). Die Sicherheitsregeln veröffentlichst du in Abschnitt 7.

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

## 7. Sicherheitsregeln veröffentlichen (Phase 3, nach jeder Änderung an `firebase/firestore.rules` erneut)
Ohne veröffentlichte Regeln bleibt die Datenbank komplett gesperrt, die App zeigt dann „Benutzerdaten konnten nicht geladen werden".
1. Auf GitHub die Datei `firebase/firestore.rules` öffnen und den **kompletten** Inhalt kopieren (Knopf „Copy raw file").
2. Firebase-Konsole → **Firestore Database → Regeln**. Den gesamten Editorinhalt ersetzen (alles markieren, einfügen).
3. **Veröffentlichen**. Die Konsole prüft die Syntax und meldet Fehler in Rot; ohne Fehler ist es aktiv.
4. Optional per Kommandozeile statt Konsole: `firebase deploy --only firestore:rules` im Ordner `firebase/` (braucht die Firebase-CLI und `firebase login`; kein Blaze nötig).

Die Regeln sind im Emulator automatisch getestet (GitHub Actions, Job „rules"). Ob sie in deinem echten Projekt greifen, zeigen die Gerätetests G3-01 bis G3-11.

## 8. Zugangscode anlegen und dich zum Admin machen (einmalig)
Ohne gültigen Zugangscode kann sich niemand registrieren. Den ersten Code legst du **von Hand** an (in der Konsole gelten die Regeln nicht):
1. **Firestore Database → Daten → Sammlung starten** → Sammlungs-ID: `config` → Weiter.
2. **Dokument-ID:** `access`.
3. Felder anlegen:
   - `code` · Typ **string** · Wert: genau **16 Zeichen**, Großbuchstaben A–Z **ohne I, L, O** und Ziffern **2–9** (keine 0, keine 1). Zufällig wählen, z. B. von Claude erzeugen lassen. **Nie ins Repository schreiben.**
   - `updatedBy` · Typ **string** · Wert `konsole`
   - `updatedAt` · Typ **timestamp** · aktuelles Datum
4. Speichern.
5. In der App mit deinem Konto anmelden (oder neu registrieren) und den Code eingeben. Du bist jetzt freigeschaltet, zunächst als Mitglied.
6. **Dich zum Admin machen:** Firestore → Daten → Sammlung `users` → das Dokument mit deinem Namen öffnen → Feld `role` auf `ADMIN` ändern → Speichern. (Die Dokument-ID ist deine Benutzer-ID; du findest sie auch unter Authentication → Benutzer.)
7. In der App die Einstellungen neu öffnen: Dort siehst du jetzt den Zugangscode und die Benutzerliste. Weitere Personen bekommen den Code von dir, später erneuerst du ihn direkt in der App.

## Checkliste für die Rückmeldung
- [ ] Tarif Spark
- [ ] E-Mail/Passwort aktiv, Vorlagen auf Deutsch
- [ ] Firestore in `europe-west3`, Produktionsmodus
- [ ] Android-App `de.hagi089.obelix` registriert, `google-services.json` vorhanden
- [ ] Regeln veröffentlicht (Abschnitt 7)
- [ ] Zugangscode angelegt und eigenes Konto auf ADMIN gesetzt (Abschnitt 8)
