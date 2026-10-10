# Übergabe: Push-Benachrichtigungen bei Änderungen (geplant als Version 26)

Stand: 10.10.2026 · Letzter Stand im Repository: Version 25 (Commit `8ea3ce9`, Lauf 38066975289, danach nur Docs). Gerätetest G16-01 bis G16-12 bestanden.

Diese Datei ist die Übergabe an den neuen Chat. Sie trennt klar: **A = aus dem Code geprüft**, **B = nicht geprüft (Annahme/Wissensstand, im neuen Chat zu recherchieren)**. Es wurde **noch nichts umgesetzt und keine Lösung beschlossen.**

---

## 1. Anforderung (Wunsch des Benutzers, 10.10.2026)

Wenn irgendein Benutzer in den Hauptbereichen **Kalender, Finanzen, Aufgaben oder Stellplätze** etwas **ändert oder neu hinzufügt**, bekommen **alle anderen Benutzer** eine **Push-Nachricht aufs Handy** mit der Information, wer etwas getan hat und in welchem Bereich. Beispiele des Benutzers (Namen hier bewusst als Platzhalter):

- „Benutzer A hat etwas unter Kalender geändert“
- „Benutzer B hat etwas unter Aufgaben eingetragen“

Der Benutzer möchte das **in einem neuen Chat Schritt für Schritt mit Claude durchgehen** (erst Klärung, dann Entscheidung, dann Umsetzung).

Randbedingungen des Projekts (unverändert, `docs/UEBERGABE.md`, Abschnitt 6): kostenlos (Firebase Spark, **keine Cloud Functions**, keine kostenpflichtigen Dienste ohne ausdrückliche Freigabe), Bau und Tests nur in GitHub Actions, Updates müssen ohne Deinstallieren möglich sein, Version bei jedem Deployment erhöhen (**nächste: 26**), keine echten Namen in den Docs.

---

## 2. Ausgangslage im Code (A: geprüft)

**Bereiche und Firestore-Sammlungen**

| Bereich (Navigation) | Sammlung(en) | Repository |
|---|---|---|
| Kalender | `calendarEntries` | `data/calendar/…` (Repository, Validator, Überschneidung) |
| Finanzen | `transactions` (Buchungen), `plannedExpenses` (geplante Ausgaben), `categories` (nur ADMIN in den Einstellungen) | `data/finance/…`, `data/planned/…` |
| Aufgaben | `repairs` (Auffälligkeiten) | `data/repairs/RepairRepository.kt` |
| Stellplätze | `campsites` | `data/campsites/CampsiteRepository.kt` |
| (nicht genannt) | `documents` (Dokumente), `files`, `users`, `config` | – |

