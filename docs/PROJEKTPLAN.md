# OBELIX – Analyse und Implementierungsplan (Phase 0)

Stand: 30.09.2026 · Status: **Entwurf, wartet auf Freigabe** · Es wurde kein Code verändert.

Grundlage: Projektwissen „Anforderungen" (verbindlich) und der Ist-Zustand des GitHub-Repositorys `Hagi089/Obelix`.

---

## 1. Ist-Zustand des Repositorys

| Prüfpunkt | Befund |
|---|---|
| Repository | `Hagi089/Obelix`, öffentlich, Schreibzugriff vorhanden |
| Klon | erfolgreich, Meldung „empty repository" |
| Remote-Refs (`git ls-remote`) | **keine** – kein Branch, kein Commit, keine Tags |
| Dateien | **keine** (weder Kotlin, Compose, Gradle, Manifest, Ressourcen, Firebase-Konfiguration, Tests, README noch `.gitignore`) |
| Excel-Datei | **nicht gefunden** – weder im Repository noch im Upload-Bereich der Sitzung (Suche nach `*.xlsx`, `*.xls`, `*.xlsm`) |
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
| Finanzen | Einnahmen, Ausgaben, Kategorien, „Bezahlt von", Bestand = Anfangsbestand + Einnahmen − Ausgaben, Belege | fehlt | **Excel fehlt**, Kategorien/Anfangsbestand unbekannt |
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

**Ergebnis: Die Excel-Datei wurde nicht gefunden und muss vor der Finanzimplementierung bereitgestellt werden.**

Es wurde nichts analysiert, geschätzt oder erfunden. Daraus folgt für die Planung:

- Kategorien, Personen, Anfangsbestand, Budgets, Zeiträume und wiederkehrende Kosten sind **unbekannt**.
- Das Finanz-Datenmodell (Abschnitt 5) ist bewusst generisch gehalten: Kategorien sind Daten in einer Sammlung `categories`, nicht im Code fest verdrahtet. Die Excel-Struktur wird dort später eingetragen.
- **Phase 4 (Finanzen) startet erst, wenn die Excel-Datei vorliegt und analysiert ist.** Alle anderen Phasen sind davon unabhängig.
- Pflichtinhalt der späteren Excel-Analyse: Tabellenblätter, Spalten, Formeln, Kategorien, Personen, Anfangsbestand, wiederkehrende Kosten, Zeiträume.
- Offen: Ob die Excel-Daten in die App **importiert** werden sollen (einmalig) oder die App nur ab jetzt neu erfasst. Das steht nicht in den Anforderungen (Entscheidung 6).

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
users/{uid}                                   → Zuordnung Benutzer → Haushalt
households/{householdId}                      → Stammdaten
  members/{uid}                               → Rolle, Anzeigename
  calendarEntries/{id}
  transactions/{id}
  plannedExpenses/{id}
  repairs/{id}
  campsites/{id}
  documents/{id}
  categories/{id}                             → Finanzkategorien (aus Excel zu befüllen)
