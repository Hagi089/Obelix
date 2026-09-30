# OBELIX – Übergabe an den nächsten Chat

Stand: 30.09.2026 · Übergabe nach Abschluss von Phase 2 · nächste Phase: **Phase 3 – Haushalt, Rollen, Sicherheitsregeln**

Diese Datei ist die Kurzfassung für einen neuen Chat. Maßgeblich bleiben [`PROJEKTPLAN.md`](PROJEKTPLAN.md) (Plan, Entscheidungen, Datenmodell) und [`TESTFAELLE.md`](TESTFAELLE.md) (Tests). Die Projektanforderungen liegen im Claude-Projekt „Obelix Wohnmobil App" (Dokument `Anforderungen`).

---

## 1. Wo wir stehen

| Phase | Inhalt | Stand |
|---|---|---|
| 0 | Analyse, Architektur, Plan | ✅ freigegeben |
| 1 | Projektbasis (Compose, Material 3, Navigation, Firebase, CI) | ✅ auf dem Gerät abgenommen |
| 2 | Authentifizierung (Registrieren, Anmelden, Abmelden, Passwort-Reset) | ✅ auf dem Gerät abgenommen (G1-01 bis G2-09) |
| **3** | **Haushalt, Rollen, Firestore-Sicherheitsregeln** | **⬜ nächste Phase** |
| 4–12 | Finanzen + Import, Geplante Ausgaben, Dateiablage/Belege, Kalender, Auffälligkeiten, Stellplätze, Dokumente, Dashboard, Qualitätssicherung | ⬜ |

- Repository: `Hagi089/Obelix` (öffentlich), Branch `main`, letzter Stand mit grünem Bau.
- Firebase-Projekt `obelix-daf7c`: Tarif Spark, E-Mail/Passwort aktiv, Firestore in `europe-west3` im Produktionsmodus (alles gesperrt, bis Regeln vorliegen). Paketname `de.hagi089.obelix`.
- 13 automatische Unit-Tests, Build und Lint laufen bei jedem Push in GitHub Actions. Die Debug-APK liegt als Artefakt `obelix-debug-apk` im jeweils neuesten Lauf.
- Bisher gibt es **keine** Firestore-Daten und **keine** Sicherheitsregeln im Repository. Jedes registrierte Konto sieht denselben leeren Hauptbereich.

## 2. Wichtigste Entscheidungen (Kurzfassung, Details im Plan)
- **Kosten:** alles kostenlos, Firebase Spark, keine Kreditkarte, kein Blaze. Firebase Storage entfällt; **Dateien liegen in Stücken (~900 KB) in Firestore**, höchstens 8 MB je Datei.
- **Haushalt:** Beitritt per **Einladungscode**. Rollen `ADMIN` und `MEMBER`. **Jedes Mitglied darf löschen** (immer mit Bestätigungsdialog).
- **Zahler:** „Bezahlt von" = **Partei** (zwei Parteien im Haushalt). Abrechnungsstatus je Ausgabe: `OPEN`, `SETTLED`, `SPONSORED`.
- **Beträge:** Cent, genau 2 Nachkommastellen. Excel-Import (325 Buchungen) einmalig, Fehler der Excel werden unverändert übernommen und vom Benutzer korrigiert.
- **Kalender:** Überschneidungen sind speicherbar, müssen aber **vor dem Speichern** eindeutig angezeigt werden.
- **Design:** Google-Richtlinien (Material 3, Systemfarben, Android-Architekturleitfaden, Barrierefreiheit). Deutsche Oberfläche.
- **Architektur:** UI → ViewModel → Repository → Firebase; manuelle Dependency Injection (`AppContainer`), Firestore ohne dauerhaften Offline-Cache, damit nichts als „gespeichert" gilt, was der Server nicht bestätigt hat.
- **Reihenfolge:** Finanzen wurden vorgezogen (direkt nach Haushalt).

## 3. Was Phase 3 leisten soll (Ziel laut Plan)
1. Erster Benutzer legt einen Haushalt an und ist ADMIN.
2. Weitere Personen treten mit einem vom ADMIN erzeugten Einladungscode bei (MEMBER).
3. Zwei Parteien im Haushalt; jedes Mitglied gehört zu einer Partei.
4. ADMIN verwaltet Mitglieder und Rollen (Einstellungen).
5. **Firestore-Sicherheitsregeln** (`firebase/firestore.rules`) mit Tests im Firebase-Emulator: kein Zugriff ohne Anmeldung; nur Mitglieder sehen Daten ihres Haushalts; Selbst-Beförderung zum ADMIN verboten; **Haushalt A sieht nie Daten von Haushalt B**.
6. Modelle `User`, `Household`, `Member`; Sammlungen `users/{uid}`, `households/{hid}`, `households/{hid}/members/{uid}` (siehe Plan, Abschnitt 5).
7. Tests in `TESTFAELLE.md` von „geplant" in konkrete Fälle überführen, danach Plan und Tests aktualisieren.

