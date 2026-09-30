# OBELIX – Testfälle

Stand: 30.09.2026 · Phase 4 abgenommen (G4 bestanden), UI-Überarbeitung: Gerätetest GU offen · zuletzt geprüfter Commit: siehe Änderungsprotokoll (GitHub-Bau und Regel-Tests grün) · gehört zu [`PROJEKTPLAN.md`](PROJEKTPLAN.md)

Diese Datei wird **nach jeder Phase und nach jedem Testlauf aktualisiert**. Der Plan verweist nur hierher.

**Status:** ✅ bestanden · ⏳ offen, wartet auf den Benutzer (Gerätetest) · ⬜ geplant (Phase noch nicht umgesetzt) · ❌ fehlgeschlagen

**Wer prüft was:** *Automatisch* = läuft bei jedem Push in GitHub Actions, das Ergebnis lese ich dort ab. *Gerät* = kann ich hier nicht testen (kein Gerät, kein Emulator, kein Zugriff auf dein Firebase-Projekt); das musst du auf dem Smartphone prüfen. *Emulator* = Firebase-Emulator für die Sicherheitsregeln (Phase 3), läuft als eigener Job „rules" in GitHub Actions; in der Cloud-Sitzung ist der Emulator nicht ausführbar (npm und Emulator-Download gesperrt).

---

## 1. Aktueller Stand (Übersicht)

| Bereich | Automatisch | Gerät (du) |
|---|---|---|
| Projektbasis (Phase 1) | ✅ Build, Lint | ✅ 3 Fälle |
| Authentifizierung (Phase 2) | ✅ 13 Unit-Tests | ✅ 9 Fälle |
| Benutzer, Rollen, Zugangscode (Phase 3) | ✅ 9 neue Unit-Tests, ✅ 30 Regel-Tests (Emulator) | ✅ 11 von 11 (G3-01 bis G3-11) |
| Finanzen und Excel-Import (Phase 4) | ✅ 27 neue Unit-Tests, ✅ 15 neue Regel-Tests (R-06, R-07) | ✅ 16 von 16 (G4-01 bis G4-16) |
| Alle weiteren Bereiche (ab Phase 5) | ⬜ | ⬜ |

**Automatische Prüfung insgesamt (GitHub, Commit `d870760`, Lauf 36734577761):** `assembleDebug` ✅ · `testDebugUnitTest` ✅ (49 Tests) · `lintDebug` ✅ · Regel-Tests im Emulator ✅ 45/45 (siehe 2.5).

---

## 2. Aktuelle Testfälle

### 2.1 Automatisch geprüft (bestanden)

