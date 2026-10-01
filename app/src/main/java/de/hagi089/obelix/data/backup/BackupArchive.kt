package de.hagi089.obelix.data.backup

import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.files.FileRef
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class BackupStep { READ_DATA, SAVE_FILES }

/** Fortschritt: [done] von [total] Schritten des Abschnitts [step] sind erledigt. */
data class BackupProgress(val step: BackupStep, val done: Int, val total: Int)

/**
 * Ergebnis eines Backups. [failedFiles] nennt Dateien, die nicht gesichert werden konnten; dann ist das Backup
 * **unvollständig** und die Oberfläche sagt das deutlich.
 */
data class BackupSummary(
    val documentCounts: Map<String, Int>,
    val fileCount: Int,
    val failedFiles: List<String>,
) {
    val documentTotal: Int get() = documentCounts.values.sum()
    val isComplete: Boolean get() = failedFiles.isEmpty()
}

/**
 * Schreibt das Backup als ZIP. Kennt Firestore nicht: Dokumente und der Zugriff auf Dateien kommen von außen.
 *
 * Aufbau des ZIP:
 * 1. `data.json`: alle Dokumente je Sammlung (`{"id", "data"}`)
 * 2. `files/<Datei-ID>/<Name>`: die Belege, Fotos und Dokumente
 * 3. `manifest.json`: **zuletzt** geschrieben, mit Anzahlen und nicht gesicherten Dateien
 *
 * Fehlt `manifest.json`, wurde das Backup abgebrochen und ist nicht vollständig.
 */
object BackupArchive {
    const val DATA_ENTRY = "data.json"
    const val MANIFEST_ENTRY = "manifest.json"
    const val FORMAT = "obelix-backup"
    const val FORMAT_VERSION = 1

    private val json = Json { prettyPrint = true }

    /**
     * Schreibt das Backup nach [out] und schließt den Strom. Eine einzelne Datei, die sich nicht laden lässt, bricht
     * das Backup nicht ab, sondern steht in [BackupSummary.failedFiles]. Scheitert das Schreiben selbst (Speicher
     * voll, Speicherort weg), kommt eine [AppException] mit [AppError.STORAGE].
     */
    suspend fun write(
        out: OutputStream,
        createdAt: Instant,
        documents: List<BackupDocument>,
        loadFile: suspend (FileRef) -> Result<ByteArray>,
        onProgress: (BackupProgress) -> Unit,
    ): BackupSummary {
        val counts = BackupCollections.ALL.associateWith { name -> documents.count { it.collection == name } }
        val collected = BackupFiles.collect(documents)
        val failed = collected.unreadable.toMutableList()
        var saved = 0
        try {
            ZipOutputStream(out).use { zip ->
                zip.entry(DATA_ENTRY, dataJson(createdAt, documents).toByteArray(Charsets.UTF_8))
                collected.refs.forEachIndexed { index, ref ->
                    onProgress(BackupProgress(BackupStep.SAVE_FILES, index, collected.refs.size))
                    val bytes = loadFile(ref).getOrNull()
                    if (bytes == null) {
                        failed += "${ref.name} (${ref.fileId})"
                    } else {
                        zip.entry(BackupFiles.entryName(ref), bytes)
                        saved++
                    }
                }
                onProgress(BackupProgress(BackupStep.SAVE_FILES, collected.refs.size, collected.refs.size))
                zip.entry(MANIFEST_ENTRY, manifestJson(createdAt, counts, saved, failed).toByteArray(Charsets.UTF_8))
            }
        } catch (e: IOException) {
            throw AppException(AppError.STORAGE, e)
        }
        return BackupSummary(counts, saved, failed)
    }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun dataJson(createdAt: Instant, documents: List<BackupDocument>): String {
        val collections = BackupCollections.ALL.associateWith { name ->
            JsonArray(
                documents.filter { it.collection == name }.sortedBy { it.id }.map(BackupJson::document),
            )
        }
        val root: JsonObject = buildJsonObject {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", createdAt.toString())
            put("collections", JsonObject(collections))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun manifestJson(createdAt: Instant, counts: Map<String, Int>, saved: Int, failed: List<String>): String {
        val root: JsonObject = buildJsonObject {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", createdAt.toString())
            put("complete", failed.isEmpty())
            put("documents", JsonObject(counts.mapValues { JsonPrimitive(it.value) }))
            put("filesSaved", saved)
            put("filesFailed", JsonArray(failed.map { JsonPrimitive(it) }))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }
}
