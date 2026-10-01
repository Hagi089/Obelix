package de.hagi089.obelix.data.backup

import de.hagi089.obelix.data.campsites.CAMPSITES_COLLECTION
import de.hagi089.obelix.data.documents.DOCUMENTS_COLLECTION
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.finance.BOOKINGS_COLLECTION
import java.time.LocalDate

/** Dateien, die zu einem Backup gehören: gültige Verweise und Stellen, deren Verweis nicht lesbar war. */
class CollectedFiles(val refs: List<FileRef>, val unreadable: List<String>)

/** Welche Dateien ein Backup braucht und wie sie im ZIP heißen. */
object BackupFiles {

    private const val MAX_NAME_LENGTH = 100

    /**
     * Sammelt alle Dateiverweise der Dokumente: Beleg einer Buchung (`receipt`), Fotos eines Stellplatzes
     * (`photos`), Datei eines Dokuments (`file`). Jede Datei kommt einmal vor. Ein Verweis, der sich nicht lesen
     * lässt, wird nicht stillschweigend übergangen, sondern in [CollectedFiles.unreadable] gemeldet.
     */
    fun collect(documents: List<BackupDocument>): CollectedFiles {
        val refs = linkedMapOf<String, FileRef>()
        val unreadable = mutableListOf<String>()
        for (doc in documents) {
            val candidates: List<Any?> = when (doc.collection) {
                BOOKINGS_COLLECTION -> listOfNotNull(doc.fields["receipt"])
                CAMPSITES_COLLECTION -> (doc.fields["photos"] as? List<*>).orEmpty()
                DOCUMENTS_COLLECTION -> listOfNotNull(doc.fields["file"])
                else -> emptyList()
            }
            for (candidate in candidates) {
                val ref = FileRef.fromMap(candidate as? Map<*, *>)
                if (ref == null) unreadable += "Verweis in ${doc.collection}/${doc.id}" else refs.putIfAbsent(ref.fileId, ref)
            }
        }
        return CollectedFiles(refs.values.toList(), unreadable)
    }

    /** Pfad der Datei im ZIP: `files/<Datei-ID>/<Name>`; der Name wird von Pfadzeichen befreit. */
    fun entryName(ref: FileRef): String = "files/${ref.fileId}/${safeName(ref.name)}"

    /** Vorschlag für den Dateinamen des Backups, z. B. `obelix-backup-2026-10-01.zip`. */
    fun fileName(date: LocalDate): String = "obelix-backup-$date.zip"

    private fun safeName(name: String): String {
        val cleaned = name
            .map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' || it == ' ') it else '_' }
            .joinToString("")
            .trim()
            .trimStart('.') // kein „..“ und keine versteckte Datei
            .take(MAX_NAME_LENGTH)
        return cleaned.ifBlank { "datei" }
    }
}
