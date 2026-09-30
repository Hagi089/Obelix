package de.hagi089.obelix.data.planned

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction
import de.hagi089.obelix.data.finance.BOOKINGS_COLLECTION
import de.hagi089.obelix.data.finance.PLANNED_COLLECTION
import de.hagi089.obelix.data.finance.bookingCreateData

/**
 * Geplante Ausgaben (Firestore-Sammlung `plannedExpenses`). Fehler kommen als
 * [de.hagi089.obelix.core.error.AppException]. Berechtigungen und Formate setzen die Firestore-Regeln
 * durch (firebase/firestore.rules, validPlanned).
 */
interface PlannedExpenseRepository {
    /** Alle Planungen (geplant und gekauft), neueste zuerst. Es sind wenige; eine Abfrage je Öffnen. */
    suspend fun loadAll(): Result<List<PlannedExpense>>

    /** null, wenn die Planung nicht (mehr) existiert. */
    suspend fun get(id: String): Result<PlannedExpense?>

    suspend fun create(input: PlannedInput, uid: String): Result<Unit>

    /** Nur für Planungen, die noch nicht gekauft sind (die Regeln lassen sonst nichts zu). */
    suspend fun update(id: String, input: PlannedInput, uid: String): Result<Unit>

    /**
     * „Gekauft": legt in **einer** Transaktion die echte Ausgabe mit dem tatsächlichen Betrag an und setzt die
     * Planung auf GEKAUFT. Ohne Serverbestätigung entsteht nichts. Ist die Planung inzwischen gelöscht oder
     * schon gekauft, wird nichts geschrieben.
     */
    suspend fun purchase(id: String, purchase: PurchaseInput, uid: String): Result<Unit>

    suspend fun delete(id: String): Result<Unit>
}

class FirestorePlannedExpenseRepository(private val db: FirebaseFirestore) : PlannedExpenseRepository {

    private val col get() = db.collection(PLANNED_COLLECTION)

    override suspend fun loadAll(): Result<List<PlannedExpense>> = repositoryCall {
        col.get(Source.SERVER).await().documents
            .mapNotNull { it.toPlanned() }
            .sortedWith(compareByDescending<PlannedExpense> { it.plannedDate }.thenBy { it.title }.thenBy { it.id })
    }

    override suspend fun get(id: String): Result<PlannedExpense?> = repositoryCall {
        col.document(id).get(Source.SERVER).await().toPlanned()
    }

    override suspend fun create(input: PlannedInput, uid: String): Result<Unit> = repositoryCall {
        val ref = col.document()
        val data = buildMap<String, Any> {
            put("title", input.title)
            put("estimatedAmountCents", input.estimatedAmountCents)
            put("plannedDate", input.plannedDate)
            put("status", PlannedStatus.PLANNED.name)
            input.priority?.let { put("priority", it.name) }
            input.link?.let { put("link", it) }
            put("comment", input.comment)
            put("createdAt", FieldValue.serverTimestamp())
            put("createdBy", uid)
        }
        db.writeTransaction { tx -> tx.set(ref, data) }
    }

    override suspend fun update(id: String, input: PlannedInput, uid: String): Result<Unit> = repositoryCall {
        val data = buildMap<String, Any> {
            put("title", input.title)
            put("estimatedAmountCents", input.estimatedAmountCents)
            put("plannedDate", input.plannedDate)
            put("priority", input.priority?.name ?: FieldValue.delete())
            put("link", input.link ?: FieldValue.delete())
            put("comment", input.comment)
            put("updatedAt", FieldValue.serverTimestamp())
            put("updatedBy", uid)
        }
        db.writeTransaction { tx -> tx.update(col.document(id), data) }
    }

    override suspend fun purchase(id: String, purchase: PurchaseInput, uid: String): Result<Unit> = repositoryCall {
        val planRef = col.document(id)
        val bookingRef = db.collection(BOOKINGS_COLLECTION).document()
        db.writeTransaction { tx ->
            // Lesen vor Schreiben: Der Stand wird in der Transaktion geprüft. Kauft ein zweites Gerät gleichzeitig,
            // wiederholt Firestore die Transaktion, sieht GEKAUFT und bricht ab: es gibt nie zwei Ausgaben.
            val plan = tx.get(planRef).toPlanned() ?: throw AppException(AppError.NOT_FOUND)
            if (plan.status != PlannedStatus.PLANNED) throw AppException(AppError.CONFLICT)
            tx.set(bookingRef, bookingCreateData(PurchasePlanner.toBooking(plan, purchase), uid))
            tx.update(
                planRef,
                mapOf(
                    "status" to PlannedStatus.PURCHASED.name,
                    "purchasedTransactionId" to bookingRef.id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid,
                ),
            )
        }
    }

    override suspend fun delete(id: String): Result<Unit> = repositoryCall {
        db.writeTransaction { tx -> tx.delete(col.document(id)) }
    }

    private fun DocumentSnapshot.toPlanned(): PlannedExpense? {
        if (!exists()) return null
        return PlannedExpense(
            id = id,
            title = getString("title") ?: return null,
            estimatedAmountCents = getLong("estimatedAmountCents") ?: return null,
            plannedDate = getString("plannedDate") ?: return null,
            status = PlannedStatus.from(getString("status")) ?: return null,
            priority = Priority.from(getString("priority")),
            link = getString("link"),
            comment = getString("comment") ?: "",
            purchasedTransactionId = getString("purchasedTransactionId"),
            createdBy = getString("createdBy") ?: return null,
        )
    }
}
