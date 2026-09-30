package de.hagi089.obelix.data.repairs

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction
import de.hagi089.obelix.data.planned.Priority

const val REPAIRS_COLLECTION = "repairs"

/**
 * Auffälligkeiten (Firestore-Sammlung `repairs`). Fehler kommen als [de.hagi089.obelix.core.error.AppException].
 * Berechtigungen und Formate setzen die Firestore-Regeln durch (firebase/firestore.rules, validRepair).
 */
interface RepairRepository {
    /** Alle Auffälligkeiten (offen und erledigt), sortiert nach [RepairLogic.sorted]. Es sind wenige; eine Abfrage je Öffnen. */
    suspend fun loadAll(): Result<List<Repair>>

    /** null, wenn die Auffälligkeit nicht (mehr) existiert. */
    suspend fun get(id: String): Result<Repair?>

    /** Neue Auffälligkeiten sind immer OFFEN. */
    suspend fun create(input: RepairInput, uid: String): Result<Unit>

    /** Ändert die Angaben und setzt den Status (Erledigen und Wiederöffnen laufen hierüber). */
    suspend fun update(id: String, input: RepairInput, status: RepairStatus, uid: String): Result<Unit>

    suspend fun delete(id: String): Result<Unit>
}

class FirestoreRepairRepository(private val db: FirebaseFirestore) : RepairRepository {

    private val col get() = db.collection(REPAIRS_COLLECTION)

    override suspend fun loadAll(): Result<List<Repair>> = repositoryCall {
        RepairLogic.sorted(col.get(Source.SERVER).await().documents.mapNotNull { it.toRepair() })
    }

    override suspend fun get(id: String): Result<Repair?> = repositoryCall {
        col.document(id).get(Source.SERVER).await().toRepair()
    }

    override suspend fun create(input: RepairInput, uid: String): Result<Unit> = repositoryCall {
        val ref = col.document()
        val data = buildMap<String, Any> {
            put("title", input.title)
            put("description", input.description)
            put("date", input.date)
            put("status", RepairStatus.OPEN.name)
            input.priority?.let { put("priority", it.name) }
            put("comment", input.comment)
            put("createdAt", FieldValue.serverTimestamp())
            put("createdBy", uid)
        }
        db.writeTransaction { tx -> tx.set(ref, data) }
    }

    override suspend fun update(id: String, input: RepairInput, status: RepairStatus, uid: String): Result<Unit> = repositoryCall {
        val data = buildMap<String, Any> {
            put("title", input.title)
            put("description", input.description)
            put("date", input.date)
            put("status", status.name)
            put("priority", input.priority?.name ?: FieldValue.delete())
            put("comment", input.comment)
            put("updatedAt", FieldValue.serverTimestamp())
            put("updatedBy", uid)
        }
        db.writeTransaction { tx -> tx.update(col.document(id), data) }
    }

    override suspend fun delete(id: String): Result<Unit> = repositoryCall {
        db.writeTransaction { tx -> tx.delete(col.document(id)) }
    }

    private fun DocumentSnapshot.toRepair(): Repair? {
        if (!exists()) return null
        return Repair(
            id = id,
            title = getString("title") ?: return null,
            description = getString("description") ?: return null,
            date = getString("date") ?: return null,
            status = RepairStatus.from(getString("status")) ?: return null,
            priority = Priority.from(getString("priority")),
            comment = getString("comment") ?: "",
            createdBy = getString("createdBy") ?: return null,
        )
    }
}
