package de.hagi089.obelix.data.notifications

import de.hagi089.obelix.data.calendar.CALENDAR_COLLECTION
import de.hagi089.obelix.data.campsites.CAMPSITES_COLLECTION
import de.hagi089.obelix.data.documents.DOCUMENTS_COLLECTION
import de.hagi089.obelix.data.finance.BOOKINGS_COLLECTION
import de.hagi089.obelix.data.finance.PLANNED_COLLECTION
import de.hagi089.obelix.data.repairs.REPAIRS_COLLECTION

/**
 * Server-Zeitstempel, sekunden- und nanosekundengenau. Firestore speichert genauer als Millisekunden; mit einem auf
 * Millisekunden gekürzten Stand käme dieselbe Änderung bei der nächsten Prüfung noch einmal.
 */
data class Stamp(val seconds: Long, val nanos: Int) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int = compareValuesBy(this, other, Stamp::seconds, Stamp::nanos)

    fun encode(): String = "$seconds:$nanos"

    companion object {
        fun ofMillis(millis: Long): Stamp =
            Stamp(Math.floorDiv(millis, 1000L), (Math.floorMod(millis, 1000L) * 1_000_000L).toInt())

        /** null bei fehlendem oder unlesbarem Text (dann beginnt die Prüfung neu). */
        fun decode(text: String?): Stamp? {
            val parts = text?.split(':') ?: return null
            if (parts.size != 2) return null
            val seconds = parts[0].toLongOrNull() ?: return null
            val nanos = parts[1].toIntOrNull() ?: return null
            if (nanos !in 0..999_999_999) return null
            return Stamp(seconds, nanos)
        }
    }
}

/** Die fünf Bereiche, die benachrichtigen (Entscheidung 53). Reihenfolge = Reihenfolge der Navigation. */
enum class ChangeArea { CALENDAR, FINANCE, TASKS, CAMPSITES, DOCUMENTS }

/**
 * Geprüfte Sammlungen je Bereich. Bewusst nicht: `categories` (nur ADMIN in den Einstellungen), `users`, `files`,
 * `config`.
 */
val CHANGE_COLLECTIONS: Map<String, ChangeArea> = linkedMapOf(
    CALENDAR_COLLECTION to ChangeArea.CALENDAR,
    BOOKINGS_COLLECTION to ChangeArea.FINANCE,
    PLANNED_COLLECTION to ChangeArea.FINANCE,
    REPAIRS_COLLECTION to ChangeArea.TASKS,
    CAMPSITES_COLLECTION to ChangeArea.CAMPSITES,
    DOCUMENTS_COLLECTION to ChangeArea.DOCUMENTS,
)

/** Audit-Felder eines Dokuments, das seit dem letzten Stand angelegt oder geändert wurde. */
data class ChangeRecord(
    val collection: String,
    val id: String,
    val createdAt: Stamp?,
    val createdBy: String?,
    val updatedAt: Stamp?,
    val updatedBy: String?,
)

enum class ChangeAction { CREATED, UPDATED, CREATED_AND_UPDATED }

/** Eine Zeile der Benachrichtigung; name = null, wenn der Name nicht geladen werden konnte. */
data class PersonChange(val uid: String, val name: String?, val action: ChangeAction)

/** Wer in einem Bereich etwas angelegt (created) bzw. geändert (updated) hat. */
data class AreaChange(val area: ChangeArea, val created: Set<String>, val updated: Set<String>)

object ChangeLogic {

    /**
     * Fasst die Änderungen anderer Benutzer seit [since] je Bereich zusammen. Eigene Änderungen ([myUid]) zählen nie.
     * Angelegt zählt mit createdBy, geändert mit updatedBy (bei mehreren Änderungen nur die letzte, mehr steht nicht im
     * Dokument). Gelöschte Einträge sind nicht erkennbar.
     */
    fun summarize(records: List<ChangeRecord>, since: Stamp, myUid: String): List<AreaChange> {
        val created = mutableMapOf<ChangeArea, MutableSet<String>>()
        val updated = mutableMapOf<ChangeArea, MutableSet<String>>()
        records.forEach { record ->
            val area = CHANGE_COLLECTIONS[record.collection] ?: return@forEach
            val createdBy = record.createdBy
            if (record.createdAt != null && record.createdAt > since && createdBy != null && createdBy != myUid) {
                created.getOrPut(area) { mutableSetOf() } += createdBy
            }
            val updatedBy = record.updatedBy
            if (record.updatedAt != null && record.updatedAt > since && updatedBy != null && updatedBy != myUid) {
                // Ein Eintrag, den dieselbe Person seit dem letzten Stand angelegt hat, ist für die anderen „eingetragen“.
                val sameNew = createdBy == updatedBy && record.createdAt != null && record.createdAt > since
                if (!sameNew) updated.getOrPut(area) { mutableSetOf() } += updatedBy
            }
        }
        return ChangeArea.entries.mapNotNull { area ->
            val c = created[area].orEmpty()
            val u = updated[area].orEmpty()
            if (c.isEmpty() && u.isEmpty()) null else AreaChange(area, c, u)
        }
    }

    /** Neuer Stand = jüngster gesehener Server-Zeitstempel (auch eigener Änderungen), nie älter als [since]. */
    fun watermark(records: List<ChangeRecord>, since: Stamp): Stamp =
        records.flatMap { listOfNotNull(it.createdAt, it.updatedAt) }.fold(since) { max, s -> if (s > max) s else max }

    /** Zeilen der Benachrichtigung eines Bereichs, nach Namen sortiert (unbekannte Namen zuletzt). */
    fun personChanges(change: AreaChange, names: Map<String, String>): List<PersonChange> =
        (change.created + change.updated)
            .map { uid ->
                val action = when {
                    uid in change.created && uid in change.updated -> ChangeAction.CREATED_AND_UPDATED
                    uid in change.created -> ChangeAction.CREATED
                    else -> ChangeAction.UPDATED
                }
                PersonChange(uid, names[uid]?.takeIf { it.isNotBlank() }, action)
            }
            .sortedWith(compareBy<PersonChange>({ it.name == null }, { it.name?.lowercase() }, { it.uid }))
}
