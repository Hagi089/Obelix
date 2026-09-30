# OBELIX – Analyse und Implementierungsplan (Phase 0)

Stand: 30.09.2026 (Rev. 11: **Phase 5 – Geplante Ausgaben umgesetzt** (Version 08), Bau und Tests grün, Gerätetest G5 offen; Phase 4 abgenommen; UI-Überarbeitung gebaut, Gerätetest GU offen) · Status: **freigegeben, in Umsetzung** (Phase 1 bis 4 abgenommen, Phase 5 wartet auf Gerätetest, nächste Phase 6).

> Datenschutz: Dieses Repository ist öffentlich. Die Excel-Datei und die detaillierte Analyse mit Namen und Beträgen liegen lokal im ignorierten Ordner `private/` und im nicht-öffentlichen Claude-Projekt (`Excel-Analyse`). Hier steht nur die anonymisierte Struktur.

Grundlage: Projektwissen „Anforderungen" (verbindlich) und der Ist-Zustand des GitHub-Repositorys `Hagi089/Obelix`.

---

## 0. Getroffene Entscheidungen (30.09.2026)

| # | Thema | Entscheidung |
|---|---|---|
| 6 | Import | Die 325 Excel-Buchungen werden **einmalig importiert**. |
| 11 | Zahler | „Bezahlt von" = **Benutzer** (Konto), angezeigt mit seinem Namen. Keine Parteien, keine Gruppen (geändert 30.09.2026, ersetzt die frühere Partei-Lösung). Beim Import werden die zwei Excel-Zahler je einem Benutzerkonto zugeordnet; die Zuordnung steht im nicht-öffentlichen Projekt-Dokument `Excel-Analyse`. |
| 12 | Kosten beglichen | Bestätigt. Ablauf: Ein Benutzer legt Kosten aus und trägt sie ein → Status **offen** (Excel „nein") → nach Erstattung **beglichen** (Excel „ja", zählt zum Kontostand). **Gesponsert** = keine Erstattung, zählt nicht zum Kontostand, erscheint aber in der Übersicht der Gesamtausgaben. Excel „gesponsert" und „wird gesponsert" werden beide zu „gesponsert". |
| – | Vermutliche Fehler in der Excel | Werden **unverändert importiert**; der Benutzer korrigiert sie selbst in der App (oder vorher in der Excel). Die betroffenen Zeilen stehen in der nicht-öffentlichen Excel-Analyse. |
| – | Nachkommastellen | Beträge haben **genau 2 Nachkommastellen** (Speicherung in Cent). Beim Import wird kaufmännisch gerundet. Nachgerechnet: Der Kontostand bleibt dabei unverändert; die Summe der Einzahlungen ändert sich um 1 Cent. |
| 15 | Kosten pro Tag | **Entfällt.** |
| 13 | Kategorien | Einmalig aus der Excel übernehmen; in den **Einstellungen** können neue Kategorien angelegt werden. |
| 14 | Reihenfolge | **Finanzen werden vorgezogen** (direkt nach Benutzern/Rollen), geplante Ausgaben direkt danach. |
| 1 | Dateispeicher | **Option F:** Dateien gestückelt in Firestore (Spark, ohne Kreditkarte). Bestätigt am 30.09.2026. |
| 2 | Firebase-Projekt | Legt der Benutzer selbst an, Region **Deutschland (`europe-west3`, Frankfurt)**. Anleitung: `docs/FIREBASE-EINRICHTUNG.md`. |
| 2b | Registrierung | **Kein Haushalt** (entschieden 30.09.2026): Alle registrierten Benutzer teilen denselben Datenbestand des Wohnmobils. Registrierung mit Name, E-Mail, Zugangscode und Passwort; danach Anmeldung nur mit E-Mail und Passwort. **Ein gemeinsamer Zugangscode**, nur der ADMIN sieht und erneuert ihn. Rollen: der Projektinhaber ist **ADMIN**, alle anderen **MEMBER**. Durchsetzung serverseitig über Firestore-Regeln (Abschnitt 7), ohne Cloud Functions. |
| 4 | Löschen | **Jedes Mitglied darf löschen** (immer mit Bestätigungsdialog). Entsprechend werden die Rules gebaut. |
| 5 | Kalender-Überschneidung | Speichern bleibt **erlaubt**, aber die Überschneidung muss **vor dem Speichern** geprüft und **eindeutig angezeigt** werden (welcher Eintrag, welche Person, welcher Zeitraum); Speichern nur nach ausdrücklicher Bestätigung. |
| 7 | Navigation | **Sechs Bereiche** (Dashboard, Kalender, Finanzen, Aufgaben, Stellplätze, Dokumente), Einstellungen über Zahnrad. |
| 8 | Dashboard-Zeitraum | Vorerst nicht nötig; Dashboard zeigt zunächst nur Bestände und Zähler ohne Zeitraum. |
| – | Design | **Google-Richtlinien:** Material 3 mit Systemfarben, Android-Architekturleitfaden, Kotlin-Styleguide, Barrierefreiheit (Kontrast, Beschriftungen, Touch-Ziele ≥ 48 dp). |
| 3, 9, 10 | Karte, Löschart, `google-services.json` | Noch nicht ausdrücklich entschieden; es gelten meine Vorschläge: OSM-Karte (Prüfung in Phase 9), hartes Löschen mit Bestätigung, `google-services.json` nicht committen. |
| 16 | Import-Weg (Phase 4) | **Weg B: einmaliger Import in der App, nur ADMIN** (entschieden 30.09.2026). Die Excel wird außerhalb der App in eine private Datei `obelix-import.json` umgewandelt (Cent gerundet, Status und Zahler-Bezeichnung zugeordnet, erwartete Kontrollwerte enthalten). Die App prüft die Datei, vergleicht die Kontrollwerte **vor** dem Schreiben, lässt die zwei Excel-Zahler je einem Benutzerkonto zuordnen (Auswahl aus der Benutzerliste, **keine E-Mail-Adressen nötig**) und schreibt nach Bestätigung. Kein Node-Skript, keine Service-Account-Datei. Der Import lässt sich wiederholen, ohne Buchungen doppelt anzulegen (feste ID `xl-<Excel-Zeile>`, Feld `importRef`). Die Importdatei liegt nie im Repository (`.gitignore`: `obelix-import*.json`). Der Import-Code kann nach dem Import wieder entfernt werden (Phase 12). |
| 17 | Kategorien ohne Art (Phase 4) | Das Feld `type` (Einnahme/Ausgabe) entfällt: „Sonstiges" wird in der Excel für Ausgaben **und** Einnahmen benutzt (2 Sonderleistungen). Eine Kategorie gilt für beide. |
| 18 | Kontostand-Berechnung (Phase 4) | Clientseitig aus **einer** Abfrage aller Buchungen je Öffnen des Finanzbereichs (~300 Lesevorgänge bei 50.000 pro Tag im Spark-Kontingent). Firestore-Aggregationen (`sum()`) nicht nötig; ob sie einen zusätzlichen Index bräuchten, wurde nicht geprüft. |
| 19 | Filter „Zeitraum" (Phase 4) | Als **Jahresfilter** umgesetzt (einfachste Lösung, Auslegung des Plans). |
| 20 | Einnahmen mit Person (Phase 4) | „Bezahlt durch" der Excel wird bei Einnahmen zum **Einzahler** (`paidByUid`, in der App „Eingezahlt von", optional). |
| 21 | Geplante Ausgaben: Einstieg (Phase 5) | Unterbereich des Finanzbereichs: Schaltfläche „Geplante Ausgaben“ in der Finanzübersicht öffnet die Liste (Vorschlag des Plans, Abschnitt 8; keine siebte Hauptnavigation). Filter **Geplant** (Standard) / **Gekauft** / **Alle**. Die Summe der offenen Schätzungen steht in der Liste, **nicht** im Kontostand (Anforderung 16). |
| 22 | Kauf einer Planung (Phase 5) | Der Dialog „Gekauft“ fragt tatsächlichen Betrag (vorbelegt mit der Schätzung), Kaufdatum (heute), Bezahlt von (angemeldeter Benutzer), Kategorie (Pflicht, keine Vorbelegung) und Abrechnung (vorbelegt „Offen“, wie im Buchungsformular). Bezeichnung und Kommentar der Planung werden Beschreibung und Kommentar der Ausgabe. Eine gekaufte Planung ist nicht mehr änderbar (nur ansehen, Buchung öffnen, löschen). |
| 23 | Buchung aus einem Kauf löschen (Phase 5, **Annahme, nicht ausdrücklich beschlossen**) | Wird die aus einem Kauf entstandene Buchung gelöscht, setzt die App die Planung im selben Schritt wieder auf **Geplant**; sonst wäre der Betrag weder geplant noch ausgegeben. Der Löschdialog weist darauf hin. Löschen der *Planung* lässt die Buchung bestehen. Eine solche Buchung bleibt eine Ausgabe (Art nicht änderbar). |

