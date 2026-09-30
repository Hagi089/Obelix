package de.hagi089.obelix.data.calendar

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction

internal const val CALENDAR_COLLECTION = "calendarEntries"

/**
 * Kalender der Wohnmobil-Nutzung (Firestore-Sammlung `calendarEntries`). Fehler kommen als
 * [de.hagi089.obelix.core.error.AppException]. Berechtigungen und Formate setzen die Firestore-Regeln
 * durch (firebase/firestore.rules, validCalendar).
 */
interface CalendarRepository {
    /** Alle Einträge, nach Start sortiert. Es sind wenige; eine Abfrage je Öffnen, immer vom Server. */
    suspend fun loadAll(): Result<List<CalendarEntry>>

    /** null, wenn der Eintrag nicht (mehr) existiert. */
    suspend fun get(id: String): Result<CalendarEntry?>

    /**
     * Einträge, die den Zeitraum berühren, **frisch vom Server** (nie aus einem Zwischenspeicher). Schlägt die
     * Abfrage fehl (z. B. ohne Verbindung), kommt ein Fehler: Die Oberfläche speichert dann nicht, statt die
     * Prüfung stillschweigend zu überspringen. Beim Bearbeiten wird der Eintrag [excludeId] nicht mitgezählt.
     */
    suspend fun findOverlaps(startDate: String, endDate: String, excludeId: String?): Result<List<CalendarEntry>>

    suspend fun create(input: CalendarInput, uid: String): Result<Unit>

    suspend fun update(id: String, input: CalendarInput, uid: String): Result<Unit>

    suspend fun delete(id: String): Result<Unit>
}

class FirestoreCalendarRepository(private val db: FirebaseFirestore) : CalendarRepository {

    private val col get() = db.collection(CALENDAR_COLLECTION)

    override suspend fun loadAll(): Result<List<CalendarEntry>> = repositoryCall {
        col.get(Source.SERVER).await().documents.mapNotNull { it.toEntry() }.sortedForDisplay()
    }

    override suspend fun get(id: String): Result<CalendarEntry?> = repositoryCall {
        col.document(id).get(Source.SERVER).await().toEntry()
    }

    override suspend fun findOverlaps(startDate: String, endDate: String, excludeId: String?): Result<List<CalendarEntry>> =
        repositoryCall {
            // Firestore kann nicht zwei Bereichsfilter auf verschiedenen Feldern: Die Abfrage holt alle Einträge, die
            // spätestens am Ende des neuen Zeitraums beginnen, der Rest (Ende am oder nach dem Start) wird hier gefiltert.
            val candidates = col
                .whereLessThanOrEqualTo("startDate", endDate)
                .orderBy("startDate", Query.Direction.ASCENDING)
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { it.toEntry() }
            CalendarOverlap.find(candidates, startDate, endDate, excludeId)
        }

    override suspend fun create(input: CalendarInput, uid: String): Result<Unit> = repositoryCall {
        val ref = col.document()
        val data = buildMap<String, Any> {
            put("startDate", input.startDate)
            put("endDate", input.endDate)
            put("personUid", input.personUid)
            put("personName", input.personName)
            input.destination?.let { put("destination", it) }
            put("comment", input.comment)
            put("createdAt", FieldValue.serverTimestamp())
            put("createdBy", uid)
        }
        db.writeTransaction { tx -> tx.set(ref, data) }
    }

    override suspend fun update(id: String, input: CalendarInput, uid: String): Result<Unit> = repositoryCall {
        val data = buildMap<String, Any> {
            put("startDate", input.startDate)
            put("endDate", input.endDate)
            put("personUid", input.personUid)
            put("personName", input.personName)
            put("destination", input.destination ?: FieldValue.delete())
            put("comment", input.comment)
            put("updatedAt", FieldValue.serverTimestamp())
            put("updatedBy", uid)
        }
        db.writeTransaction { tx -> tx.update(col.document(id), data) }
    }

    override suspend fun delete(id: String): Result<Unit> = repositoryCall {
        db.writeTransaction { tx -> tx.delete(col.document(id)) }
    }

    private fun List<CalendarEntry>.sortedForDisplay(): List<CalendarEntry> =
        sortedWith(compareBy<CalendarEntry> { it.startDate }.thenBy { it.endDate }.thenBy { it.id })

    private fun DocumentSnapshot.toEntry(): CalendarEntry? {
        if (!exists()) return null
        return CalendarEntry(
            id = id,
            startDate = getString("startDate") ?: return null,
            endDate = getString("endDate") ?: return null,
            personUid = getString("personUid") ?: return null,
            personName = getString("personName") ?: return null,
            destination = getString("destination"),
            comment = getString("comment") ?: "",
            createdBy = getString("createdBy") ?: return null,
        )
    }
}