| ID | Was | Datei | Status |
|---|---|---|---|
| A-01 | Netzwerkfehler (`IOException`) → Meldung „Keine Verbindung zum Server …" | `ErrorMapperTest` | ✅ |
| A-02 | Unbekannter Fehler → allgemeine Fehlermeldung | `ErrorMapperTest` | ✅ |
| A-03 | Firestore-Fehlercodes (keine Berechtigung, nicht gefunden, nicht angemeldet, nicht erreichbar, Zeitüberschreitung, intern) → richtige Meldung | `ErrorMapperTest` | ✅ |
| A-04 | Anmeldefehler: falsche Zugangsdaten, E-Mail schon vergeben, zu viele Versuche, kein Netz | `ErrorMapperTest` | ✅ |
| A-05 | „Zu schwaches Passwort" wird nicht mit „falsche Zugangsdaten" verwechselt | `ErrorMapperTest` | ✅ |
| A-06 | Bereits abgebildeter Fehler behält seine Art | `ErrorMapperTest` | ✅ |
| A-07 | Jede Fehlerart hat eine Meldung | `ErrorMapperTest` | ✅ |
| A-08 | E-Mail leer → „Bitte gib deine E-Mail-Adresse ein." | `AuthValidatorTest` | ✅ |
| A-09 | Ungültige E-Mail-Formate (7 Beispiele) werden abgelehnt | `AuthValidatorTest` | ✅ |
| A-10 | Gültige E-Mail-Formate (4 Beispiele) werden akzeptiert | `AuthValidatorTest` | ✅ |
| A-11 | Login: Passwort nur auf „nicht leer" prüfen | `AuthValidatorTest` | ✅ |
| A-12 | Registrierung: Passwort mindestens 8 Zeichen (7 abgelehnt, 8 akzeptiert) | `AuthValidatorTest` | ✅ |
| A-13 | Name nicht leer, höchstens 50 Zeichen | `AuthValidatorTest` | ✅ |
| A-14 | Build der Debug-APK, Lint | GitHub Actions | ✅ |
| A-15 | Zugangscode: erzeugte Codes haben 16 Zeichen, nur erlaubte Zeichen, sind verschieden; Normalisieren (Bindestriche, Groß-/Kleinschreibung), Format, Rundlauf | `AccessCodeTest` | ✅ |
| A-16 | Eingabeprüfung Zugangscode (leer, falsches Format, mit Bindestrichen/Kleinbuchstaben gültig) | `AuthValidatorTest` | ✅ |
| A-17 | Geld: „12,5", „1.234,56", „12.50", Tausenderpunkte, ungültige Eingaben (3 Nachkommastellen, Buchstaben, zu lang), Anzeige „1.234,56 €", negatives Vorzeichen | `MoneyTest` (4) | ✅ |
| A-18 | Kontostand = Einnahmen − erstattete Ausgaben; offene und gesponserte Ausgaben ändern ihn nicht; offene Forderungen je Zahler; Stand nach Begleichung; Gesamtausgaben; leere Liste | `FinanceCalculatorTest` (6) | ✅ |
| A-19 | Buchungsprüfung: Betrag > 0 und ≤ 1.000.000,00 €, Datum, Kategorie, Zahler bei Ausgabe, Länge Beschreibung/Kommentar, Kategoriename | `BookingValidatorTest` (11) | ✅ |
| A-20 | Import: Datei lesen (nur synthetische Testdaten), falsche Version/kaputte Datei, Kontrollwerte, Abweichung erkannt, Zuordnung Zahler → Benutzer, Dokument-ID `xl-<Zeile>` | `ImportPlannerTest` (6) | ✅ |

### 2.2 Gerätetest Phase 1 – Projektbasis (✅ bestanden, 30.09.2026)

Voraussetzung: Debug-APK aus dem obersten GitHub-Lauf (Actions → Artifacts → `obelix-debug-apk`) installiert.

| ID | Schritte | Erwartet | Ergebnis |
|---|---|---|---|
| G1-01 | App starten | Startet ohne Absturz. Weil du nicht angemeldet bist, erscheint die Anmeldeseite (nicht der Hauptbereich) | ✅ (30.09.2026, Benutzer) |
| G1-02 | Nach Anmeldung (siehe G2-04): unten sind **sechs** Bereiche sichtbar: Dashboard, Kalender, Finanzen, Aufgaben, Stellplätze, Dokumente. Tippe jeden an | Jeder Bereich wird geöffnet, der aktive ist markiert, Titel oben passt, Text „Dieser Bereich ist noch nicht verfügbar" | ✅ (30.09.2026, Benutzer) |
| G1-03 | Zahnrad oben rechts tippen, dann den Zurück-Pfeil | Einstellungen öffnen, Zurück führt zum vorigen Bereich | ✅ (30.09.2026, Benutzer) |

### 2.3 Gerätetest Phase 2 – Authentifizierung (✅ bestanden, 30.09.2026)

Hinweis: Für neue Konten eine E-Mail-Adresse verwenden, auf die du Zugriff hast (für den Passwort-Reset).

