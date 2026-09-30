# OBELIX – Testfälle

Stand: 30.09.2026 · Phase 3 umgesetzt, Gerätetest G3 offen · zuletzt geprüfter Commit: siehe Änderungsprotokoll (GitHub-Bau und Regel-Tests grün) · gehört zu [`PROJEKTPLAN.md`](PROJEKTPLAN.md)

Diese Datei wird **nach jeder Phase und nach jedem Testlauf aktualisiert**. Der Plan verweist nur hierher.

**Status:** ✅ bestanden · ⏳ offen, wartet auf den Benutzer (Gerätetest) · ⬜ geplant (Phase noch nicht umgesetzt) · ❌ fehlgeschlagen

**Wer prüft was:** *Automatisch* = läuft bei jedem Push in GitHub Actions, das Ergebnis lese ich dort ab. *Gerät* = kann ich hier nicht testen (kein Gerät, kein Emulator, kein Zugriff auf dein Firebase-Projekt); das musst du auf dem Smartphone prüfen. *Emulator* = Firebase-Emulator für die Sicherheitsregeln (Phase 3), läuft als eigener Job „rules" in GitHub Actions; in der Cloud-Sitzung ist der Emulator nicht ausführbar (npm und Emulator-Download gesperrt).

---

## 1. Aktueller Stand (Übersicht)

| Bereich | Automatisch | Gerät (du) |
|---|---|---|
| Projektbasis (Phase 1) | ✅ Build, Lint | ✅ 3 Fälle |
| Authentifizierung (Phase 2) | ✅ 13 Unit-Tests | ✅ 9 Fälle |
| Haushalt, Rollen, Zugangscode (Phase 3) | ✅ 11 Unit-Tests, ✅ 56 Regel-Tests (Emulator) | ⏳ 13 Fälle (G3-01 bis G3-13) |
| Alle weiteren Bereiche (ab Phase 4) | ⬜ | ⬜ |

**Automatische Prüfung insgesamt (GitHub, Stand Phase 3):** `assembleDebug` ✅ · `testDebugUnitTest` ✅ (24 Tests) · `lintDebug` ✅ · Regel-Tests im Emulator ✅ (siehe 2.5).

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
| A-16 | Eingabeprüfung Zugangscode, Haushaltsname (höchstens 60), Parteiname (höchstens 40) | `HouseholdValidatorTest` | ✅ |

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


### 2.4 Gerätetest Phase 3 – Haushalt, Rollen, Zugangscode (⏳ wartet auf dich)

**Voraussetzungen (einmalig, siehe [`FIREBASE-EINRICHTUNG.md`](FIREBASE-EINRICHTUNG.md), Abschnitte 7 und 8):** (1) Regeln aus `firebase/firestore.rules` in der Firebase-Konsole veröffentlicht, (2) Start-Code als Dokument in `invites` angelegt, (3) neueste Debug-APK aus GitHub Actions installiert. Für die Fälle mit zwei Personen brauchst du zwei Geräte oder zwei Konten nacheinander (Abmelden/Anmelden). Zugangscodes bitte nicht in Nachrichten posten, die öffentlich sind.

