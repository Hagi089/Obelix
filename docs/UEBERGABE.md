# OBELIX – Übergabe an den nächsten Chat

Stand: 01.10.2026 · **Phase 11 – Dashboard abgenommen (Version 19; Bau, Lint, 293 Unit-Tests, 114 Regel-Tests grün; Gerätetest G11-01 bis G11-12 bestanden)** · Phase 10 – Dokumente abgenommen (Version 18, G10-01 bis G10-14 bestanden) · Phase 9 – Stellplätze abgenommen (Version 14, Kartenkorrekturen bis Version 17, G9-01 bis G9-18 bestanden)** · Phase 8 – Auffälligkeiten abgenommen (Version 13, G8-01 bis G8-12 bestanden) · Phase 4 (Finanzen, Kategorien, Excel-Import) **abgenommen** · UI-Überarbeitung (Version 07) abgenommen (GU bestanden) · Phase 5 – Geplante Ausgaben abgenommen (Version 08, G5 bestanden) · Phase 6 – Dateiablage und Belege abgenommen (Version 10, G6 bestanden) · Phase 7 – Kalender abgenommen (Version 11, G7-01 bis G7-14 bestanden) · Erweiterung Personenfarben (Version 12, G7-15 bestanden) · danach: **Phase 8 – Auffälligkeiten**

Diese Datei ist die Kurzfassung für einen neuen Chat. Maßgeblich bleiben [`PROJEKTPLAN.md`](PROJEKTPLAN.md) (Plan, Entscheidungen, Datenmodell) und [`TESTFAELLE.md`](TESTFAELLE.md) (Tests). Die Projektanforderungen liegen im Claude-Projekt „Obelix Wohnmobil App" (Dokument `Anforderungen`).

---

## 1. Wo wir stehen

| Phase | Inhalt | Stand |
|---|---|---|
| 0 | Analyse, Architektur, Plan | ✅ freigegeben |
| 1 | Projektbasis (Compose, Material 3, Navigation, Firebase, CI) | ✅ auf dem Gerät abgenommen |
| 2 | Authentifizierung (Registrieren, Anmelden, Abmelden, Passwort-Reset) | ✅ auf dem Gerät abgenommen (G1-01 bis G2-09) |
| 3 | Benutzer, Rollen, Zugangscode, Firestore-Sicherheitsregeln | ✅ umgesetzt, Bau und Regel-Tests grün · ✅ Gerätetest G3-01 bis G3-11 bestanden |
| 4 | Finanzen, Kategorien, Excel-Import | ✅ abgenommen (G4-01 bis G4-16 bestanden, Import durchgeführt) |
| UI | App-Icon, Login-Hintergrund, Menü nur mit Symbolen, Hell-/Dunkelmodus, Versionsanzeige (Version 07) | ✅ umgesetzt, Bau grün (`5543941`) · ✅ Gerätetest GU-01 bis GU-10 bestanden |
| 5 | Geplante Ausgaben (Version 08) | ✅ umgesetzt, Bau, Lint, 63 Unit-Tests und 58 Regel-Tests grün (Commit `d20c658`) · ✅ Gerätetest G5-01 bis G5-12 bestanden |
| 6 | Dateiablage und Belege (Version 09) | ✅ abgenommen: Bau, Lint, 96 Unit-Tests (aus den Quellen gezählt) und 76 Regel-Tests grün (Commit `5956f25`, Fehlerkorrektur Version 10) · ✅ Gerätetest G6-01 bis G6-14 bestanden |
| **7** | **Kalender (Version 11; Personenfarben Version 12)** | **✅ abgenommen** (G7-01 bis G7-15 bestanden) |
| **8** | **Auffälligkeiten (Version 13)** | **✅ abgenommen**: Bau, Lint, 184 Unit-Tests (aus den Quellen gezählt) und 92 Regel-Tests grün (Commit `6227ef9`, Lauf 36762739295) · Gerätetest G8-01 bis G8-12 bestanden |
| **9** | **Stellplätze (Version 14, Kartenkorrekturen 15 bis 17)** | **✅ abgenommen**: Bau, Lint, 238 Unit-Tests (aus den Quellen gezählt) und 104 Regel-Tests grün (Commit `e85babf`) · Gerätetest G9-01 bis G9-18 bestanden |
| **10** | **Dokumente (Version 18)** | **✅ abgenommen**: Bau, Lint, 269 Unit-Tests (aus den Quellen gezählt) und 114 Regel-Tests grün (Commit `4fdf6b5`) · Gerätetest G10-01 bis G10-14 bestanden |
| **11** | **Dashboard (Version 19)** | **✅ abgenommen**: Bau, Lint, 293 Unit-Tests (aus den Quellen gezählt) und 114 Regel-Tests grün (Commit `5d205b3`, Lauf 36820452860), keine Regeländerung · **Gerätetest G11-01 bis G11-12 bestanden** |
| 12 | Qualitätssicherung | ⬜ (nächste Phase: 12) |

