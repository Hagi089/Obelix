package de.hagi089.obelix.data.finance

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.DEFAULT_TIMEOUT_MS
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction
import de.hagi089.obelix.data.files.FileLimits
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.FileStore
import de.hagi089.obelix.data.files.NewFile

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

    /** Legt die Buchung an. Ein [receipt] (Beleg) wird in **derselben Transaktion** gespeichert (nur bei Ausgaben). */
    suspend fun create(input: BookingInput, uid: String, receipt: NewFile? = null): Result<Unit>

    /**
     * [previous] ist der bisherige Status; beim Wechsel von OPEN zu SETTLED werden Zeitpunkt und Person vermerkt.
     * [receipt] bestimmt, was mit dem Beleg geschieht: behalten, entfernen (Datei wird im selben Schritt gelöscht)
     * oder ersetzen (neue Datei im selben Schritt, die alte danach in einem eigenen Schritt).
     */
    suspend fun update(
        id: String,
        input: BookingInput,
        previous: Settlement,
        uid: String,
        receipt: ReceiptChange = ReceiptChange.Keep,
    ): Result<Unit>

    /** Aktion „Erstattet": OPEN → SETTLED. */
    suspend fun markSettled(id: String, uid: String): Result<Unit>

    /**
     * Löscht die Buchung. Entstand sie aus dem Kauf einer geplanten Ausgabe ([plannedExpenseId]), wird die
     * Planung im selben Schritt wieder auf GEPLANT gesetzt (sonst wäre der Betrag weder geplant noch ausgegeben).
     * Ein vorhandener Beleg wird im selben Schritt mitgelöscht.
     */
    suspend fun delete(id: String, plannedExpenseId: String?, uid: String): Result<Unit>

    /**
     * Einmaliger Import. Jede Buchung braucht [BookingInput.importRef]; sie wird unter dieser ID angelegt.
     * Schreibt in Blöcken; [onProgress] meldet die Zahl der bestätigten Buchungen.
     * Bereits vorhandene IDs werden von den Regeln abgelehnt (keine Überschreibung).
     */
    suspend fun importBookings(inputs: List<BookingInput>, uid: String, onProgress: (Int) -> Unit): Result<Int>
}

class FirestoreFinanceRepository(private val db: FirebaseFirestore, private val files: FileStore) : FinanceRepository {

    private val col get() = db.collection(COLLECTION)

    override suspend fun loadAll(): Result<List<Booking>> = repositoryCall {
        col.get(Source.SERVER).await().documents
            .mapNotNull { it.toBooking() }
            .sortedWith(compareByDescending<Booking> { it.date }.thenByDescending { it.importRef ?: "" }.thenBy { it.id })
    }

    override suspend fun get(id: String): Result<Booking?> = repositoryCall {
        col.document(id).get(Source.SERVER).await().toBooking()
    }

    override suspend fun create(input: BookingInput, uid: String, receipt: NewFile?): Result<Unit> =
        repositoryCall(timeoutFor(receipt != null)) {
            val ref = col.document()
            db.writeTransaction { tx ->
                val data = createData(input, uid).toMutableMap()
                // Datei und Buchung entstehen in einem Schritt: entweder beides oder nichts.
                if (receipt != null) data["receipt"] = files.stageUpload(tx, receipt, uid).toMap()
                tx.set(ref, data)
            }
        }