```

Abweichung von der Beispielstruktur der Anforderungen: Ein Top-Level-Dokument `users/{uid}` ergänzt die Struktur, damit die App nach dem Login den Haushalt direkt findet, ohne den Haushalt vorher zu kennen. Die Anforderung erlaubt Verbesserungen ausdrücklich.

### Entitäten

**User** – `users/{uid}` (Pflicht: alle Felder außer `updatedAt`)
| Feld | Typ | Bemerkung |
|---|---|---|
| householdId | String | Pflicht |
| email | String | Pflicht |
| displayName | String | Pflicht, dient als „Name" in Kalender/Finanzen |
| createdAt | Timestamp | Server |

**Household** – `households/{hid}`
| Feld | Typ | Bemerkung |
|---|---|---|
| name | String | Pflicht |
| openingBalanceCents | Long | **Wert erst aus Excel** – kein Standardwert erfunden; Feld wird erst mit Phase 4 gesetzt |
| openingBalanceDate | String (Tag) | wie oben |
| createdAt/By | | Audit |

**Member** – `households/{hid}/members/{uid}`
| Feld | Typ | Bemerkung |
|---|---|---|
| role | String | `ADMIN` \| `MEMBER`, Pflicht |
| displayName | String | Pflicht |
| createdAt | Timestamp | |

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
| paidByUid / paidByName | String | Ausgabe: ja; Einnahme: optional |
| description | String | ja |
| comment | String | nein |
| receipt | Map {path, contentType, sizeBytes} | nein (nur Ausgabe) |
| plannedExpenseId | String | nur bei Umwandlung |
| Audit | | ja |

**PlannedExpense**
| Feld | Typ | Pflicht |
|---|---|---|
| title | String | ja |
| estimatedAmountCents | Long > 0 | ja |
| plannedDate | String (Tag) | ja |
| status | `PLANNED` \| `PURCHASED` | ja (UI: GEPLANT/GEKAUFT) |
| priority | `LOW`\|`MEDIUM`\|`HIGH` | nein |
| link | String (URL) | nein |
| comment | String | nein |
| purchasedTransactionId | String | gesetzt bei Kauf |
| Audit | | ja |

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

**Category** – `categories/{id}`: `name`, `type` (`INCOME`\|`EXPENSE`), `active` (Bool), Audit. Inhalt **ausschließlich aus der Excel-Datei**.

### Wichtige Datenflüsse

1. **Bestand:** `openingBalanceCents + Σ Einnahmen − Σ Ausgaben`. Berechnung über Firestore-Aggregationsabfragen (`sum()`) oder clientseitig über alle Transaktionen. Geplante Ausgaben zählen nicht.
2. **Geplant → gekauft:** Ein einziger Firestore-Batch (atomar): neue `transactions`-Ausgabe mit tatsächlichem Betrag + `plannedExpenses.status = PURCHASED` + `purchasedTransactionId`. Bei fehlender Verbindung schlägt der Batch fehl; es entsteht nichts Halbes.
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

### Datei-Ablage (unabhängig vom Speicherort)
- Pfad: `households/{hid}/campsites/{campsiteId}/{photoId}.jpg`, `households/{hid}/receipts/{transactionId}/{name}`, `households/{hid}/documents/{docId}/{name}`.
- Nie öffentliche URLs speichern; nur den Pfad im Firestore-Dokument.
- Grenzen: Fotos nach Kompression typisch ~200–500 KB (Annahme, wird in Phase 8 gemessen); Dokumente/Belege begrenzt auf Bild oder PDF, Größenlimit in Entscheidung 1 festzulegen.

---

## 7. Security-Konzept

Alle Regeln liegen versioniert in `firebase/`. Sie werden **zusammen mit dem jeweiligen Datenbereich** geschrieben und mit dem **Firebase Emulator** getestet (lokal, kostenlos).

**Firestore-Regeln (Prinzip):**
- Ohne Login: alles verboten.
- Hilfsfunktion `isMember(hid)`: `exists(/households/$(hid)/members/$(request.auth.uid))`.
- Hilfsfunktion `isAdmin(hid)`: dieses Member-Dokument hat `role == 'ADMIN'`.
- Fachliche Sammlungen: Lesen/Anlegen/Ändern für Mitglieder; **Löschen**: ADMIN (Entscheidung 4, ob MEMBER eigene Einträge löschen darf).
- `createdBy`/`updatedBy` müssen `request.auth.uid` entsprechen; `createdAt` nicht änderbar.
- Validierung in den Regeln: Pflichtfelder, Typen, `amountCents > 0`, `endDate ≥ startDate`, `photos.size() ≤ 3`, Enum-Werte.
- `members`: Rolle nur durch ADMIN änderbar; niemand kann sich selbst zum ADMIN machen; der letzte ADMIN darf nicht entfernt werden (im Client abgesichert, Grenze der Rules dokumentieren).
- `users/{uid}`: nur der Benutzer selbst.
- Haushalt anlegen bzw. beitreten: siehe Entscheidung 2b – **ohne Cloud Functions** (die brauchen Blaze) muss der Beitritt allein über Rules abgesichert werden, z. B. über Einladungscode-Dokument. Das ist die technisch heikelste Stelle der Security und wird in Phase 3 zuerst entworfen und getestet.

**Storage-Regeln (falls Storage):** Zugriff nur, wenn `request.auth != null` und Mitglied des Haushalts im Pfad (Storage-Regeln können Firestore per `firestore.exists()` abfragen); Größen- und `contentType`-Limits.

**Nicht-funktionale Sicherheit:** Passwort-Mindestlänge, keine Secrets im Repo, `.gitignore` deckt `google-services.json`, Keystores, `local.properties` ab.

**Sicherheitstest (Pflicht):** Zwei Testbenutzer in zwei Haushalten; automatisierter Rules-Test, dass A niemals Daten von B lesen oder schreiben kann (Firestore und ggf. Storage) – mit echten Rules im Emulator, zusätzlich einmal manuell gegen das echte Projekt.

---

## 8. Screen- und Navigationsstruktur

- **Auth-Graph:** Login · Registrierung/Haushalt beitreten oder anlegen · Passwort zurücksetzen
- **Hauptgraph (Bottom Bar):**
  1. **Dashboard** – nächster Termin/aktuelle Nutzung, Bestand, Ausgaben im Zeitraum, offene Auffälligkeiten, offene Anschaffungen, Anzahl Stellplätze
  2. **Kalender** – Monatsansicht/Liste, Eintrag anlegen/bearbeiten, Überschneidungs-Dialog
  3. **Finanzen** – Übersicht (Bestand), Liste Einnahmen/Ausgaben, Formular, Beleg, Kategorien-Filter; Unterbereich **Geplante Ausgaben** (Liste, Formular, „Gekauft"-Dialog)
  4. **Aufgaben** – Auffälligkeiten (Filter Alle/Offen/Erledigt, Standard „Offen"), Formular
  5. **Stellplätze** – Liste + Karte, Detail (Fotos, Navigation starten), „Aktuellen Standort speichern"
  6. **Dokumente** – Liste nach Kategorie, Upload, Öffnen
- **Einstellungen** (Zahnrad in der Top Bar): Profil, Logout, für ADMIN: Benutzerverwaltung/Haushalt
- Bottom Bar hat 6 Ziele (Empfehlung Material 3: max. 5). Vorschlag: „Aufgaben" und „Dokumente" unter „Mehr" zusammenfassen oder Navigation Drawer – Entscheidung 7.

Jeder Screen implementiert Loading, Success, Empty, Error, Offline. Löschen immer mit Bestätigungsdialog.

---

## 9. Implementierungsplan

Reihenfolge gegenüber dem Vorschlag der Anforderungen leicht angepasst: **Kalender, Auffälligkeiten und Stellplätze hängen nicht von der Excel-Datei ab und können vor den Finanzen kommen.** Finanzen und geplante Ausgaben folgen, sobald die Excel-Datei vorliegt. So blockiert die fehlende Datei nichts.

Nach **jeder** Phase: implementieren → kompilieren → Tests → Fehler beheben → Ergebnis prüfen → dieses Dokument aktualisieren → offene Punkte notieren → erst dann weiter.

### Phase 0 – Analyse und Architektur ✅ (dieses Dokument)
Abschlusskriterium: Freigabe durch dich.

### Phase 1 – Projektbasis und Firebase
- **Ziel:** Leere App startet, Firebase verbunden, CI kompiliert.
- **Dateien:** `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `MainActivity`, Theme, Navigation-Gerüst, `strings.xml`, `.gitignore`, `README.md`, `.github/workflows/build.yml`, `firebase/firebase.json`
- **Umsetzung:** Projekt, Abhängigkeiten (Compose BOM, Navigation, Firebase BOM, Auth, Firestore), Firestore-Persistenz aus, Netzwerkstatus, Fehler-Mapper, Theme, leere Navigation, `google-services.json` lokal.
- **Tests:** Build, Unit-Test-Lauf, App startet im Emulator/Gerät.
- **Abschluss:** `assembleDebug` und `testDebugUnitTest` laufen in GitHub Actions grün; App startet; kein Secret im Repo.

