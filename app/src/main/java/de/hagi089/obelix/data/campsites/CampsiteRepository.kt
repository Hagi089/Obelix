package de.hagi089.obelix.data.campsites

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.util.DEFAULT_TIMEOUT_MS
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.core.util.writeTransaction
import de.hagi089.obelix.data.files.FileLimits
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.FileStore
import de.hagi089.obelix.data.files.NewFile

const val CAMPSITES_COLLECTION = "campsites"

/**
 * Stellplätze (Firestore-Sammlung `campsites`). Fehler kommen als [de.hagi089.obelix.core.error.AppException].
 * Berechtigungen, Formate und die Grenze von drei Fotos setzen die Firestore-Regeln durch
 * (firebase/firestore.rules, validCampsite).
 */
interface CampsiteRepository {
    /** Alle Stellplätze, neueste zuerst. Es sind wenige; eine Abfrage je Öffnen des Bereichs. */
    suspend fun loadAll(): Result<List<Campsite>>

    /** null, wenn der Stellplatz nicht (mehr) existiert. */
    suspend fun get(id: String): Result<Campsite?>

    /** Legt den Stellplatz an; die neuen [photos] entstehen in **derselben Transaktion** (alles oder nichts). */
    suspend fun create(position: GeoPosition, date: String, input: CampsiteInput, photos: List<NewFile>, uid: String): Result<Unit>

    /**
     * Ändert die Angaben. [removedPhotoIds] sind gespeicherte Fotos, die wegfallen; [addedPhotos] entstehen im selben
     * Schritt. Die Dateien entfernter Fotos werden danach in einem eigenen Schritt gelöscht (scheitert nur dieser
     * Teil, bleibt höchstens eine unsichtbare Datei zurück).
     */
    suspend fun update(id: String, input: CampsiteInput, removedPhotoIds: Set<String>, addedPhotos: List<NewFile>, uid: String): Result<Unit>

    /**
     * Speichert eine von Hand korrigierte Position (Marker verschoben). Es ändern sich nur Breite, Länge und die
     * Audit-Felder; Angaben und Fotos bleiben unberührt. Das ist die **einzige** Stelle, die die Position eines
     * bestehenden Stellplatzes schreibt.
     */
    suspend fun updatePosition(id: String, position: GeoPosition, uid: String): Result<Unit>

    /** Löscht den Stellplatz und im selben Schritt alle seine Fotodateien. */
    suspend fun delete(id: String): Result<Unit>

    /** Lädt die Bilddaten eines Fotos (nur beim Anzeigen, Anforderung 37). */
    suspend fun loadPhoto(ref: FileRef): Result<ByteArray>
}

class FirestoreCampsiteRepository(private val db: FirebaseFirestore, private val files: FileStore) : CampsiteRepository {

    private val col get() = db.collection(CAMPSITES_COLLECTION)

    override suspend fun loadAll(): Result<List<Campsite>> = repositoryCall {
        CampsiteLogic.sorted(col.get(Source.SERVER).await().documents.mapNotNull { it.toCampsite() })
    }

    override suspend fun get(id: String): Result<Campsite?> = repositoryCall {
        col.document(id).get(Source.SERVER).await().toCampsite()
    }

    override suspend fun create(
        position: GeoPosition,
        date: String,
        input: CampsiteInput,
        photos: List<NewFile>,
        uid: String,
    ): Result<Unit> = repositoryCall(timeoutFor(photos.isNotEmpty())) {
        val ref = col.document()
        db.writeTransaction { tx ->
            // Fotos und Stellplatz entstehen in einem Schritt: entweder alles oder nichts.
            val photoRefs = photos.map { files.stageUpload(tx, it, uid).toMap() }
            val data = buildMap<String, Any> {
                put("latitude", position.latitude)
                put("longitude", position.longitude)
                put("date", date)
                put("comment", input.comment)
                input.name?.let { put("name", it) }
                input.address?.let { put("address", it) }
                input.note?.let { put("note", it) }
                input.rating?.let { put("rating", it) }
                put("photos", photoRefs)
                put("createdAt", FieldValue.serverTimestamp())
                put("createdBy", uid)
            }
            tx.set(ref, data)
        }
    }