| ID | Schritte | Erwartet | Ergebnis |
|---|---|---|---|
| G2-01 | „Konto erstellen" mit Name, gültiger E-Mail, Passwort mit mindestens 8 Zeichen | Danach erscheint der Hauptbereich. In der Firebase-Konsole (Authentication → Benutzer) erscheint das Konto | ✅ (30.09.2026, Benutzer) |
| G2-02 | Erneut registrieren mit **derselben** E-Mail | Meldung „Zu dieser E-Mail-Adresse gibt es bereits ein Konto. Bitte melde dich an." | ✅ (30.09.2026, Benutzer) |
| G2-03 | Registrieren mit leerem Namen, ungültiger E-Mail (z. B. `abc`), Passwort mit 5 Zeichen | Jeweils Meldung am Feld, kein Serveraufruf, kein neues Konto in Firebase | ✅ (30.09.2026, Benutzer) |
| G2-04 | Zahnrad → „Abmelden", danach mit den Daten aus G2-01 anmelden | Nach dem Abmelden erscheint die Anmeldeseite; die Anmeldung führt in den Hauptbereich | ✅ (30.09.2026, Benutzer) |
| G2-05 | Angemeldet: App komplett schließen (aus der Übersicht wischen) und neu öffnen | Du bleibst angemeldet. Nach Abmelden, Schließen und Öffnen bleibst du abgemeldet | ✅ (30.09.2026, Benutzer) |
| G2-06 | Anmelden mit falschem Passwort; dann mit unbekannter E-Mail | Beide Male „E-Mail-Adresse oder Passwort ist falsch." (gleicher Text) | ✅ (30.09.2026, Benutzer) |
| G2-07 | „Passwort vergessen?" → E-Mail eingeben → Link senden. Danach Link in der E-Mail öffnen (auch Spam prüfen), neues Passwort setzen, damit anmelden | Bestätigungstext erscheint (auch bei unbekannter E-Mail derselbe Text); E-Mail kommt an; Anmeldung mit neuem Passwort klappt | ✅ (30.09.2026, Benutzer) |
| G2-08 | Flugmodus an, dann App öffnen, dann Anmelden versuchen | Oben rotes Feld „Keine Internetverbindung. Die angezeigten Daten sind möglicherweise nicht aktuell."; Anmelden zeigt „Keine Verbindung zum Server …", **keine** Erfolgsmeldung. Flugmodus aus: Hinweis verschwindet | ✅ (30.09.2026, Benutzer) |
| G2-09 | Passwort-Augensymbol antippen; Bildschirm drehen; Tastatur „Weiter/Fertig"-Taste benutzen | Passwort wird angezeigt bzw. verborgen; Eingaben bleiben beim Drehen erhalten; „Fertig" sendet das Formular ab | ✅ (30.09.2026, Benutzer) |

Ergebnis: alle 12 Fälle (G1-01 bis G1-03, G2-01 bis G2-09) wurden vom Benutzer auf dem Gerät erfolgreich getestet. Bei künftigen Fällen bitte ✅ oder ❌ melden, bei ❌ mit Screenshot oder genauer Meldung.


### 2.4 Gerätetest Phase 3 – Benutzer, Rollen, Zugangscode (✅ bestanden)

**Voraussetzungen (einmalig, siehe [`FIREBASE-EINRICHTUNG.md`](FIREBASE-EINRICHTUNG.md), Abschnitte 7 und 8):** (1) Regeln aus `firebase/firestore.rules` in der Firebase-Konsole veröffentlicht, (2) Dokument `config/access` mit Code angelegt, (3) neueste Debug-APK aus GitHub Actions installiert (vorher die alte App deinstallieren, die Signatur ändert sich bei jedem CI-Bau). Zugangscodes nicht öffentlich posten.