### Phase 2 – Authentifizierung
- **Ziel:** Registrierung, Login, Logout, Passwort-Reset.
- **Dateien:** `AuthRepository`, `auth/*`, Navigation-Guard.
- **Umsetzung:** Formulare mit Validierung (E-Mail, Passwortlänge), deutsche Fehlermeldungen (falsches Passwort, Netzwerk), Sitzung bleibt erhalten.
- **Tests:** Registrierung, Login, Logout, Reset, falsches Passwort, Offline-Meldung.
- **Abschluss:** Alle genannten Fälle manuell und per Test bestanden.

### Phase 3 – Haushalt, Rollen, Firestore-Rules (Basis)
- **Ziel:** Haushalt anlegen/beitreten, Rollen, Rules mit Emulator-Tests.
- **Dateien:** `HouseholdRepository`, Modelle `User/Household/Member`, `firestore.rules`, `rules-tests/*`, Einstellungen/Benutzerverwaltung.
- **Umsetzung:** Erster Benutzer legt Haushalt an und ist ADMIN; Beitrittsmechanismus (Entscheidung 2b); ADMIN verwaltet Mitglieder/Rollen.
- **Tests:** MEMBER vs. ADMIN, **zwei-Haushalte-Isolation**, Selbst-Beförderung verboten.
- **Abschluss:** Rules-Tests grün; manuelle Prüfung gegen das echte Projekt mit zwei Testkonten.