- Repository: `Hagi089/Obelix` (öffentlich), Branch `main`, letzter Stand mit grünem Bau.
- Firebase-Projekt `obelix-daf7c`: Tarif Spark, E-Mail/Passwort aktiv, Firestore in `europe-west3` im Produktionsmodus (alles gesperrt, bis Regeln vorliegen). Paketname `de.hagi089.obelix`.
- 293 automatische Unit-Tests (aus den Quelltexten gezählt), Build und Lint sowie 114 Regel-Tests im Firebase-Emulator (Job „rules") laufen bei jedem Push in GitHub Actions. Die Debug-APK liegt als Artefakt `obelix-debug-apk` im jeweils neuesten Lauf.
- **Die Regeln wirken erst, wenn du sie in der Firebase-Konsole veröffentlichst**, und der erste Zugangscode sowie deine Admin-Rolle werden einmalig von Hand angelegt: `docs/FIREBASE-EINRICHTUNG.md`, Abschnitte 7 und 8. Bis dahin ist die Datenbank komplett gesperrt.

## 2. Wichtigste Entscheidungen (Kurzfassung, Details im Plan)
- **Kosten:** alles kostenlos, Firebase Spark, keine Kreditkarte, kein Blaze. Firebase Storage entfällt; **Dateien liegen in Stücken (~900 KB) in Firestore**, höchstens 8 MB je Datei.
- **Kein Haushalt** (entschieden 30.09.2026): Alle registrierten Benutzer teilen denselben Datenbestand. Registrierung mit Name, E-Mail, **Zugangscode** und Passwort; danach nie wieder ein Code. Ein gemeinsamer Code in `config/access`, nur der ADMIN sieht und erneuert ihn. **Der Projektinhaber ist ADMIN, alle anderen MEMBER.** Serverseitig per Firestore-Regeln, kein Cloud Function. **Jeder Benutzer darf löschen** (immer mit Bestätigungsdialog).
- **Zahler:** „Bezahlt von" = **Benutzer** (sein Name), keine Parteien. Beim Import wird jede der zwei Excel-Parteien einem Benutzerkonto zugeordnet (Zuordnung nur im nicht-öffentlichen Projekt-Dokument `Excel-Analyse`). Abrechnungsstatus je Ausgabe: `OPEN`, `SETTLED`, `SPONSORED`.
- **Beträge:** Cent, genau 2 Nachkommastellen. Excel-Import (300 importierbare Buchungen, 25 Nullbetragszeilen übersprungen) einmalig durch den ADMIN in der App, Fehler der Excel werden unverändert übernommen und vom Benutzer korrigiert.
- **Kalender:** Überschneidungen sind speicherbar, müssen aber **vor dem Speichern** eindeutig angezeigt werden (umgesetzt in Phase 7, siehe Abschnitt 3e).
- **Design:** Google-Richtlinien (Material 3, Systemfarben, Android-Architekturleitfaden, Barrierefreiheit). Deutsche Oberfläche.
- **Architektur:** UI → ViewModel → Repository → Firebase; manuelle Dependency Injection (`AppContainer`), Firestore ohne dauerhaften Offline-Cache, damit nichts als „gespeichert" gilt, was der Server nicht bestätigt hat.
- **Reihenfolge:** Finanzen wurden vorgezogen (direkt nach Benutzern und Rollen).

## 3. Phase 3 – was umgesetzt ist (Details: Plan, Abschnitte 5, 7 und 9)
- Zuerst mit Haushalt, Parteien und Start-Code gebaut, am 30.09.2026 auf Wunsch des Benutzers **komplett zurückgebaut**. Es gibt keine `households`, `members`, `invites` und keine Parteien mehr.
- `firebase/firestore.rules`: nur `users/{uid}` (Name, Rolle, Code bei der Registrierung) und `config/access` (Code, nur ADMIN). Alle anderen Sammlungen sind bis zu ihrer Phase gesperrt. Tests in `firebase/rules-tests/`, CI-Job „rules".
- App: Registrierung mit Codefeld → Konto anlegen → `users/{uid}` anlegen (die Regel vergleicht den Code) → Hauptbereich; falscher Code löscht das Konto wieder. Konto ohne Freischaltung sieht nur „Zugangscode eingeben". Einstellungen: Rolle; ADMIN: Code anzeigen/kopieren/teilen/erneuern, Benutzer zum Admin/Mitglied machen, entfernen. Schreiben nur per Transaktion (offline Fehler statt Schein-Erfolg).
- Anforderung 43 (Haushalt A/B) entfällt, weil es keine Haushalte gibt; geschützt ist: ohne gültigen Code kein Zugriff.
- **Offen:** Gerätetest G3-01 bis G3-11 ([`TESTFAELLE.md`](TESTFAELLE.md), Abschnitt 2.4). Ohne diese Abnahme gilt Phase 3 nicht als abgeschlossen.
- **APK-Installation:** Jeder CI-Bau hat einen neuen Debug-Schlüssel. Vor dem Installieren einer neuen APK die alte App deinstallieren (offener Punkt: fester Debug-Schlüssel für CI).

## 3a. Phase 4 – was umgesetzt ist (Details: Plan, Phase 4)
- **Import-Weg B:** ADMIN wählt in der App (Einstellungen → Excel-Import) die private Datei `obelix-import.json` (nicht im Repository, ignoriert per `.gitignore`). Die App vergleicht Kontrollwerte (300 Buchungen, Einnahmen 69.617,94 €, Ausgaben 70.415,60 €, Kontostand 107,17 €, offen 99,00 €, nach Begleichung 8,17 €), der ADMIN ordnet die zwei Excel-Zahler Benutzerkonten zu (Auswahl, E-Mail-Adressen technisch nicht nötig), dann Import in Blöcken zu 10 je Transaktion. Dokument-ID `xl-<Excel-Zeile>` ⇒ wiederholbar ohne Dubletten.
- Neu: Sammlungen `transactions` und `categories` (ohne Art), Regeln + Tests R-06/R-07, Finanzen-Übersicht (Kontostand, Forderungen je Zahler, Filter), Buchungsformular (Einnahme/Ausgabe, offen/erstattet/gesponsert), Kategorien in den Einstellungen, Geldklasse `Money` (Cent, deutsche Ein-/Ausgabe, Maximum 1.000.000,00 €).
- Kontostand wird clientseitig aus allen Buchungen berechnet (ein Laden, keine Dauer-Listener).
- Bekannte Auffälligkeit in den Daten: Zeilen 104/105 haben das Datum 15.01.2016 (unverändert importiert, vom Benutzer zu prüfen). Rundung halb auf: Zeilen 13/20/181/182/188 ⇒ 6,30 / 29,73 / 1.361,47 / 1.361,47 / 0,16 €; Kontostand bleibt 107,17 €.
- **Abgenommen:** G4-01 bis G4-16 bestanden, Import in das echte Projekt ausgeführt. Der Import-Code bleibt vorerst (Entfernen in Phase 12 möglich).
- **Lehre:** Der Benutzer hatte zuerst eine ältere `obelix-import.json` gewählt (Zahler Tobias/Robert, keine Kommentare); die App lehnte sie mit der Meldung zu Zeile 170 (Einnahmen müssen beglichen sein) ab. Verbesserungsvorschlag, nicht umgesetzt: Fehlermeldung nennt den gefundenen Wert.

## 3b. UI-Überarbeitung (30.09.2026, Details: Plan)
- Icon: Adaptive Icon aus Benutzerbild (`mipmap-anydpi-v26`, `drawable-nodpi/ic_launcher_foreground.png`, Farbe `ic_launcher_background`). Login: Hintergrundbild `drawable-nodpi/login_background.jpg`, Formular auf halbtransparenter Karte (`ui/auth/AuthScreens.kt`). Menü: `label = null`, Name als `contentDescription` (`ui/ObelixApp.kt`).
- **Version 07:** Hell-/Dunkelmodus-Umschalter oben in den Einstellungen (lokal gespeichert), „Version 07“ unten, Kategorien nur für ADMIN sichtbar. **Regel: Bei jedem Deployment Version erhöhen** (`versionCode` +1, `versionName` zweistellig 09, 10, … in `app/build.gradle.kts`). Version 08 = Phase 5; 09/10 = Phase 6; 11 = Phase 7; 12 = Personenfarben im Kalender; 13 = Phase 8; nächstes Deployment ist Version 14.
- **Abgenommen:** Gerätetest GU-01 bis GU-10 bestanden ([`TESTFAELLE.md`](TESTFAELLE.md), 2.4b). Alte App vor der Installation deinstallieren.

## 3c. Phase 5 – Geplante Ausgaben (30.09.2026, Details: Plan, Phase 5)
- **Umgesetzt (Commit `d20c658`, Version 08):** Sammlung `plannedExpenses` (Bezeichnung, geschätzter Betrag, Plandatum, Status `PLANNED`/`PURCHASED`, optional Priorität, Link, Kommentar), Regeln `validPlanned` + Tests R-08. Einstieg über die Schaltfläche „Geplante Ausgaben" im Finanzbereich; Liste mit Filter Geplant (Standard)/Gekauft/Alle und Summe der offenen Schätzungen; Formular; Dialog „Gekauft" (tatsächlicher Betrag, Kaufdatum, Bezahlt von, Kategorie, Abrechnung).
- **Kauf = eine Transaktion** (`PlannedExpenseRepository.purchase`): liest den Status, legt die Ausgabe mit dem **tatsächlichen** Betrag an (`plannedExpenseId` gesetzt) und setzt die Planung auf `PURCHASED`. Die Regeln erzwingen das (`existsAfter`/`getAfter`), eine gekaufte Planung ist nicht mehr änderbar. Geplante Ausgaben zählen **nie** im Kontostand.
- **Buchung löschen** (`FinanceRepository.delete(id, plannedExpenseId, uid)`): stammt sie aus einem Kauf, wird die Planung im selben Schritt wieder `PLANNED` (Annahme des Entwicklers, Plan Entscheidung 23; auf Wunsch änderbar). Buchungen aus einem Kauf bleiben Ausgaben (Art gesperrt).
- **Neu im Code:** `data/planned/*` (Modelle, `PlannedValidator`, `PurchasePlanner`, `PlannedCalculator`, Repository), `ui/planned/*`, `AppError.CONFLICT`, gemeinsame Funktion `bookingCreateData` und Sammlungsnamen in `FinanceRepository.kt`, `AppContainer.plannedExpenseRepository`.
- **Abgenommen:** Gerätetest G5-01 bis G5-12 bestanden ([`TESTFAELLE.md`](TESTFAELLE.md), 2.4c). Hinweis (gilt weiter bei jeder Regeländerung): **Die Regeln müssen in der Firebase-Konsole neu veröffentlicht werden**, sonst schlägt jeder Zugriff auf `plannedExpenses` mit „Dafür fehlt dir die Berechtigung" fehl. Alte App vor der Installation deinstallieren. Dashboard-Zähler für offene Anschaffungen folgen in Phase 11.

## 3d. Phase 6 – Dateiablage und Belege (30.09.2026, Details: Plan, Phase 6 und Entscheidungen 24 bis 29)
- **Umgesetzt (Commit `5956f25`, Version 09):** Dateien gestückelt in Firestore (Option F). `files/{fileId}` (Name, Typ, Größe, Stückzahl, `createdAt/By`) und `files/{fileId}/chunks/{0..9}` (Feld `data`); Stücke zu 900 KiB (921.600 Byte), höchstens 10 Stücke, höchstens 8 MiB, nur JPEG und PDF. Beleg an Ausgaben: Feld `receipt` = `{fileId, name, contentType, sizeBytes}` (nicht `path`). Regeln + Tests R-09.
- **Eine Transaktion für Buchung und Datei:** `FirestoreFinanceRepository.create/update/delete` schreiben Buchung und Datei in **einer** `runTransaction` (`FileStore.stageUpload/stageDelete`). Jede Datei gehört zu genau einer Buchung (Regeln: `!exists` vor, `getAfter` nach dem Schreiben, `newReceiptOk`). **Ersetzen** = zwei Schritte: neue Datei + Buchung, danach `deleteQuietly` der alten (scheitert nur dieser Teil, bleibt eine unsichtbare Datei). Die alte Datei wird **in der Transaktion** gelesen (`tx.get`), nicht aus dem Bildschirmzustand.
- **Bilder/PDF:** `AndroidFileReader` (`OpenDocument`, keine Berechtigung): Bilder mit `inSampleSize` dekodiert, EXIF gedreht, höchstens 1800 px, weißer Grund, JPEG 80 %, Quelle bis 30 MB; PDF unverändert bis 8 MiB. PDF wird über FileProvider (`${applicationId}.fileprovider`, Cache-Ordner `receipts/`) in einer externen App geöffnet. Grenzen an einer Stelle: `FileLimits`; Zeitlimit für Dateivorgänge 120 s.
- **Fallstrick Regelabfragen:** Je Transaktion höchstens 20. Stück = 1 Zugriff (`isUser`), Metadaten = 3, Buchung mit Beleg = 3 → rund 16 bei 10 Stücken, ohne Zwischenspeicherung der Regelauswertung gerechnet. Zahlen lassen sich in Regelpfade nicht einsetzen: Stück-IDs kommen aus Listenkonstanten, indiziert mit `chunkCount - 1`. Nur das letzte Stück wird geprüft (`existsAfter`); mittlere Stücke prüft die App beim Zusammensetzen (`FILE_CORRUPT`).
- **Annahmen des Entwicklers (nicht ausdrücklich beschlossen):** Einnahme-Umwandlung entfernt den Beleg; PDF extern statt in der App; keine Kamera in Phase 6 (kommt mit Phase 9); kein Zoom; Dateiname wird bei Bildern `.jpg`.
- **Abgenommen:** Gerätetest G6-01 bis G6-14 bestanden ([`TESTFAELLE.md`](TESTFAELLE.md), 2.4d); gemessen: Foto 687 KB → 85 KB, Ladezustand wie erwartet. Ursprünglich von mir nicht prüfbar: Bild/PDF auswählen, Verkleinerung und Drehung, PDF öffnen, Dauer im Mobilfunk, **gemessene Fotogröße** (das Formular zeigt sie; bitte melden), 10-Stücke-Transaktion gegen die **echte** Datenbank (Emulator: bestanden). Hinweis: **Die Regeln müssen in der Firebase-Konsole neu veröffentlicht werden**, sonst schlägt jedes Speichern eines Belegs fehl. Alte App vor der Installation deinstallieren; unten in den Einstellungen steht „Version 09“.
- **Neu im Code:** `data/files/*` (siehe Plan, Phase 6), `ui/finance/ReceiptScreen.kt` und `ReceiptViewModel.kt`, Abschnitt „Beleg“ in `BookingFormScreen`/`BookingFormViewModel`, `Booking.receipt`, `ReceiptChange`, `AppError.FILE_CORRUPT`, `AppContainer.fileStore/localFileReader/receiptCache`, `ReceiptRoute` in `ui/navigation/`, Manifest und `res/xml/file_paths.xml`.
- **Fehler im Gerätetest G6-05 (Version 09 → 10):** Nach der Dateiauswahl kam die Fehlerseite „Benutzerdaten konnten nicht geladen werden“ (G6-01 bis G6-04 bestanden). Vermutete Ursache: `SessionViewModel` lud das Benutzerdokument nach mehr als 5 s Abwesenheit neu (`WhileSubscribed(5_000)`) und ersetzte den Hauptbereich samt Formular. Behoben mit `SharingStarted.Eagerly` (Version 10); Mit Version 10 bestätigt (G6-05/G6-06 bestanden). **Nächste Version ist 11.**
- **Vorschläge (nicht umgesetzt):** Büroklammer-Symbol in der Buchungsliste, Zoom im Bildschirm „Beleg“, Kamera-Aufnahme im Formular (mit Phase 9).

## 3e. Phase 7 – Kalender (30.09.2026, Details: Plan, Phase 7 und Entscheidungen 30 bis 32)
- **Umgesetzt (Commit `6109e38`, Regel-Test-Korrektur `00e7e5b`, Version 11):** Sammlung `calendarEntries` (`startDate`, `endDate` als Tage, **beide zählen zur Nutzung**; `personUid` + `personName` (Name zum Zeitpunkt des Eintrags); optional `destination` ≤ 100, `comment` ≤ 500; Audit), Regeln `validCalendar` + Tests R-10. Jeder Benutzer darf anlegen, ändern, löschen (Entscheidung 4). **Überschneidungen verbieten die Regeln nicht** (Entscheidung 5).
- **Überschneidung (Entscheidung 30/31):** Mindestens ein gemeinsamer Tag. Gleicher Tag, „Ende = Start des anderen“, umschließend und teilweise überlappend zählen; **direkt aufeinanderfolgende Tage nicht**. Beim Speichern fragt die App **frisch vom Server** (`Source.SERVER`, `startDate ≤ Ende`, Filter im Client, beim Bearbeiten ohne den Eintrag selbst). Bei Treffern Dialog mit Person, Zeitraum und Ziel jedes kollidierenden Eintrags; geschrieben wird erst nach **„Trotzdem speichern“**. **Schlägt die Prüfung fehl (z. B. offline), wird nicht gespeichert.** Bekannte Grenze: Prüfen und Schreiben sind zwei Schritte (Client-Transaktionen können nicht abfragen), gleichzeitiges Speichern zweier Nutzer kann sich überschneiden (Risiko 8, akzeptiert).
- **Ansicht (Entscheidung 32, Annahme):** Karte „Aktuell in Nutzung“, Monatsraster (Montag zuerst; belegt = farbig, mehrfach belegt = rot und fett, heute umrandet; Zustand auch als Text für TalkBack), Monat vor/zurück, „Heute“, Liste der Einträge des Monats, „+“. Formular: Von, Bis, Person (Standard ich, Auswahl unter allen Benutzerkonten), Ziel, Kommentar, Löschen mit Bestätigung. Liegt der neue Start hinter dem Ende, rückt das Ende mit. Vergangene Einträge sind erlaubt. Das Raster ist nur Anzeige.
- **Neu im Code:** `data/calendar/*` (`CalendarEntry`/`CalendarInput`, `CalendarValidator`, `CalendarOverlap`, `CalendarMonth`, `CalendarRepository`), `ui/calendar/*` (`CalendarScreen`, `CalendarFormScreen`, `CalendarViewModel`, `CalendarFormViewModel`), `CalendarFormRoute` in `ui/navigation/Destinations.kt`, Verdrahtung in `ObelixNavHost`/`ObelixApp`, `AppContainer.calendarRepository`, Texte „Kalender (Phase 7)“ in `strings.xml`, Regeln/Tests in `firebase/`.
- **Fallstrick Regel-Test R-03c:** Der Test „noch nicht freigegebene Sammlungen sind gesperrt“ nannte eine Sammlung der nächsten Phase als Beispiel. Mit jeder Phase, die eine Sammlung freigibt, muss er auf eine noch gesperrte zeigen (jetzt `repairs`, Phase 8; danach `campsites`, `documents`). Der erste Lauf von Phase 7 schlug genau daran fehl; die Regeln waren richtig.
- **Erstes ViewModel mit Test:** `CalendarFormViewModel` nimmt optional einen eigenen `CoroutineScope` (`scopeOverride`); `CalendarFormViewModelTest` prüft mit Fakes (Dispatchers.Unconfined) den Speicherablauf ohne Android und ohne Coroutine-Testbibliothek. Dasselbe Muster eignet sich für die nächsten Formulare (Phase 8).
- **Personenfarben (Version 12, Entscheidung 33), G7-15 bestanden:** `data/calendar/PersonColors` (8 Farben ohne Rot/Rosa/Orange, Zuordnung nach sortierter Benutzerkennung, nichts gespeichert), `CalendarMonth.occupants`, `CalendarState.colors/legend`, `CalendarViewModel` lädt zusätzlich die Benutzerliste, UI in `CalendarScreen`. Keine Regel-/Datenänderung. Grenze: Neue Benutzer können die Farben der alphabetisch folgenden Personen verschieben; dauerhaft feste Farben bräuchten ein gespeichertes Feld (Regeländerung). 
- **Abgenommen:** Gerätetest G7-01 bis G7-14 ([`TESTFAELLE.md`](TESTFAELLE.md), 2.4e) bestanden. Hinweis zu den Regeln: nur für Version 11 nötig, bereits veröffentlicht. **Regeln in der Firebase-Konsole neu veröffentlichen**, sonst schlägt jeder Zugriff auf `calendarEntries` mit „Dafür fehlt dir die Berechtigung“ fehl. Alte App vor der Installation deinstallieren; unten in den Einstellungen steht „Version 11“. Von mir nicht prüfbar: Darstellung des Rasters (Farben, Hell/Dunkel, kleine Bildschirme, TalkBack), Bedienung der Auswahlen und des Dialogs, Verhalten gegen das echte Projekt, Offline.
- **Vorschläge (nicht umgesetzt):** Überschneidung schon beim Wählen der Tage im Formular anzeigen; Tippen auf einen Tag legt einen Eintrag an; Dashboard-Anzeige des nächsten Termins kommt mit Phase 11.

## 3f. Phase 8 – Auffälligkeiten (30.09.2026, Details: Plan, Phase 8 und Entscheidungen 34/35)
- **Umgesetzt (Commit `6227ef9`, Version 13):** Sammlung `repairs` (`title` ≤ 200, **`description` Pflicht** ≤ 2000, `date`, `status` `OPEN`/`DONE`, optional `priority`, `comment` ≤ 500, Audit), Regeln `validRepair` + Tests R-11. Jeder Benutzer darf anlegen, ändern, erledigen, wieder öffnen und löschen (Entscheidung 4). Neue Auffälligkeiten sind immer `OPEN`.
- **Ablauf (Entscheidung 34/35, Annahmen):** Bereich „Aufgaben“ = Liste mit Zähler, Filter **Offen (Standard)/Erledigt/Alle**, Sortierung offen vor erledigt, Priorität, neueres Datum. Formular mit „Als erledigt markieren“/„Wieder öffnen“ (speichert die Angaben des Formulars mit, ungültige Eingaben verhindern es), Löschen mit Bestätigung. Kein eigenes Erledigt-Datum.
- **Neu im Code:** `data/repairs/*` (`Models`, `RepairValidator`, `RepairLogic`, `RepairRepository`), `ui/repairs/*` (`RepairListScreen`/`-ViewModel`, `RepairFormScreen`/`-ViewModel`), `RepairFormRoute` in `Destinations.kt`, Verdrahtung in `ObelixNavHost`/`ObelixApp`, `AppContainer.repairRepository`, Texte „Auffälligkeiten (Phase 8)“ in `strings.xml`, Regeln/Tests in `firebase/`. Die Priorität nutzt `Priority` aus `data/planned`.
- **R-03c (Fallstrick aus Phase 7, diesmal vorbeugend):** Der Test „noch nicht freigegebene Sammlungen sind gesperrt“ zeigt jetzt auf `campsites` (Phase 9); danach auf `documents` (Phase 10); seit Phase 10 auf die nicht existierende Sammlung `unbekannt`.
- **Abgenommen:** Gerätetest G8-01 bis G8-12 ([`TESTFAELLE.md`](TESTFAELLE.md), 2.4f) bestanden; die Regeln für `repairs` sind in der Firebase-Konsole veröffentlicht.
- **Vorschläge (nicht umgesetzt):** „Zuletzt geändert von/am“ im Formular anzeigen; Dashboard-Zähler offener Auffälligkeiten kommt mit Phase 11; Foto zur Auffälligkeit nach Phase 9 (Kamera vorhanden).

## 3g. Phase 9 – Stellplätze (30.09.2026, Details: Plan, Phase 9 und Entscheidungen 36 bis 39)
- **Umgesetzt (Commit `961f686`, Version 14; abgenommen am 01.10.2026, G9-01 bis G9-18 bestanden):** Sammlung `campsites` (`latitude`, `longitude`, `date` unveränderlich; `comment` Pflicht ≤ 500; optional `name`, `address`, `note`, `rating` 1–5; `photos` = Liste von höchstens 3 Dateiverweisen, JPEG je höchstens 1 Stück; Audit). Standort **einmalig auf Knopfdruck** (Fused Location), Liste und **Karte (osmdroid 6.1.20, OpenStreetMap, ohne API-Schlüssel)**, Formular mit Fotos (Kamera über den System-Dialog ohne Kamera-Berechtigung, Galerie), Sterne, „Navigation starten“ (`google.navigation:`, sonst `geo:`), Löschen mit Bestätigung und mit den Fotos.
- **Neu im Code:** `data/campsites/*`, `ui/campsites/*` (`CampsiteMap` ist die **einzige** Datei mit osmdroid), `data/files/PhotoCompression`/`PhotoCameraCache`, `LocalFileReader.readPhoto`, `CampsiteFormRoute` (Position als Text), `AppContainer` (`campsiteRepository`, `locationProvider`, `photoCameraCache`), Manifest (Standort; `WRITE/READ_EXTERNAL_STORAGE` der Bibliothek mit `tools:node="remove"` entfernt), `file_paths.xml` (`camera/`), CI-Schritt „Berechtigungen der App anzeigen“.
- **Fallstricke / Erkenntnisse:**
  - **Regelbudget:** Stellplatz + 3 neue Fotos ≈ 19 von 20 Abfragen je Transaktion; deshalb ist **jedes Foto auf ein Stück (900 KiB) begrenzt**. Bei Änderungen an `validCampsite`/`photoOk` das Budget neu zählen (R-12d testet den Grenzfall im Emulator; die echte Datenbank prüft G9-09).
  - Die Regelsprache hat keine Schleifen: Fotos werden über die Positionen 0 bis 2 geprüft (`photoOk`), nicht über eine Liste.
  - **osmdroid ist archiviert** (November 2024). Nutzungsregeln der OSM-Kacheln einhalten (User-Agent = Paketname, Quellenangabe, kein Vorabladen, Cache höchstens 50 MB). Ersatz betrifft nur `CampsiteMap.kt`.
  - Lint verbietet `LocalContext.current.getString(...)` in Compose-Lambdas (`LocalContextGetResourceValueCall`): Text vorher mit `stringResource` holen. Dieser eine Fehler war im ersten Lauf der einzige.
  - Die Datei `CampsiteFormViewModelTest` nutzt `addPhotoFrom { }` statt `Uri`, damit die JVM-Tests kein Android brauchen (Muster aus Phase 7: `scopeOverride` + Fakes).
  - **Karten-Fallstricke (Gerätefund):** eine Android-View in Compose immer mit `clipToBounds` einbetten (sonst überdeckt sie Nachbar-Elemente); `zoomToBoundingBox` nie mit sehr engem Ausschnitt aufrufen (Zoom jenseits der Kachelgrenze → leere Karte). Seit Version 17 gilt Zoom 3 bis 19.
  - Auf dem Gerät bestätigt (G9, vorher von hier nicht prüfbar): GPS, Berechtigungsdialog, Kamera, Kartendarstellung, Navigation, drei große Fotos gegen die echte Datenbank, Berechtigungen der APK.
- **R-03c und R-11h** zeigten auf `documents`; in Phase 10 vorbeugend umgestellt (siehe 3h).
- **Vorschläge (nicht umgesetzt):** Fotos im Vollbild mit Zoom; Stellplatz-Bewertung/Filter in der Liste; Kartenbibliothek später durch MapLibre ersetzen; Dashboard-Hinweis „letzter Stellplatz“ in Phase 11.

## 3h. Phase 10 – Dokumente (01.10.2026, Details: Plan, Phase 10 und Entscheidungen 40 bis 43)
- **Umgesetzt (Commit `4fdf6b5`, Version 18; Gerätetest G10-01 bis G10-14 bestanden, abgenommen 01.10.2026):** Sammlung `documents` (`name` 1–100, `category` eine von sechs festen Werten, `file` = Dateiverweis wie beim Beleg, `date` = Tag des Hochladens, Audit). Genau eine Datei (Bild oder PDF, höchstens 8 MiB) in der Dateiablage aus Phase 6; die Datei ist nach dem Hochladen unveränderlich (Regeln `newReceiptOk`/`validReceiptRef` unverändert wiederverwendet). Liste mit Kategorie-Filter, Formular (Hochladen, Name und Kategorie ändern, Öffnen, Löschen mit Bestätigung; jeder Benutzer darf löschen, Entscheidung 4).
- **Neu im Code:** `data/documents/*`, `ui/documents/*`, `DocumentFormRoute`/`DocumentViewRoute`, `AppContainer.documentRepository`, `ReceiptScreen` (optionale Textparameter, Standard = Belegtexte), Regeln `validDocument`, Regel-Tests R-13, Unit-Tests A-44 bis A-47.
- **Fallstricke / Erkenntnisse:**
  - **R-03c, R-11h und R-12l** (drei Stellen, nicht zwei) zeigten auf `documents`; alle drei nutzen jetzt `unbekannt`. Beim Freigeben der nächsten Sammlung wieder prüfen, ob ein Test auf sie zeigt.
  - Regelbudget: 10 Stücke + Datei + Dokument ≈ 15–16 von 20, wie beim Beleg (R-13b testet den Grenzfall im Emulator; die echte Datenbank prüft G10-08).
  - Hat eine Datei keinen Anzeigenamen, setzt `LocalFileReader` „Beleg“/„Beleg.pdf“ (kleine Schwäche aus Phase 6, unverändert).
  - Der erste Lauf war grün (kein Lint-Fehler), weil die Lehren aus Phase 9 (Texte vorher mit `stringResource` holen) von Anfang an beachtet wurden.
- **Auf dem Gerät bestätigt (G10):** Dateiauswahl, PDF-Öffnen in einer externen App, Darstellung, große Datei gegen die echte Datenbank, Offline, zwei Konten; die Regeln für `documents` sind veröffentlicht.
- **Vorschläge (nicht umgesetzt):** Suche nach Namen; Vorschau-Bild in der Liste; Datei austauschen (bräuchte eine Regeländerung); Teilen/Herunterladen; Dashboard-Hinweis „zuletzt hochgeladenes Dokument“ in Phase 11.

## 3i. Phase 11 – Dashboard (01.10.2026, Details: Plan, Phase 11 und Entscheidungen 44/45)
- **Umgesetzt (Commit `5d205b3`, Version 19; Gerätetest G11-01 bis G11-12 bestanden):** Das Dashboard (Startseite) zeigt fünf Kacheln aus den echten Daten: **Kalender** („Aktuell in Nutzung“, sonst „Nächster Termin“, sonst „Kein Termin geplant.“), **Finanzen** (Kontostand, „Ausgaben <Jahr>“, offene Forderungen; ohne Buchungen „Noch keine Buchungen vorhanden.“ statt 0,00 €), **Offene Auffälligkeiten**, **Geplante Ausgaben** (Anzahl offen, Schätzsumme; ändern den Kontostand nie), **Gespeicherte Stellplätze**. Tippen auf eine Kachel öffnet den Bereich.
- **Annahmen (nicht ausdrücklich beschlossen, Entscheidungen 44/45):** „Aktueller Zeitraum“ = **laufendes Kalenderjahr** (Entscheidung 8 hatte „vorerst kein Zeitraum“ gesagt, die Anforderung 10 nennt ihn aber als Mindestangabe); Jahreszahl zählt alle Ausgaben des Jahres, auch offene und gesponserte. Keine Dokumente-Kachel, kein „letzter Stellplatz“, keine Diagramme.
- **Technik:** `data/dashboard/DashboardLogic` (reine Logik), `ui/dashboard/DashboardViewModel` + `DashboardScreen`, Verdrahtung in `ObelixNavHost`. Je **Quelle** ein eigener Zustand (`Source`: Wert und Fehler): eine fehlgeschlagene Quelle zeigt „Nicht geladen“, **nie eine 0**; ein früherer Wert bleibt nach einem Fehler stehen und der Fehler wird zusätzlich gemeldet. Ein Laden je Öffnen und je Rückkehr in die App (`ON_RESUME`), keine Dauer-Listener; ein neues Laden ersetzt ein laufendes. **Keine Regeländerung**, daher kein Regelbudget-Thema und **keine neue Veröffentlichung der Regeln nötig**.
- **Fallstricke / Erkenntnisse:**
  - Mein erster Plan versprach, die Jahreszahl müsse „genau mit dem Finanzbereich übereinstimmen“. Der Finanzbereich zeigt keine Jahressumme (nur „Ausgaben gesamt“); die Zahl entspricht der Summe der Zeilen bei Filter „Ausgabe“ + Jahr. Test A-48 prüft das gegen `FinanceFilter`, G11-03 von Hand.
  - Der Platzhalter `SectionNotAvailableScreen` (Text `section_not_available`) ist jetzt **unbenutzt** und bleibt bis zur Code-Bereinigung in Phase 12 bestehen.
  - Lint-Lehre aus Phase 9 (Text vorher mit `stringResource` holen, kein `LocalContext.current.getString` in Lambdas) von Anfang an beachtet; der erste Lauf war grün.
  - Das ViewModel nutzt wie in Phase 7 bis 10 `scopeOverride` + Fakes für JVM-Tests (24 neue Tests, 13 Logik + 11 ViewModel).
- **Abgenommen:** Der Benutzer meldet G11-01 bis G11-12 als bestanden (01.10.2026).
- **Von mir nicht prüfbar (G11, vom Benutzer bestätigt):** Darstellung (Hell/Dunkel, große Schrift, kleine Bildschirme, TalkBack), Zahlen gegen das echte Projekt, Bedienung, Flugmodus, Anzeige bei **völlig leerer** Datenbank (nur das ViewModel ist getestet).
- **Vorschläge (nicht umgesetzt):** Kachel „zuletzt hochgeladenes Dokument“ und „letzter Stellplatz“; Aktualisierung per Wischgeste; Einnahmen des Jahres; Personenfarbe beim nächsten Termin.

## 4. Technische Fallstricke (aus diesem Chat gelernt)
- **Kein Android-SDK in der Cloud-Sitzung.** Netzzugang zu `dl.google.com`, `maven.google.com`, `services.gradle.org` ist gesperrt. Gebaut und getestet wird **in GitHub Actions**. Kompilierfehler stehen als Annotation am Lauf (Job „Fehler zusammenfassen"), abrufbar mit  
  `curl https://api.github.com/repos/Hagi089/Obelix/check-runs/<JOB-ID>/annotations` (Job-ID über `/actions/runs/<RUN-ID>/jobs`). Die Rohlogs sind nicht erreichbar.
- **Firebase-Emulator und npm:** In der Cloud-Sitzung sind die npm-Registry und der Emulator-Download gesperrt (403). Die Regel-Tests laufen deshalb nur in GitHub Actions (Job „rules": Node 22, Java 21, `firebase-tools`, `@firebase/rules-unit-testing` 3.0.4 mit `firebase` 10.14.x). Das Ergebnis liest man am Lauf ab; die Testanzahl steht als Hinweis („Regel-Tests") in den Annotationen.
- **Direkte Aufrufe an dein Firebase-Projekt** sind aus der Sitzung blockiert. Alles, was echtes Firebase braucht (Anmeldung, Datenbank, Regeln „live"), muss auf dem Gerät oder in der Konsole geprüft werden. Regeln veröffentlicht der Benutzer in der Firebase-Konsole (Firestore → Regeln) oder per `firebase deploy` (ohne Blaze möglich).
- **Git:** Der Benutzer lädt manchmal Dateien über die GitHub-Weboberfläche hoch (z. B. `google-services.json`). Vor jedem Push `git pull --rebase origin main`. Ein Stop-Hook fordert Commit und Push bei ungetrackten Dateien.
- **Commit-Zusatzzeilen** am Ende jeder Commit-Nachricht: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` und `Claude-Session: <URL aus der Sitzungsvorgabe>` (bei neuem Chat die dort genannte aktuelle Zeile verwenden).
- **Nicht im Repository (bewusst):** Excel-Datei, Analyse mit Namen und Beträgen, `obelix-import.json` (Muster `obelix-import*.json` ist ignoriert). Nie committen. Die Analyse steht im Claude-Projekt als `Excel-Analyse`.
- **Firestore-Transaktionen:** Regeln erlauben rund 20 Zugriffe je Transaktion; Import und Kategorie-Anlage arbeiten deshalb in Blöcken zu 10 (nicht empirisch als nötig bewiesen, bewusst vorsichtig).
- **Regel-Tests:** Bei Schreibtests muss `createdBy` der angemeldeten uid entsprechen, sonst schlägt der Test aus dem falschen Grund fehl.
- **Android:** kein `readNBytes` (erst Android 13), `import` ist ein weiches Schlüsselwort (Methode `importBookings`).
- **Transaktionen mit mehreren Dokumenten (Phase 5):** In Firestore-Transaktionen erst lesen (`tx.get`), dann schreiben. Regeln mit `existsAfter`/`getAfter` prüfen den Zustand **nach** dem gesamten Schreibvorgang; so verknüpfen sie Planung und Buchung. Die Regel-Tests bilden die Schreibform der App nach (gleiche Felder wie `bookingCreateData` und `PlannedExpenseRepository`); ändert sich eine Schreibform in der App, muss der Test mitgezogen werden. Eine in der Transaktion geworfene `AppException` kommt direkt oder als Ursache an; `ErrorMapper.classify` behandelt beides.
- **Dateien in Firestore (Phase 6):** Firestore-Dokument höchstens 1 MiB, Anfrage höchstens 10 MiB, Transaktion bis 270 s (60 s Leerlauf); die App-Standardgrenze von 20 s ist für Dateien zu kurz (`FileLimits.TIMEOUT_MS` = 120 s). Der Emulator-Test R-09b schreibt die größte Datei mit Buchung in einer Transaktion; ob die echte Datenbank dasselbe Regelbudget ansetzt, prüft G6-06. Ein Test, der mehrere Firestore-Instanzen in einer Transaktion nutzt, schlägt fehl – der Regel-Test benutzt dafür eine einzige `adminDb`.
- **`google-services.json`** liegt (vom Benutzer hochgeladen) im öffentlichen Repository unter `app/`. Kein Geheimnis im Firebase-Sinn, aber jeder kann damit Konten anlegen; die Daten schützen die Regeln (Phase 3).
- Versionen (geprüft 30.09.2026): AGP 9.3.3, Gradle 9.5.1, Kotlin 2.4.10, Compose BOM 2026.09.00, Navigation 2.10.2, Lifecycle 2.11.0, Firebase BoM 34.19.0, `compileSdk 37`, `targetSdk 36`, `minSdk 26`. Bei AGP 9 wird das Kotlin-Plugin **nicht** mehr separat angewendet.

## 5. Offene Punkte
| Nr. | Punkt | Vorschlag |
|---|---|---|
| 3 | Kartenlösung für Stellplätze | ✅ erledigt (Annahme, Entscheidung 36): osmdroid 6.1.20 mit OpenStreetMap-Kacheln; Bibliothek archiviert, Ersatz später nur in `CampsiteMap.kt` |
| 9 | Löschen: hart oder als „gelöscht" markiert | hart, mit Bestätigung |
| 10 | `google-services.json` im Repository lassen | ja (CI-APK ist dann sofort testbar) |
| 15b | „Verantwortung" aus der Excel | ✅ erledigt: an den Kommentar angehängt |
| 15c | Nullbeträge der Excel (25 Zeilen) | ✅ erledigt: nicht importiert, in der Vorschau aufgelistet |
| – | API-Schlüssel in der Google Cloud Console auf die App beschränken | braucht festen Debug-Schlüssel (SHA-1); CI erzeugt bei jedem Bau einen neuen. Vorschlag: festen Debug-Schlüssel für CI anlegen (nur zum Testen, keine Geheimnisse) |
| 6a | Belege: Büroklammer in der Liste, Zoom, Kamera-Aufnahme direkt im Buchungsformular | Büroklammer und Zoom auf Wunsch; Kamera-Aufnahme für Belege wäre mit `PhotoCameraCache` jetzt leicht möglich (auf Wunsch) |
| 6b | Fotogröße nach der Kompression | ✅ erledigt: 687 KB → 85 KB gemessen (Plan, Abschnitt 6) |
| 7a | Kalender: Überschneidung schon beim Wählen der Tage anzeigen; Tippen auf Tag im Raster | auf Wunsch |
| 11a | Dashboard: Kachel „zuletzt hochgeladenes Dokument“ / „letzter Stellplatz“, Aktualisierung per Wischgeste, Einnahmen des Jahres | auf Wunsch (nicht umgesetzt) |
| – | `targetSdk` von 36 auf 37 | später |
| – | ViewModel-Tests für Anmeldung, Einrichtung und Einstellungen: mit dem Muster aus Phase 7 (`scopeOverride` + Fakes) oder der Coroutine-Testbibliothek | bei Gelegenheit |
| – | Mutationsprüfung der Regel-Tests (Regel bewusst schwächen, Test muss rot werden) | bei Gelegenheit |

Für offene Punkte gelten bis zur Antwort des Benutzers die Vorschläge.

## 6. Arbeitsregeln (aus Projektanweisungen und Anforderungen)
- Rolle: Senior Softwareentwickler und Senior QA Engineer. Erst analysieren, dann ändern; nur das Nötige; keine unnötigen Refactorings; nach jeder Änderung auf Regressionen prüfen; kurze Zusammenfassung geänderter Dateien und Funktionen.
- Nichts als „fertig" melden, was nicht getestet ist. Was ich nicht selbst testen kann, ausdrücklich als „vom Benutzer zu prüfen" kennzeichnen.
- Keine erfundenen Daten, keine ungefragten Erweiterungen (Verbesserungen nur vorschlagen), keine kostenpflichtigen Dienste.
- Nach jeder Phase: Implementieren → Bauen (GitHub) → Tests → Fehler beheben → Plan und `TESTFAELLE.md` aktualisieren → offene Punkte nennen → erst dann weiter. Aktuelle und zu erledigende Testfälle stehen immer in `docs/TESTFAELLE.md`.
- Plan und Testfälle zusätzlich im Claude-Projekt aktualisieren (`claude/Projektplan`, `claude/Testfaelle`).

---

## 7. Prompt für den neuen Chat (Phase 12)

Kopiere den folgenden Block als erste Nachricht in den neuen Chat (im selben Claude-Projekt „Obelix Wohnmobil App"). Der Gerätetest G11 ist bestanden.

```text
Wir arbeiten am Projekt OBELIX (native Android-App für das gemeinsame Familien-Wohnmobil). Das GitHub-Repository heißt Obelix (Hagi089/Obelix, Branch main). Bitte binde es ein und lies zuerst diese Dateien, bevor du etwas änderst:
1. docs/UEBERGABE.md (Stand, Entscheidungen, Fallstricke)
2. docs/PROJEKTPLAN.md (Plan, Datenmodell, Security-Konzept, Phasen)
3. docs/TESTFAELLE.md (aktuelle und offene Testfälle)
4. im Claude-Projekt das Dokument "Anforderungen" (verbindliche Anforderungen)

Stand: Phase 1 bis 10 abgenommen. Phase 11 (Dashboard) ist Version 19 (Commit 5d205b3; Bau, Lint, 293 Unit-Tests, 114 Regel-Tests grün; keine Regeländerung, die Regeln müssen nicht neu veröffentlicht werden). Gerätetest G11-01 bis G11-12: alle bestanden.

Auftrag jetzt: PHASE 12 – Qualitätssicherung gemäß Plan und Anforderungen Abschnitt 42, 43, 54 und 60 (Abschlusskriterien): Gesamttest, Code-Bereinigung (u. a. der unbenutzte SectionNotAvailableScreen), README vollständig, Nachweis jedes Abschlusskriteriums. Erst analysieren und einen kurzen Plan zeigen, Fragen nur, wenn sie wirklich meine Entscheidung brauchen. Beachte die Fallstricke aus UEBERGABE.md Abschnitt 3g, 3h, 3i und 4 (Regelbudget, Lint, Android-Views in Compose).

Rahmenbedingungen wie bisher: Senior Softwareentwickler und Senior QA Engineer, erst analysieren, nur das Nötige ändern, auf Regressionen prüfen, kurze Zusammenfassung der geänderten Dateien; alles kostenlos (Firebase Spark, ohne Cloud Functions), keine erfundenen Daten, keine ungefragten Erweiterungen; gebaut und getestet wird in GitHub Actions (kein Android-SDK, kein npm in der Cloud-Sitzung); was du nicht testen kannst, kennzeichne als "von mir zu prüfen"; vor jedem Push git pull --rebase origin main; Plan, docs/TESTFAELLE.md und die Projektdokumente (claude/Projektplan, claude/Testfaelle, claude/Uebergabe) am Ende aktualisieren. Zu den offenen Punkten in docs/UEBERGABE.md (Abschnitt 5) gelten deine Vorschläge, sofern ich nichts anderes sage. Excel-Dateien, Analysen und Importdateien mit Namen/Beträgen nie committen. Bei jedem Deployment die Version erhöhen (versionCode +1, versionName zweistellig, nächste ist 20, in app/build.gradle.kts; die Version steht unten in den Einstellungen). Beim Warten auf GitHub Actions nur kurze Abfragen (unter 2 Minuten je Befehl), sonst bricht der Befehl ab.
```
