package de.hagi089.obelix.data.backup

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.data.files.FileStore
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sichert den gesamten Datenbestand (nur ADMIN in der Oberfläche; gelesen wird mit den Rechten des angemeldeten
 * Benutzers, die Firestore-Regeln erlauben jedem Benutzer das Lesen, siehe firebase/firestore.rules).
 */
interface BackupRepository {

    /**
     * Liest alle Sammlungen **frisch vom Server** und schreibt ZIP-Datei nach [out] (wird geschlossen).
     * Scheitert das Lesen einer Sammlung, scheitert das ganze Backup (nie ein stillschweigend unvollständiges).
     * Einzelne nicht ladbare Dateien dagegen stehen in [BackupSummary.failedFiles].
     */
    suspend fun write(out: OutputStream, createdAt: Instant, onProgress: (BackupProgress) -> Unit): Result<BackupSummary>
}

class FirestoreBackupRepository(
    private val db: FirebaseFirestore,
    private val fileStore: FileStore,
) : BackupRepository {

    override suspend fun write(
        out: OutputStream,
        createdAt: Instant,
        onProgress: (BackupProgress) -> Unit,
    ): Result<BackupSummary> = repositoryCall(TIMEOUT_MS) {
        val names = BackupCollections.ALL
        val documents = names.flatMapIndexed { index, name ->
            onProgress(BackupProgress(BackupStep.READ_DATA, index, names.size))
            db.collection(name).get(Source.SERVER).await().documents
                .map { BackupDocument(name, it.id, it.data.orEmpty()) }
        }
        onProgress(BackupProgress(BackupStep.READ_DATA, names.size, names.size))
        // Komprimieren und Schreiben gehören nicht auf den Hauptfaden; die Firestore-Zugriffe darin warten nur.
        withContext(Dispatchers.IO) {
            BackupArchive.write(out, createdAt, documents, fileStore::load, onProgress)
        }
    }

    private companion object {
        /** Ein Backup mit vielen Belegen dauert länger als ein normaler Vorgang; jede Datei hat zusätzlich ihr eigenes Zeitlimit. */
        const val TIMEOUT_MS = 15 * 60 * 1000L
    }
}