### Phase 4 – Kalender
- **Ziel:** Nutzung eintragen, bearbeiten, löschen, Überschneidung.
- **Dateien:** `CalendarEntry`, `CalendarRepository`, `calendar/*`, Rules-Ergänzung.
- **Umsetzung:** Liste/Monatsansicht, Formular (von/bis/Name, optional Ziel/Kommentar), Überschneidungswarnung, Lösch-Bestätigung.
- **Tests:** Unit-Tests der Überschneidungslogik (Randfälle: gleicher Tag, angrenzend, umschließend), ungültige Datumsbereiche, Rules, Offline.
- **Abschluss:** Alle Kalenderfälle der Teststrategie bestanden.

### Phase 5 – Auffälligkeiten
- **Ziel:** Erstellen, bearbeiten, erledigen, wieder öffnen, filtern, löschen.
- **Dateien:** `Repair`, `RepairRepository`, `repairs/*`, Rules.
- **Tests:** Status-Wechsel, Filter, Rechte, Offline.
- **Abschluss:** Alle Fälle bestanden.

### Phase 6 – Speicher-Entscheidung umsetzen und Stellplätze
- **Voraussetzung:** Entscheidung 1 (Speicher) und Entscheidung 3 (Karte).
- **Ziel:** Standort speichern, bis zu 3 Fotos, Karte, externe Navigation.
- **Dateien:** `Campsite`, `CampsiteRepository`, `FileStore`, `ImageCompressor`, `campsites/*`, Manifest-Berechtigungen (Standort, Kamera), Rules (Firestore + ggf. Storage).
- **Umsetzung:** Standortberechtigung erklärt und korrekt behandelt, kein Hintergrund-Tracking; Foto aufnehmen/auswählen, Vorschau, Entfernen, Kompression; Marker → Detail; „Navigation starten" per Intent.
- **Tests:** GPS/Berechtigung verweigert/erteilt, 3-Foto-Grenze (UI und Rules), Foto einzeln löschen, Löschen des Stellplatzes löscht auch Dateien, Karte, Navigation, Offline.
- **Abschluss:** Alle Fälle auf echtem Gerät geprüft (GPS und Kamera lassen sich nur dort verlässlich testen).