- Alle genannten Sammlungen tragen die Audit-Felder `createdBy`/`createdAt` und beim Ändern `updatedBy`/`updatedAt`; die **Regeln erzwingen** `createdBy == request.auth.uid` (und `updatedBy` beim Ändern). Die Auslöser-Person steht also schon im Dokument.
- **Löschen** hinterlässt keine Spur im Dokument (jeder Benutzer darf löschen, hart, mit Bestätigung). Das Löschen steht nicht in der Anforderung („ändert bzw. neu hinzufügt“) – **mit dem Benutzer klären**.
- Benutzer: `users/{uid}` mit `displayName`, `role` (ADMIN/MEMBER), `createdAt`; alle Benutzer dürfen alle Benutzerdokumente lesen (`allow list: isUser()`). Der Anzeigename kann also für den Nachrichtentext verwendet werden.
- Alle Schreibzugriffe laufen über die Repository-Klassen (`create`, `update`, `delete`, teils in `writeTransaction`) – das wäre die Stelle für einen clientseitigen Auslöser.
- **Keine** Push-/Hintergrund-Bausteine vorhanden: In `app/build.gradle.kts` nur `firebase-auth` und `firebase-firestore` (Firebase BoM 34.19.0), **kein** `firebase-messaging`, **kein** WorkManager; im Manifest nur `INTERNET`, `ACCESS_NETWORK_STATE`, Standort; **keine** Benachrichtigungsberechtigung (`POST_NOTIFICATIONS`), kein Dienst, kein Benachrichtigungskanal.
- Firebase-Projekt `obelix-daf7c`, Tarif **Spark**, Firestore `europe-west3`. Aus der Cloud-Sitzung sind **direkte Aufrufe an das Firebase-Projekt blockiert** (`docs/UEBERGABE.md`, Abschnitt 4): Alles, was echtes Firebase braucht, kann nur auf dem Gerät oder in der Konsole geprüft werden. **Eine Push-Nachricht lässt sich in der Sitzung nicht end-to-end testen** (nur „von mir zu prüfen“, mit zwei Konten/Geräten).
- Backup (`BackupCollections.excludedFields`): Felder, die nie ins Backup gehören, werden dort je Sammlung festgelegt (bisher `users.accessCode`). Ein neues Benutzerfeld (z. B. ein Gerätetoken) müsste dort **bewusst entschieden** werden. Ein Wächtertest (`BackupCollectionsTest`) schlägt bei neuen Sammlungen in den Regeln fehl, bis sie im Backup stehen oder ausgenommen sind.
- Die Regeln für `users/{uid}` erlauben beim Ändern nur den eigenen Namen (`ownProfileUpdateOk`) bzw. die Rolle durch ADMIN (Abschnitt `users` in `firebase/firestore.rules`); **ein neues Feld im Benutzerdokument braucht eine Regeländerung** (Regeln neu veröffentlichen) und neue Regel-Tests. **Benutzerdokumente sind für alle Benutzer lesbar** – ein dort gespeichertes Gerätetoken wäre für alle sichtbar; eine eigene, nur für den Besitzer lesbare Sammlung wäre zu erwägen (zugleich neue Sammlung → Backup-Wächtertest).

---

## 3. Das zentrale Problem (B: Annahmen, zu recherchieren – nicht als Fakten behandeln)

Eine Push-Nachricht an **andere** Geräte muss von **irgendetwas außerhalb des Geräts des Auslösers** verschickt werden. Nach meinem Kenntnisstand gilt:

1. **FCM (Firebase Cloud Messaging) selbst kostet nichts**, aber das **Senden** an fremde Geräte braucht Server-Zugangsdaten (Dienstkonto bzw. OAuth-Token der FCM-HTTP-v1-API). Diese **dürfen nicht in der App stehen** (jeder mit der APK könnte sie auslesen und an alle Nachrichten schicken). *Zu prüfen: ob es für ein reines Familien-Projekt eine tragbare, sichere clientseitige Variante gibt – ich halte das für unwahrscheinlich.*
2. **Cloud Functions** (der übliche Weg: Firestore-Auslöser → FCM) brauchen nach meinem Wissen **den Blaze-Tarif** (Zahlungsmittel hinterlegt, auch wenn die Free-Quote meist reicht). Das widerspricht „nur kostenlos“ (Entscheidung 1, vgl. Entscheidung 50 zum Backup). *Zu prüfen in der aktuellen Firebase-Dokumentation und Preisseite.* (Eine Websuche in diesem Chat lieferte nur Titel, keine belastbaren Inhalte; deshalb nicht als Fakt übernommen.)
3. Ab **Android 13** braucht die App zur Laufzeit die Berechtigung **`POST_NOTIFICATIONS`** (Zustimmung des Benutzers), und es braucht einen **Benachrichtigungskanal**. *Zu prüfen für `targetSdk` 36.*

---

## 4. Lösungsoptionen (Hypothesen zum Prüfen, alle mit offenen Fragen)

