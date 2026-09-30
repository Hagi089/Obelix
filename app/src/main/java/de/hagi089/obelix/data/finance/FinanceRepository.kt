package de.hagi089.obelix.data.finance

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction

/**
 * Einnahmen und Ausgaben (Firestore-Sammlung `transactions`). Alle Operationen liefern bei Fehlern eine
 * [de.hagi089.obelix.core.error.AppException]. Die Berechtigungen und Formate setzen die Firestore-Regeln
 * durch (firebase/firestore.rules, validBooking).
 */
interface FinanceRepository {
    /** Alle Buchungen, neueste zuerst. Es sind wenige Hundert; eine Abfrage je Öffnen des Bereichs. */
    suspend fun loadAll(): Result<List<Booking>>

    /** null, wenn die Buchung nicht (mehr) existiert. */
    suspend fun get(id: String): Result<Booking?>

    suspend fun create(input: BookingInput, uid: String): Result<Unit>

    /** [previous] ist der bisherige Status; beim Wechsel von OPEN zu SETTLED werden Zeitpunkt und Person vermerkt. */
    suspend fun update(id: String, input: BookingInput, previous: Settlement, uid: String): Result<Unit>

    /** Aktion „Erstattet": OPEN → SETTLED. */
    suspend fun markSettled(id: String, uid: String): Result<Unit>

    suspend fun delete(id: String): Result<Unit>

    /**
     * Einmaliger Import. Jede Buchung braucht [BookingInput.importRef]; sie wird unter dieser ID angelegt.
     * Schreibt in Blöcken; [onProgress] meldet die Zahl der bestätigten Buchungen.
     * Bereits vorhandene IDs werden von den Regeln abgelehnt (keine Überschreibung).
     */
    suspend fun importBookings(inputs: List<BookingInput>, uid: String, onProgress: (Int) -> Unit): Result<Int>
}

class FirestoreFinanceRepository(private val db: FirebaseFirestore) : FinanceRepository {

    private val col get() = db.collection(COLLECTION)

    override suspend fun loadAll(): Result<List<Booking>> = repositoryCall {
        col.get(Source.SERVER).await().documents
            .mapNotNull { it.toBooking() }
            .sortedWith(compareByDescending<Booking> { it.date }.thenByDescending { it.importRef ?: "" }.thenBy { it.id })
    }

    override suspend fun get(id: String): Result<Booking?> = repositoryCall {
        col.document(id).get(Source.SERVER).await().toBooking()
    }

    override suspend fun create(input: BookingInput, uid: String): Result<Unit> = repositoryCall {
        val ref = col.document()
        db.writeTransaction { tx -> tx.set(ref, createData(input, uid)) }
    }

    override suspend fun update(id: String, input: BookingInput, previous: Settlement, uid: String): Result<Unit> =
        repositoryCall {
            val data = buildMap<String, Any> {
                put("type", input.type.name)
                put("date", input.date)
                put("amountCents", input.amountCents)
                put("categoryId", input.categoryId)
                put("paidByUid", input.paidByUid ?: FieldValue.delete())
                put("settlement", input.settlement.name)
                put("description", input.description)
                put("comment", input.comment)
                put("updatedAt", FieldValue.serverTimestamp())
                put("updatedBy", uid)
                when {
                    input.settlement == Settlement.SETTLED && previous == Settlement.OPEN -> {
                        put("settledAt", FieldValue.serverTimestamp())
                        put("settledBy", uid)
                    }
                    input.settlement != Settlement.SETTLED -> {
                        put("settledAt", FieldValue.delete())
                        put("settledBy", FieldValue.delete())
                    }
                    else -> Unit
                }
            }
            db.writeTransaction { tx -> tx.update(col.document(id), data) }
        }

    override suspend fun markSettled(id: String, uid: String): Result<Unit> = repositoryCall {
        val data = mapOf<String, Any>(
            "settlement" to Settlement.SETTLED.name,
            "settledAt" to FieldValue.serverTimestamp(),
            "settledBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to uid,
        )
        db.writeTransaction { tx -> tx.update(col.document(id), data) }
    }

    override suspend fun delete(id: String): Result<Unit> = repositoryCall {
        db.writeTransaction { tx -> tx.delete(col.document(id)) }
    }

    override suspend fun importBookings(inputs: List<BookingInput>, uid: String, onProgress: (Int) -> Unit): Result<Int> =
        repositoryCall(IMPORT_TIMEOUT_MS) {
            var done = 0
            inputs.chunked(IMPORT_CHUNK_SIZE).forEach { chunk ->
                db.writeTransaction { tx ->
                    chunk.forEach { input ->
                        val ref = col.document(requireNotNull(input.importRef) { "importRef fehlt" })
                        tx.set(ref, createData(input, uid))
                    }
                }
                done += chunk.size
                onProgress(done)
            }
            done
        }

    private fun createData(input: BookingInput, uid: String): Map<String, Any> = buildMap {
        put("type", input.type.name)
        put("date", input.date)
        put("amountCents", input.amountCents)
        put("categoryId", input.categoryId)
        input.paidByUid?.let { put("paidByUid", it) }
        put("settlement", input.settlement.name)
        put("description", input.description)
        put("comment", input.comment)
        input.importRef?.let { put("importRef", it) }
        put("createdAt", FieldValue.serverTimestamp())
        put("createdBy", uid)
    }

    private fun DocumentSnapshot.toBooking(): Booking? {
        if (!exists()) return null
        return Booking(
            id = id,
            type = BookingType.from(getString("type")) ?: return null,
            date = getString("date") ?: return null,
            amountCents = getLong("amountCents") ?: return null,
            categoryId = getString("categoryId") ?: return null,
            paidByUid = getString("paidByUid"),
            settlement = Settlement.from(getString("settlement")) ?: return null,
            description = getString("description") ?: return null,
            comment = getString("comment") ?: "",
            importRef = getString("importRef"),
        )
    }

    private companion object {
        const val COLLECTION = "transactions"
        /** Firestore begrenzt die Regelabfragen je Transaktion auf 20 (je Buchung eine): 10 sind sicher (Regel-Test R-06j). */
        const val IMPORT_CHUNK_SIZE = 10
        const val IMPORT_TIMEOUT_MS = 300_000L
    }
}
