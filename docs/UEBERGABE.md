# OBELIX – Übergabe an den nächsten Chat

Stand: 30.09.2026 · Phase 3 (Benutzer, Rollen, Zugangscode, Sicherheitsregeln) umgesetzt, **Haushalt wieder entfernt**, **Geräteabnahme offen** · danach: **Phase 4 – Finanzen und Excel-Import**

Diese Datei ist die Kurzfassung für einen neuen Chat. Maßgeblich bleiben [`PROJEKTPLAN.md`](PROJEKTPLAN.md) (Plan, Entscheidungen, Datenmodell) und [`TESTFAELLE.md`](TESTFAELLE.md) (Tests). Die Projektanforderungen liegen im Claude-Projekt „Obelix Wohnmobil App" (Dokument `Anforderungen`).

---

## 1. Wo wir stehen

| Phase | Inhalt | Stand |
|---|---|---|
| 0 | Analyse, Architektur, Plan | ✅ freigegeben |
| 1 | Projektbasis (Compose, Material 3, Navigation, Firebase, CI) | ✅ auf dem Gerät abgenommen |
| 2 | Authentifizierung (Registrieren, Anmelden, Abmelden, Passwort-Reset) | ✅ auf dem Gerät abgenommen (G1-01 bis G2-09) |
| 3 | Benutzer, Rollen, Zugangscode, Firestore-Sicherheitsregeln | ✅ umgesetzt, Bau und Regel-Tests grün · ⏳ Gerätetest G3-01 bis G3-11 offen |
| **4** | **Finanzen und Excel-Import** (nach Abnahme von Phase 3) | **⬜ nächste Phase** |
| 5–12 | Geplante Ausgaben, Dateiablage/Belege, Kalender, Auffälligkeiten, Stellplätze, Dokumente, Dashboard, Qualitätssicherung | ⬜ |