| Option | Idee | Mögliche Vorteile | Mögliche Nachteile / zu prüfen |
|---|---|---|---|
| **A. Cloud Functions + FCM** | Firestore-Auslöser versendet FCM an alle anderen Geräte | Sofort, zuverlässig, Standardweg, kein Code im Gerät für das Senden | Vermutlich **Blaze-Tarif nötig** → Freigabe des Benutzers; Einrichtung (Firebase CLI, Deploy, Region) |
| **B. GitHub Actions als „Server“** | Geplanter Workflow liest Änderungen per Dienstkonto (Secret) und sendet FCM | Kostenlos (GitHub bereits genutzt) | Geplante Läufe sind **nicht sekundengenau** und können verzögert werden (Mindestabstand, Last); Dienstkonto-Schlüssel als Secret; ob das Dienstkonto im Spark-Tarif nutzbar ist, **prüfen**; Läufe verbrauchen Minuten |
| **C. Lokale Benachrichtigungen per Hintergrundabfrage** | Jede App fragt per WorkManager regelmäßig Firestore nach Änderungen anderer (`createdAt`/`updatedAt` > letzter Stand, `createdBy`/`updatedBy` ≠ ich) und zeigt eine **lokale** Benachrichtigung | Kein Server, kein FCM, kostenlos | Kein echter Push: Verzögerung typischerweise **mindestens ~15 Minuten** (Android-Mindestintervall für periodische Arbeit, Doze/Energiesparen), Lesezugriffe (Kontingent prüfen), Batterie; Löschen nicht erkennbar |
| **D. Snapshot-Listener nur bei laufender App** | Firestore-Echtzeitabfrage, solange die App offen ist | Einfach, sofort | Nur bei geöffneter App; keine Nachricht „aufs Handy“, wenn die App geschlossen ist |
| **E. Externer kostenloser Dienst** | z. B. ein anderer Anbieter mit Gratis-Kontingent als Sende-Server | Evtl. sofort | Neue Abhängigkeit/Konto, ob wirklich ohne Zahlungsmittel nutzbar, **prüfen**; Vertrauen/Datenschutz |

**Wichtig:** Die Reihenfolge der Tabelle ist keine Empfehlung. Erst Machbarkeit klären, dann dem Benutzer Optionen mit Vor- und Nachteilen vorlegen und **seine Entscheidung abwarten**, bevor Code geändert wird (so wie bei Entscheidung 50).

---

## 5. Offene Fragen an den Benutzer (vor der Umsetzung)

1. **Zuverlässigkeit/Verzögerung:** Muss die Nachricht sofort kommen (Sekunden) oder reicht eine Verzögerung von Minuten bis ~15+ Minuten? (entscheidet über Option A/B/E gegen C)
2. **Kosten:** Ist der **Blaze-Tarif** (Zahlungsmittel hinterlegt, Kosten bei der Nutzungsgröße voraussichtlich null, aber nicht garantiert) ausdrücklich ausgeschlossen, oder käme er mit Budgetgrenze infrage?
3. **Welche Aktionen lösen aus?** Neu anlegen und Ändern – und **Löschen**? Zählen in „Finanzen“ auch **geplante Ausgaben** und **Kategorien**? „Erledigt/Wieder öffnen“ in Aufgaben, „als gekauft markieren“ in geplanten Ausgaben und das Verschieben des Stellplatz-Markers sind technisch auch Änderungen. **Dokumente** sind nicht genannt – ausdrücklich ausschließen oder aufnehmen?
4. **Text:** Unterschied zwischen „neu eingetragen“ und „geändert“ (wie in den Beispielen) – oder immer gleich? Nur Bereich oder auch Titel des Eintrags (Datenschutz der Beträge/Texte auf dem Sperrbildschirm)? Sprache Deutsch.
5. **Bündelung:** Viele Änderungen kurz hintereinander (z. B. Excel-Import durch den Admin, mehrere Buchungen) → eine Sammelnachricht statt einer pro Eintrag? Der **einmalige Excel-Import** sollte vermutlich **keine** Nachrichten auslösen.
6. **Empfänger:** Alle anderen angemeldeten Benutzer inklusive ADMIN? Nie der Auslöser selbst.
7. **Einstellung:** Soll jeder Benutzer Benachrichtigungen in den Einstellungen ein-/ausschalten oder je Bereich wählen können (oder reicht die Systemeinstellung des Handys)?
8. **Antippen:** Soll die Nachricht beim Antippen den betroffenen Bereich öffnen?
9. **Berechtigung:** Wann soll die App um die Benachrichtigungs-Erlaubnis bitten (Android 13+)?

---

## 6. Auswirkungen, die in der Analyse zu prüfen sind (Checkliste)

