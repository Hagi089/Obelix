# OBELIX

Native Android-App zur gemeinsamen Verwaltung des Familien-Wohnmobils. Alle Benutzer arbeiten auf demselben, aktuellen Datenbestand im Backend (Firebase, kostenloser Spark-Tarif). Es gibt keinen eigenen Server, keine Werbung, kein Tracking und keine Analytics.

Status: **Alle Phasen (1 bis 12) abgenommen; alle Abschlusskriterien erfüllt.** Version 23 (Admin-Backup; seit Version 22 fester Signaturschlüssel, Updates ohne Deinstallieren). Fortschritt und Entscheidungen: [`docs/PROJEKTPLAN.md`](docs/PROJEKTPLAN.md), Testfälle und Nachweis der Abschlusskriterien: [`docs/TESTFAELLE.md`](docs/TESTFAELLE.md), Verteilung und Updates: [`docs/VERTEILUNG.md`](docs/VERTEILUNG.md), Stand für einen neuen Chat: [`docs/UEBERGABE.md`](docs/UEBERGABE.md).

## Funktionen
| Bereich | Inhalt |
|---|---|
| Dashboard | Kennzahlen aus den echten Daten: aktuelle Nutzung oder nächster Termin, Kontostand, Ausgaben des laufenden Jahres, offene Forderungen, offene Auffälligkeiten, offene geplante Anschaffungen, Anzahl Stellplätze. Ohne Daten steht ein Hinweis, nie eine erfundene Zahl. |
| Kalender | Monatsraster mit Farbe je Person, Einträge mit Von/Bis, Person, Ziel und Kommentar. Überschneidungen werden vor dem Speichern angezeigt; gespeichert wird erst nach „Trotzdem speichern“. |
| Finanzen | Einnahmen und Ausgaben mit Kategorie, „Bezahlt von“, Abrechnungsstatus (offen, erstattet, gesponsert) und optionalem Beleg (Foto oder PDF). Kontostand und Forderungen werden aus den Buchungen berechnet. |
| Geplante Ausgaben | Anschaffungen mit geschätztem Betrag. „Gekauft“ erzeugt in einer Transaktion eine echte Ausgabe mit dem tatsächlichen Betrag. Planungen verändern den Kontostand nie. |
| Aufgaben | Auffälligkeiten und Reparaturen: erstellen, bearbeiten, erledigen, wieder öffnen, Filter Offen/Erledigt/Alle. |
| Stellplätze | Standort einmalig auf Knopfdruck, Kommentar, bis zu drei Fotos, Liste und Karte (OpenStreetMap). Im Formular zeigt eine Karte mit verschiebbarem Marker die Position; ein ungenauer Standort lässt sich durch Ziehen des Markers korrigieren. „Navigation starten“ nutzt immer die gespeicherte (korrigierte) Position und öffnet eine externe App. |
| Dokumente | Wichtige Unterlagen (Bild oder PDF) mit Name und Kategorie, öffnen und löschen. |
| Einstellungen | Hell-/Dunkelmodus, Benutzerverwaltung und Zugangscode (nur ADMIN), Kategorien (nur ADMIN), Excel-Import (einmalig, nur ADMIN), **Backup als ZIP (nur ADMIN, siehe [`docs/BACKUP.md`](docs/BACKUP.md))**, Version. |

Jeder Bildschirm kennt die Zustände Laden, Leer, Fehler und Offline. Bei fehlender Verbindung steht „Keine Internetverbindung. Die angezeigten Daten sind möglicherweise nicht aktuell.“ Eine Änderung gilt erst als gespeichert, wenn der Server sie bestätigt hat (kein dauerhafter Offline-Cache, keine Offline-Synchronisation). Gelöscht wird immer nach einer Bestätigung.

