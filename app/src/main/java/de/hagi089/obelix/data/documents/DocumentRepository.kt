package de.hagi089.obelix.data.documents

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction
import de.hagi089.obelix.data.files.FileLimits
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.FileStore
import de.hagi089.obelix.data.files.NewFile

const val DOCUMENTS_COLLECTION = "documents"

/**
 * Dokumente (Firestore-Sammlung `documents`). Fehler kommen als [de.hagi089.obelix.core.error.AppException].
 * Berechtigungen, Formate und die Einmalverwendung der Datei setzen die Firestore-Regeln durch
 * (firebase/firestore.rules, validDocument).
 */
interface DocumentRepository {
    /** Alle Dokumente, neueste zuerst. Es sind wenige; eine Abfrage je Öffnen des Bereichs (keine Dateien, nur Angaben). */
    suspend fun loadAll(): Result<List<Document>>

    /** null, wenn das Dokument nicht (mehr) existiert. */
    suspend fun get(id: String): Result<Document?>

    /** Legt das Dokument an; die [file] (Metadaten und alle Stücke) entsteht in **derselben Transaktion** (alles oder nichts). */
    suspend fun create(input: DocumentInput, date: String, file: NewFile, uid: String): Result<Unit>

    /** Ändert Name und Kategorie. Die Datei bleibt unverändert (auch die Regeln lassen keine andere zu). */
    suspend fun update(id: String, input: DocumentInput, uid: String): Result<Unit>

    /** Löscht das Dokument und im selben Schritt seine Datei. Ein schon gelöschtes Dokument gilt als erledigt. */
    suspend fun delete(id: String): Result<Unit>
}

class FirestoreDocumentRepository(private val db: FirebaseFirestore, private val files: FileStore) : DocumentRepository {

    private val col get() = db.collection(DOCUMENTS_COLLECTION)

    override suspend fun loadAll(): Result<List<Document>> = repositoryCall {
        DocumentLogic.sorted(col.get(Source.SERVER).await().documents.mapNotNull { it.toDocument() })
    }

    override suspend fun get(id: String): Result<Document?> = repositoryCall {
        col.document(id).get(Source.SERVER).await().toDocument()
    }

    override suspend fun create(input: DocumentInput, date: String, file: NewFile, uid: String): Result<Unit> =
        repositoryCall(FileLimits.TIMEOUT_MS) {
            val ref = col.document()
            db.writeTransaction { tx ->
                // Datei und Dokument entstehen in einem Schritt: entweder alles oder nichts.
                val fileRef = files.stageUpload(tx, file, uid)
                tx.set(
                    ref,
                    mapOf(
                        "name" to input.name,
                        "category" to input.category.name,
                        "file" to fileRef.toMap(),
                        "date" to date,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "createdBy" to uid,
                    ),
                )
            }
        }

    override suspend fun update(id: String, input: DocumentInput, uid: String): Result<Unit> = repositoryCall {
        db.writeTransaction { tx ->
            tx.update(
                col.document(id),
                mapOf(
                    "name" to input.name,
                    "category" to input.category.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid,
                ),
            )
        }
    }

    override suspend fun delete(id: String): Result<Unit> = repositoryCall(FileLimits.TIMEOUT_MS) {
        val ref = col.document(id)
        db.writeTransaction { tx ->
            // Lesen vor Schreiben: Die Datei wird in der Transaktion gelesen (nicht aus der Anzeige übernommen).
            val snapshot = tx.get(ref)
            if (snapshot.exists()) {
                val file = snapshot.fileRef()
                tx.delete(ref)
                file?.let { files.stageDelete(tx, it) }
            }
        }
    }

    private fun DocumentSnapshot.fileRef(): FileRef? = FileRef.fromMap(get("file") as? Map<*, *>)

    private fun DocumentSnapshot.toDocument(): Document? {
        if (!exists()) return null
        return Document(
            id = id,
            name = getString("name") ?: return null,
            category = DocumentCategory.from(getString("category")) ?: return null,
            file = fileRef() ?: return null,
            date = getString("date") ?: return null,
            createdBy = getString("createdBy") ?: return null,
        )
    }
}
