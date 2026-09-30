package de.hagi089.obelix.data.files

import android.util.Log
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction

/**
 * Dateiablage in Firestore (Option F): `files/{fileId}` (Metadaten) und `files/{fileId}/chunks/{n}` (Stücke zu
 * 900 KiB). Jede Datei gehört zu genau einer Buchung und entsteht **in derselben Transaktion** wie deren Verweis
 * (`receipt`), damit weder eine Datei ohne Verweis noch ein Verweis ohne Datei zurückbleibt.
 * Der Zugriff wird von den Firestore-Regeln geschützt (firebase/firestore.rules).
 */
interface FileStore {

    /** Liest die Datei vom Server und setzt sie aus ihren Stücken zusammen. */
    suspend fun load(ref: FileRef): Result<ByteArray>

    /**
     * Schreibt die Datei (Metadaten und alle Stücke) in die Transaktion des Aufrufers und liefert den Verweis,
     * der im selben Schritt in der Buchung gespeichert werden muss. Nur innerhalb einer Transaktion aufrufen.
     */
    fun stageUpload(tx: Transaction, file: NewFile, uid: String): FileRef

    /** Löscht Metadaten und Stücke in der Transaktion des Aufrufers. */
    fun stageDelete(tx: Transaction, ref: FileRef)

    /**
     * Löscht eine nicht mehr gebrauchte Datei in einem eigenen Schritt (Aufräumen nach dem Ersetzen eines Belegs).
     * Fehler werden nur protokolliert: dann bleibt höchstens eine unsichtbare Datei zurück.
     */
    suspend fun deleteQuietly(ref: FileRef)
}

class FirestoreFileStore(private val db: FirebaseFirestore) : FileStore {

    private val files get() = db.collection(FILES_COLLECTION)

    override suspend fun load(ref: FileRef): Result<ByteArray> = repositoryCall(FileLimits.TIMEOUT_MS) {
        val fileDoc = files.document(ref.fileId)
        val meta = fileDoc.get(Source.SERVER).await()
        if (!meta.exists()) throw AppException(AppError.NOT_FOUND)
        val chunkCount = meta.getLong("chunkCount")?.toInt() ?: throw AppException(AppError.FILE_CORRUPT)
        val size = meta.getLong("sizeBytes") ?: throw AppException(AppError.FILE_CORRUPT)
        val parts = fileDoc.collection(CHUNKS_COLLECTION).get(Source.SERVER).await().documents
            // Die IDs sind 0 bis 9; als Text sortiert wäre „10" vor „2", deshalb nach Zahl sortieren.
            .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            .map { it.getBlob("data")?.toBytes() ?: throw AppException(AppError.FILE_CORRUPT) }
        FileChunker.join(parts, chunkCount, size) ?: throw AppException(AppError.FILE_CORRUPT)
    }

    override fun stageUpload(tx: Transaction, file: NewFile, uid: String): FileRef {
        val fileRef = files.document()
        FileChunker.split(file.bytes).forEachIndexed { index, part ->
            tx.set(fileRef.collection(CHUNKS_COLLECTION).document(index.toString()), mapOf("data" to Blob.fromBytes(part)))
        }
        tx.set(
            fileRef,
            mapOf(
                "name" to file.name,
                "contentType" to file.contentType,
                "sizeBytes" to file.sizeBytes,
                "chunkCount" to FileLimits.chunkCountFor(file.sizeBytes),
                "createdAt" to FieldValue.serverTimestamp(),
                "createdBy" to uid,
            ),
        )
        return FileRef(fileRef.id, file.name, file.contentType, file.sizeBytes)
    }

    override fun stageDelete(tx: Transaction, ref: FileRef) {
        val fileRef = files.document(ref.fileId)
        repeat(FileLimits.chunkCountFor(ref.sizeBytes)) { index ->
            tx.delete(fileRef.collection(CHUNKS_COLLECTION).document(index.toString()))
        }
        tx.delete(fileRef)
    }

    override suspend fun deleteQuietly(ref: FileRef) {
        repositoryCall(FileLimits.TIMEOUT_MS) { db.writeTransaction { tx -> stageDelete(tx, ref) } }
            .onFailure { Log.w(TAG, "Alte Datei ${ref.fileId} konnte nicht gelöscht werden", it) }
    }

    private companion object {
        const val TAG = "Obelix"
    }
}

internal const val FILES_COLLECTION = "files"
internal const val CHUNKS_COLLECTION = "chunks"
