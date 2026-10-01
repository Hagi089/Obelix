package de.hagi089.obelix.data.backup

import de.hagi089.obelix.data.calendar.CALENDAR_COLLECTION
import de.hagi089.obelix.data.campsites.CAMPSITES_COLLECTION
import de.hagi089.obelix.data.documents.DOCUMENTS_COLLECTION
import de.hagi089.obelix.data.finance.BOOKINGS_COLLECTION
import de.hagi089.obelix.data.finance.PLANNED_COLLECTION
import de.hagi089.obelix.data.repairs.REPAIRS_COLLECTION

/**
 * Welche Firestore-Sammlungen ein Backup enthält (Namen wie in firebase/firestore.rules).
 *
 * Bewusst **nicht** enthalten:
 * - `config/access`: der Zugangscode ist ein Geheimnis und lässt sich jederzeit erneuern.
 * - `files`: Dateien stehen nicht in einer eigenen Liste (die Regeln verbieten das Auflisten), sondern werden über
 *   die Verweise in Buchungen, Stellplätzen und Dokumenten gesichert (siehe [BackupFiles]).
 *
 * Kommt in den Regeln eine neue Sammlung hinzu, schlägt `BackupCollectionsTest` fehl, bis sie hier steht.
 */
object BackupCollections {

    val ALL: List<String> = listOf(
        "users",
        "categories",
        BOOKINGS_COLLECTION,
        PLANNED_COLLECTION,
        CALENDAR_COLLECTION,
        REPAIRS_COLLECTION,
        CAMPSITES_COLLECTION,
        DOCUMENTS_COLLECTION,
    )

    /**
     * Felder, die nie in ein Backup gehören. Ältere Benutzerdokumente können noch den Zugangscode der Registrierung
     * tragen (die App entfernt ihn beim Laden des Profils, Entscheidung 49).
     */
    fun excludedFields(collection: String): Set<String> = if (collection == "users") setOf("accessCode") else emptySet()
}