    override suspend fun update(
        id: String,
        input: BookingInput,
        previous: Settlement,
        uid: String,
        receipt: ReceiptChange,
    ): Result<Unit> {
        var replacedReceipt: FileRef? = null
        val result = repositoryCall(timeoutFor(receipt is ReceiptChange.Replace)) {
            val ref = col.document(id)
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
            db.writeTransaction { tx ->
                // Lesen vor Schreiben: Der aktuelle Beleg wird in der Transaktion gelesen (nicht aus der Anzeige übernommen),
                // damit nie eine fremde Datei gelöscht wird, falls ein anderer Benutzer ihn zwischenzeitlich geändert hat.
                val currentReceipt = if (receipt is ReceiptChange.Keep) null else FileRef.fromMap(tx.get(ref).get("receipt") as? Map<*, *>)
                val fields = data.toMutableMap()
                replacedReceipt = null
                when (receipt) {
                    ReceiptChange.Keep -> Unit
                    ReceiptChange.Remove -> {
                        fields["receipt"] = FieldValue.delete()
                        currentReceipt?.let { files.stageDelete(tx, it) }
                    }
                    is ReceiptChange.Replace -> {
                        fields["receipt"] = files.stageUpload(tx, receipt.file, uid).toMap()
                        replacedReceipt = currentReceipt
                    }
                }
                tx.update(ref, fields)
            }
        }
        // Die alte Datei wird erst gelöscht, wenn der neue Beleg sicher gespeichert ist (eigener Schritt, Fehler nur im Log).
        if (result.isSuccess) replacedReceipt?.let { files.deleteQuietly(it) }
        return result
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

    override suspend fun delete(id: String, plannedExpenseId: String?, uid: String): Result<Unit> = repositoryCall {
        val ref = col.document(id)
        val planRef = plannedExpenseId?.let { db.collection(PLANNED_COLLECTION).document(it) }
        db.writeTransaction { tx ->
            // Lesen vor Schreiben (Transaktionsregel). Fehlt die Planung inzwischen, wird nur die Buchung gelöscht.
            val receipt = FileRef.fromMap(tx.get(ref).get("receipt") as? Map<*, *>)
            val plan = planRef?.let { tx.get(it) }
            tx.delete(ref)
            receipt?.let { files.stageDelete(tx, it) }
            if (planRef != null && plan != null && plan.exists() &&
                plan.getString("status") == "PURCHASED" && plan.getString("purchasedTransactionId") == id
            ) {
                tx.update(
                    planRef,
                    mapOf(
                        "status" to "PLANNED",
                        "purchasedTransactionId" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "updatedBy" to uid,
                    ),
                )
            }
        }
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

    private fun createData(input: BookingInput, uid: String): Map<String, Any> = bookingCreateData(input, uid)

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
            plannedExpenseId = getString("plannedExpenseId"),
            receipt = FileRef.fromMap(get("receipt") as? Map<*, *>),
        )
    }

    /** Mit Datei-Upload braucht der Vorgang länger als ein normaler Schreibvorgang. */
    private fun timeoutFor(uploadsFile: Boolean): Long = if (uploadsFile) FileLimits.TIMEOUT_MS else DEFAULT_TIMEOUT_MS

    private companion object {
        const val COLLECTION = BOOKINGS_COLLECTION
        /** Firestore begrenzt die Regelabfragen je Transaktion auf 20 (je Buchung eine): 10 sind sicher (Regel-Test R-06j). */
        const val IMPORT_CHUNK_SIZE = 10
        const val IMPORT_TIMEOUT_MS = 300_000L
    }
}

/** Firestore-Sammlungen (auch vom Repository der geplanten Ausgaben genutzt, damit die Namen nur einmal stehen). */
internal const val BOOKINGS_COLLECTION = "transactions"
internal const val PLANNED_COLLECTION = "plannedExpenses"

/** Felder einer neuen Buchung. Muss zu firebase/firestore.rules (validBooking) passen. */
internal fun bookingCreateData(input: BookingInput, uid: String): Map<String, Any> = buildMap {
    put("type", input.type.name)
    put("date", input.date)
    put("amountCents", input.amountCents)
    put("categoryId", input.categoryId)
    input.paidByUid?.let { put("paidByUid", it) }
    put("settlement", input.settlement.name)
    put("description", input.description)
    put("comment", input.comment)
    input.importRef?.let { put("importRef", it) }
    input.plannedExpenseId?.let { put("plannedExpenseId", it) }
    put("createdAt", FieldValue.serverTimestamp())
    put("createdBy", uid)
}