- Repository: `Hagi089/Obelix` (öffentlich), Branch `main`, letzter Stand mit grünem Bau.
- Firebase-Projekt `obelix-daf7c`: Tarif Spark, E-Mail/Passwort aktiv, Firestore in `europe-west3` im Produktionsmodus (alles gesperrt, bis Regeln vorliegen). Paketname `de.hagi089.obelix`.
- 22 automatische Unit-Tests, Build und Lint sowie 29 Regel-Tests im Firebase-Emulator (Job „rules") laufen bei jedem Push in GitHub Actions. Die Debug-APK liegt als Artefakt `obelix-debug-apk` im jeweils neuesten Lauf.
- **Die Regeln wirken erst, wenn du sie in der Firebase-Konsole veröffentlichst**, und der erste Zugangscode sowie deine Admin-Rolle werden einmalig von Hand angelegt: `docs/FIREBASE-EINRICHTUNG.md`, Abschnitte 7 und 8. Bis dahin ist die Datenbank komplett gesperrt.

## 2. Wichtigste Entscheidungen (Kurzfassung, Details im Plan)
- **Kosten:** alles kostenlos, Firebase Spark, keine Kreditkarte, kein Blaze. Firebase Storage entfällt; **Dateien liegen in Stücken (~900 KB) in Firestore**, höchstens 8 MB je Datei.
- **Kein Haushalt** (entschieden 30.09.2026): Alle registrierten Benutzer teilen denselben Datenbestand. Registrierung mit Name, E-Mail, **Zugangscode** und Passwort; danach nie wieder ein Code. Ein gemeinsamer Code in `config/access`, nur der ADMIN sieht und erneuert ihn. **Der Projektinhaber ist ADMIN, alle anderen MEMBER.** Serverseitig per Firestore-Regeln, kein Cloud Function. **Jeder Benutzer darf löschen** (immer mit Bestätigungsdialog).
- **Zahler:** „Bezahlt von" = **Benutzer** (sein Name), keine Parteien. Beim Import wird jede der zwei Excel-Parteien einem Benutzerkonto zugeordnet (Zuordnung nur im nicht-öffentlichen Projekt-Dokument `Excel-Analyse`). Abrechnungsstatus je Ausgabe: `OPEN`, `SETTLED`, `SPONSORED`.
- **Beträge:** Cent, genau 2 Nachkommastellen. Excel-Import (325 Buchungen) einmalig, Fehler der Excel werden unverändert übernommen und vom Benutzer korrigiert.
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

## 4. Technische Fallstricke (aus diesem Chat gelernt)
- **Kein Android-SDK in der Cloud-Sitzung.** Netzzugang zu `dl.google.com`, `maven.google.com`, `services.gradle.org` ist gesperrt. Gebaut und getestet wird **in GitHub Actions**. Kompilierfehler stehen als Annotation am Lauf (Job „Fehler zusammenfassen"), abrufbar mit  
  `curl https://api.github.com/repos/Hagi089/Obelix/check-runs/<JOB-ID>/annotations` (Job-ID über `/actions/runs/<RUN-ID>/jobs`). Die Rohlogs sind nicht erreichbar.
- **Firebase-Emulator und npm:** In der Cloud-Sitzung sind die npm-Registry und der Emulator-Download gesperrt (403). Die Regel-Tests laufen deshalb nur in GitHub Actions (Job „rules": Node 22, Java 21, `firebase-tools`, `@firebase/rules-unit-testing` 3.0.4 mit `firebase` 10.14.x). Das Ergebnis liest man am Lauf ab; die Testanzahl steht als Hinweis („Regel-Tests") in den Annotationen.
- **Direkte Aufrufe an dein Firebase-Projekt** sind aus der Sitzung blockiert. Alles, was echtes Firebase braucht (Anmeldung, Datenbank, Regeln „live"), muss auf dem Gerät oder in der Konsole geprüft werden. Regeln veröffentlicht der Benutzer in der Firebase-Konsole (Firestore → Regeln) oder per `firebase deploy` (ohne Blaze möglich).
- **Git:** Der Benutzer lädt manchmal Dateien über die GitHub-Weboberfläche hoch (z. B. `google-services.json`). Vor jedem Push `git pull --rebase origin main`. Ein Stop-Hook fordert Commit und Push bei ungetrackten Dateien.
- **Commit-Zusatzzeilen** am Ende jeder Commit-Nachricht: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` und `Claude-Session: <URL aus der Sitzungsvorgabe>` (bei neuem Chat die dort genannte aktuelle Zeile verwenden).
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

## 7. Prompt für den neuen Chat (Abnahme Phase 3, dann Phase 4)

Kopiere den folgenden Block als erste Nachricht in den neuen Chat (im selben Claude-Projekt „Obelix Wohnmobil App"). Für Phase 4 die Excel-Datei `Einkausliste_WoMo_v2_1.xlsx` im Chat anhängen.

```text
Wir arbeiten am Projekt OBELIX (native Android-App für das gemeinsame Familien-Wohnmobil). Das GitHub-Repository heißt Obelix (Hagi089/Obelix, Branch main). Bitte binde es ein und lies zuerst diese Dateien, bevor du etwas änderst:
1. docs/UEBERGABE.md (Stand, Entscheidungen, Fallstricke)
2. docs/PROJEKTPLAN.md (Plan, Datenmodell, Security-Konzept, Phasen)
3. docs/TESTFAELLE.md (aktuelle und offene Testfälle)
4. im Claude-Projekt das Dokument "Anforderungen" (verbindliche Anforderungen)

Stand: Phase 1 bis 3 sind umgesetzt (Bau, Unit-Tests und Regel-Tests in GitHub Actions grün). Phase 1 und 2 sind auf dem Gerät abgenommen. Für Phase 3 habe ich [ALLES / folgende Fälle: …] aus docs/TESTFAELLE.md (G3-01 bis G3-11) getestet: [Ergebnisse hier eintragen, bei Fehlern mit Meldung oder Screenshot].

Auftrag jetzt: [Fehler aus dem Gerätetest beheben, danach] PHASE 4 – Finanzen und Excel-Import gemäß Plan. Die Excel-Datei hänge ich an; die Analyse steht im Projekt-Dokument "Excel-Analyse". Erst analysieren und einen kurzen Plan für Phase 4 zeigen, Fragen nur, wenn sie wirklich meine Entscheidung brauchen.

Rahmenbedingungen wie bisher: Senior Softwareentwickler und Senior QA Engineer, erst analysieren, nur das Nötige ändern, auf Regressionen prüfen, kurze Zusammenfassung der geänderten Dateien; alles kostenlos (Firebase Spark, ohne Cloud Functions), keine erfundenen Daten, keine ungefragten Erweiterungen; gebaut und getestet wird in GitHub Actions (kein Android-SDK, kein npm in der Cloud-Sitzung); was du nicht testen kannst, kennzeichne als "von mir zu prüfen"; vor jedem Push git pull --rebase origin main; Plan, docs/TESTFAELLE.md und die Projektdokumente (claude/Projektplan, claude/Testfaelle, claude/Uebergabe) am Ende aktualisieren. Zu den offenen Punkten in docs/UEBERGABE.md (Abschnitt 5) gelten deine Vorschläge, sofern ich nichts anderes sage.
```
