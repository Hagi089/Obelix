# OBELIX – Übergabe an den nächsten Chat

Stand: 30.09.2026 · Phase 4 (Finanzen, Kategorien, Excel-Import) **umgesetzt, Gerätetest G4 offen** · danach: **Phase 5 – Geplante Ausgaben**

Diese Datei ist die Kurzfassung für einen neuen Chat. Maßgeblich bleiben [`PROJEKTPLAN.md`](PROJEKTPLAN.md) (Plan, Entscheidungen, Datenmodell) und [`TESTFAELLE.md`](TESTFAELLE.md) (Tests). Die Projektanforderungen liegen im Claude-Projekt „Obelix Wohnmobil App" (Dokument `Anforderungen`).

---

## 1. Wo wir stehen

| Phase | Inhalt | Stand |
|---|---|---|
| 0 | Analyse, Architektur, Plan | ✅ freigegeben |
| 1 | Projektbasis (Compose, Material 3, Navigation, Firebase, CI) | ✅ auf dem Gerät abgenommen |
| 2 | Authentifizierung (Registrieren, Anmelden, Abmelden, Passwort-Reset) | ✅ auf dem Gerät abgenommen (G1-01 bis G2-09) |
| 3 | Benutzer, Rollen, Zugangscode, Firestore-Sicherheitsregeln | ✅ umgesetzt, Bau und Regel-Tests grün · ✅ Gerätetest G3-01 bis G3-11 bestanden |
| 4 | Finanzen, Kategorien, Excel-Import | ✅ umgesetzt, Bau, 49 Unit-Tests und 45 Regel-Tests grün · ⏳ Gerätetest G4-01 bis G4-16 offen |
| **5** | **Geplante Ausgaben** (nach Abnahme von Phase 4) | **⬜ nächste Phase** |
| 6–12 |  Dateiablage/Belege, Kalender, Auffälligkeiten, Stellplätze, Dokumente, Dashboard, Qualitätssicherung | ⬜ |