## Benutzer, Rollen und Zugangscode
- Anmeldung mit E-Mail und Passwort (Firebase Authentication), Passwort zurücksetzen per E-Mail.
- Es gibt **keine Haushalte**: Alle freigeschalteten Benutzer teilen einen Datenbestand.
- **Registrierung nur mit Zugangscode.** Ein gemeinsamer Code, den der ADMIN verteilt und in den Einstellungen erneuern kann. Ohne gültigen Code legen die Regeln kein Benutzerdokument an und gewähren keinen Zugriff.
- Rollen: **ADMIN** (Benutzer und Rollen verwalten, Code, Kategorien, Import) und **MEMBER** (alle normalen Daten anlegen, ändern und löschen).
- Die Berechtigungen setzen die Firestore-Sicherheitsregeln (`firebase/firestore.rules`) serverseitig durch, nicht nur die Oberfläche.

## Technologien
- Kotlin, Jetpack Compose, Material 3 (adaptive Navigation: Leiste auf Smartphones, Rail auf Tablets), `minSdk` 26
- Navigation Compose mit typsicheren Routen
- Firebase Authentication und Cloud Firestore (Spark-Tarif, Region `europe-west3`), keine Cloud Functions
- Dateien (Belege, Fotos, Dokumente) liegen in Stücken zu 900 KiB in Firestore, höchstens 8 MiB je Datei (Firebase Storage ist ohne Bezahltarif nicht nutzbar, siehe Plan, Abschnitt 6)
- Standort über Google Play Services, nur auf Knopfdruck und im Vordergrund; Karte mit osmdroid und OpenStreetMap-Kacheln (ohne API-Schlüssel)
- Android-Architekturleitfaden, [Kotlin-Styleguide](https://developer.android.com/kotlin/style-guide) und [Material 3](https://m3.material.io)

## Architektur
```
UI (Compose) → ViewModel (StateFlow) → Repository → Firebase
```
Manuelle Dependency Injection über `AppContainer`, kein Hilt. Reine Rechen- und Prüflogik (Kontostand, Überschneidung, Validierung, Dashboard) liegt ohne Android-Abhängigkeit in `data/…` und ist per JUnit getestet.

```
app/src/main/java/de/hagi089/obelix/
  AppContainer.kt, ObelixApplication.kt, MainActivity.kt
  core/           Fehler → deutsche Meldungen, Geld (Cent), Netzwerkstatus, Eingabeprüfung
  data/auth       Anmeldung (Firebase Auth)
  data/user       Benutzer, Rollen, Zugangscode
  data/finance, planned, calendar, repairs, campsites, documents, dashboard, files
                  Fachbereiche mit Modellen, Prüfung, Logik und Repository; files = Dateiablage
  ui/             App-Gerüst, Navigation, Theme und je Bereich Bildschirme und ViewModels
firebase/         Firestore-Sicherheitsregeln und ihre Tests (rules-tests/)
docs/             Plan, Testfälle, Übergabe, Firebase-Einrichtung
```
Datenmodell, Sicherheitskonzept und Datenflüsse: [`docs/PROJEKTPLAN.md`](docs/PROJEKTPLAN.md), Abschnitte 5 bis 7.

## Einrichtung
1. Android Studio (aktuelle stabile Version) mit JDK 17 oder neuer.
2. Eigenes Firebase-Projekt anlegen, Anmeldung und Firestore einrichten: [`docs/FIREBASE-EINRICHTUNG.md`](docs/FIREBASE-EINRICHTUNG.md).
3. `app/google-services.json` des eigenen Projekts ablegen. Im Repository liegt die Datei des Familien-Projekts; sie ist kein Geheimnis im Firebase-Sinn (Plan, Risiko 2c), geschützt werden die Daten durch die Sicherheitsregeln. Wer ein eigenes Projekt nutzt, ersetzt sie. Fehlt sie ganz, baut das Projekt trotzdem und die App zeigt „Backend nicht eingerichtet“.
4. **Sicherheitsregeln veröffentlichen** (`firebase/firestore.rules`, Firebase-Konsole → Firestore → Regeln) und den ersten Zugangscode sowie die ADMIN-Rolle einmalig anlegen: [`docs/FIREBASE-EINRICHTUNG.md`](docs/FIREBASE-EINRICHTUNG.md), Abschnitte 7 und 8. Bis dahin ist die Datenbank komplett gesperrt. Nach jeder Änderung an den Regeln erneut veröffentlichen.

## Build
```bash
./gradlew assembleDebug        # Debug-APK: app/build/outputs/apk/debug/
./gradlew assembleRelease      # Release-APK: app/build/outputs/apk/release/ (signiert nur mit festem Schlüssel)
```
**Verteilen:** GitHub Actions signiert Debug- und Release-APK mit einem **festen Schlüssel** aus den Repository-Secrets `OBELIX_KEYSTORE_BASE64` und `OBELIX_KEYSTORE_PASSWORD` und stellt die Release-APK als Artefakt **`obelix-apk`** bereit. Damit lassen sich neue Versionen als **Update** installieren, ohne zu deinstallieren. Der Schlüssel liegt nie im Repository. Ohne die Secrets entsteht nur eine Debug-APK mit zufälligem Schlüssel (`obelix-debug-apk`, nur zum Testen). Einrichtung und Ablauf: [`docs/VERTEILUNG.md`](docs/VERTEILUNG.md).

Die Version steht unten in den Einstellungen; bei jedem Deployment wird sie erhöht (`versionCode` +1, `versionName` zweistellig in `app/build.gradle.kts`), sonst lehnt Android das Update ab.

## Tests
Testfälle, Ergebnisse und der Nachweis der Abschlusskriterien: [`docs/TESTFAELLE.md`](docs/TESTFAELLE.md).

```bash
./gradlew testDebugUnitTest    # Unit-Tests (JUnit, ohne Gerät)
./gradlew lintDebug            # Android Lint
```
Jeder Push auf `main` und jeder Pull Request laufen in GitHub Actions (`.github/workflows/build.yml`):
- Job „build“: `assembleDebug`, Unit-Tests, Lint, Anzeige der angeforderten Berechtigungen.
- Job „rules“: Tests der Sicherheitsregeln im Firebase-Emulator, ohne Zugriff auf das echte Projekt.

Regel-Tests lokal (Node 22 und Java 21 nötig):
```bash
npm install -g firebase-tools
npm install --prefix firebase/rules-tests
cd firebase && firebase emulators:exec --only firestore --project demo-obelix "npm --prefix rules-tests test"
```
Nicht automatisch testbar sind Gerätefunktionen (Standort, Kamera, Karte, Dateiauswahl, Darstellung, Offline) und das Verhalten gegen das echte Firebase-Projekt. Dafür gibt es die Gerätetestfälle in `docs/TESTFAELLE.md`.

## Daten und Datenschutz
- Es werden nur die nötigen personenbezogenen Daten gespeichert (Name, E-Mail, Rolle, Einträge der Benutzer). Keine Analytics, kein Tracking, keine Werbung, keine Weitergabe an Dritte außer den technisch nötigen Diensten (Firebase, OpenStreetMap-Kacheln, Google Play Services für den Standort).
- Der Standort wird nur beim ausdrücklichen Speichern eines Stellplatzes abgefragt, nie im Hintergrund.
- Private Daten (Excel-Datei, Analysen, `obelix-import*.json`) liegen nie im Repository (`.gitignore`).
- Keine Passwörter, privaten Schlüssel (auch nicht der Signaturschlüssel) oder Service-Account-Dateien im Repository.
- Der bei der Registrierung mitgeschickte Zugangscode wird von der App sofort wieder aus dem Benutzerdokument entfernt; den aktuellen Code sieht nur der ADMIN.

## Bekannte Grenzen
- Keine Offline-Synchronisation (bewusst, Anforderung 8).
- Zwei Benutzer, die im selben Augenblick überlappende Kalendereinträge speichern, können sich überschneiden (die Prüfung läuft vor dem Schreiben, Plan Risiko 8).
- osmdroid ist archiviert; ein Ersatz beträfe nur `ui/campsites/CampsiteMap.kt`.
- Das Backup (Einstellungen, nur ADMIN) sichert Daten und Dateien, aber **ohne** Anmeldekonten und Zugangscode, und es gibt **kein Wiederherstellen in der App** (siehe [`docs/BACKUP.md`](docs/BACKUP.md)).
- Die Release-APK wird ohne R8 (Verkleinern/Verschleiern) gebaut, weil R8 nie getestet wurde (Plan, Entscheidung 48).
- Das Repository ist öffentlich; Empfehlung: auf privat stellen (siehe Plan, Abschnitt Sicherheitsprüfung).