- **Bausteine:** je nach Option `firebase-messaging` und/oder `androidx.work`; Manifest (`POST_NOTIFICATIONS`, Dienst), Benachrichtigungskanal, Hinweis zu `targetSdk` 36. Ggf. Ersatz/Ergänzung in `AppContainer` (Dienstverdrahtung).
- **Gerätekennung (Token):** Falls FCM: wo wird das Token je Benutzer/Gerät gespeichert (Benutzerdokument oder eigene Sammlung), Aufräumen beim Abmelden/Token-Wechsel, Mehrfachgeräte je Benutzer. **Regeländerung und Regel-Tests**; Tokens gehören **nicht ins Backup** (`BackupCollections.excludedFields`, ggf. Wächtertest) und nicht in die Docs.
- **Sicherheit:** Keine Server-Schlüssel im Repository oder in der App (Repository ist öffentlich); Secrets nur in GitHub-Secrets; Prinzip der geringsten Rechte.
- **Regressionen:** Alle Schreibwege der vier Bereiche (inkl. Transaktionen mit Belegen/Fotos, Kalender-Überschneidungsprüfung, Kauf einer geplanten Ausgabe, Marker-Verschiebung) dürfen **nicht** langsamer oder fehleranfälliger werden, wenn die Benachrichtigung fehlschlägt: **Ein Fehler beim Benachrichtigen darf das Speichern nie verhindern oder als Fehler melden.**
- **Offline:** Offline gespeicherte Änderungen (Firestore-Cache) erscheinen erst beim Synchronisieren – Verhalten festlegen.
- **Backup/Excel-Import:** Import und Wiederherstellung dürfen keine Nachrichtenflut auslösen.
- **Datenschutz:** Inhalt der Nachricht (Namen, Bereiche) und was auf dem Sperrbildschirm sichtbar ist.
- **Akku/Datenverbrauch:** besonders bei Option C.

---

## 7. Testplan (Vorschlag, Nummern wie bisher fortlaufend: G17-xx, R-14…)

- **Automatisch (GitHub Actions):** reine Logik (Nachrichtentext je Bereich/Aktion, Empfängerauswahl ohne Auslöser, Bündelung/Entprellung, Ausschluss Import), ViewModel-/Repository-Tests mit Fakes (Benachrichtigungsfehler verhindert das Speichern nicht), Regel-Tests für jedes neue Feld/jede neue Sammlung (Format, Rechte, nur eigenes Token ändern, Fremde dürfen nicht lesen/ändern), Backup-Wächtertest.
- **Von mir nicht prüfbar (am Gerät, zwei Konten/Geräte, vom Benutzer):** Zustellung, Verzögerung, Verhalten bei geschlossener App und im Energiesparmodus, Berechtigungsdialog (Android 13+), Sperrbildschirm, Antippen, Kanal-Einstellungen, Mehrfachgeräte, Abmelden/Anmelden, Offline.
- **Regression:** Gerätefälle für Kalender (inkl. Überschneidungsdialog), Finanzen (inkl. Beleg, geplante Ausgabe kaufen), Aufgaben (inkl. „Geplant bis zum“), Stellplätze (inkl. Foto, Marker) – Speichern funktioniert wie in Version 25, auch **ohne** Netz/ohne Berechtigung.

---

## 8. Vorgehen im neuen Chat (Vorschlag)

1. Repository einbinden, Docs lesen, kurz bestätigen, was verstanden wurde.
2. **Machbarkeit recherchieren** (aktuelle Firebase-Dokumentation zu Spark/Blaze, Cloud Functions, FCM-Senden, Android-Hintergrundregeln; Quellen nennen, Unsicherheiten kennzeichnen) – Abschnitt 3 und 4 dieser Datei überprüfen.
3. **Optionen mit Vor- und Nachteilen** und Empfehlung vorlegen; die offenen Fragen aus Abschnitt 5 klären; **Entscheidung 53** im Plan festhalten (nächste freie Nummer; Entscheidung 52 ist Version 25).
4. Erst danach: Umsetzung in kleinen Schritten (Analyse → Änderung → Regressionsprüfung → kurze Zusammenfassung der geänderten Dateien und Funktionen), Version 26, Regeln ggf. veröffentlichen lassen.
5. Plan, `docs/TESTFAELLE.md` (neuer Abschnitt 2.4p, Gerätefälle G17-xx), `docs/UEBERGABE.md` und die Projektdokumente (`claude/Projektplan`, `claude/Testfaelle`, `claude/Uebergabe`) aktualisieren.

Der **kopierbare Prompt** für den neuen Chat steht in `docs/UEBERGABE.md`, Abschnitt 7.
