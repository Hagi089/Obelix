# OBELIX – Testfälle

Stand: 30.09.2026 · zuletzt geprüfter Commit: `9aa78b2` (GitHub-Bau grün) · gehört zu [`PROJEKTPLAN.md`](PROJEKTPLAN.md)

Diese Datei wird **nach jeder Phase und nach jedem Testlauf aktualisiert**. Der Plan verweist nur hierher.

**Status:** ✅ bestanden · ⏳ offen, wartet auf den Benutzer (Gerätetest) · ⬜ geplant (Phase noch nicht umgesetzt) · ❌ fehlgeschlagen

**Wer prüft was:** *Automatisch* = läuft bei jedem Push in GitHub Actions, das Ergebnis lese ich dort ab. *Gerät* = kann ich hier nicht testen (kein Gerät, kein Emulator, kein Zugriff auf dein Firebase-Projekt); das musst du auf dem Smartphone prüfen. *Emulator* = Firebase-Emulator für die Sicherheitsregeln, ab Phase 3.

---

## 1. Aktueller Stand (Übersicht)

| Bereich | Automatisch | Gerät (du) |
|---|---|---|
| Projektbasis (Phase 1) | ✅ Build, Lint | ⏳ 3 Fälle |
| Authentifizierung (Phase 2) | ✅ 13 Unit-Tests | ⏳ 9 Fälle |
| Alle weiteren Bereiche | ⬜ | ⬜ |

**Automatische Prüfung insgesamt (GitHub, Commit `9aa78b2`):** `assembleDebug` ✅ · `testDebugUnitTest` ✅ (13 Tests) · `lintDebug` ✅ · keine Warnungen im Bauablauf.

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

### 2.2 Gerätetest Phase 1 – Projektbasis (⏳ offen, du)

Voraussetzung: Debug-APK aus dem obersten GitHub-Lauf (Actions → Artifacts → `obelix-debug-apk`) installiert.

| ID | Schritte | Erwartet | Ergebnis |
|---|---|---|---|
| G1-01 | App starten | Startet ohne Absturz. Weil du nicht angemeldet bist, erscheint die Anmeldeseite (nicht der Hauptbereich) | ⏳ |
| G1-02 | Nach Anmeldung (siehe G2-04): unten sind **sechs** Bereiche sichtbar: Dashboard, Kalender, Finanzen, Aufgaben, Stellplätze, Dokumente. Tippe jeden an | Jeder Bereich wird geöffnet, der aktive ist markiert, Titel oben passt, Text „Dieser Bereich ist noch nicht verfügbar" | ⏳ |
| G1-03 | Zahnrad oben rechts tippen, dann den Zurück-Pfeil | Einstellungen öffnen, Zurück führt zum vorigen Bereich | ⏳ |

### 2.3 Gerätetest Phase 2 – Authentifizierung (⏳ offen, du)

Hinweis: Für neue Konten eine E-Mail-Adresse verwenden, auf die du Zugriff hast (für den Passwort-Reset).

| ID | Schritte | Erwartet | Ergebnis |
|---|---|---|---|
| G2-01 | „Konto erstellen" mit Name, gültiger E-Mail, Passwort mit mindestens 8 Zeichen | Danach erscheint der Hauptbereich. In der Firebase-Konsole (Authentication → Benutzer) erscheint das Konto | ⏳ |
| G2-02 | Erneut registrieren mit **derselben** E-Mail | Meldung „Zu dieser E-Mail-Adresse gibt es bereits ein Konto. Bitte melde dich an." | ⏳ |
| G2-03 | Registrieren mit leerem Namen, ungültiger E-Mail (z. B. `abc`), Passwort mit 5 Zeichen | Jeweils Meldung am Feld, kein Serveraufruf, kein neues Konto in Firebase | ⏳ |
| G2-04 | Zahnrad → „Abmelden", danach mit den Daten aus G2-01 anmelden | Nach dem Abmelden erscheint die Anmeldeseite; die Anmeldung führt in den Hauptbereich | ⏳ |
| G2-05 | Angemeldet: App komplett schließen (aus der Übersicht wischen) und neu öffnen | Du bleibst angemeldet. Nach Abmelden, Schließen und Öffnen bleibst du abgemeldet | ⏳ |
| G2-06 | Anmelden mit falschem Passwort; dann mit unbekannter E-Mail | Beide Male „E-Mail-Adresse oder Passwort ist falsch." (gleicher Text) | ⏳ |
| G2-07 | „Passwort vergessen?" → E-Mail eingeben → Link senden. Danach Link in der E-Mail öffnen (auch Spam prüfen), neues Passwort setzen, damit anmelden | Bestätigungstext erscheint (auch bei unbekannter E-Mail derselbe Text); E-Mail kommt an; Anmeldung mit neuem Passwort klappt | ⏳ |
| G2-08 | Flugmodus an, dann App öffnen, dann Anmelden versuchen | Oben rotes Feld „Keine Internetverbindung. Die angezeigten Daten sind möglicherweise nicht aktuell."; Anmelden zeigt „Keine Verbindung zum Server …", **keine** Erfolgsmeldung. Flugmodus aus: Hinweis verschwindet | ⏳ |
| G2-09 | Passwort-Augensymbol antippen; Bildschirm drehen; Tastatur „Weiter/Fertig"-Taste benutzen | Passwort wird angezeigt bzw. verborgen; Eingaben bleiben beim Drehen erhalten; „Fertig" sendet das Formular ab | ⏳ |

Bitte für jeden Fall ✅ oder ❌ melden, bei ❌ mit einem Screenshot oder der genauen Meldung.

### 2.4 Bekannte Lücken der Tests
- Die ViewModels (Anmeldeablauf) haben noch keine automatischen Tests, weil die Coroutine-Testbibliothek noch nicht eingebunden ist. Der Ablauf wird bisher nur durch G2-01 bis G2-08 geprüft.
- Kein automatischer Bedienungstest der Oberfläche (Compose-UI-Test). Der Bau in der Cloud kann keinen Emulator starten.

---

## 3. Zu erledigende Testfälle (geplant, je Phase)

Wird beim Umsetzen der jeweiligen Phase in konkrete Fälle mit Schritten überführt. Grundlage: Anforderungen Abschnitt 42 und 43.

| Phase | Bereich | Geplante Testfälle | Art |
|---|---|---|---|
| 3 | Haushalt, Rollen, Sicherheitsregeln | Haushalt anlegen, Beitritt per Einladungscode (gültig, ungültig, schon benutzt) · Rolle ADMIN/MEMBER · MEMBER darf normale Daten ändern, aber keine Mitglieder/Rollen verwalten · Selbst-Beförderung zum ADMIN verboten · nicht angemeldet = kein Zugriff · **Benutzer A darf nie Daten von Haushalt B sehen oder ändern (und umgekehrt)** | Emulator (automatisch), zusätzlich Gerät mit zwei Testkonten |
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