### Phase 7 – Dokumente
- **Ziel:** Hochladen, Kategorie, öffnen, löschen, Zugriffsschutz.
- **Dateien:** `Document`, `DocumentRepository`, `documents/*`, Rules.
- **Tests:** Upload, Öffnen, Löschen, Zugriff durch Fremdhaushalt verboten, Dateityp-/Größenlimit.
- **Abschluss:** Alle Fälle bestanden.

### Phase 8 – Finanzen *(startet erst nach Excel-Analyse)*
- **Ziel:** Einnahmen, Ausgaben, Kategorien, Personen, Bestand, Belege, Übersicht gemäß Excel-Struktur.
- **Voraussetzung:** Excel-Datei liegt vor; kurze Struktur-Zusammenfassung von mir wurde von dir bestätigt.
- **Dateien:** `Transaction`, `Category`, `FinanceRepository`, `finance/*`, Rules.
- **Tests:** Bestandsberechnung mit bekannten Zahlen, Personenzuordnung, Validierung (kein negativer Betrag), Beleg, Rechte, Offline.
- **Abschluss:** Berechnung stimmt mit Excel-Kontrollwerten überein.

### Phase 9 – Geplante Ausgaben
- **Ziel:** Planung, „Gekauft"-Workflow mit tatsächlichem Betrag.
- **Dateien:** `PlannedExpense`, `PlannedExpenseRepository`, `planned/*`.
- **Tests:** Beispiel 500 € geplant / 472 € gekauft ⇒ Ausgabe 472 €; geplant erscheint danach nicht mehr offen; Bestand ändert sich nur durch den Kauf; Abbruch ohne Netz erzeugt nichts Halbes.
- **Abschluss:** Alle Fälle bestanden.

### Phase 10 – Dashboard
- **Ziel:** Kennzahlen aus echten Daten.
- **Umsetzung:** Nächster/aktueller Termin, Bestand, Ausgaben im aktuellen Zeitraum, Anzahl offener Auffälligkeiten, Anzahl offener Anschaffungen, Anzahl Stellplätze; sparsame Abfragen (Zähl-/Aggregationsabfragen). „Aktueller Zeitraum" muss definiert werden (Entscheidung 8).
- **Abschluss:** Zahlen stimmen mit den Detailbereichen überein; Leerzustand ohne Fake-Zahlen.

### Phase 11 – Qualitätssicherung
- Gesamttest laut Abschnitt 42/43/54 der Anforderungen (Auth, Rules, Firestore, Storage, alle Bereiche, Offline, Fehlerfälle, mehrere Bildschirmgrößen), Code-Bereinigung, README vollständig.
- **Abschluss:** Alle Abschlusskriterien aus Abschnitt 60 der Anforderungen erfüllt und belegt.

---

## 10. Teststrategie (Zusammenfassung)

| Ebene | Werkzeug | Was |
|---|---|---|
| Logik | JUnit | Bestand, Überschneidung, Validierung, Fehler-Mapper, Bildskalierung |
| Rules | Firebase Emulator Suite + Rules-Unit-Testing (Node) | Rollen, Haushaltstrennung, Validierung, 3-Foto-Grenze |
| UI/Abläufe | Compose-UI-Tests / Instrumentation | Kernabläufe, Zustände |
| Gerät | manuell | GPS, Kamera, Berechtigungen, Offline (Flugmodus), Kartenanzeige |
| Build | GitHub Actions | `assembleDebug`, Unit-Tests |

Regel 7 der Anforderungen gilt: Was nicht getestet wurde, wird nicht als fertig gemeldet. Was ich hier **nicht** selbst testen kann (Gerätefunktionen, echtes Firebase-Projekt), kennzeichne ich ausdrücklich als „vom Nutzer zu prüfen".

---

## 11. Offene Entscheidungen (deine Freigabe nötig)