| ID | Schritte | Erwartet | Ergebnis |
|---|---|---|---|
| G3-01 | Mit einem **vorhandenen Testkonto aus Phase 2** anmelden | Kein Hauptbereich, sondern „Zugangscode eingeben" mit Knopf „Weiter" und „Abmelden". Ohne veröffentlichte Regeln erscheint stattdessen „Haushalt konnte nicht geladen werden … Dafür fehlt dir die Berechtigung" | ⏳ |
| G3-02 | „Konto erstellen" mit neuer E-Mail, aber **falschem Zugangscode** (z. B. `AAAAAAAA22222222`) | Nach kurzer Wartezeit zurück zur Anmeldung mit „Der Zugangscode ist ungültig oder nicht mehr gültig." In der Firebase-Konsole (Authentication) ist **kein** Konto zu dieser E-Mail vorhanden | ⏳ |
| G3-03 | Registrieren mit zu kurzem oder leerem Code | Meldung am Feld (Pflicht bzw. 16 Zeichen), kein Serveraufruf, kein neues Konto | ⏳ |
| G3-04 | Registrieren mit dem **Start-Code** (Name, neue E-Mail, Code, Passwort) | Schritt „Haushalt einrichten": Haushaltsname, zwei Parteinamen, eigene Partei wählen → „Haushalt anlegen" → Hauptbereich. Einstellungen: dein Name, Haushaltsname, „Deine Rolle: Admin · Partei: …", Abschnitt Mitglieder mit dir als „(du)", **noch kein Zugangscode** und Knopf „Zugangscode erzeugen" | ⏳ |
| G3-05 | Denselben **Start-Code** mit einer weiteren neuen E-Mail benutzen | „Zugangscode ungültig", Konto wird wieder gelöscht (nicht in der Konsole) | ⏳ |
| G3-06 | Als Admin: „Zugangscode erzeugen"; dann „Kopieren" und „Teilen" | Code erscheint als `XXXX-XXXX-XXXX-XXXX`; Kopieren legt ihn in die Zwischenablage; Teilen öffnet die Android-Auswahl mit Text „Dein Zugangscode für OBELIX: …" | ⏳ |
| G3-07 | **Zweite Person** registriert sich mit dem gemeinsamen Code (Code darf mit oder ohne Bindestriche und in Kleinbuchstaben eingegeben werden) | Schritt „Deine Partei" mit den zwei Parteinamen → „Beitreten" → Hauptbereich. Einstellungen: Rolle „Mitglied", Code sichtbar, **kein** Knopf zum Erneuern, in der Mitgliederliste **keine** Aktionsmenüs | ⏳ |
| G3-08 | Als Admin Einstellungen öffnen: bei der zweiten Person Menü (drei Punkte) → „Zum Admin machen", dann „Zum Mitglied machen", dann „Andere Partei zuordnen" | Rolle bzw. Partei ändert sich sichtbar nach dem Neuladen; bei der **eigenen** Zeile gibt es kein Menü | ⏳ |
| G3-09 | Admin: „Neuen Zugangscode erzeugen" (Bestätigung). Dann mit dem **alten** Code eine weitere Person registrieren, danach mit dem **neuen** | Alter Code: „ungültig" und kein Konto; neuer Code: Beitritt gelingt. Bereits angemeldete Mitglieder behalten ihren Zugriff | ⏳ |
| G3-10 | Admin: Mitglied „Entfernen" (Bestätigungsdialog erscheint). Das entfernte Mitglied startet die App neu | Person ist aus der Liste weg; sie sieht „Zugangscode eingeben" und kann mit dem aktuellen Code wieder beitreten | ⏳ |
| G3-11 | Flugmodus an: als Admin „Neuen Zugangscode erzeugen" bzw. Mitglied entfernen; außerdem eine Registrierung versuchen | Fehlermeldung „Keine Verbindung zum Server …", **keine** Erfolgsmeldung, Liste und Code bleiben unverändert. Flugmodus aus: Aktion funktioniert | ⏳ |
| G3-12 | App komplett schließen und öffnen; Abmelden und wieder anmelden (ohne Code) | Direkt der Hauptbereich (kein Codefeld); Rolle und Haushalt stimmen | ⏳ |
| G3-13 | **Haushaltstrennung am echten Projekt** (Anforderung 43): Zweiten Start-Code in der Konsole anlegen, damit einen zweiten Haushalt mit einem dritten Konto einrichten | Die Mitgliederliste und der Code des zweiten Haushalts zeigen nur dessen Personen; Konten des ersten Haushalts sehen nichts davon (und umgekehrt) | ⏳ |

### 2.5 Emulator-Tests der Sicherheitsregeln (✅ bestanden, GitHub Actions)

Datei `firebase/rules-tests/rules.test.mjs`, Regeln `firebase/firestore.rules`. Lauf: Job „rules" in GitHub Actions (Firestore-Emulator, Projekt `demo-obelix`, keine echten Daten). Ergebnis am ersten vollständigen Lauf: **52 von 52 bestanden** (R-01 bis R-07); mit den Transaktionstests R-08 sind es 56.

