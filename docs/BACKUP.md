# OBELIX – Backup

Stand: 01.10.2026 · Version 23 · gehört zu [`PROJEKTPLAN.md`](PROJEKTPLAN.md) (Entscheidung 50)

## Was das Backup ist

Einstellungen → **Backup erstellen** (nur ADMIN). Der Android-Dateidialog fragt nach dem Speicherort (z. B. privates Google Drive oder Downloads); die App braucht dafür keine zusätzliche Berechtigung. Gelesen wird **frisch vom Server** mit den Rechten des angemeldeten Benutzers; es entsteht kein Cloud-Dienst und es fallen keine Kosten an (Firebase Spark, nur Lesezugriffe).

Eine einzige ZIP-Datei, `obelix-backup-<Datum>.zip`:

| Eintrag | Inhalt |
|---|---|
| `data.json` | alle Dokumente der Sammlungen `users`, `categories`, `transactions`, `plannedExpenses`, `calendarEntries`, `repairs`, `campsites`, `documents` als `{"id": …, "data": {…}}`, nach ID sortiert; Zeitpunkte als ISO-8601 (UTC) |
| `files/<Datei-ID>/<Name>` | alle Belege, Stellplatzfotos und Dokumente, so wie sie in der App liegen (JPEG/PDF) |
| `manifest.json` | **zuletzt geschrieben**: Anzahl der Dokumente je Sammlung, gesicherte Dateien, nicht gesicherte Dateien, `complete` (true/false) |

**Ein Backup ohne `manifest.json` ist abgebrochen und unvollständig** (Speicher voll, App beendet, Netz weg). `"complete": false` bedeutet: Das Backup ist fertig geschrieben, aber einzelne Dateien fehlen; die App zeigt das in Rot an und nennt die Dateien. In beiden Fällen: Datei löschen und das Backup wiederholen.

## Was **nicht** enthalten ist (Absicht)

- **Zugangscode** (`config/access` und das alte Feld `accessCode` in Benutzerdokumenten): ein Geheimnis, jederzeit erneuerbar.
- **Anmeldedaten** (E-Mail-Adressen, Passwörter): liegen in Firebase Authentication, nicht in Firestore. Die Datei enthält nur Benutzerkennung, Anzeigename und Rolle. Ob und wie sich Authentication-Konten im Spark-Tarif sichern lassen, ist **nicht geprüft** (siehe Plan, Entscheidung 50).
- Die Datei enthält **Namen und Beträge**: sicher aufbewahren, nicht in einem öffentlichen Ordner und nicht ins Repository.

## Grenzen (ehrlich)

- **Kein Wiederherstellen in der App.** Das Backup sichert die Daten; zurückspielen müsste eine eigene Funktion (nicht beauftragt). Die Dateien sind lesbar (JSON, Original-JPEG/PDF), die Daten gehen also nicht verloren, aber ein Rückspielen wäre Handarbeit oder eine spätere Erweiterung. Ohne getesteten Rückweg ist ein Backup nur die halbe Sicherheit.
- **Kein Schnappschuss zu einem Zeitpunkt.** Die Sammlungen werden nacheinander gelesen; schreibt gleichzeitig jemand, kann ein Eintrag im Backup fehlen oder neuer sein als ein anderer. Besser außerhalb der Nutzung sichern.
- **Nur auf Knopfdruck.** Es gibt keinen automatischen Zeitplan (das bräuchte Cloud Functions oder einen Dienst mit Zugang zum Projekt). Empfehlung: nach größeren Eingaben und in festen Abständen von Hand.
- **Größe:** Alle Belege, Fotos und Dokumente werden einzeln aus Firestore gelesen. Bei vielen großen Dateien dauert das und verbraucht Lesezugriffe und mobile Daten; im WLAN sichern. Das Tageskontingent des Spark-Tarifs (50.000 Lesezugriffe, Stand meines Wissens, bitte in der Firebase-Konsole prüfen) wird von einem Backup dieser Datenmenge bei Weitem nicht erreicht.

## Wächter gegen Lücken

`BackupCollectionsTest` liest `firebase/firestore.rules` und schlägt fehl, sobald dort eine Sammlung steht, die weder im Backup (`BackupCollections.ALL`) noch bewusst ausgenommen ist (`config`, `files`/`chunks`). Wer eine Sammlung freigibt, muss sie also ins Backup aufnehmen.

## Code

`data/backup/` (`BackupCollections`, `BackupJson`, `BackupFiles`, `BackupArchive`, `BackupRepository`), `ui/settings/BackupViewModel`, `ui/screens/BackupSection`; Tests `BackupJsonTest`, `BackupFilesTest`, `BackupArchiveTest`, `BackupCollectionsTest`, `BackupViewModelTest`. Gerätetests: [`TESTFAELLE.md`](TESTFAELLE.md), Abschnitt 2.4m.