**1. Dateispeicher (dringend, blockiert Phase 6 und 7)** – Problem: Firebase Storage verlangt für neue Projekte den kostenpflichtigen Blaze-Tarif.
| Option | Kosten | Vorteile | Nachteile |
|---|---|---|---|
| A. Blaze-Tarif mit Storage in `US-CENTRAL1/EAST1/WEST1` (Always-Free) plus Budgetalarm | Erwartet 0 €, aber Kreditkarte/Abrechnungskonto nötig; Überschreiten der Gratisgrenze wäre kostenpflichtig | Ein System, saubere Rules, keine Größenprobleme | Widerspricht „kein kostenpflichtiger Tarif"; Daten in den USA (Datenschutz, Familien-App mit Dokumenten wie Fahrzeugschein) |
| B. Dateien direkt in Firestore ablegen (Base64, komprimiert) | 0 € im Spark-Tarif | Kein Blaze, alles in einem System, Rules gelten | 1-MiB-Grenze je Dokument: reicht für komprimierte Fotos und kleine Bilder, **nicht** für größere PDFs (Versicherung, Bedienungsanleitungen); Größeres müsste in Teilstücke zerlegt oder abgelehnt werden |
| C. Fremder kostenloser Dateispeicher (z. B. Supabase) | 0 € | Große Dateien möglich | Zweites Backend, eigene Auth-Anbindung, Datenschutz; mehr Komplexität – widerspricht „einfach vor komplex" |
| D. Bestehendes älteres Firebase-Projekt mit `*.appspot.com`-Bucket, falls du eines hast | 0 € | Gratis-Kontingent bleibt erhalten | Nur wenn ein solches Projekt existiert; nicht neu anlegbar |

*Meine technische Empfehlung:* **B für Stellplatzfotos und Belege (Bilder), und für PDFs eine Größenbegrenzung (~700 KB) oder Option A nur wenn du bewusst Blaze zulässt.** Begründung: Damit bleibt die Regel „kostenlos, ein System, keine Drittanbieter" erfüllt. Die Einschränkung bei großen PDFs ist der Preis dafür. Wenn dir große Dokumente wichtig sind, ist A die technisch sauberere Lösung – aber das ist deine Entscheidung. Es wird nichts eingerichtet, bevor du entschieden hast.
*Unsicherheit:* Die tatsächliche Kompressionsgröße der Fotos (Annahme 200–500 KB) und Firestore-Kontingente (Stand heute) sind nicht von mir gemessen bzw. neu geprüft worden – das prüfe ich in Phase 1/6, bevor ich baue.

**2. Firebase-Projekt** – Wer legt es an (ich kann keine Konsole bedienen)? Region Firestore (Vorschlag EU, z. B. `eur3`/`europe-west`)? **2b.** Wie treten Familienmitglieder dem Haushalt bei (Vorschlag: Einladungscode, den der ADMIN erzeugt)?

**3. Karte** – Vorschlag: OpenStreetMap-Kacheln mit einer Open-Source-Bibliothek (Kandidaten: osmdroid, MapLibre; Pflegezustand und Lizenz prüfe ich in Phase 6). Google Maps SDK **nicht** ohne Prüfung, da API-Schlüssel und Abrechnungskonto nötig sein können (nicht verifiziert). Die OSM-Nutzungsrichtlinien für Kacheln erlauben nur moderate Nutzung – für eine kleine Familien-App vermutlich unkritisch, das ist nicht geprüft.

**4. Löschrechte** – Darf MEMBER eigene Einträge löschen, oder nur ADMIN? (Vorschlag: MEMBER löscht eigene Einträge, ADMIN alles.)