## 4. Technische Fallstricke (aus diesem Chat gelernt)
- **Kein Android-SDK in der Cloud-Sitzung.** Netzzugang zu `dl.google.com`, `maven.google.com`, `services.gradle.org` ist gesperrt. Gebaut und getestet wird **in GitHub Actions**. Kompilierfehler stehen als Annotation am Lauf (Job „Fehler zusammenfassen"), abrufbar mit  
  `curl https://api.github.com/repos/Hagi089/Obelix/check-runs/<JOB-ID>/annotations` (Job-ID über `/actions/runs/<RUN-ID>/jobs`). Die Rohlogs sind nicht erreichbar.
- **Firebase-Emulator:** Ob er in der Cloud-Sitzung läuft (er lädt Dateien von Google), ist ungeprüft. Sicher ist: In GitHub Actions ist Netzzugang vorhanden. Regel-Tests deshalb voraussichtlich als eigener Schritt im Workflow (`setup-node`, `firebase-tools`, `firebase emulators:exec`).
- **Direkte Aufrufe an dein Firebase-Projekt** sind aus der Sitzung blockiert. Alles, was echtes Firebase braucht (Anmeldung, Datenbank, Regeln „live"), muss auf dem Gerät oder in der Konsole geprüft werden. Regeln veröffentlicht der Benutzer in der Firebase-Konsole (Firestore → Regeln) oder per `firebase deploy` (ohne Blaze möglich).
- **Git:** Der Benutzer lädt manchmal Dateien über die GitHub-Weboberfläche hoch (z. B. `google-services.json`). Vor jedem Push `git pull --rebase origin main`. Ein Stop-Hook fordert Commit und Push bei ungetrackten Dateien.
- **Commit-Zusatzzeilen** am Ende jeder Commit-Nachricht: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` und `Claude-Session: https://claude.ai/code/session_0128iXrTn5tXsy9UonZZs9qE` (bei neuem Chat die dort genannte aktuelle Zeile verwenden).
- **Nicht im Repository (bewusst):** Ordner `private/` (Excel-Datei `Einkausliste_WoMo_v2_1.xlsx` und die Analyse mit Namen und Beträgen). Ein neuer Chat hat diese Dateien **nicht**. Die Analyse steht im Claude-Projekt als Dokument `Excel-Analyse`. Für Phase 4 (Finanzen, Import) muss der Benutzer die Excel-Datei erneut anhängen.
- **`google-services.json`** liegt (vom Benutzer hochgeladen) im öffentlichen Repository unter `app/`. Kein Geheimnis im Firebase-Sinn, aber jeder kann damit Konten anlegen; die Daten schützen die Regeln (Phase 3).
- Versionen (geprüft 30.09.2026): AGP 9.3.3, Gradle 9.5.1, Kotlin 2.4.10, Compose BOM 2026.09.00, Navigation 2.10.2, Lifecycle 2.11.0, Firebase BoM 34.19.0, `compileSdk 37`, `targetSdk 36`, `minSdk 26`. Bei AGP 9 wird das Kotlin-Plugin **nicht** mehr separat angewendet.

## 5. Offene Punkte
| Nr. | Punkt | Vorschlag |
|---|---|---|
| 3 | Kartenlösung für Stellplätze | OpenStreetMap-Kacheln, Bibliothek in Phase 9 prüfen |
| 9 | Löschen: hart oder als „gelöscht" markiert | hart, mit Bestätigung |
| 10 | `google-services.json` im Repository lassen | ja (CI-APK ist dann sofort testbar) |
| 15b | „Verantwortung" aus der Excel | an den Kommentar anhängen, kein eigenes Feld |
| 15c | Nullbeträge der Excel (Inventarliste, 25 Zeilen) | nicht importieren, nur protokollieren |
| – | API-Schlüssel in der Google Cloud Console auf die App beschränken | braucht festen Debug-Schlüssel (SHA-1); CI erzeugt bei jedem Bau einen neuen. Vorschlag: festen Debug-Schlüssel für CI anlegen (nur zum Testen, keine Geheimnisse) |
| – | `targetSdk` von 36 auf 37 | später |
| – | ViewModel-Tests (Coroutine-Testbibliothek einbinden) | bei Gelegenheit |

Für offene Punkte gelten bis zur Antwort des Benutzers die Vorschläge.

## 6. Arbeitsregeln (aus Projektanweisungen und Anforderungen)
- Rolle: Senior Softwareentwickler und Senior QA Engineer. Erst analysieren, dann ändern; nur das Nötige; keine unnötigen Refactorings; nach jeder Änderung auf Regressionen prüfen; kurze Zusammenfassung geänderter Dateien und Funktionen.
- Nichts als „fertig" melden, was nicht getestet ist. Was ich nicht selbst testen kann, ausdrücklich als „vom Benutzer zu prüfen" kennzeichnen.
- Keine erfundenen Daten, keine ungefragten Erweiterungen (Verbesserungen nur vorschlagen), keine kostenpflichtigen Dienste.
- Nach jeder Phase: Implementieren → Bauen (GitHub) → Tests → Fehler beheben → Plan und `TESTFAELLE.md` aktualisieren → offene Punkte nennen → erst dann weiter. Aktuelle und zu erledigende Testfälle stehen immer in `docs/TESTFAELLE.md`.
- Plan und Testfälle zusätzlich im Claude-Projekt aktualisieren (`claude/Projektplan`, `claude/Testfaelle`).

---

## 7. Prompt für den neuen Chat (Phase 3)

Kopiere den folgenden Block als erste Nachricht in den neuen Chat (im selben Claude-Projekt „Obelix Wohnmobil App").

```text
Wir arbeiten am Projekt OBELIX (native Android-App für das gemeinsame Familien-Wohnmobil). Das GitHub-Repository heißt Obelix (Hagi089/Obelix, Branch main). Bitte binde es ein und lies zuerst diese Dateien, bevor du etwas änderst:
1. docs/UEBERGABE.md (Stand, Entscheidungen, Fallstricke)
2. docs/PROJEKTPLAN.md (Plan, Datenmodell, Security-Konzept, Phasen)
3. docs/TESTFAELLE.md (aktuelle und offene Testfälle)
4. im Claude-Projekt das Dokument "Anforderungen" (verbindliche Anforderungen)

Stand: Phase 1 (Projektbasis) und Phase 2 (Authentifizierung) sind umgesetzt und von mir auf dem Gerät erfolgreich getestet (G1-01 bis G2-09). Der Bau in GitHub Actions ist grün. Das Firebase-Projekt (obelix-daf7c, Spark-Tarif, Firestore in europe-west3, Produktionsmodus, E-Mail/Passwort aktiv) steht und ist in der App verbunden.

Auftrag jetzt: PHASE 3 – Haushalt, Rollen und Firestore-Sicherheitsregeln. Setze sie gemäß Plan um:
- Erster Benutzer legt einen Haushalt an und ist ADMIN. Weitere Personen treten per Einladungscode bei (MEMBER), den der ADMIN erzeugt.
- Haushalt hat zwei Parteien; jedes Mitglied gehört zu einer Partei.
- ADMIN verwaltet Mitglieder und Rollen in den Einstellungen.
- Sicherheitsregeln (firebase/firestore.rules) mit automatisierten Tests: ohne Anmeldung kein Zugriff, nur Mitglieder sehen Daten ihres Haushalts, keine Selbst-Beförderung zum ADMIN, Haushalt A sieht nie Daten von Haushalt B. Bitte ohne Cloud Functions und ohne Blaze-Tarif.
- Danach Plan, docs/TESTFAELLE.md und die Projektdokumente (claude/Projektplan, claude/Testfaelle) aktualisieren.

Wichtige Rahmenbedingungen:
- Arbeite als Senior Softwareentwickler und Senior QA Engineer. Erst analysieren, dann ändern, nur das Nötige, keine unnötigen Refactorings, nach Änderungen auf Regressionen prüfen, kurze Zusammenfassung der geänderten Dateien und Funktionen.
- Alles kostenlos (Firebase Spark). Keine ungefragten Erweiterungen, keine erfundenen Daten. Google-Richtlinien für Design und Architektur (Material 3).
- Die Cloud-Umgebung hat kein Android-SDK; gebaut und getestet wird in GitHub Actions. Fehler stehen als Annotation am Lauf. Was du nicht selbst testen kannst, kennzeichne als "von mir zu prüfen". Nichts als fertig melden, was nicht getestet ist.
- Vor jedem Push git pull --rebase origin main (ich lade manchmal Dateien über die GitHub-Weboberfläche hoch).
- Die Excel-Datei ist nicht im Repository (privat). Für die Finanzphase (Phase 4) hänge ich sie dann erneut an. Die Analyse steht im Projekt-Dokument "Excel-Analyse".
- Zu den offenen Punkten in docs/UEBERGABE.md (Abschnitt 5) gelten deine Vorschläge, sofern ich nichts anderes sage.

Fange bitte mit einem kurzen Plan für Phase 3 an (Datenmodell, Regeln, Einladungscode-Ablauf ohne Cloud Functions, Testansatz) und stelle mir nur Fragen, die wirklich meine Entscheidung brauchen. Der Einladungscode-Ablauf ist die sicherheitskritischste Stelle: entwirf ihn zuerst und zeige mir das Ergebnis, bevor du ihn umsetzt.
```