- Repository: `Hagi089/Obelix` (öffentlich), Branch `main`, letzter Stand mit grünem Bau.
- Firebase-Projekt `obelix-daf7c`: Tarif Spark, E-Mail/Passwort aktiv, Firestore in `europe-west3` im Produktionsmodus (alles gesperrt, bis Regeln vorliegen). Paketname `de.hagi089.obelix`.
- 49 automatische Unit-Tests, Build und Lint sowie 45 Regel-Tests im Firebase-Emulator (Job „rules") laufen bei jedem Push in GitHub Actions. Die Debug-APK liegt als Artefakt `obelix-debug-apk` im jeweils neuesten Lauf.
- **Die Regeln wirken erst, wenn du sie in der Firebase-Konsole veröffentlichst**, und der erste Zugangscode sowie deine Admin-Rolle werden einmalig von Hand angelegt: `docs/FIREBASE-EINRICHTUNG.md`, Abschnitte 7 und 8. Bis dahin ist die Datenbank komplett gesperrt.

## 2. Wichtigste Entscheidungen (Kurzfassung, Details im Plan)
- **Kosten:** alles kostenlos, Firebase Spark, keine Kreditkarte, kein Blaze. Firebase Storage entfällt; **Dateien liegen in Stücken (~900 KB) in Firestore**, höchstens 8 MB je Datei.
- **Kein Haushalt** (entschieden 30.09.2026): Alle registrierten Benutzer teilen denselben Datenbestand. Registrierung mit Name, E-Mail, **Zugangscode** und Passwort; danach nie wieder ein Code. Ein gemeinsamer Code in `config/access`, nur der ADMIN sieht und erneuert ihn. **Der Projektinhaber ist ADMIN, alle anderen MEMBER.** Serverseitig per Firestore-Regeln, kein Cloud Function. **Jeder Benutzer darf löschen** (immer mit Bestätigungsdialog).
- **Zahler:** „Bezahlt von" = **Benutzer** (sein Name), keine Parteien. Beim Import wird jede der zwei Excel-Parteien einem Benutzerkonto zugeordnet (Zuordnung nur im nicht-öffentlichen Projekt-Dokument `Excel-Analyse`). Abrechnungsstatus je Ausgabe: `OPEN`, `SETTLED`, `SPONSORED`.
- **Beträge:** Cent, genau 2 Nachkommastellen. Excel-Import (300 importierbare Buchungen, 25 Nullbetragszeilen übersprungen) einmalig durch den ADMIN in der App, Fehler der Excel werden unverändert übernommen und vom Benutzer korrigiert.
- **Kalender:** Überschneidungen sind speicherbar, müssen aber **vor dem Speichern** eindeutig angezeigt werden.
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
- **Offen:** Gerätetest G4-01 bis G4-16 ([`TESTFAELLE.md`](TESTFAELLE.md), 2.4a). **Vom Benutzer:** Regeln neu in der Firebase-Konsole veröffentlichen, neue APK installieren (alte vorher deinstallieren), Robert registrieren, Import ausführen.

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
- **`google-services.json`** liegt (vom Benutzer hochgeladen) im öffentlichen Repository unter `app/`. Kein Geheimnis im Firebase-Sinn, aber jeder kann damit Konten anlegen; die Daten schützen die Regeln (Phase 3).
- Versionen (geprüft 30.09.2026): AGP 9.3.3, Gradle 9.5.1, Kotlin 2.4.10, Compose BOM 2026.09.00, Navigation 2.10.2, Lifecycle 2.11.0, Firebase BoM 34.19.0, `compileSdk 37`, `targetSdk 36`, `minSdk 26`. Bei AGP 9 wird das Kotlin-Plugin **nicht** mehr separat angewendet.

## 5. Offene Punkte
| Nr. | Punkt | Vorschlag |
|---|---|---|
| 3 | Kartenlösung für Stellplätze | OpenStreetMap-Kacheln, Bibliothek in Phase 9 prüfen |
| 9 | Löschen: hart oder als „gelöscht" markiert | hart, mit Bestätigung |
| 10 | `google-services.json` im Repository lassen | ja (CI-APK ist dann sofort testbar) |
| 15b | „Verantwortung" aus der Excel | ✅ erledigt: an den Kommentar angehängt |
| 15c | Nullbeträge der Excel (25 Zeilen) | ✅ erledigt: nicht importiert, in der Vorschau aufgelistet |
| – | API-Schlüssel in der Google Cloud Console auf die App beschränken | braucht festen Debug-Schlüssel (SHA-1); CI erzeugt bei jedem Bau einen neuen. Vorschlag: festen Debug-Schlüssel für CI anlegen (nur zum Testen, keine Geheimnisse) |
| – | `targetSdk` von 36 auf 37 | später |
| – | ViewModel-Tests (Coroutine-Testbibliothek einbinden), jetzt auch für Einrichtung und Einstellungen | bei Gelegenheit |
| – | Mutationsprüfung der Regel-Tests (Regel bewusst schwächen, Test muss rot werden) | bei Gelegenheit |

Für offene Punkte gelten bis zur Antwort des Benutzers die Vorschläge.

## 6. Arbeitsregeln (aus Projektanweisungen und Anforderungen)
- Rolle: Senior Softwareentwickler und Senior QA Engineer. Erst analysieren, dann ändern; nur das Nötige; keine unnötigen Refactorings; nach jeder Änderung auf Regressionen prüfen; kurze Zusammenfassung geänderter Dateien und Funktionen.
- Nichts als „fertig" melden, was nicht getestet ist. Was ich nicht selbst testen kann, ausdrücklich als „vom Benutzer zu prüfen" kennzeichnen.
- Keine erfundenen Daten, keine ungefragten Erweiterungen (Verbesserungen nur vorschlagen), keine kostenpflichtigen Dienste.
- Nach jeder Phase: Implementieren → Bauen (GitHub) → Tests → Fehler beheben → Plan und `TESTFAELLE.md` aktualisieren → offene Punkte nennen → erst dann weiter. Aktuelle und zu erledigende Testfälle stehen immer in `docs/TESTFAELLE.md`.
- Plan und Testfälle zusätzlich im Claude-Projekt aktualisieren (`claude/Projektplan`, `claude/Testfaelle`).

---

## 7. Prompt für den neuen Chat (Phase 5)

Kopiere den folgenden Block als erste Nachricht in den neuen Chat (im selben Claude-Projekt „Obelix Wohnmobil App").

```text
Wir arbeiten am Projekt OBELIX (native Android-App für das gemeinsame Familien-Wohnmobil). Das GitHub-Repository heißt Obelix (Hagi089/Obelix, Branch main). Bitte binde es ein und lies zuerst diese Dateien, bevor du etwas änderst:
1. docs/UEBERGABE.md (Stand, Entscheidungen, Fallstricke)
2. docs/PROJEKTPLAN.md (Plan, Datenmodell, Security-Konzept, Phasen)
3. docs/TESTFAELLE.md (aktuelle und offene Testfälle)
4. im Claude-Projekt das Dokument "Anforderungen" (verbindliche Anforderungen)

Stand: Phase 1 bis 3 abgenommen. Phase 4 (Finanzen, Kategorien, Excel-Import) ist umgesetzt (Bau, 49 Unit-Tests, 45 Regel-Tests grün); der Gerätetest G4-01 bis G4-16 ist: [bestanden / Ergebnisse hier eintragen].

Auftrag jetzt: PHASE 5 – Geplante Ausgaben gemäß Plan. Erst analysieren und einen kurzen Plan zeigen, Fragen nur, wenn sie wirklich meine Entscheidung brauchen.

Rahmenbedingungen wie bisher: Senior Softwareentwickler und Senior QA Engineer, erst analysieren, nur das Nötige ändern, auf Regressionen prüfen, kurze Zusammenfassung der geänderten Dateien; alles kostenlos (Firebase Spark, ohne Cloud Functions), keine erfundenen Daten, keine ungefragten Erweiterungen; gebaut und getestet wird in GitHub Actions (kein Android-SDK, kein npm in der Cloud-Sitzung); was du nicht testen kannst, kennzeichne als "von mir zu prüfen"; vor jedem Push git pull --rebase origin main; Plan, docs/TESTFAELLE.md und die Projektdokumente (claude/Projektplan, claude/Testfaelle, claude/Uebergabe) am Ende aktualisieren. Zu den offenen Punkten in docs/UEBERGABE.md (Abschnitt 5) gelten deine Vorschläge, sofern ich nichts anderes sage. Excel-Dateien, Analysen und Importdateien mit Namen/Beträgen nie committen.
```