**5. Kalender-Überschneidung** – Speichern nach Warnung erlauben? (Vorschlag: ja, mit ausdrücklicher Bestätigung „Trotzdem speichern".)

**6. Excel** – Bitte Datei bereitstellen; soll vorhandener Datenbestand einmalig importiert werden?

**7. Navigation** – 6 Hauptbereiche in Bottom Bar oder Aufteilung/„Mehr"?

**8. Dashboard** – Was bedeutet „aktueller Zeitraum" (Kalendermonat, laufendes Jahr, Zeitraum aus Excel)?

**9. Löschen als „gelöscht markieren"** statt hartes Löschen? (Vorschlag: hartes Löschen mit Bestätigung; einfacher, passend zur kleinen Datenmenge.)

**10. `google-services.json`** – nicht committen (Vorschlag) oder committen?

---

## 12. Erkannte technische Risiken

| # | Risiko | Wirkung | Gegenmaßnahme |
|---|---|---|---|
| 1 | **Firebase Storage nur mit Blaze** | Konflikt mit Kostenvorgabe | Entscheidung 1 vor Phase 6 |
| 2 | **Excel fehlt** | Finanzmodell, Kategorien, Anfangsbestand unbekannt | Finanzphase verschoben; generisches Modell; Datei anfordern |
| 3 | **Kein Android-SDK in dieser Sitzung; Netzzugang zu `dl.google.com`, `maven.google.com`, `services.gradle.org` blockiert** (geprüft) | Ich kann die App hier voraussichtlich **nicht kompilieren** | Build über GitHub Actions (öffentliches Repo, kostenlos) oder lokal bei dir; ich melde nichts als „kompiliert", was nicht tatsächlich gebaut wurde |
| 4 | **Keine Geräte-/Firebase-Konsole-Zugriffe** | GPS, Kamera, echtes Firebase-Projekt nicht von mir testbar | Emulator-Tests für Rules; Gerätetests kennzeichne ich als „von dir zu prüfen" |
| 5 | **Firestore-Offline-Cache** meldet lokalen Schreiberfolg | Widerspricht Anforderung 8 | Persistenz deaktivieren; Schreiben nur mit Serverbestätigung (`await()` auf Server-Antwort) |
| 6 | **Haushaltsbeitritt ohne Cloud Functions** | Schwierigste Rules-Stelle; Fehler hier untergräbt Haushaltstrennung | Früh entwerfen (Phase 3), ausführlich im Emulator testen |
| 7 | **Rules mit `exists()/get()`** | Jede Regelprüfung kann Lesevorgänge kosten | Bei kleiner Datenmenge unkritisch; im Blick behalten |
| 8 | **Kalender-Überschneidung ohne Transaktion** | Zwei gleichzeitige Einträge könnten kollidieren | Akzeptiert und dokumentiert |
| 9 | **Öffentliches Repository** | Versehentliche Veröffentlichung von Konfigurationsdateien | `.gitignore` zuerst, keine Keystores/JSON-Secrets, Prüfung vor jedem Push |
| 10 | **OSM-Kacheln** – Nutzungsrichtlinien | Sperrung bei Missbrauch | Nur moderate Nutzung, User-Agent setzen, Alternative prüfen |
| 11 | **Versionsstände** (Kotlin, AGP, Compose, Firebase) | Inkompatibilitäten | Aktuelle stabile Versionen zu Beginn von Phase 1 recherchieren, nicht aus dem Gedächtnis setzen |
| 12 | **6 Ziele in der Bottom Bar** | Schlechte Bedienbarkeit | Entscheidung 7 |

---

## 13. Verbesserungsvorschläge (nicht umgesetzt, nur zur Entscheidung)

- Einladung per Code statt offener Registrierung (siehe 2b) – ist Teil des Plans, sobald bestätigt.
- Kategorien-Verwaltung durch ADMIN in der App, falls sich die Excel-Struktur ändert.
- Export der Finanzdaten als CSV (nicht in den Anforderungen; nur bei Bedarf).

---

## 14. Fortschrittsprotokoll

| Phase | Status | Datum | Offene Punkte |
|---|---|---|---|
| 0 Analyse | abgeschlossen, wartet auf Freigabe | 30.09.2026 | Entscheidungen 1–10, Excel-Datei |
| 1–11 | nicht begonnen | | |