**Annahmen, die ich getroffen habe (bitte widersprechen, falls falsch):**
- Übernommen werden die **12 tatsächlich verwendeten** Kategorien, unverändert (auch „Werkstatt" neben „TÜV/Werkstatt"). Die nie benutzten `Look`-Einträge werden nicht angelegt, sie können über die Einstellungen ergänzt werden.
- **Kategorien anlegen dürfen alle Mitglieder**; umbenennen/deaktivieren nur ADMIN. Kategorien werden nie gelöscht, nur deaktiviert, damit alte Buchungen gültig bleiben.
- **Als erstattet markieren** darf jedes Mitglied (Aktion „Erstattet" an einer offenen Ausgabe).
- „Verantwortung" (Freitext in der Excel) wird **nicht** als eigenes Feld übernommen; beim Import wird der Wert an den Kommentar angehängt, damit nichts verloren geht. Hierzu hast du dich noch nicht geäußert (Entscheidung 15b).
- Die 25 Buchungen mit Betrag 0 (Inventarliste) werden **nicht** als Finanzbuchungen importiert, da ein Betrag > 0 Pflicht ist (Anforderung 35). Sie stehen nach dem Import in einer Protokolldatei. Wenn du sie behalten willst, bitte sagen (Entscheidung 15c).

---

## 1. Ist-Zustand des Repositorys

| Prüfpunkt | Befund |
|---|---|
| Repository | `Hagi089/Obelix`, öffentlich, Schreibzugriff vorhanden |
| Klon | erfolgreich, Meldung „empty repository" |
| Remote-Refs (`git ls-remote`) | **keine** – kein Branch, kein Commit, keine Tags |
| Dateien | **keine** (weder Kotlin, Compose, Gradle, Manifest, Ressourcen, Firebase-Konfiguration, Tests, README noch `.gitignore`) |
| Excel-Datei | Zunächst nicht vorhanden; am 30.09.2026 nachgereicht (`Einkausliste_WoMo_v2_1.xlsx`), liegt lokal in `private/` (per `.gitignore` vom Commit ausgeschlossen). Analyse: Abschnitt 3 |
| Dokumentation | Nur das Projektwissen „Anforderungen" (im Claude-Projekt, nicht im Repo) |

**Schlussfolgerung:** Es gibt keinen wiederverwendbaren Code. Das Projekt beginnt bei null. Der Grundsatz „vorhandenen Code erhalten" greift hier nicht, weil nichts vorhanden ist. Es wird also nichts ersetzt oder gelöscht.

> Falls du lokal bereits ein Android-Projekt oder die Excel-Datei hast, die nur nicht gepusht wurden: Bitte pushen bzw. hier anhängen, bevor Phase 1 beginnt. Sonst entsteht doppelte Arbeit.

### Bestandsaufnahme

- **Bereits vorhanden:** nichts.
- **Teilweise vorhanden:** nichts.
- **Noch nicht vorhanden:** alle Anforderungen (siehe Matrix).
- **Technische Probleme / Schulden im Bestand:** keine, da kein Bestand. Die relevanten Risiken stehen in Abschnitt 12.

---

## 2. Abgleich Anforderungen ↔ Repository

| Bereich | Anforderung (Kurzform) | Status | Bemerkung |
|---|---|---|---|
| Projektbasis | Kotlin, Compose, Material 3, Gradle, Navigation | fehlt | Kein Projekt |
| Authentifizierung | E-Mail/Passwort, Login, Logout, Passwort zurücksetzen (Firebase Auth) | fehlt | |
| Benutzer | Mehrere Benutzer, Haushalt (`householdId`), Rollen ADMIN/MEMBER | fehlt | |
| Finanzen | Einnahmen, Ausgaben, Kategorien, „Bezahlt von", Bestand = Anfangsbestand + Einnahmen − Ausgaben, Belege | fehlt | Excel analysiert; Struktur bekannt (Abschnitt 3); Deutung „Kosten beglichen" muss bestätigt werden |
| Geplante Ausgaben | Status GEPLANT/GEKAUFT, Umwandlung in echte Ausgabe mit tatsächlichem Betrag | fehlt | |
| Kalender | Nutzungseinträge, Überschneidungswarnung | fehlt | |
| Reparaturen | Auffälligkeiten, OFFEN/ERLEDIGT, Filter, Wiedereröffnen | fehlt | |
| Stellplätze | GPS, Kommentar, bis 3 Fotos, Karte, Navigation extern | fehlt | |
| Fotos | Max. 3 pro Stellplatz, Verkleinerung auf ~1600–2000 px | fehlt | Speicherort offen (Abschnitt 11, Entscheidung 1) |
| Dokumente | Upload, Kategorie, öffnen, löschen, nicht öffentlich | fehlt | Speicherort offen |
| Dashboard | Kennzahlen aus echten Daten | fehlt | erst nach den Datenbereichen |
| Security | Firestore-/Storage-Rules, Haushaltstrennung, Rollen | fehlt | |
| Online/Offline | Klare Fehler, kein falscher Erfolg, Offline-Hinweis | fehlt | |
| Tests | Auth, Rollen, Bereiche, Offline, 2-Haushalte-Sicherheitstest | fehlt | |
| GitHub | `.gitignore`, README, keine Secrets | fehlt | |

---

## 3. Excel-Analyse

Datei `Einkausliste_WoMo_v2_1.xlsx`, vollständig gelesen (Werte und Formeln), Kennzahlen nachgerechnet. Detailfassung mit Namen und Beträgen: nicht-öffentlich (siehe Kopf). Nichts wurde geschätzt oder korrigiert.

**Aufbau (3 Blätter)**
| Blatt | Inhalt |
|---|---|
| `Datenerfassung` | Datenbasis: 325 Buchungen (Zeilen 4–328), Zeitraum 01/2016 bis 08/2026, Spalten A–H, **keine Formeln** |
| `Kontostand` | 5 Kennzahlen-Formeln, eine Pivot-Tabelle, eine „Kosten pro Tag"-Rechnung |
| `Look` | Werteliste der Kategorien (15 Einträge) |

**Spalten der Datenbasis:** `erledigt` (ja/nein) · `Kategorie` · `Was` · `Verantwortung` (Freitext) · `Preis` (negativ = Ausgabe, positiv = Einzahlung) · `Anschaffungsdatum` · `Bezahlt durch` · `Kosten beglichen`.

**Personen:** Zwei **Parteien** (zwei Paare, vier Personen) besitzen das Wohnmobil gemeinsam. „Bezahlt durch" kennt nur die zwei Parteien, nicht einzelne Personen. „Verantwortung" ist Freitext mit 10 Schreibweisen.

**Kategorien:** 12 in den Daten verwendet: Einzahlung, Inventar, Lfd. Verbrauchsgüter, Reparatur, Elektro, Versicherung/Steuer, TÜV/Werkstatt, Sonstiges, Mobiliar aussen, Mobiliar innen, Wohnmobilkauf, Werkstatt. `Look` enthält zusätzlich Bad, Erstanschaffung, Küche und „Schalfen" (vermutlich Tippfehler), die nie benutzt wurden; „Werkstatt" fehlt dort. Kategorien sind in der Excel nicht durchgängig erzwungen.

**Anfangsbestand:** Es gibt **keinen separaten Anfangsbestand.** Die beiden ersten Buchungen sind die Anfangszahlungen als normale „Einzahlung". Rechnerischer Anfangsbestand = 0.

**Kennzahlen-Logik (Blatt `Kontostand`)**
- *Kontostand aktuell* = Summe aller Beträge mit „Kosten beglichen = ja" (nachgerechnet, stimmt mit der Datei überein).
- *Offene Forderungen je Partei* = Summe der Beträge je Zahler mit „beglichen = nein".
- *Kontostand nach Forderungsbegleichung* = Kontostand + offene Forderungen.
- *Forecast* = Summe der Posten mit „erledigt = nein" (aktuell keine vorhanden; künftig = geplante Ausgaben).
- *Kosten pro Tag / pro Nutzungstag*: Ausgaben ÷ Tage seit Kauf, mit fest eingetippter Annahme „Nutzung an 15 % der Tage". **Entfällt** (Entscheidung 15).

**Bedeutung von „Kosten beglichen" (aus den Formeln abgeleitet, muss bestätigt werden):** `ja` = über das gemeinsame Konto abgewickelt, zählt zum Kontostand · `nein` = eine Partei hat privat ausgelegt, offene Forderung · `gesponsert` / `wird gesponsert` = wird nicht erstattet, zählt nicht zum Kontostand · leer = nur bei Preis 0.

**Wiederkehrende Kosten:** Keine eigene Spalte. Erkennbar sind jährlich Versicherung und KFZ-Steuer (Kategorie „Versicherung/Steuer", 24 Buchungen) sowie unregelmäßig TÜV/Werkstatt. Die Anforderungen verlangen keine automatische Wiederholung; deshalb bleiben sie normale Buchungen.

**Auffälligkeiten (nicht korrigiert):**
1. Die **Pivot-Tabelle ist veraltet** (fehlt die neueste Zeile). Dadurch ist „Summe Ausgaben" und „Kosten pro Tag" etwas zu niedrig; der Kontostand ist nicht betroffen.
2. **Uneinheitliche Formelbereiche** (bis Zeile 401 bzw. nur bis 296, Daten bis 328).
3. Zwei Buchungen tragen ein vermutlich falsches Jahr (stehen zwischen Einträgen von Ende 2016, sind auf Januar 2016 datiert); 31 Zeilen sind nicht chronologisch.
4. Fünf Beträge haben mehr als zwei Nachkommastellen → Rundungsregel für den Import nötig.
5. Zwei positive Beträge außerhalb der Kategorie „Einzahlung" (Sonderleistungen) → in OBELIX Einnahmen.
6. 25 Buchungen mit Preis 0 (Inventarliste) sind keine Finanzbewegungen.

**Folgerungen für das Datenmodell (in Abschnitt 5 eingearbeitet):** kein `openingBalance`; Zahler = Benutzerkonto (jede Excel-Partei wird einem Konto zugeordnet, Entscheidung 11); Abrechnungsstatus je Buchung (`SETTLED`/`OPEN`/`SPONSORED`); Kategorien als Daten aus den 12 verwendeten Werten. **Kontrollwerte für den Import-Test** liegen in der nicht-öffentlichen Detailfassung (Kontostand, offene Forderungen, Summen, Buchungsanzahl).

---

## 4. Zielarchitektur

Ein-Modul-Android-App, bewusst schlicht:

```
UI (Compose, Material 3)  →  ViewModel (StateFlow)  →  Repository  →  Firebase (Auth / Firestore / Storage*)
```

*Speicherort für Dateien: offen, siehe Entscheidung 1.

| Thema | Vorschlag | Begründung |
|---|---|---|
| Sprache/UI | Kotlin, Jetpack Compose, Material 3 | Vorgabe |
| Navigation | Navigation Compose, Bottom Bar (Dashboard, Kalender, Finanzen, Aufgaben, Stellplätze, Dokumente); Einstellungen über Icon in der Top Bar | 7 Hauptbereiche passen nicht sauber in eine Bottom Bar (max. 5 empfohlen) |
| Dependency Injection | Manuell (ein `AppContainer`), **kein** Hilt | „Keine unnötige Abstraktion"; Umfang klein |
| Zustände | Pro Screen `sealed interface UiState`: Loading / Success / Empty / Error / Offline | Anforderung Abschnitt 34 |
| Nebenläufigkeit | Coroutines + Flow, `await()` für Firebase-Tasks | Standard |
| Geld | `Long` in Cent, nie `Double` | Datenkorrektheit (Priorität 3) |
| Datum | `Timestamp` für Zeitpunkte; Kalendertage als `String` `yyyy-MM-dd` | Keine Zeitzonen-Fehler bei Urlaubstagen |
| Listener | Einmalige Abfragen pro Screen statt permanenter Listener | Kostenkontrolle (Anforderung 37); Ausnahme nur wenn sinnvoll |
| Fehler | Einheitlicher Mapper Firebase-Exception → deutsche Meldung, technische Details nur ins Log | Anforderung 40 |
| Offline | Firestore-Offline-Persistenz **deaktivieren**; Netzwerkstatus über `ConnectivityManager`; Schreiben nur mit Serverbestätigung | Sonst meldet Firestore lokalen Erfolg ohne Server – das verbietet Anforderung 8 |
| Karte | OpenStreetMap-basiert (Bibliothek offen, Entscheidung 3) | Kein kostenpflichtiger API-Zwang |
| Navigation zum Stellplatz | `geo:`-Intent / Google-Maps-URL | Anforderung 26 |
| Standort | `FusedLocationProviderClient` oder Android `LocationManager`, nur auf Knopfdruck, kein Hintergrund | Anforderung 23 |
| Bilder | Beim Auswählen auf lange Seite ≤ 1800 px skalieren, JPEG ~80 % | Anforderung 5 |
| Sprache | Alle Texte in `strings.xml` (Deutsch) | Anforderung 33 |
| Tests | JUnit für Logik (Bestand, Überschneidung, Validierung), Firebase Emulator für Rules, Compose-/Instrumentation-Tests für Kernabläufe | Anforderung 42/43 |

### Geplante Projektstruktur (Paket `…obelix`)

```
app/src/main/java/…/obelix/
  MainActivity.kt, ObelixApp.kt, AppContainer.kt
  core/        (Result/Fehler-Mapper, Netzwerkstatus, Geld/Datum-Helfer, Bildkompression)
  data/
    model/     (User, Household, CalendarEntry, Transaction, PlannedExpense, Repair, Campsite, Document, Category)
    repo/      (AuthRepository, HouseholdRepository, CalendarRepository, FinanceRepository,
                PlannedExpenseRepository, RepairRepository, CampsiteRepository, DocumentRepository, FileStore)
  ui/
    nav/, theme/, components/
    auth/, dashboard/, calendar/, finance/, repairs/, planned/, campsites/, documents/, settings/
app/src/test/, app/src/androidTest/
firebase/  firestore.rules, storage.rules (falls Storage), firebase.json, rules-tests/
docs/      PROJEKTPLAN.md (dieser Plan, wird laufend aktualisiert), ARCHITEKTUR.md
README.md, .gitignore
```

---

## 5. Firestore-Datenmodell

Konventionen für alle fachlichen Dokumente: `createdAt` (Server-Timestamp), `createdBy` (uid), `updatedAt`, `updatedBy`. Geldbeträge als `amountCents` (Long). Kalendertage als `yyyy-MM-dd`-String.

### Struktur

```
users/{uid}                                   → Benutzer: Name, Rolle
config/access                                 → gemeinsamer Zugangscode (nur ADMIN)
calendarEntries/{id}
transactions/{id}
plannedExpenses/{id}
repairs/{id}
campsites/{id}
documents/{id}
categories/{id}                               → Finanzkategorien (aus Excel zu befüllen)
files/{fileId}/chunks/{n}                     → Dateiablage (Option F)
```

Abweichung von der Beispielstruktur der Anforderungen (`households/{householdId}/…`): Es gibt **keinen Haushalt** (Entscheidung 2b vom 30.09.2026). Alle Sammlungen liegen auf oberster Ebene und gehören allen registrierten Benutzern gemeinsam. Die Anforderung erlaubt Verbesserungen ausdrücklich; `householdId` entfällt.

### Entitäten

**User** – `users/{uid}`
| Feld | Typ | Bemerkung |
|---|---|---|
| displayName | String | Pflicht, 1–50 Zeichen; erscheint bei „Bezahlt von", im Kalender usw. |
| role | String | `ADMIN` \| `MEMBER`; bei der Registrierung immer `MEMBER`, nur ein ADMIN ändert sie |
| accessCode | String | Code, mit dem sich der Benutzer registriert hat (Nachweis für die Regel) |
| createdAt | Timestamp | Server |

Die E-Mail-Adresse steht nicht in Firestore (Datensparsamkeit, sie liegt in Firebase Auth).

**Zugangscode** – `config/access`
| Feld | Typ | Bemerkung |
|---|---|---|
| code | String \| null | 16 Zeichen (siehe Abschnitt 7); `null` sperrt neue Registrierungen |
| updatedBy, updatedAt | uid, Timestamp | wer den Code zuletzt gesetzt hat |

**CalendarEntry**
| Feld | Typ | Pflicht |
|---|---|---|
| startDate, endDate | String (Tag) | ja; `endDate ≥ startDate` |
| personName (+ `personUid`) | String | ja |
| destination | String | nein |
| comment | String | nein |
| Audit | | ja |

**Transaction** (Einnahme und Ausgabe in einer Sammlung)
| Feld | Typ | Pflicht |
|---|---|---|
| type | `INCOME` \| `EXPENSE` | ja |
| date | String (Tag) | ja |
| amountCents | Long, > 0 | ja |
| categoryId | String → `categories` | ja |
| paidByUid | String → `users` | Ausgabe: ja; Einnahme: optional. Angezeigt wird der Name des Benutzers |
| settlement | `OPEN` \| `SETTLED` \| `SPONSORED` | Ausgabe: ja (Standard beim Anlegen: `OPEN`). Einnahme: immer `SETTLED`. Excel „nein" → `OPEN`, „ja" → `SETTLED`, „gesponsert"/„wird gesponsert" → `SPONSORED` |
| settledAt / settledBy | Timestamp / uid | gesetzt beim Übergang `OPEN` → `SETTLED` |
| importRef | String | nur bei importierten Buchungen: `xl-<Excel-Zeile>` (zugleich Dokument-ID); unveränderlich |
| description | String | ja |
| comment | String | nein |
| receipt | Map {path, contentType, sizeBytes} | nein (nur Ausgabe) |
| plannedExpenseId | String | nur bei Umwandlung |
| Audit | | ja |

**PlannedExpense** (umgesetzt in Phase 5)
| Feld | Typ | Pflicht |
|---|---|---|
| title | String, 1–200 Zeichen | ja |
| estimatedAmountCents | Long, 1 bis 100.000.000 (1.000.000,00 €) | ja |
| plannedDate | String (Tag) | ja (Vorbelegung: heute) |
| status | `PLANNED` \| `PURCHASED` | ja (UI: GEPLANT/GEKAUFT); beim Anlegen immer `PLANNED` |
| priority | `LOW`\|`MEDIUM`\|`HIGH` | nein |
| link | String, muss mit `http://` oder `https://` beginnen, keine Leerzeichen, höchstens 500 Zeichen | nein |
| comment | String, höchstens 500 Zeichen (leer erlaubt, Feld immer vorhanden) | ja (Feld), Inhalt optional |
| purchasedTransactionId | String | genau bei `PURCHASED` (Verweis auf die Buchung) |
| Audit (`createdAt/By`, bei Änderung `updatedAt/By`) | | ja |

**Repair (Auffälligkeit)**
| Feld | Typ | Pflicht |
|---|---|---|
| title | String | ja |
| description | String | ja |
| date | String (Tag) | ja |
| status | `OPEN` \| `DONE` | ja (UI: OFFEN/ERLEDIGT) |
| priority | `LOW`\|`MEDIUM`\|`HIGH` | nein |
| comment | String | nein |
| Audit | | ja |

**Campsite (Stellplatz)**
| Feld | Typ | Pflicht |
|---|---|---|
| latitude, longitude | Double | ja |
| date | String (Tag) | ja |
| comment | String | ja (laut Anforderung „mindestens") |
| name, address, note | String | nein |
| rating | Int 1–5 | nein |
| photos | Liste von Map {path, contentType, sizeBytes}, **max. 3** | nein |
| Audit | | ja |

**Document**
| Feld | Typ | Pflicht |
|---|---|---|
| name | String | ja |
| category | String | ja (Vorschlag aus Anforderung: Fahrzeug, Versicherung, Rechnung, Garantie, Bedienungsanleitung, Sonstiges) |
| file | Map {path, contentType, sizeBytes} | ja |
| uploadedBy (uid + Name), uploadedAt | | ja |

**Category** – `categories/{id}`: `name` (1–50 Zeichen), `active` (Bool), Audit (`createdAt/By`, bei Änderung `updatedAt/By`). **Kein `type`** (Entscheidung 17). Inhalt beim Import **ausschließlich aus der Excel-Datei** (12 verwendete Werte, „Werkstatt" bleibt neben „TÜV/Werkstatt", ungenutzte `Look`-Einträge entfallen: Entscheidung 13). Anlegen: jeder Benutzer; umbenennen/deaktivieren: nur ADMIN; nie löschen.

### Wichtige Datenflüsse

1. **Bestand:** Anfangsbestand ist 0 (laut Excel). Bestand = Σ Einnahmen + Σ Ausgaben mit `SETTLED` (entspricht der Excel-Formel „Kontostand aktuell"). Zusätzlich: offene Forderungen je Benutzer = Σ `OPEN`-Ausgaben je Zahler; Kontostand nach Begleichung = Bestand + offene Forderungen. Berechnung über Firestore-Aggregationsabfragen (`sum()`) oder clientseitig. Geplante Ausgaben zählen nicht. Hinweis: Die Anforderung nennt „Anfangsbestand + Einnahmen − Ausgaben"; der Abrechnungsstatus ist eine Erweiterung aus der Excel, die bestätigt werden muss.
2. **Geplant → gekauft (umgesetzt in Phase 5):** Eine einzige Firestore-**Transaktion**: liest die Planung (Status muss noch `PLANNED` sein), legt die `transactions`-Ausgabe mit **tatsächlichem** Betrag und `plannedExpenseId` an und setzt `plannedExpenses.status = PURCHASED` mit `purchasedTransactionId`. Bei fehlender Verbindung oder gleichzeitigem Kauf von einem zweiten Gerät entsteht nichts Halbes und keine zweite Ausgabe. **Buchung löschen:** Verweist sie auf eine Planung, öffnet dieselbe Transaktion die Planung wieder (`PLANNED`, `purchasedTransactionId` entfernt).
3. **Kalender-Überschneidung:** Abfrage `startDate ≤ neuesEnde`, danach Filter `endDate ≥ neuerStart` im Client (Firestore kann nicht zwei Bereichsfilter auf verschiedenen Feldern). Bei Treffer Warnung, danach Speichern nur nach ausdrücklicher Bestätigung (Entscheidung 5). Bei geringer Datenmenge ist die Race-Condition zwischen zwei gleichzeitigen Nutzern akzeptabel; das wird dokumentiert, nicht verschwiegen.
4. **Stellplatz mit Fotos:** Fotos werden zuerst hochgeladen, danach wird das Firestore-Dokument geschrieben; bei Abbruch werden hochgeladene Dateien wieder gelöscht. Die 3-Foto-Grenze wird in UI **und** Rules erzwungen.

---

## 6. Firebase- und Speicherkonzept

### Firebase-Dienste
- **Authentication:** E-Mail/Passwort, Passwort-Reset per Firebase-Standardmail. Kein Analytics, kein Crashlytics, kein Tracking (Anforderung 36).
- **Firestore:** Native Modus, Region in der EU (Standort ist nach Anlage nicht änderbar – Entscheidung 2).
- **`google-services.json`:** Enthält keine geheimen Schlüssel im Sinne von Passwörtern, ist aber projektbezogen. Vorschlag: nicht committen, sondern lokal ablegen und im README beschreiben; für CI ggf. als GitHub-Secret. (Kleines Restrisiko: Der öffentliche Repo-Status macht das Committen unnötig riskant.)

### ⚠ Zentrale Unsicherheit: Firebase Storage ist nicht mehr kostenlos ohne Weiteres

Recherche (Firebase-Dokumentation „Default bucket and billing requirements for Cloud Storage for Firebase", geprüft am 30.09.2026):

- Neue Firebase-Projekte auf dem kostenlosen **Spark-Tarif** können **keinen Standard-Bucket** anlegen. Dafür ist der **Blaze-Tarif (Pay-as-you-go)** nötig; auf Spark liefern Storage-Zugriffe 402/403.
- Ausnahmen: Alte `*.appspot.com`-Buckets behalten ihr Gratis-Kontingent; neue Buckets (`*.firebasestorage.app`) nutzen die Always-Free-Stufe von Google Cloud Storage nur in `US-CENTRAL1`, `US-EAST1`, `US-WEST1`.

Das kollidiert direkt mit der Vorgabe „keine kostenpflichtige Infrastruktur / kein Upgrade auf einen kostenpflichtigen Tarif". Nach Abschnitt 63 der Anforderungen entscheidest **du**. Optionen in Abschnitt 11, Entscheidung 1.

### Supabase als Alternative (geprüft am 30.09.2026)

Quellen: Supabase-Preisseite, Doku zu Storage-Zugriff, Firebase-Auth-Integration und Projekt-Pausierung.

| Punkt | Befund |
|---|---|
| Free-Plan | 1 GB Dateispeicher, max. 50 MB je Datei, 5 GB Traffic, 500 MB Datenbank, 50.000 aktive Nutzer, 2 aktive Projekte. Keine Kreditkarte nach den geprüften Seiten erforderlich (nicht explizit bestätigt) |
| Überschreitung | Keine automatische Abrechnung; laut Doku „service restrictions" (z. B. Pausierung oder nur-lesende Datenbank) |
| Dateigröße/Umfang | Für ~40 Fotos und wenige Dokumente ausreichend, große PDFs (bis 50 MB) möglich |
| Zugriffsschutz | Storage-Regeln als PostgreSQL-Row-Level-Security auf `storage.objects`. Ohne Regeln kein Upload |
| **Kopplung an Firebase Auth** | Supabase kann Firebase-Auth-Tokens akzeptieren, **aber** jeder Nutzer braucht dafür ein Custom-Claim `role: authenticated`. Das wird laut Doku über Firebase-„Blocking Functions" (Identity Platform) oder eine `onCreate` **Cloud Function** gesetzt. Beides setzt bei Firebase **Cloud Functions bzw. den Blaze-Tarif** voraus (Annahme, aus der Doku abgeleitet, Einzelheiten nicht separat geprüft) |
| **Pausierung** | Free-Projekte werden pausiert, wenn eine Woche lang zu wenig Datenbankaktivität stattfindet. Wiederherstellung per Dashboard bis zu 1 Jahr, Daten bleiben erhalten. Eine selten genutzte Familien-App ist genau der Risikofall |
| Kotlin-Client | Community-gepflegtes `supabase-kt` (Kotlin Multiplatform). Pflegezustand und Kompatibilität nicht geprüft |

**Bewertung:**
- **Supabase nur für Dateien, Rest in Firebase:** Das umgeht Blaze **nicht**, weil die Verknüpfung der Firebase-Anmeldung mit Supabase das Custom-Claim braucht (Cloud Function/Identity Platform). Ohne diese Verknüpfung müsste man Dateien mit einem gemeinsamen geheimen Schlüssel in der App schützen – das ist unsicher und widerspricht Sicherheit vor Bequemlichkeit. **Nicht empfohlen.**
- **Supabase komplett statt Firebase (Auth + Datenbank + Storage aus einem System, Free-Plan):** Technisch machbar, alles kostenlos ohne Kreditkarte, Dateien bis 50 MB, Rules als RLS. Preis: **Abweichung von der Vorgabe „bevorzugt Firebase"** (erlaubt, wenn technisch erforderlich), anderes Datenmodell (Postgres statt Firestore), anderer Client, **Pausierung nach einer Woche ohne Aktivität** (Gegenmaßnahme: regelmäßiger Aufruf durch die App oder manuelles Fortsetzen). Der Plan müsste in Phase 1–3 umgeschrieben werden.
- **Firebase mit Bildern in Firestore (Option B unten)** bleibt die einfachste Lösung ohne Blaze, mit Grenze bei großen PDFs.

### Dropbox als Alternative (geprüft am 30.09.2026)

Quellen: Dropbox-OAuth-Dokumentation, Dropbox-Hilfe zu geteilten Ordnern.

Es gibt zwei Varianten, die sich grundlegend unterscheiden:

**Variante 1 – ein gemeinsames Dropbox-Konto, Zugangsschlüssel in der App: nicht vertretbar.** Der Schlüssel steckt dann in jeder installierten App und lässt sich aus der APK auslesen. Wer ihn hat, hat vollen Zugriff auf das Dropbox-Konto, am Haushalt und an den Firebase-Regeln vorbei. Das verletzt Anforderung 28 („nur authentifizierte Benutzer des Haushalts") und 32. Außerdem gilt das Konto dann für Fahrzeugschein und Versicherungsunterlagen als ungeschützt.

**Variante 2 – jedes Familienmitglied verbindet sein eigenes Dropbox-Konto, Dateien liegen in einem geteilten Ordner: technisch sauber, aber aufwendig.**
- Dropbox unterstützt für Apps die Anmeldung per PKCE (kein Geheimnis in der App) und dauerhafte Refresh-Tokens.
- Den Zugriffsschutz übernimmt Dropbox über die Mitgliedschaft im geteilten Ordner, **nicht** Firebase. Es gibt dann zwei getrennte Rechtesysteme, die zusammenpassen müssen (wer im Haushalt ist, muss auch im Ordner sein – beides von Hand gepflegt).
- **Jedes** Familienmitglied braucht ein Dropbox-Konto und muss es in der App zusätzlich verbinden (zweite Anmeldung).
- Beim kostenlosen Dropbox Basic zählt der geteilte Ordner laut Dropbox-Hilfe **gegen den Speicher jedes Mitglieds**. Basic hat 2 GB (plus eventueller Bonusspeicher). Für ~40 Fotos und einige Dokumente reicht das, sofern die Konten nicht schon voll sind.
- Nicht geprüft: Gerätelimit von Dropbox Basic für App-Zugriffe, Nutzer-Obergrenze einer Dropbox-App im Entwicklungsstatus.
- Zusätzlicher Code: Dropbox-SDK, Kontoverknüpfung, Fehlerfälle (Konto nicht verbunden, Ordner nicht geteilt, Speicher voll).

**Bewertung:** Variante 2 ist möglich und kostenlos, erfüllt „einfach vor komplex" aber schlecht. Die Anforderungen sagen ausdrücklich, Dropbox soll nicht allein wegen der wenigen Fotos integriert werden.

### Empfehlung: Dateien in Firestore, in Stücke geteilt (neue Option F)

Firebase Spark bleibt für alles andere (Anmeldung und Daten) **kostenlos und ohne Kreditkarte** – nur Firebase *Storage* ist das Problem. Deshalb können Dateien ebenfalls in Firestore liegen:

- Firestore kann Binärdaten (`Bytes`) direkt speichern. Grenze je Dokument 1 MiB, je Anfrage 10 MiB.
- Jede Datei wird in Stücke zu ~900 KB aufgeteilt: `files/{fileId}` (Metadaten: Name, Typ, Größe, Anzahl Stücke) und `files/{fileId}/chunks/{n}`. Hochladen in **einem** Batch (alles oder nichts, passt zu Anforderung 8). Beim Öffnen werden die Stücke gelesen und zusammengesetzt.
- Ein komprimiertes Foto (~1800 px) ist voraussichtlich ein einziges Stück; ein 5-MB-PDF sind 6 Stücke.
- **Kostenloses Kontingent** (Spark, laut Firebase-Doku): 1 GiB gespeicherte Daten, 50.000 Lesevorgänge/Tag, 20.000 Schreibvorgänge/Tag, 10 GiB ausgehender Datenverkehr/Monat. Beispielrechnung (Annahme, nicht gemessen): 40 Fotos × 0,4 MB + 50 Dokumente × 3 MB ≈ 170 MB – deutlich unter 1 GiB.
- **Sicherheit:** Dieselben Firestore-Regeln wie für alle anderen Daten (nur registrierte Benutzer). Keine öffentlichen Links.
- **Grenzen:** Dateigröße je Datei auf **8 MB** begrenzen (unter dem 10-MiB-Anfragelimit). Sehr große Handbücher wären zu groß – dann Link statt Datei. Keine Vorschaubilder-Automatik; die App lädt Dateien nur beim Öffnen (sparsam).
- **Risiko:** Ungewöhnliches, aber bekanntes Muster. Wird das Kontingent irgendwann knapp, lässt sich die Dateiablage hinter der Schnittstelle `FileStore` austauschen, ohne den Rest der App zu ändern.

Damit ist die Anwendung **vollständig kostenlos auf Firebase Spark**, mit einer Anmeldung und einem Rechtesystem.

### Datei-Ablage (unabhängig vom Speicherort)
- Pfad: `campsites/{campsiteId}/{photoId}.jpg`, `receipts/{transactionId}/{name}`, `documents/{docId}/{name}`.
- Nie öffentliche URLs speichern; nur den Pfad im Firestore-Dokument.
- Grenzen: Fotos nach Kompression typisch ~200–500 KB (Annahme, wird in Phase 6 gemessen); Dokumente/Belege begrenzt auf Bild oder PDF, bei Option F max. 8 MB je Datei.

---

## 7. Security-Konzept

Alle Regeln liegen versioniert in `firebase/`. Sie werden **zusammen mit dem jeweiligen Datenbereich** geschrieben und mit dem **Firebase Emulator** getestet (lokal, kostenlos).

**Firestore-Regeln (Prinzip):**
- Ohne Login: alles verboten.
- Hilfsfunktion `isUser()`: `exists(/users/$(request.auth.uid))`, also nur Konten, die sich mit gültigem Zugangscode registriert haben.
- Hilfsfunktion `isAdmin()`: dieses Benutzerdokument hat `role == 'ADMIN'`.
- Fachliche Sammlungen (ab Phase 4): Lesen/Anlegen/Ändern/**Löschen** für alle Benutzer (Entscheidung 4: jeder darf löschen).
- `createdBy`/`updatedBy` müssen `request.auth.uid` entsprechen; `createdAt` nicht änderbar.
- Validierung in den Regeln: Pflichtfelder, Typen, `amountCents > 0`, `endDate ≥ startDate`, `photos.size() ≤ 3`, Enum-Werte.
- `users/{uid}`: lesbar für alle Benutzer (Namen für „Bezahlt von"). Jeder ändert nur seinen eigenen Namen. Die Rolle ändert nur ein ADMIN, und **nie die eigene**; ein ADMIN kann sich auch nicht selbst entfernen. Dadurch bleibt immer mindestens ein ADMIN übrig (Regeln werden beim Schreiben nacheinander gegen den aktuellen Stand geprüft).
- `config/access`: lesen und schreiben nur ADMIN.

**Zugangscode und Registrierung (umgesetzt in Phase 3, ohne Cloud Functions):**
- Code: 16 Zeichen aus einem Alphabet ohne I, L, O, 0, 1 (31 Zeichen, rund 79 Bit, `SecureRandom`), Anzeige `XXXX-XXXX-XXXX-XXXX`. Er liegt in `config/access.code` und ist **nur für ADMINs lesbar**.
- **Registrierung in der App:** Formular mit Name, E-Mail, Zugangscode, Passwort → Konto in Firebase Auth anlegen → Benutzerdokument `users/{uid}` anlegen (Name, Rolle `MEMBER`, eingegebener Code). Die Regel erlaubt das nur, wenn der Code mit `config/access.code` übereinstimmt (`get()` in der Regel, der Client kann den Code nicht lesen). Bei falschem Code löscht die App das Konto sofort wieder und zeigt „Zugangscode ungültig". Danach meldet sich der Benutzer nur noch mit E-Mail und Passwort an.
- Ein angemeldetes Konto ohne Benutzerdokument (z. B. Konten aus den Phase-2-Tests, entfernte Benutzer) sieht nur „Zugangscode eingeben" oder „Abmelden".
- **Code erneuern** (nur ADMIN, in den Einstellungen): neuer Code in `config/access`, der alte ist sofort ungültig. Bereits registrierte Benutzer behalten ihren Zugriff.
- **Erster ADMIN:** einmalig in der Firebase-Konsole: `config/access` mit einem Code anlegen, in der App registrieren, dann im eigenen `users`-Dokument `role` auf `ADMIN` setzen (Anleitung `FIREBASE-EINRICHTUNG.md`, Abschnitt 8). In der App kann sich niemand selbst zum ADMIN machen.
- **Grenzen (ehrlich):** Firebase Auth kann das bloße Anlegen eines Kontos ohne Blaze nicht sperren. Ein Fremder kann kurz ein leeres Konto anlegen, hat aber keinerlei Datenzugriff; die App löscht es bei falschem Code wieder. Ein **weitergegebener Code** gilt, bis der ADMIN ihn erneuert. Gegenmaßnahme: ADMIN sieht alle Benutzer, kann Unbekannte entfernen und danach den Code erneuern. Jeder Benutzer kann in `users` den Code sehen, mit dem sich andere registriert haben; das ist höchstens ein früherer oder der aktuelle Code, den ohnehin alle Benutzer bekommen haben.
- **Ab Phase 5 freigegeben:** `plannedExpenses` (Lesen/Anlegen/Löschen für alle Benutzer; Validierung `validPlanned`). Der Kauf (`PLANNED` → `PURCHASED`) ist nur erlaubt, wenn im selben Schritt die passende Buchung entsteht (`existsAfter`/`getAfter`: Buchung existiert, ist eine Ausgabe und verweist mit `plannedExpenseId` auf die Planung; nur `status`, `purchasedTransactionId`, `updatedAt/By` ändern sich). Umgekehrt gibt es eine Buchung mit `plannedExpenseId` nur zusammen mit dem Kauf; `plannedExpenseId` ist unveränderlich und nur bei Ausgaben erlaubt. Eine gekaufte Planung ist nicht änderbar; sie wird nur wieder geöffnet, wenn ihre Buchung im selben Schritt gelöscht wird.
- Die fachlichen Sammlungen (Kalender, …) sind bis zu ihrer jeweiligen Phase komplett gesperrt (auch für ADMINs), damit nichts versehentlich offen ist. **Ab Phase 4 freigegeben:** `transactions` (Lesen/Anlegen/Ändern/Löschen für alle Benutzer; Validierung: Pflichtfelder, `amountCents` ganze Zahl 1 bis 100.000.000, Datumsformat, Status, Einnahme immer `SETTLED`, Ausgabe braucht Zahler, `createdBy/At` und `updatedBy/At` erzwungen, `importRef` unveränderlich) und `categories` (Lesen/Anlegen alle, Ändern nur ADMIN, Löschen nie).

**Storage-Regeln (falls Storage):** Zugriff nur für registrierte Benutzer (Storage-Regeln können Firestore per `firestore.exists()` abfragen); Größen- und `contentType`-Limits.

**Nicht-funktionale Sicherheit:** Passwort-Mindestlänge, keine Secrets im Repo, `.gitignore` deckt `google-services.json`, Keystores, `local.properties` ab.

**Sicherheitstest (Pflicht):** Anforderung 43 (Haushalt A darf nie Daten von Haushalt B sehen) **entfällt**, weil es keine Haushalte mehr gibt (Entscheidung 2b). Stattdessen: automatisierter Rules-Test, dass Konten **ohne** gültigen Zugangscode und nicht angemeldete Nutzer nichts lesen oder schreiben können, und dass niemand sich selbst zum ADMIN macht – mit echten Rules im Emulator, zusätzlich manuell gegen das echte Projekt.

---

## 8. Screen- und Navigationsstruktur

- **Auth-Graph:** Login · Registrierung (mit Zugangscode) · Passwort zurücksetzen · „Zugangscode eingeben" für Konten ohne Freischaltung
- **Hauptgraph (Bottom Bar):**
  1. **Dashboard** – nächster Termin/aktuelle Nutzung, Bestand, Ausgaben im Zeitraum, offene Auffälligkeiten, offene Anschaffungen, Anzahl Stellplätze
  2. **Kalender** – Monatsansicht/Liste, Eintrag anlegen/bearbeiten, Überschneidungs-Dialog
  3. **Finanzen** – Übersicht (Bestand), Liste Einnahmen/Ausgaben, Formular, Beleg, Kategorien-Filter; Unterbereich **Geplante Ausgaben** (Liste, Formular, „Gekauft"-Dialog)
  4. **Aufgaben** – Auffälligkeiten (Filter Alle/Offen/Erledigt, Standard „Offen"), Formular
  5. **Stellplätze** – Liste + Karte, Detail (Fotos, Navigation starten), „Aktuellen Standort speichern"
  6. **Dokumente** – Liste nach Kategorie, Upload, Öffnen
- **Einstellungen** (Zahnrad in der Top Bar): Profil, Rolle, Logout; für ADMIN: Zugangscode und Benutzerverwaltung
- Bottom Bar hat 6 Ziele (Empfehlung Material 3: max. 5). Vorschlag: „Aufgaben" und „Dokumente" unter „Mehr" zusammenfassen oder Navigation Drawer – Entscheidung 7.

Jeder Screen implementiert Loading, Success, Empty, Error, Offline. Löschen immer mit Bestätigungsdialog.

---

## 9. Implementierungsplan

Reihenfolge nach deiner Entscheidung: Finanzen (mit Import) und geplante Ausgaben direkt nach Benutzern und Rollen. Die Dateiablage kommt als eigene Phase vor Stellplätzen und Dokumenten, weil Belege, Fotos und Dokumente sie gemeinsam nutzen.

Nach **jeder** Phase: implementieren → kompilieren → Tests → Fehler beheben → Ergebnis prüfen → dieses Dokument aktualisieren → offene Punkte notieren → erst dann weiter.

**Testfälle** stehen in einer eigenen Datei: [`TESTFAELLE.md`](TESTFAELLE.md) (aktuelle und zu erledigende Fälle, mit Status). Sie wird nach jeder Phase und jedem Testlauf aktualisiert.

### Phase 0 – Analyse und Architektur ✅ (dieses Dokument)
Abschlusskriterium: Freigabe durch dich.

### Phase 1 – Projektbasis und Firebase ✅ (30.09.2026, auf dem Gerät abgenommen)
- **Ziel:** Leere App startet, Firebase verbunden, CI kompiliert.
- **Dateien:** `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `MainActivity`, Theme, Navigation-Gerüst, `strings.xml`, `.gitignore`, `README.md`, `.github/workflows/build.yml`, `firebase/firebase.json`
- **Umsetzung:** Projekt, Abhängigkeiten (Compose BOM, Navigation, Firebase BOM, Auth, Firestore), Firestore-Persistenz aus, Netzwerkstatus, Fehler-Mapper, Theme, leere Navigation, `google-services.json` lokal.
- **Tests:** Build, Unit-Test-Lauf, App startet im Emulator/Gerät.
- **Abschluss:** `assembleDebug` und `testDebugUnitTest` laufen in GitHub Actions grün; App startet; kein Secret im Repo.
- **Ergebnis:** CI-Lauf auf Commit `60460b7`: Build, Unit-Tests und Lint **grün**. Kein Secret im Repo (`.gitignore` geprüft).
- **Nicht von mir geprüft (kein Gerät/Emulator hier):** Start der App auf einem Gerät, Darstellung der Navigation, Verhalten mit echter `google-services.json`. Bitte die Debug-APK aus dem GitHub-Actions-Lauf (Artefakt `obelix-debug-apk`) installieren: Ohne `google-services.json` muss „Backend nicht eingerichtet" erscheinen. Den Lauf mit Firebase-Konfiguration gibt es erst nach deinem lokalen Build oder nach Phase 2.
- **Festgelegte Versionen (geprüft am 30.09.2026):** AGP 9.3.3, Gradle 9.5.1, Kotlin 2.4.10, Compose BOM 2026.09.00, Navigation 2.10.2, Lifecycle 2.11.0, Firebase BoM 34.19.0, `minSdk 26`, `compileSdk 37` (von den AndroidX-Bibliotheken verlangt), `targetSdk 36`.
- **Offene Punkte:** (1) `targetSdk` später auf 37 heben; (2) GitHub-Actions-Aktionen `setup-java@v4` und Node-20-Hinweis: kein Fehler, aber Update auf v5 sinnvoll; (3) Compose-Material-Icons-Erweiterung wird über `material-icons-extended 1.7.8` bezogen (Version nicht gesondert gegen die BOM geprüft, Build ist grün).

### Phase 2 – Authentifizierung ✅ (30.09.2026, auf dem Gerät abgenommen)
- **Ziel:** Registrierung, Login, Logout, Passwort-Reset.
- **Dateien:** `AuthRepository`, `auth/*`, Navigation-Guard.
- **Umsetzung:** Formulare mit Validierung (E-Mail, Passwortlänge), deutsche Fehlermeldungen (falsches Passwort, Netzwerk), Sitzung bleibt erhalten.
- **Tests:** Registrierung, Login, Logout, Reset, falsches Passwort, Offline-Meldung.
- **Abschluss:** Alle genannten Fälle manuell und per Test bestanden.
- **Umgesetzt (Commit `432e9fa`, CI grün):** `AuthRepository` (Firebase Auth), Formulare Anmelden / Konto erstellen / Passwort zurücksetzen, Sitzungsstatus (ohne Anmeldung sieht man nur die Anmeldeseiten), Einstellungen mit Konto und „Abmelden", Fehlermeldungen auf Deutsch. Passwort mindestens 8 Zeichen. Bei „falsche E-Mail" und „falsches Passwort" erscheint dieselbe Meldung (verrät nicht, ob es ein Konto gibt); beim Passwort-Reset erscheint immer dieselbe neutrale Bestätigung.
- **Automatisch geprüft (Unit-Tests in CI):** Validierung von E-Mail, Passwort und Name; Abbildung der Firebase-Fehler auf Meldungen (falsche Zugangsdaten, Konto existiert, zu schwaches Passwort, zu viele Versuche, kein Netz).
- **Gerätetest:** Alle 12 Fälle (G1-01 bis G1-03, G2-01 bis G2-09) hat der Benutzer am 30.09.2026 erfolgreich getestet, siehe [`TESTFAELLE.md`](TESTFAELLE.md).
- **Bekannt / offen:** (1) Die Rollen (ADMIN/MEMBER) gibt es erst in Phase 3; bis dahin sieht jedes registrierte Konto denselben leeren Hauptbereich. (2) Firebase-Auth erlaubt derzeit die Registrierung für jeden, der die App hat. Der **Zugangscode für die Registrierung ist der Kern von Phase 3** (Zugriff nur mit Code, serverseitig über Firestore-Regeln). (3) Kein Test der ViewModels (Coroutine-Testbibliothek noch nicht eingebunden); Anmeldeablauf wird deshalb nur manuell geprüft.

### Phase 3 – Benutzer, Rollen, Zugangscode, Firestore-Rules ✅ abgenommen (30.09.2026)
- **Ziel:** Registrierung nur mit Zugangscode, Rollen ADMIN/MEMBER, Rules mit Emulator-Tests.
- **Verlauf:** Zuerst mit Haushalt, Parteien und Start-Code umgesetzt (Commits `5d4414f` bis `a26b665`). Am 30.09.2026 auf Wunsch des Benutzers **zurückgebaut**: kein Haushalt, keine Parteien, ein gemeinsamer Datenbestand (Commit `656d1e9`).
- **Umgesetzt:**
  - `firebase/firestore.rules` (nur `users` und `config/access`, alles andere gesperrt), `firebase/firebase.json`, `firebase/rules-tests/` (Node, `@firebase/rules-unit-testing`).
  - CI-Job „rules" in `.github/workflows/build.yml`: Firestore-Emulator, Regel-Tests; die Testanzahl erscheint als Hinweis am Lauf.
  - App: `data/user/` (`UserProfile`, `Role`, `AccessCode`, `UserRepository`), `data/auth/RegistrationHandoff`, Registrierung mit Pflichtfeld „Zugangscode", Bildschirm „Zugangscode eingeben" (`ui/onboarding/`), `SessionViewModel` mit Benutzerstand, Einstellungen mit Rolle; für ADMIN Zugangscode (anzeigen, kopieren, teilen, erneuern) und Benutzerverwaltung (Admin/Mitglied, entfernen; mit Bestätigungsdialog).
  - Alle Schreibvorgänge laufen als **Transaktion** mit 20 s Zeitlimit: offline entsteht ein Fehler statt einer lokalen Scheinbestätigung (Anforderung 8). Lesen nur vom Server.
- **Automatisch geprüft (GitHub Actions, Commit `656d1e9`):** Android-Bau, Lint und 22 Unit-Tests grün; 30 Regel-Tests im Emulator grün (Fix `e2b2483`: eigenes, noch fehlendes Benutzerdokument abfragbar), siehe [`TESTFAELLE.md`](TESTFAELLE.md) (R-01 bis R-05).
- **Noch nicht geprüft (kein Gerät, kein Zugriff auf das echte Projekt):** Ablauf in der App und mit dem echten Firebase-Projekt (Tests G3-xx). Dafür muss der Benutzer die Regeln veröffentlichen, den Code anlegen und sich zum ADMIN machen (`FIREBASE-EINRICHTUNG.md`, Abschnitte 7 und 8).
- **Bekannt / offen:** (1) Entfernte Benutzer behalten ihr Konto in Firebase Auth (ohne Datenzugriff); löschen kann man es in der Konsole. (2) Die Rolle wird beim Start und beim Öffnen der Einstellungen geladen, nicht laufend. (3) Keine Mutationsprüfung der Regel-Tests.

### Phase 4 – Finanzen und Excel-Import ✅ umgesetzt und auf dem Gerät abgenommen (30.09.2026)
- **Ziel:** Einnahmen, Ausgaben, Kategorien, Zahler (Benutzer), Abrechnungsstatus, Kontostand, Übersicht; historische Daten aus der Excel übernommen.
- **Umgesetzt (Commits `4c2dc88`, `c2b2b61`, `d870760`):**
  - `firebase/firestore.rules`: `transactions` und `categories` (siehe Abschnitt 7). Regel-Tests R-06 (10 Fälle) und R-07 (5 Fälle).
  - `core/money/Money` (Cent, deutsche Eingabe/Anzeige), `data/finance/` (`Booking`, `BookingInput`, `Category`, `FinanceCalculator`, `BookingValidator`, `FinanceRepository`, `CategoryRepository`), `core/util/RepositoryCall` (gemeinsame Fehler-/Zeitlimit-/Transaktionshilfe für neue Repositories).
  - Finanzbereich (`ui/finance/`): Übersicht (Kontostand, offene Forderungen je Zahler, Kontostand nach Begleichung, Einnahmen/Ausgaben gesamt, davon gesponsert), Filter (Art, Kategorie, Zahler, Jahr), Liste, Formular (Einnahme/Ausgabe, Datumsauswahl, Betrag mit Komma, Kategorie, Zahler, Status, Beschreibung, Kommentar), Aktion „Als erstattet markieren", Löschen mit Bestätigung. Ladezustand, Fehler mit „Erneut versuchen" (nie eine leere Liste statt eines Fehlers), Leerzustand, Offline-Hinweis; alle Schreibvorgänge als Transaktion.
  - Kategorien in den Einstellungen (`CategorySection`): alle legen an, ADMIN benennt um und (de)aktiviert.
  - Excel-Import (`data/finance/importing/`, `ui/importing/`): Datei wählen → Prüfung → Kontrollwerte → Zahler zuordnen → Bestätigung → Schreiben in Blöcken à 10 → Ergebnis; Entscheidung 16. Nur für ADMIN sichtbar (Einstellungen). Neue Bibliothek: `kotlinx-serialization-json`.
  - Excel-Regeln: 300 von 325 Zeilen werden importiert (59 Einnahmen, 241 Ausgaben); die 25 Nullbeträge (Inventarliste) werden nicht importiert und in der Vorschau aufgelistet (15c); „Verantwortung" steht im Kommentar, unverändert (15b); Beträge kaufmännisch auf Cent gerundet; Zahler = Konto laut Zuordnung.
- **Kontrollwerte (aus den Rohwerten der Excel unabhängig nachgerechnet; auch nach der Cent-Rundung unverändert):** Kontostand 107,17 € · offene Forderung des Kontos von Robert (Excel „Heidi/Robert") 99,00 €, des Kontos von Tobias 0,00 € · Kontostand nach Begleichung 8,17 € · Einnahmen 69.617,94 € (59 Buchungen: 57 Einzahlungen und 2 Sonderleistungen) · Ausgaben 70.415,60 € (241 Buchungen) · davon gesponsert 805,83 € (16 Buchungen). Die frühere Angabe „Ausgaben −70.293,59 €" in der Excel-Analyse war ein Nettowert (Sonderleistungen von den Ausgaben abgezogen) und ist berichtigt.
- **Automatisch geprüft (GitHub Actions, Commit `d870760`):** Android-Bau, Lint und **49 Unit-Tests** grün (27 neu: Geld, Kontostand, Validierung, Importprüfung); **45 Regel-Tests** im Emulator grün (15 neu). Details: [`TESTFAELLE.md`](TESTFAELLE.md).
- **Noch nicht geprüft (von dir zu prüfen, Tests G4-01 bis G4-16):** Regeln in der Konsole neu veröffentlichen; Bedienung auf dem Gerät; der eigentliche Import in dein echtes Projekt; Darstellung (Datumsauswahl, Auswahlfelder, Filter, Bildschirmgrößen).
- **Erkenntnisse / Grenzen:**
  - Firestore begrenzt die Regelabfragen (`exists`) je Transaktion auf 20 (laut Firebase-Dokumentation für Mehrfachschreibvorgänge). Deshalb schreibt der Import **10 Buchungen je Block**. Ob mehr im Emulator gegangen wäre, wurde nicht ausprobiert.
  - Der Import ist nicht atomar (30 Blöcke), aber **fortsetzbar**: Bei Abbruch (z. B. Netz) startet man ihn erneut, bereits vorhandene Buchungen werden übersprungen.
  - Wie bei allen Zeitlimits (20 s) kann eine Transaktion nach einer „Keine Verbindung"-Meldung auf dem Server trotzdem angekommen sein; die Liste lädt beim nächsten Öffnen neu und zeigt den wahren Stand.
  - Die Excel-Fehler (z. B. vermutlich falsches Jahr bei den Zeilen 104/105, in der App 15.01.2016) sind **unverändert** übernommen; du korrigierst sie in der App.
  - Einnahmen und Ausgaben lassen sich beim Bearbeiten in die jeweils andere Art umwandeln; die Regeln prüfen die Kombination (Einnahme immer „beglichen").
- **Nicht Teil von Phase 4:** Belege (Phase 6, Dateiablage), geplante Ausgaben (Phase 5).

### UI-Überarbeitung nach Phase 4 (30.09.2026, auf Wunsch des Benutzers)
- **App-Icon:** vom Benutzer geliefertes Bild (Wohnmobil mit Schriftzug OBELIX) als Adaptive Icon (`mipmap-anydpi-v26/ic_launcher.xml` und `ic_launcher_round.xml`, Vordergrund `drawable-nodpi/ic_launcher_foreground.png` mit Grafik auf 78 % der Fläche, Hintergrundfarbe `#1E3557`). Kein Icon für ältere Android-Versionen nötig (minSdk 26). Kein Monochrom-Icon (Themed Icons): nicht geliefert, nicht erfunden.
- **Login-Hintergrund:** vom Benutzer geliefertes Bild `drawable-nodpi/login_background.jpg` (Wohnmobil vor Bergen, Sonnenuntergang) vollflächig, unten ausgerichtet; Formular (Login, Registrierung, Passwort zurücksetzen) auf halbtransparenter Karte. Nur der Anmeldebereich; der Zugangscode-Bildschirm bleibt unverändert.
- **Menü:** untere Navigation nur mit Symbolen, kein Text mehr (`label = null`); der Name bleibt als `contentDescription` für TalkBack. Die obere Leiste zeigt weiter den Bereichsnamen.
- **Hell-/Dunkelmodus (Version 07):** Symbol ganz oben in den Einstellungen (Mond = zu Dunkel wechseln, Sonne = zu Hell), für alle Benutzer. Wahl nur lokal auf dem Gerät (`data/settings/ThemePreference.kt`, SharedPreferences `obelix_settings`), ohne Wahl gilt die Systemeinstellung. Symbole der Statusleiste/Navigationsleiste folgen der gewählten Darstellung (`MainActivity`).
- **Versionsanzeige:** ganz unten in den Einstellungen „Version 07" (liest `versionName` zur Laufzeit). **Regel: bei jedem Deployment `versionCode` +1 und `versionName` zweistellig erhöhen (08, 09, …) in `app/build.gradle.kts`.** Version 07 = erster Stand mit dieser Anzeige (`versionCode = 7`).
- **Kategorien nur für ADMIN:** Der Abschnitt „Kategorien" in den Einstellungen wird nur ADMINs angezeigt (Entscheidung des Benutzers, 30.09.2026). Die Firestore-Regeln erlauben weiterhin jedem Benutzer das Anlegen (R-07); Mitglieder haben in der App aber keinen Weg dafür. Regeln bewusst nicht verschärft (nicht verlangt).
- Nicht geprüft (Gerätetest GU): Aussehen auf verschiedenen Launchern und Formen, Lesbarkeit der Karte im hellen und dunklen Modus. Bildgrößen: Hintergrund 169 KB, Icon-Vordergrund 270 KB.

### Phase 5 – Geplante Ausgaben ✅ umgesetzt (30.09.2026, Version 08), Gerätetest G5 offen
- **Ziel:** Planung, „Gekauft“-Workflow mit tatsächlichem Betrag.
- **Umgesetzt (Commit `29eea16`):**
  - `firebase/firestore.rules`: Sammlung `plannedExpenses` (`validPlanned`), Buchungen mit optionalem, unveränderlichem `plannedExpenseId` (siehe Abschnitt 7). Regel-Tests R-08 (13 Fälle).
  - `data/planned/`: `PlannedExpense`, `PlannedInput`, `PurchaseInput`, `PlannedStatus`, `Priority` (Models), `PlannedValidator` (Titel, Link; Betrag/Datum/Kommentar über `BookingValidator`), `PurchasePlanner` (Kauf → Buchung mit tatsächlichem Betrag) und `PlannedCalculator` (Anzahl und Summe der offenen Planungen), `PlannedExpenseRepository` (`loadAll`, `get`, `create`, `update`, `purchase`, `delete`; alle Schreibvorgänge als Transaktion).
  - Geändert: `FinanceRepository.delete(id, plannedExpenseId, uid)` öffnet die Planung wieder (Entscheidung 23); `bookingCreateData` als gemeinsame Funktion; `Booking`/`BookingInput` mit `plannedExpenseId`; neue Fehlerart `AppError.CONFLICT` („Stand hat sich geändert“), `ErrorMapper` erkennt umhüllte `AppException`.
  - Oberfläche (`ui/planned/`): Liste (Summe der offenen Schätzungen, Filter Geplant/Gekauft/Alle, Ladezustand, Fehler mit „Erneut versuchen“, Leerzustände), Formular (anlegen, bearbeiten, löschen mit Bestätigung, Link öffnen), Dialog „Gekauft“, Anzeige gekaufter Planungen mit Verweis auf die Buchung. Einstieg: Schaltfläche „Geplante Ausgaben“ im Finanzbereich; im Buchungsformular ist die Art einer aus einem Kauf entstandenen Buchung gesperrt und der Löschdialog erwähnt die Planung.
  - Version 08 (`versionCode 8`).
- **Automatisch geprüft (GitHub Actions, Commit `29eea16`, Lauf 36743091193):** Android-Bau, Lint und **63 Unit-Tests** grün (14 neu: Validierung, Kauf 500 € → 472 €, Kontostand nur durch den Kauf, Summe der offenen Planungen, Fehlerabbildung); **58 Regel-Tests** im Emulator grün (13 neu, R-08). Details: [`TESTFAELLE.md`](TESTFAELLE.md). R-08d wurde nach diesem Stand um einen Fall erweitert (Entfernen von Priorität und Link); Commit `d20c658` ist ebenfalls grün (63 Unit-Tests, 58 Regel-Tests).
- **Noch nicht geprüft (von dir zu prüfen, Tests G5-01 bis G5-12):** Regeln in der Firebase-Konsole **neu veröffentlichen**; Bedienung auf dem Gerät; Verhalten gegen dein echtes Projekt; Darstellung (Dialog, Datumsauswahl, Auswahlfelder); Kauf gleichzeitig von zwei Geräten (G5-11).
- **Erkenntnisse / Grenzen:**
  - Der Schutz vor Doppelkauf hat zwei Ebenen: Die Transaktion der App liest den Status und bricht bei `PURCHASED` mit „Stand hat sich geändert“ ab; die Regeln verbieten zusätzlich jeden zweiten Kauf (R-08h). Die App-Ebene (Ausnahme in der Firestore-Transaktion) ist nicht automatisch testbar und wird durch G5-11 geprüft. Ist die Meldung dort nur allgemein („unerwarteter Fehler“), ist trotzdem nichts doppelt gebucht.
  - Wird eine Planung gelöscht, während ihre Buchung besteht, bleibt in der Buchung ein Verweis auf eine nicht mehr vorhandene Planung. Das ist harmlos; beim späteren Löschen dieser Buchung wird nur die Buchung gelöscht (der Löschdialog nennt die Planung dann trotzdem).
  - Bearbeitet man eine gekaufte Buchung (Betrag, Datum, …), ändert das die Planung nicht; die Schätzung bleibt als Historie.
  - Kein automatischer Test der Oberfläche und der Transaktion gegen echtes Firestore (kein Emulator für die App in der Cloud-Sitzung).
- **Nicht Teil von Phase 5:** Dashboard-Zähler für offene Anschaffungen (Phase 11); Belege (Phase 6).

### Phase 6 – Dateiablage und Belege
- **Voraussetzung:** Entscheidung 1.
- **Ziel:** Gemeinsame Dateiablage (`FileStore`), Bildverkleinerung, Belege an Ausgaben.
- **Dateien:** `FileStore`, `ImageCompressor`, Rules für Dateien, Beleg-UI in `finance/*`.
- **Tests:** Upload/Anzeige/Löschen, Größen- und Typgrenzen, Zugriff ohne Freischaltung verboten, Abbruch ohne Netz hinterlässt keine Reste.
- **Abschluss:** Alle Fälle bestanden; gemessene Fotogröße im Plan nachgetragen.

### Phase 7 – Kalender
- **Ziel:** Nutzung eintragen, bearbeiten, löschen, Überschneidung.
- **Dateien:** `CalendarEntry`, `CalendarRepository`, `calendar/*`, Rules.
- **Umsetzung:** Liste/Monatsansicht, Formular (von/bis/Name, optional Ziel/Kommentar), Überschneidungswarnung, Lösch-Bestätigung.
- **Tests:** Überschneidungslogik (gleicher Tag, angrenzend, umschließend), ungültige Datumsbereiche, Rules, Offline.
- **Abschluss:** Alle Kalenderfälle der Teststrategie bestanden.

### Phase 8 – Auffälligkeiten
- **Ziel:** Erstellen, bearbeiten, erledigen, wieder öffnen, filtern, löschen.
- **Dateien:** `Repair`, `RepairRepository`, `repairs/*`, Rules.
- **Tests:** Status-Wechsel, Filter, Rechte, Offline.
- **Abschluss:** Alle Fälle bestanden.

### Phase 9 – Stellplätze
- **Voraussetzung:** Phase 6, Entscheidung 3 (Karte).
- **Ziel:** Standort speichern, bis zu 3 Fotos, Karte, externe Navigation.
- **Dateien:** `Campsite`, `CampsiteRepository`, `campsites/*`, Manifest-Berechtigungen (Standort, Kamera), Rules.
- **Umsetzung:** Standortberechtigung korrekt behandelt, kein Hintergrund-Tracking; Foto aufnehmen/auswählen, Vorschau, Entfernen; Marker → Detail; „Navigation starten" per Intent.
- **Tests:** GPS/Berechtigung verweigert/erteilt, 3-Foto-Grenze (UI und Rules), Foto einzeln löschen, Stellplatz löschen entfernt auch Fotos, Karte, Navigation, Offline.
- **Abschluss:** Alle Fälle auf echtem Gerät geprüft.

### Phase 10 – Dokumente
- **Ziel:** Hochladen, Kategorie, öffnen, löschen, Zugriffsschutz.
- **Dateien:** `Document`, `DocumentRepository`, `documents/*`, Rules.
- **Tests:** Upload, Öffnen, Löschen, Zugriff ohne Freischaltung verboten, Dateityp-/Größenlimit.
- **Abschluss:** Alle Fälle bestanden.

### Phase 11 – Dashboard
- **Ziel:** Kennzahlen aus echten Daten.
- **Umsetzung:** Nächster/aktueller Termin, Kontostand, Ausgaben im aktuellen Zeitraum, offene Forderungen, Anzahl offener Auffälligkeiten, Anzahl offener Anschaffungen, Anzahl Stellplätze; sparsame Zähl-/Aggregationsabfragen. „Aktueller Zeitraum": Entscheidung 8.
- **Abschluss:** Zahlen stimmen mit den Detailbereichen überein; Leerzustand ohne Fake-Zahlen.

### Phase 12 – Qualitätssicherung
- Gesamttest laut Abschnitt 42/43/54 der Anforderungen, Code-Bereinigung, README vollständig.
- **Abschluss:** Alle Abschlusskriterien aus Abschnitt 60 der Anforderungen erfüllt und belegt.

---

## 10. Teststrategie (Zusammenfassung)

| Ebene | Werkzeug | Was |
|---|---|---|
| Logik | JUnit | Bestand, Überschneidung, Validierung, Fehler-Mapper, Bildskalierung |
| Rules | Firebase Emulator Suite + Rules-Unit-Testing (Node) | Rollen, Zugriff nur mit Zugangscode, Validierung, 3-Foto-Grenze |
| UI/Abläufe | Compose-UI-Tests / Instrumentation | Kernabläufe, Zustände |
| Gerät | manuell | GPS, Kamera, Berechtigungen, Offline (Flugmodus), Kartenanzeige |
| Build | GitHub Actions | `assembleDebug`, Unit-Tests |

Regel 7 der Anforderungen gilt: Was nicht getestet wurde, wird nicht als fertig gemeldet. Was ich hier **nicht** selbst testen kann (Gerätefunktionen, echtes Firebase-Projekt), kennzeichne ich ausdrücklich als „vom Nutzer zu prüfen".

---

## 11. Offene Entscheidungen (deine Freigabe nötig)

**1. Dateispeicher (dringend, blockiert Belege, Stellplatzfotos und Dokumente)** – *Neue Empfehlung: Option F.* – Problem: Firebase Storage verlangt für neue Projekte den kostenpflichtigen Blaze-Tarif.
| Option | Kosten | Vorteile | Nachteile |
|---|---|---|---|
| A. Blaze-Tarif mit Storage in `US-CENTRAL1/EAST1/WEST1` (Always-Free) plus Budgetalarm | Erwartet 0 €, aber Kreditkarte/Abrechnungskonto nötig; Überschreiten der Gratisgrenze wäre kostenpflichtig | Ein System, saubere Rules, keine Größenprobleme | Widerspricht „kein kostenpflichtiger Tarif"; Daten in den USA (Datenschutz, Familien-App mit Dokumenten wie Fahrzeugschein) |
| B. Dateien direkt in Firestore ablegen (Base64, komprimiert) | 0 € im Spark-Tarif | Kein Blaze, alles in einem System, Rules gelten | 1-MiB-Grenze je Dokument: reicht für komprimierte Fotos und kleine Bilder, **nicht** für größere PDFs (Versicherung, Bedienungsanleitungen); Größeres müsste in Teilstücke zerlegt oder abgelehnt werden |
| C. Fremder kostenloser Dateispeicher (z. B. Supabase) | 0 € | Große Dateien möglich | Zweites Backend, eigene Auth-Anbindung, Datenschutz; mehr Komplexität – widerspricht „einfach vor komplex" |
| D. Bestehendes älteres Firebase-Projekt mit `*.appspot.com`-Bucket, falls du eines hast | 0 € | Gratis-Kontingent bleibt erhalten | Nur wenn ein solches Projekt existiert; nicht neu anlegbar |
| F. **Dateien gestückelt in Firestore** (Empfehlung, siehe Abschnitt 6) | 0 €, ohne Kreditkarte | Ein System, eine Anmeldung, Firestore-Regeln gelten | Max. ~8 MB je Datei; eigener Code für das Stückeln |
| G. Dropbox, jedes Mitglied mit eigenem Konto | 0 € | Große Dateien möglich | Zweite Anmeldung für jeden, zweites Rechtesystem, Speicher zählt bei jedem Mitglied; ein gemeinsames Konto mit Schlüssel in der App ist unsicher und ausgeschlossen |
| E. Komplett auf Supabase wechseln (statt Firebase) | 0 €, ohne Kreditkarte | Ein System, Dateien bis 50 MB, Rules per RLS | Abweichung von „bevorzugt Firebase", anderes Datenmodell, Pausierung nach 1 Woche Inaktivität, Plan muss umgeschrieben werden |

*Supabase nur für Dateien (Option C) ist durch die Prüfung praktisch ausgeschieden:* Die Kopplung an Firebase Auth braucht Cloud Functions bzw. Identity Platform und damit wieder den Blaze-Tarif.

*Frühere Empfehlung (durch F ersetzt):* **B für Stellplatzfotos und Belege (Bilder), und für PDFs eine Größenbegrenzung (~700 KB) oder Option A nur wenn du bewusst Blaze zulässt.** Option E ist die ernsthafte Alternative, wenn dir große PDFs wichtig sind und du kein Blaze willst – dann aber bitte bewusst, weil sie das Fundament ändert. Begründung: Damit bleibt die Regel „kostenlos, ein System, keine Drittanbieter" erfüllt. Die Einschränkung bei großen PDFs ist der Preis dafür. Wenn dir große Dokumente wichtig sind, ist A die technisch sauberere Lösung – aber das ist deine Entscheidung. Es wird nichts eingerichtet, bevor du entschieden hast.
*Unsicherheit:* Die tatsächliche Kompressionsgröße der Fotos (Annahme 200–500 KB) und die tatsächliche Nutzung des Kontingents sind nicht gemessen – das prüfe ich in Phase 6, bevor Fotos und Dokumente folgen. (Die Spark-Kontingente selbst wurden am 30.09.2026 in der Firebase-Doku geprüft.)

**2. Firebase-Projekt** – ✅ angelegt (`obelix-daf7c`, `europe-west3`). **2b.** ✅ entschieden: kein Haushalt, Registrierung mit gemeinsamem Zugangscode (Abschnitt 0).

**3. Karte** – Vorschlag: OpenStreetMap-Kacheln mit einer Open-Source-Bibliothek (Kandidaten: osmdroid, MapLibre; Pflegezustand und Lizenz prüfe ich in Phase 9). Google Maps SDK **nicht** ohne Prüfung, da API-Schlüssel und Abrechnungskonto nötig sein können (nicht verifiziert). Die OSM-Nutzungsrichtlinien für Kacheln erlauben nur moderate Nutzung – für eine kleine Familien-App vermutlich unkritisch, das ist nicht geprüft.

**4. Löschrechte** – Darf MEMBER eigene Einträge löschen, oder nur ADMIN? (Vorschlag: MEMBER löscht eigene Einträge, ADMIN alles.)

**5. Kalender-Überschneidung** – Speichern nach Warnung erlauben? (Vorschlag: ja, mit ausdrücklicher Bestätigung „Trotzdem speichern".)

**6. Import** – ✅ entschieden: ja (siehe Abschnitt 0).

**7. Navigation** – 6 Hauptbereiche in Bottom Bar oder Aufteilung/„Mehr"?

**8. Dashboard** – Was bedeutet „aktueller Zeitraum" (Kalendermonat, laufendes Jahr, Zeitraum aus Excel)?

**9. Löschen als „gelöscht markieren"** statt hartes Löschen? (Vorschlag: hartes Löschen mit Bestätigung; einfacher, passend zur kleinen Datenmenge.)

**10. `google-services.json`** – nicht committen (Vorschlag) oder committen?

**11.** ✅ entschieden (siehe Abschnitt 0).

**12.** ✅ entschieden (siehe Abschnitt 0).

**13.** ✅ entschieden (siehe Abschnitt 0).

**14.** ✅ entschieden (siehe Abschnitt 0).

**15.** „Kosten pro Tag" ✅ entfällt. **15b.** „Verantwortung": Vorschlag – an den Kommentar anhängen, kein eigenes Feld. **15c.** Nullbeträge (Inventarliste): Vorschlag – nicht importieren, nur protokollieren.

---

## 12. Erkannte technische Risiken

| # | Risiko | Wirkung | Gegenmaßnahme |
|---|---|---|---|
| 1 | **Firebase Storage nur mit Blaze** | Konflikt mit Kostenvorgabe | Empfehlung Option F (Dateien in Firestore); Entscheidung 1 vor Phase 6 |
| 2 | **Excel-Daten uneinheitlich** (veraltete Pivot, Rundungsreste, vermutlich falsches Jahr bei 2 Buchungen, Freitext-Felder) | Falscher Kontostand oder Importfehler | Kontrollwerte aus der Excel als Abnahmetest; Auffälligkeiten nicht stillschweigend „reparieren", sondern mit dir klären |
| 2c | **`google-services.json` liegt im öffentlichen Repository** (am 30.09.2026 vom Benutzer über die Weboberfläche hochgeladen, Commit `740fd07`) | Die Datei enthält Projektnummer, App-ID und einen Android-API-Schlüssel. Das sind laut Firebase keine Geheimnisse, aber jeder kann damit Konten anlegen. Aus der Git-Historie lässt sich die Datei nicht mehr entfernen | API-Schlüssel in der Google Cloud Console auf Android-App `de.hagi089.obelix` beschränken; Datenschutz über Firestore-Regeln (Phase 3); keine echten Zugangsdaten oder Service-Account-Dateien ins Repository. Entscheidung offen, ob die Datei im Repository bleibt (Vorteil: CI-APK ist sofort testbar) |
| 2b | **Öffentliches Repository und private Finanzdaten** | Namen/Beträge könnten versehentlich veröffentlicht werden | `private/` und `*.xlsx` in `.gitignore`; Analyse mit Namen nur im nicht-öffentlichen Claude-Projekt |
| 3 | **Kein Android-SDK in dieser Sitzung; Netzzugang zu `dl.google.com`, `maven.google.com`, `services.gradle.org` blockiert** (geprüft) | Ich kann die App hier voraussichtlich **nicht kompilieren** | Build über GitHub Actions (öffentliches Repo, kostenlos) oder lokal bei dir; ich melde nichts als „kompiliert", was nicht tatsächlich gebaut wurde |
| 4 | **Keine Geräte-/Firebase-Konsole-Zugriffe** | GPS, Kamera, echtes Firebase-Projekt nicht von mir testbar | Emulator-Tests für Rules; Gerätetests kennzeichne ich als „von dir zu prüfen" |
| 5 | **Firestore-Offline-Cache** meldet lokalen Schreiberfolg | Widerspricht Anforderung 8 | Persistenz deaktivieren; Schreiben nur mit Serverbestätigung (`await()` auf Server-Antwort) |
| 6 | **Zugangscode ohne Cloud Functions** | Schwierigste Rules-Stelle; ein Fehler hier öffnet die Daten für Fremde | Code nur für ADMIN lesbar, Vergleich in der Regel; 29 Emulator-Tests (Phase 3) |
| 7 | **Rules mit `exists()/get()`** | Jede Regelprüfung kann Lesevorgänge kosten | Bei kleiner Datenmenge unkritisch; im Blick behalten |
| 8 | **Kalender-Überschneidung ohne Transaktion** | Zwei gleichzeitige Einträge könnten kollidieren | Akzeptiert und dokumentiert |
| 9 | **Öffentliches Repository** | Versehentliche Veröffentlichung von Konfigurationsdateien | `.gitignore` zuerst, keine Keystores/JSON-Secrets, Prüfung vor jedem Push |
| 10 | **OSM-Kacheln** – Nutzungsrichtlinien | Sperrung bei Missbrauch | Nur moderate Nutzung, User-Agent setzen, Alternative prüfen |
| 11 | **Versionsstände** (Kotlin, AGP, Compose, Firebase) | Inkompatibilitäten | Aktuelle stabile Versionen zu Beginn von Phase 1 recherchieren, nicht aus dem Gedächtnis setzen |
| 12 | **6 Ziele in der Bottom Bar** | Schlechte Bedienbarkeit | Entscheidung 7 |
| 13 | **Gemeinsamer Zugangscode** kann weitergegeben werden und gilt bis zur Erneuerung | Fremde könnten sich als MEMBER registrieren | ADMIN sieht alle Benutzer, kann entfernen und den Code erneuern; Code ist nicht erratbar (79 Bit) |
| 14 | **Regeln wirken erst nach Veröffentlichung** in der Firebase-Konsole | Bis dahin bleibt die Datenbank im Produktionsmodus komplett gesperrt (App zeigt „Benutzerdaten konnten nicht geladen werden") | Anleitung in `FIREBASE-EINRICHTUNG.md`, Gerätetest G3-01 |

---

## 13. Verbesserungsvorschläge (nicht umgesetzt, nur zur Entscheidung)

- Kategorien-Verwaltung durch ADMIN in der App, falls sich die Excel-Struktur ändert.
- Export der Finanzdaten als CSV (nicht in den Anforderungen; nur bei Bedarf).

---

## 14. Fortschrittsprotokoll

| Phase | Status | Datum | Offene Punkte |
|---|---|---|---|
| 0 Analyse | abgeschlossen, **freigegeben** | 30.09.2026 | 15b, 15c gelten als Vorschlag (siehe Abschnitt 0) |
| 1 Projektbasis | abgeschlossen, auf dem Gerät abgenommen | 30.09.2026 | – |
| 2 Authentifizierung | abgeschlossen, auf dem Gerät abgenommen | 30.09.2026 | – |
| 3 Benutzer, Rollen, Regeln, Zugangscode | umgesetzt (Haushalt am 30.09.2026 wieder entfernt), Bau und Regel-Tests grün, **abgenommen** (Gerätetests G3-01 bis G3-11 ✅) | 30.09.2026 | – |
| 4 Finanzen und Excel-Import | ✅ abgenommen: Bau, 49 Unit-Tests und 45 Regel-Tests grün (Commit `d870760`); Gerätetest G4-01 bis G4-16 bestanden (Benutzer, 30.09.2026), Import in das echte Projekt durchgeführt | 30.09.2026 | – |
| 5 Geplante Ausgaben | umgesetzt (Version 08): Bau, Lint, 63 Unit-Tests und 58 Regel-Tests grün; **Gerätetest G5-01 bis G5-12 offen**, Regeln müssen neu veröffentlicht werden | 30.09.2026 | Gerätetest G5, GU (UI-Überarbeitung) |
| 6–12 | nicht begonnen | | |
