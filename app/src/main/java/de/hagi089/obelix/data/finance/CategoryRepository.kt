package de.hagi089.obelix.data.finance

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction

/**
 * Finanzkategorien (Sammlung `categories`). Anlegen darf jeder Benutzer, umbenennen und (de)aktivieren
 * nur ein ADMIN (Firestore-Regeln). Kategorien werden nie gelöscht.
 */
interface CategoryRepository {
    /** Alle Kategorien (auch deaktivierte), nach Name sortiert. */
    suspend fun loadAll(): Result<List<Category>>

    suspend fun add(name: String, uid: String): Result<Unit>

    /** Nur ADMIN (Regeln). */
    suspend fun update(id: String, name: String, active: Boolean, uid: String): Result<Unit>

    /**
     * Legt fehlende Kategorien an (Import) und liefert für jeden Namen die ID.
     * Vorhandene Kategorien werden ohne Beachtung der Groß-/Kleinschreibung wiederverwendet.
     */
    suspend fun ensure(names: List<String>, uid: String): Result<Map<String, String>>
}

class FirestoreCategoryRepository(private val db: FirebaseFirestore) : CategoryRepository {

    private val col get() = db.collection(COLLECTION)

    override suspend fun loadAll(): Result<List<Category>> = repositoryCall { fetch() }

    override suspend fun add(name: String, uid: String): Result<Unit> = repositoryCall {
        val ref = col.document()
        db.writeTransaction { tx -> tx.set(ref, createData(name.trim(), uid)) }
    }

    override suspend fun update(id: String, name: String, active: Boolean, uid: String): Result<Unit> = repositoryCall {
        val data = mapOf<String, Any>(
            "name" to name.trim(),
            "active" to active,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to uid,
        )
        db.writeTransaction { tx -> tx.update(col.document(id), data) }
    }

    override suspend fun ensure(names: List<String>, uid: String): Result<Map<String, String>> = repositoryCall {
        val existing = fetch().associate { it.name.lowercase() to it.id }
        val result = linkedMapOf<String, String>()
        val toCreate = linkedMapOf<String, String>() // Name → neue ID
        names.forEach { name ->
            val known = existing[name.trim().lowercase()]
            if (known != null) {
                result[name] = known
            } else {
                val id = col.document().id
                toCreate[name] = id
                result[name] = id
            }
        }
        if (toCreate.isNotEmpty()) {
            db.writeTransaction { tx ->
                toCreate.forEach { (name, id) -> tx.set(col.document(id), createData(name.trim(), uid)) }
            }
        }
        result
    }

    private suspend fun fetch(): List<Category> =
        col.get(Source.SERVER).await().documents.mapNotNull { it.toCategory() }.sortedBy { it.name.lowercase() }

    private fun createData(name: String, uid: String): Map<String, Any> = mapOf(
        "name" to name,
        "active" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "createdBy" to uid,
    )

    private fun DocumentSnapshot.toCategory(): Category? {
        if (!exists()) return null
        return Category(id = id, name = getString("name") ?: return null, active = getBoolean("active") ?: true)
    }

    private companion object {
        const val COLLECTION = "categories"
    }
}