| ID | Prüft | Fälle |
|---|---|---|
| R-01 | Ohne Anmeldung kein Lesen und kein Schreiben | 2 |
| R-02 | Beitritt mit dem gemeinsamen Code: gültig, mehrere Personen mit demselben Code, ADMIN per Code verboten, unbekannter Code, Code eines anderen Haushalts, erneuerter (widerrufener) Code, ungültige Partei, ohne `users`-Dokument, Zeiger auf fremden Haushalt, fremde Mitglieds-ID, Zusatzfelder/falsche Zeit/leerer Name, schon in einem Haushalt, deaktivierter Code | 13 |
| R-03 | Haushalt anlegen mit Start-Code: gültig (Gründer = ADMIN), einmalig, benutzter Code, abgelaufener Code, ohne Einlösen, gemeinsamer Code taugt nicht, ohne Code, ADMIN in bestehendem Haushalt, falscher `createdBy`/falsche Parteien, Gründer als MEMBER | 10 |
| R-04 | **Haushalt A sieht nie Daten von Haushalt B** (beide Richtungen, Lesen und Schreiben, auch ADMINs), Einladungen nicht auflistbar, Konto ohne Mitgliedschaft sieht nichts, noch nicht freigegebene Sammlungen gesperrt | 8 |
| R-05 | Rollen: **keine Selbst-Beförderung**, MEMBER darf nichts verwalten, ADMIN verwaltet andere, ADMIN kann sich nicht selbst herabstufen/entfernen, zwei ADMINs, Entfernen samt Zeiger, Haushaltsname | 9 |
| R-06 | Gemeinsamen Code erzeugen, erneuern, deaktivieren; Missbrauch (falsche Parteien, ohne Aktivierung, falsches Format, fremder Code); Codes nicht änderbar | 6 |
| R-07 | `users`-Zeiger: nur eigener lesbar, Haushalt unveränderlich, nicht löschbar, ohne Mitglieds-Dokument nicht anlegbar | 4 |
| R-08 | Dieselben Schreibvorgänge wie in der App als **Transaktionen**: Beitritt, Haushalt anlegen + erster Code, Code erneuern + Mitglied entfernen + Rolle ändern, widerrufener Code | 4 |

**Grenze dieser Tests:** Sie laufen gegen den Emulator und die Regeldatei, nicht gegen dein echtes Projekt. Ob die veröffentlichten Regeln dort greifen, prüfen G3-01, G3-02 und G3-13.

### 2.6 Bekannte Lücken der Tests
- Die Abläufe der Einrichtung (`OnboardingViewModel`, `SessionViewModel`, `SettingsViewModel`) haben keine automatischen Tests, weil die Coroutine-Testbibliothek noch nicht eingebunden ist; sie werden durch G3-01 bis G3-13 geprüft.
- Die Regel-Tests wurden nicht durch bewusst kaputt gemachte Regeln gegengeprüft (Mutationsprüfung). Fehlgeschlagene Zugriffe zählen im Test nur als bestanden, wenn der Server „Berechtigung verweigert" meldet; die Erfolgsfälle sichern ab, dass die Regeln nicht einfach alles ablehnen.
- Die ViewModels (Anmeldeablauf) haben noch keine automatischen Tests, weil die Coroutine-Testbibliothek noch nicht eingebunden ist. Der Ablauf wird bisher nur durch G2-01 bis G2-08 geprüft.
- Kein automatischer Bedienungstest der Oberfläche (Compose-UI-Test). Der Bau in der Cloud kann keinen Emulator starten.

---

## 3. Zu erledigende Testfälle (geplant, je Phase)

Wird beim Umsetzen der jeweiligen Phase in konkrete Fälle mit Schritten überführt. Grundlage: Anforderungen Abschnitt 42 und 43.

| Phase | Bereich | Geplante Testfälle | Art |
|---|---|---|---|
| 3 | Haushalt, Rollen, Sicherheitsregeln | **Umgesetzt**: Emulator-Tests R-01 bis R-08 ✅ (siehe 2.5), Unit-Tests A-15/A-16 ✅. **Offen: Gerätetests G3-01 bis G3-13** (siehe 2.4) | Emulator (automatisch), Gerät mit zwei Testkonten |
| 4 | Finanzen und Import | Einnahme/Ausgabe erfassen, ändern, löschen (mit Bestätigung) · Betrag muss größer als 0 sein, genau 2 Nachkommastellen · Pflichtfelder · Bezahlt von = Partei · Status offen → erstattet · gesponsert zählt nicht zum Kontostand · Kontostand, offene Forderungen je Partei, Kontostand nach Begleichung · **Importtest gegen die Excel-Kontrollwerte** (Kontostand 107,17 €, offene Forderungen −99,00 €, Kontostand nach Begleichung 8,17 €, 325 Buchungen, Rundung auf Cent) · neue Kategorie in den Einstellungen · Offline | Unit, Emulator, Gerät |
| 5 | Geplante Ausgaben | Planung anlegen · „Gekauft" mit tatsächlichem Betrag (500 € geplant, 472 € gekauft ⇒ Ausgabe 472 €) · geplante Ausgabe erscheint danach nicht mehr offen · Kontostand ändert sich nur durch den Kauf · Abbruch ohne Netz erzeugt nichts Halbes | Unit, Gerät |
| 6 | Dateiablage, Belege | Datei hochladen, anzeigen, löschen · Größe höchstens 8 MB, erlaubte Typen · Datei in Stücken korrekt zusammengesetzt · Zugriff durch fremden Haushalt verboten · Abbruch hinterlässt keine Reste · Fotogröße messen | Unit, Emulator, Gerät |
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