    override suspend fun update(
        id: String,
        input: CampsiteInput,
        removedPhotoIds: Set<String>,
        addedPhotos: List<NewFile>,
        uid: String,
    ): Result<Unit> {
        var removedFiles: List<FileRef> = emptyList()
        val result = repositoryCall(timeoutFor(addedPhotos.isNotEmpty())) {
            val ref = col.document(id)
            db.writeTransaction { tx ->
                // Lesen vor Schreiben: Die Fotos werden in der Transaktion gelesen (nicht aus der Anzeige übernommen),
                // damit nie ein fremdes Foto gelöscht wird, falls ein anderer Benutzer den Stellplatz zwischenzeitlich geändert hat.
                val snapshot = tx.get(ref)
                if (!snapshot.exists()) throw AppException(AppError.NOT_FOUND)
                val current = snapshot.photoRefs()
                val kept = current.filter { it.fileId !in removedPhotoIds }
                removedFiles = current.filter { it.fileId in removedPhotoIds }
                val uploaded = addedPhotos.map { files.stageUpload(tx, it, uid) }
                // Hat ein anderer Benutzer inzwischen Fotos ergänzt, würde die Grenze überschritten.
                if (kept.size + uploaded.size > MAX_CAMPSITE_PHOTOS) throw AppException(AppError.CONFLICT)
                val data = buildMap<String, Any> {
                    put("comment", input.comment)
                    put("name", input.name ?: FieldValue.delete())
                    put("address", input.address ?: FieldValue.delete())
                    put("note", input.note ?: FieldValue.delete())
                    put("rating", input.rating ?: FieldValue.delete())
                    put("photos", (kept + uploaded).map { it.toMap() })
                    put("updatedAt", FieldValue.serverTimestamp())
                    put("updatedBy", uid)
                }
                tx.update(ref, data)
            }
        }
        // Die alten Dateien werden erst gelöscht, wenn der Stellplatz sicher gespeichert ist (eigener Schritt, Fehler nur im Log).
        if (result.isSuccess) removedFiles.forEach { files.deleteQuietly(it) }
        return result
    }

    override suspend fun updatePosition(id: String, position: GeoPosition, uid: String): Result<Unit> = repositoryCall {
        if (!CampsiteValidator.isValidPosition(position.latitude, position.longitude)) throw AppException(AppError.UNKNOWN)
        val ref = col.document(id)
        db.writeTransaction { tx ->
            // Lesen vor Schreiben: gibt es den Stellplatz nicht mehr (ein anderer Benutzer hat ihn gelöscht), nichts anlegen.
            if (!tx.get(ref).exists()) throw AppException(AppError.NOT_FOUND)
            tx.update(
                ref,
                mapOf(
                    "latitude" to position.latitude,
                    "longitude" to position.longitude,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid,
                ),
            )
        }
    }

    override suspend fun delete(id: String): Result<Unit> = repositoryCall(FileLimits.TIMEOUT_MS) {
        val ref = col.document(id)
        db.writeTransaction { tx ->
            val photos = tx.get(ref).photoRefs() // Lesen vor Schreiben
            tx.delete(ref)
            photos.forEach { files.stageDelete(tx, it) }
        }
    }

    override suspend fun loadPhoto(ref: FileRef): Result<ByteArray> = files.load(ref)

    private fun DocumentSnapshot.photoRefs(): List<FileRef> =
        (get("photos") as? List<*>)?.mapNotNull { FileRef.fromMap(it as? Map<*, *>) }.orEmpty()

    private fun DocumentSnapshot.toCampsite(): Campsite? {
        if (!exists()) return null
        return Campsite(
            id = id,
            latitude = getDouble("latitude") ?: return null,
            longitude = getDouble("longitude") ?: return null,
            date = getString("date") ?: return null,
            comment = getString("comment") ?: return null,
            name = getString("name"),
            address = getString("address"),
            note = getString("note"),
            rating = getLong("rating")?.toInt(),
            photos = photoRefs(),
            createdBy = getString("createdBy") ?: return null,
        )
    }

    /** Mit Foto-Upload braucht der Vorgang länger als ein normaler Schreibvorgang. */
    private fun timeoutFor(uploadsFile: Boolean): Long = if (uploadsFile) FileLimits.TIMEOUT_MS else DEFAULT_TIMEOUT_MS
}
