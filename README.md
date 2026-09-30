# OBELIX

Native Android-App zur gemeinsamen Verwaltung des Familien-Wohnmobils: Kalender, Einnahmen und Ausgaben, Auffälligkeiten, geplante Anschaffungen, Stellplätze und Dokumente.

Status: **Phase 1 bis 8 abgenommen** (zuletzt Auffälligkeiten), **Phase 9 (Stellplätze) umgesetzt, Gerätetest offen**. Fortschritt und Entscheidungen: [`docs/PROJEKTPLAN.md`](docs/PROJEKTPLAN.md), Übergabe an einen neuen Chat: [`docs/UEBERGABE.md`](docs/UEBERGABE.md).

## Technologien
- Kotlin, Jetpack Compose, Material 3 (adaptive Navigation: Leiste auf Smartphones, Rail auf Tablets)
- Navigation Compose mit typsicheren Routen
- Firebase Authentication und Cloud Firestore (kostenloser Spark-Tarif, Region `europe-west3`)
- Dateien (Fotos, Belege, Dokumente) werden in Firestore abgelegt, kein Firebase Storage (siehe Plan, Abschnitt 6)
- Stellplätze: Standort (Google Play Services, nur auf Knopfdruck) und Karte mit OpenStreetMap-Kacheln (osmdroid, ohne API-Schlüssel)
- Kein Analytics, kein Tracking, keine Werbung

## Architektur
```
UI (Compose) → ViewModel (StateFlow) → Repository → Firebase
```
Orientiert am [Android-Architekturleitfaden](https://developer.android.com/topic/architecture) (UI-Layer, Data-Layer, unidirektionaler Datenfluss), am [Kotlin-Styleguide](https://developer.android.com/kotlin/style-guide) und an [Material 3](https://m3.material.io). Manuelle Dependency Injection über `AppContainer`, kein Hilt.

```
app/src/main/java/de/hagi089/obelix/
  AppContainer.kt, ObelixApplication.kt, MainActivity.kt
  core/error      Fehler → verständliche deutsche Meldungen
  core/network    Erkennung der Internetverbindung
  core/validation Eingabeprüfung (Anmeldung, Zugangscode)
  data/auth       Anmeldung (Firebase Auth)
  data/user       Benutzer, Rollen, Zugangscode (Firestore)
  data/finance, planned, calendar, repairs, campsites, files   Fachbereiche und Dateiablage
  ui/             App-Gerüst, Navigation, Theme, Bildschirme (auth, onboarding, settings, finance, planned, calendar, repairs, campsites)
firebase/         Firestore-Sicherheitsregeln und ihre Tests (rules-tests/)
```

## Einrichtung
1. Android Studio (aktuelle stabile Version) mit JDK 17 oder neuer.
2. Firebase-Projekt anlegen: [`docs/FIREBASE-EINRICHTUNG.md`](docs/FIREBASE-EINRICHTUNG.md).
3. `google-services.json` nach `app/` kopieren (im Repository liegt die des Projekts, siehe Plan, Risiko 2c; eigene Projekte: Datei ersetzen, nicht weitergeben).
   Ohne die Datei baut das Projekt trotzdem; die App zeigt dann „Backend nicht eingerichtet".

## Build
```bash
./gradlew assembleDebug        # Debug-APK: app/build/outputs/apk/debug/
```

## Tests
Testfälle und Ergebnisse: [`docs/TESTFAELLE.md`](docs/TESTFAELLE.md).

```bash
./gradlew testDebugUnitTest    # Unit-Tests
./gradlew lintDebug            # Android Lint
```
Jeder Push auf `main` baut die App in GitHub Actions (`.github/workflows/build.yml`) und führt in einem zweiten Job die Tests der Sicherheitsregeln im Firebase-Emulator aus.

Regel-Tests lokal (Node 22 und Java 21 nötig):
```bash
npm install -g firebase-tools
npm install --prefix firebase/rules-tests
cd firebase && firebase emulators:exec --only firestore --project demo-obelix "npm --prefix rules-tests test"
```
Die Regeln selbst werden in der Firebase-Konsole veröffentlicht, siehe [`docs/FIREBASE-EINRICHTUNG.md`](docs/FIREBASE-EINRICHTUNG.md), Abschnitte 7 und 8.

## Daten und Datenschutz
- Private Daten (Excel-Datei, Analysen mit Namen und Beträgen) liegen im Ordner `private/` und werden nie committet.
- Keine Passwörter, Schlüssel oder `google-services.json` im Repository.