| ID | Schritte | Erwartet | Ergebnis |
|---|---|---|---|
| G3-01 | Mit deinem **vorhandenen Konto aus Phase 2** anmelden | Kein Hauptbereich, sondern „Zugangscode eingeben" mit „Weiter" und „Abmelden". Ohne veröffentlichte Regeln erscheint stattdessen „Benutzerdaten konnten nicht geladen werden … Dafür fehlt dir die Berechtigung" | ✅ (30.09.2026, Benutzer, nach Fix `e2b2483`) |
| G3-02 | Dort den Code aus `config/access` eingeben (mit oder ohne Bindestriche, auch Kleinbuchstaben) | Hauptbereich öffnet sich. Einstellungen: dein Name, E-Mail, „Deine Rolle: Mitglied" | ✅ (30.09.2026, Benutzer) |
| G3-03 | In der Firebase-Konsole in `users/<deine uid>` das Feld `role` auf `ADMIN` setzen; in der App Einstellungen neu öffnen | „Deine Rolle: Admin"; Abschnitte „Zugangscode" (Code sichtbar, Kopieren, Teilen, „Neuen Zugangscode erzeugen") und „Benutzer" (du mit „(du)", ohne Menü) | ✅ (30.09.2026, Benutzer) |
| G3-04 | Abmelden, „Konto erstellen" mit neuer E-Mail und **falschem Code** (z. B. `AAAAAAAA22222222`) | Zurück zur Anmeldung mit „Der Zugangscode ist ungültig oder nicht mehr gültig." In der Konsole (Authentication) **kein** Konto zu dieser E-Mail | ✅ (30.09.2026, Benutzer) |
| G3-05 | Registrieren mit leerem oder zu kurzem Code | Meldung am Feld, kein Serveraufruf, kein neues Konto | ✅ (30.09.2026, Benutzer) |
| G3-06 | **Zweite Person** registriert sich mit Name, E-Mail, gültigem Code, Passwort | Direkt der Hauptbereich. Einstellungen: „Deine Rolle: Mitglied", **kein** Zugangscode, **keine** Benutzerliste | ✅ (30.09.2026, Benutzer) |
| G3-07 | Als Admin: bei der zweiten Person Menü (drei Punkte) → „Zum Admin machen", dann „Zum Mitglied machen" | Rolle ändert sich sichtbar; die zweite Person sieht nach erneutem Öffnen der Einstellungen jeweils die passende Ansicht | ✅ (30.09.2026, Benutzer) |
| G3-08 | Admin: „Neuen Zugangscode erzeugen" (Bestätigung). Dann mit dem **alten** Code eine weitere Person registrieren, danach mit dem **neuen** | Alter Code: „ungültig", kein Konto; neuer Code: Registrierung gelingt. Bereits registrierte Benutzer behalten ihren Zugriff | ✅ (30.09.2026, Benutzer) |
| G3-09 | Admin: Benutzer „Entfernen" (Bestätigungsdialog). Die entfernte Person startet die App neu | Person ist aus der Liste weg; sie sieht „Zugangscode eingeben" und kommt nur mit dem aktuellen Code wieder hinein | ✅ (30.09.2026, Benutzer) |
| G3-10 | Flugmodus an: als Admin Code erneuern bzw. Benutzer entfernen; außerdem eine Registrierung versuchen | „Keine Verbindung zum Server …", **keine** Erfolgsmeldung, nichts geändert. Flugmodus aus: Aktion funktioniert | ✅ (30.09.2026, Benutzer) |
| G3-11 | App schließen und öffnen; Abmelden und wieder anmelden (ohne Code) | Direkt der Hauptbereich, Rolle stimmt | ✅ (30.09.2026, Benutzer) |

### 2.4a Gerätetest Phase 4 – Finanzen und Excel-Import (✅ bestanden, 30.09.2026, Benutzer)

Voraussetzung: Regeln aus `firebase/firestore.rules` in der Firebase-Konsole veröffentlicht, neue Debug-APK installiert, Robert (Heidi/Robert) hat sich mit Zugangscode registriert, Datei `obelix-import.json` liegt auf dem Gerät (nicht im Repository).

| ID | Schritte | Erwartet | Status |
|---|---|---|---|
| G4-01 | Regeln veröffentlichen, APK installieren, Finanzen öffnen (noch leer) | Leerzustand „Noch keine Buchungen", Kontostand 0,00 €, keine Fake-Zahlen | ✅ |
| G4-02 | Einstellungen → Kategorie hinzufügen; gleichen Namen nochmal | Neue Kategorie erscheint; Duplikat wird abgelehnt | ✅ |
| G4-03 | Als ADMIN Kategorie umbenennen und deaktivieren; als MEMBER prüfen | ADMIN kann, MEMBER sieht keine Bearbeitung | ✅ |
| G4-04 | ADMIN: Einstellungen → Excel-Import → Datei wählen | Vorschau: 300 Buchungen, Einnahmen 69.617,94 €, Ausgaben 70.415,60 €, Kontostand 107,17 €, Robert offen 99,00 €, nach Begleichung 8,17 €, 25 übersprungene Zeilen aufgelistet | ✅ |
| G4-05 | Zuordnung: nichts gewählt bzw. beide Zahler dasselbe Konto | Import gesperrt mit Hinweis | ✅ |
| G4-06 | Zuordnung Anna/Tobias → Tobias, Heidi/Robert → Robert, Import bestätigen | Bestätigungsdialog, Fortschritt, „Import abgeschlossen: 300" | ✅ |
| G4-07 | Finanzen öffnen | Kontostand 107,17 €, „Ausgelegt von Robert 99,00 €", gesponsert 805,83 € | ✅ |
| G4-08 | Import erneut ausführen | 300 von 300 bereits vorhanden, Schaltfläche gesperrt, keine Dubletten | ✅ |
| G4-09 | Filter (Jahr, Art, Kategorie, Status) | Liste und Summen passen zum Filter | ✅ |
| G4-10 | Neue Ausgabe „offen" (Zahler Robert) anlegen | Forderung steigt, Kontostand unverändert | ✅ |
| G4-11 | „Als erstattet markieren" | Forderung sinkt, Kontostand sinkt um den Betrag | ✅ |
| G4-12 | Buchung bearbeiten; Einnahme mit „Keine Angabe" als Zahler anlegen | Änderung sichtbar; Einnahme wird gespeichert | ✅ |
| G4-13 | Testbuchung löschen (mit Bestätigung) | Kontostand wieder 107,17 € | ✅ |
| G4-14 | Flugmodus: Buchung speichern | Fehlermeldung, kein falscher Erfolg, nichts gespeichert | ✅ |
| G4-15 | Mit Robert anmelden | Sieht dieselben Buchungen und Summen | ✅ |
| G4-16 | Rundungsstichprobe und Regression: Anteil Schaden 1.361,47 €, Schneidebrett 6,30 €, 2xTöpfe 29,73 €; Einstellungen (Code, Benutzer, Abmelden) funktionieren wie zuvor | Werte stimmen, keine Regression | ✅ |

### 2.4b Gerätetest UI-Überarbeitung (⏳ offen)

Bau und Lint grün (Commit `833599b`, Lauf 36739044165). Das Aussehen kann ich nicht prüfen (kein Gerät). Vor dem Installieren die alte App deinstallieren.

| ID | Schritte | Erwartet | Status |
|---|---|---|---|
| GU-01 | App auf dem Startbildschirm und in der App-Liste ansehen | Neues Icon (Wohnmobil mit OBELIX), nicht abgeschnitten, keine weißen Ränder | ⏳ |
| GU-02 | Abmelden, Login-Bildschirm ansehen (hell und dunkel) | Hintergrundbild vollflächig, Formular auf Karte gut lesbar | ⏳ |
| GU-03 | Login, Registrierung, Passwort vergessen umschalten; Tastatur öffnen | Alle Felder erreichbar, Bildschirm scrollt, nichts verdeckt | ⏳ |
| GU-04 | Anmelden, untere Leiste ansehen | Nur Symbole, keine Texte, kein Umbrechen; Auswahl erkennbar; alle sechs Bereiche erreichbar | ⏳ |
| GU-05 | TalkBack (optional): Symbole in der Leiste | Jedes Symbol wird mit seinem Namen vorgelesen | ⏳ |
| GU-06 | Regression: Finanzen, Einstellungen, Abmelden und Anmelden, Zugangscode-Bildschirm (frisches Konto) | Verhalten wie zuvor | ⏳ |

### 2.5 Emulator-Tests der Sicherheitsregeln (✅ bestanden, GitHub Actions)

Datei `firebase/rules-tests/rules.test.mjs`, Regeln `firebase/firestore.rules`. Lauf: Job „rules" in GitHub Actions (Firestore-Emulator, Projekt `demo-obelix`, keine echten Daten). Ergebnis Commit `d870760`: **45 von 45 bestanden** (Phase 3: 30, Phase 4: 15).

| ID | Prüft | Fälle |
|---|---|---|
| R-01 | Ohne Anmeldung kein Lesen, kein Schreiben, keine Registrierung | 2 |
| R-02 | Registrierung nur mit gültigem Code: gültig, mehrere Personen, falscher Code, ohne/leer/falsches Format, erneuerter Code (alt ungültig, neu gültig), gesperrt (`code = null`), ohne `config/access`, **als ADMIN verboten**, nicht für andere, Zusatzfelder/leerer/zu langer Name/falsche Zeit, kein Überschreiben, als Transaktion | 12 |
| R-03 | Angemeldet ohne Benutzerdokument sieht und ändert nichts, darf aber das **eigene** (noch fehlende) Dokument abfragen; entfernter Benutzer verliert den Zugriff; noch nicht freigegebene Sammlungen für alle gesperrt | 4 |
| R-04 | Rollen: **keine Selbst-Beförderung**, MEMBER darf andere nicht ändern und den Code weder lesen noch ändern, eigener Name änderbar, ADMIN befördert/degradiert andere, ADMIN kann sich nicht selbst degradieren/entfernen, zwei ADMINs, Entfernen | 8 |
| R-05 | Zugangscode: ADMIN liest und erneuert, falsches Format/Zusatzfelder/falscher Bearbeiter verboten, Löschen verboten, als Transaktion | 4 |
| R-06 | Buchungen: gültig anlegen, Pflichtfelder/Betrag/Datum/Status/Art ungültig, falscher Ersteller/Zeitstempel, Ändern (Ersteller, Anlagezeit, importRef unveränderlich), Begleichen, Löschen, ohne Anmeldung/Freischaltung gesperrt, 300 Buchungen in Blöcken zu 10 | 10 |
| R-07 | Kategorien: jeder Benutzer legt an (aktiv), nur ADMIN ändert Name/aktiv, Zusatzfelder verboten, Löschen verboten | 5 |

Anforderung 43 (Haushalt A sieht nie Daten von Haushalt B) entfällt, weil es keine Haushalte mehr gibt (Entscheidung vom 30.09.2026). Der entsprechende Schutz ist jetzt: ohne gültigen Zugangscode kein Zugriff (R-01, R-03).

**Grenze dieser Tests:** Sie laufen gegen den Emulator und die Regeldatei, nicht gegen dein echtes Projekt. Ob die veröffentlichten Regeln dort greifen, zeigen G3-01, G3-04 und G3-06.

### 2.6 Bekannte Lücken der Tests
- Die Abläufe `AccessCodeViewModel`, `SessionViewModel` und `SettingsViewModel` haben keine automatischen Tests, weil die Coroutine-Testbibliothek noch nicht eingebunden ist; sie werden durch G3-01 bis G3-11 geprüft.
- Die Regel-Tests wurden nicht durch bewusst kaputt gemachte Regeln gegengeprüft (Mutationsprüfung). Fehlgeschlagene Zugriffe zählen im Test nur als bestanden, wenn der Server „Berechtigung verweigert" meldet; die Erfolgsfälle sichern ab, dass die Regeln nicht einfach alles ablehnen.
- Die ViewModels (Anmeldeablauf) haben noch keine automatischen Tests, weil die Coroutine-Testbibliothek noch nicht eingebunden ist. Der Ablauf wird bisher nur durch G2-01 bis G2-08 geprüft.
- Kein automatischer Bedienungstest der Oberfläche (Compose-UI-Test). Der Bau in der Cloud kann keinen Emulator starten.

---

## 3. Zu erledigende Testfälle (geplant, je Phase)

Wird beim Umsetzen der jeweiligen Phase in konkrete Fälle mit Schritten überführt. Grundlage: Anforderungen Abschnitt 42 und 43.

| Phase | Bereich | Geplante Testfälle | Art |
|---|---|---|---|
| 3 | Benutzer, Rollen, Sicherheitsregeln | **Umgesetzt**: Emulator-Tests R-01 bis R-05 ✅ (siehe 2.5), Unit-Tests A-15/A-16 ✅. Gerätetests G3-01 bis G3-11 ✅ (siehe 2.4) | Emulator (automatisch), Gerät |
| 4 | Finanzen und Import | **Umgesetzt**: A-17 bis A-20 ✅, R-06/R-07 ✅, Gerätetests G4-01 bis G4-16 ✅ (siehe 2.4a). Ursprünglich geplant: Einnahme/Ausgabe erfassen, ändern, löschen (mit Bestätigung) · Betrag muss größer als 0 sein, genau 2 Nachkommastellen · Pflichtfelder · Bezahlt von = Benutzer · Status offen → erstattet · gesponsert zählt nicht zum Kontostand · Kontostand, offene Forderungen je Zahler, Kontostand nach Begleichung · **Importtest gegen die Excel-Kontrollwerte** (Kontostand 107,17 €, offene Forderungen 99,00 €, nach Begleichung 8,17 €, 300 importierbare Buchungen + 25 übersprungene Zeilen, Rundung auf Cent) · neue Kategorie in den Einstellungen · Offline | Unit, Emulator, Gerät |
| 5 | Geplante Ausgaben | Planung anlegen · „Gekauft" mit tatsächlichem Betrag (500 € geplant, 472 € gekauft ⇒ Ausgabe 472 €) · geplante Ausgabe erscheint danach nicht mehr offen · Kontostand ändert sich nur durch den Kauf · Abbruch ohne Netz erzeugt nichts Halbes | Unit, Gerät |
| 6 | Dateiablage, Belege | Datei hochladen, anzeigen, löschen · Größe höchstens 8 MB, erlaubte Typen · Datei in Stücken korrekt zusammengesetzt · Zugriff ohne Freischaltung verboten · Abbruch hinterlässt keine Reste · Fotogröße messen | Unit, Emulator, Gerät |
| 7 | Kalender | Termin anlegen, ändern, löschen · ungültiger Zeitraum (Ende vor Start) · **Überschneidung vor dem Speichern eindeutig angezeigt** (welcher Eintrag, wer, wann), Speichern nur nach Bestätigung · Randfälle: gleicher Tag, angrenzend, umschließend | Unit, Gerät |
| 8 | Auffälligkeiten | Erstellen, bearbeiten, erledigen, wieder öffnen · Filter Alle/Offen/Erledigt, Standard „Offen" · löschen mit Bestätigung | Unit, Gerät |
| 9 | Stellplätze | Standortberechtigung erteilt/verweigert · GPS speichern · Kommentar · bis zu 3 Fotos (Kamera, Galerie), vierte wird abgelehnt (App **und** Regeln) · Foto einzeln löschen · Stellplatz löschen entfernt Fotos · Karte, Marker öffnet Stellplatz · „Navigation starten" öffnet externe App | Unit, Emulator, Gerät (GPS/Kamera nur dort prüfbar) |
| 10 | Dokumente | Hochladen, Kategorie, öffnen, löschen · Dateityp-/Größenlimit · Zugriffsschutz | Unit, Emulator, Gerät |
| 11 | Dashboard | Zahlen stimmen mit den Detailbereichen überein · Leerzustand ohne Fake-Zahlen · Offline | Unit, Gerät |
| 12 | Gesamtqualität | Auth, Sicherheitsregeln, Firestore, alle Bereiche, Offline (kein falscher Erfolg, keine scheinbar gespeicherten Änderungen), Fehlerfälle, verschiedene Bildschirmgrößen, Bedienung mit TalkBack und großer Schrift | Gerät, Emulator |

**Querschnitt in jeder Phase:** Zustände Laden / Leer / Fehler / Offline · Fehlermeldungen auf Deutsch · Bestätigungsdialog beim Löschen · keine Fake-Daten · keine Geheimnisse im Repository.

---

## 4. Änderungsprotokoll

| Datum | Änderung |
|---|---|
| 30.09.2026 | Datei angelegt. 13 automatische Tests (A-01 bis A-13) und Bau/Lint (A-14) bestanden; Gerätefälle G1-01 bis G1-03 und G2-01 bis G2-09 offen |
| 30.09.2026 | Benutzer meldet alle 12 Gerätefälle (G1-01 bis G2-09) als bestanden. Phase 1 und 2 damit vollständig abgenommen. Nächste Phase: 3 |
| 30.09.2026 | Phase-3-Testplan erweitert: Registrierung nur mit Zugangscode (ohne/falscher/abgelaufener/benutzter Code, Konto ohne Mitgliedschaft) |
| 30.09.2026 | Phase 3 umgesetzt: Sicherheitsregeln, Zugangscode-Registrierung mit gemeinsamem Code und Start-Code, Haushalt/Rollen/Mitgliederverwaltung. Automatisch grün (Bau, Lint, 24 Unit-Tests, Regel-Tests im Emulator). Gerätefälle G3-01 bis G3-13 offen |
| 30.09.2026 | **Haushalt entfernt** (Entscheidung des Benutzers): keine Haushalte und Parteien, alle Benutzer teilen einen Datenbestand; Registrierung mit gemeinsamem Zugangscode, Admin = Projektinhaber. Regeln und Tests neu (29 Regel-Tests, 22 Unit-Tests, alles grün, Commit `656d1e9`). Gerätefälle neu: G3-01 bis G3-11 |
| 30.09.2026 | **Fehler beim Gerätetest gefunden (G3-01):** Konto ohne Freischaltung sah „Benutzerdaten konnten nicht geladen werden – Dafür fehlt dir die Berechtigung" statt „Zugangscode eingeben". Ursache: Regel erlaubte das Abfragen des eigenen, noch nicht vorhandenen Benutzerdokuments nicht. Behoben in `e2b2483`, Test R-03d ergänzt (30/30 grün). Regeln müssen neu veröffentlicht werden |
| 30.09.2026 | Benutzer meldet G3-01 bis G3-03 als bestanden (Freischaltung mit Zugangscode, Rolle ADMIN per Konsole). Offen: G3-04 bis G3-11 |
| 30.09.2026 | Benutzer meldet G3-04 bis G3-11 als bestanden. **Phase 3 vollständig abgenommen** |
| 30.09.2026 | **Phase 4 umgesetzt** (Finanzen, Kategorien, Excel-Import in der App durch ADMIN, Import-Weg B). Automatisch grün: Bau, Lint, 49 Unit-Tests, 45 Regel-Tests (Commit `d870760`). Zwei Testfehler in den Regel-Tests (falscher Ersteller, falsch erwarteter Fehlerfall) wurden im Test behoben, nicht in den Regeln. Gerätefälle G4-01 bis G4-16 offen; Regeln müssen neu veröffentlicht werden |
| 30.09.2026 | Benutzer meldet G4-01 bis G4-16 als bestanden. **Phase 4 abgenommen.** Zwischenfall beim Import: eine ältere Importdatei (Zahler Tobias/Robert, Zeile 170 als gesponserte Einnahme) wurde von der App zu Recht abgelehnt; mit der richtigen Datei lief der Import |
| 30.09.2026 | UI-Überarbeitung: App-Icon, Login-Hintergrund, Menü nur mit Symbolen. Bau, Lint, 49 Unit-Tests, 45 Regel-Tests grün (Commit `833599b`). Gerätefälle GU-01 bis GU-06 offen |
