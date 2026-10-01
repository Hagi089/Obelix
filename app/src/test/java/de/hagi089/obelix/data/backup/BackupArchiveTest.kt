package de.hagi089.obelix.data.backup

import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.files.FileRef
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupArchiveTest {

    private val createdAt = Instant.parse("2026-10-01T12:00:00Z")

    private fun refMap(id: String, name: String, type: String = "application/pdf", size: Long = 3) =
        mapOf("fileId" to id, "name" to name, "contentType" to type, "sizeBytes" to size)

    private val documents = listOf(
        BackupDocument("transactions", "t2", mapOf("description" to "Müll", "receipt" to refMap("f1", "Beleg.pdf"))),
        BackupDocument("transactions", "t1", mapOf("description" to "Gas")),
        BackupDocument("campsites", "c1", mapOf("photos" to listOf(refMap("f2", "a.jpg", "image/jpeg")))),
        BackupDocument("users", "u1", mapOf("displayName" to "Anna", "role" to "ADMIN", "accessCode" to "GEHEIMERCODE1234")),
    )

    private val contents = mapOf("f1" to byteArrayOf(1, 2, 3), "f2" to byteArrayOf(9, 8, 7))

    private fun load(ref: FileRef): Result<ByteArray> =
        contents[ref.fileId]?.let { Result.success(it) } ?: Result.failure(AppException(AppError.NOT_FOUND))

    private class Run(val entries: LinkedHashMap<String, ByteArray>, val summary: BackupSummary, val progress: List<BackupProgress>)

    private fun backup(
        docs: List<BackupDocument> = documents,
        loader: suspend (FileRef) -> Result<ByteArray> = { load(it) },
    ): Run {
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<BackupProgress>()
        val summary = runBlocking { BackupArchive.write(out, createdAt, docs, loader) { progress += it } }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return Run(entries, summary, progress)
    }

    private fun Run.json(name: String): JsonObject = Json.parseToJsonElement(entries.getValue(name).toString(Charsets.UTF_8)).jsonObject

    @Test
    fun entries_dataFirst_filesInBetween_manifestLast() {
        val run = backup()
        assertEquals(listOf("data.json", "files/f1/Beleg.pdf", "files/f2/a.jpg", "manifest.json"), run.entries.keys.toList())
    }

    @Test
    fun files_areStoredByteForByte() {
        val run = backup()
        assertArrayEquals(contents.getValue("f1"), run.entries.getValue("files/f1/Beleg.pdf"))
        assertArrayEquals(contents.getValue("f2"), run.entries.getValue("files/f2/a.jpg"))
    }

    @Test
    fun dataJson_containsEveryCollection_evenEmptyOnes() {
        val collections = backup().json("data.json").getValue("collections").jsonObject
        assertEquals(BackupCollections.ALL, collections.keys.toList())
        assertEquals(JsonArray(emptyList()), collections["repairs"])
    }

    @Test
    fun dataJson_documentsAreSortedByIdAndKeepUmlauts() {
        val data = backup().json("data.json")
        val transactions = data.getValue("collections").jsonObject.getValue("transactions") as JsonArray
        assertEquals(listOf("t1", "t2"), transactions.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        assertEquals("Müll", transactions[1].jsonObject.getValue("data").jsonObject.getValue("description").jsonPrimitive.content)
        assertEquals("obelix-backup", data.getValue("format").jsonPrimitive.content)
        assertEquals(1, data.getValue("formatVersion").jsonPrimitive.int)
        assertEquals("2026-10-01T12:00:00Z", data.getValue("createdAt").jsonPrimitive.content)
    }

    @Test
    fun accessCode_isNowhereInTheArchive() {
        val run = backup()
        run.entries.forEach { (name, bytes) ->
            assertFalse("Zugangscode in $name", bytes.toString(Charsets.ISO_8859_1).contains("GEHEIMERCODE1234"))
        }
    }

    @Test
    fun manifest_describesACompleteBackup() {
        val run = backup()
        val manifest = run.json("manifest.json")
        assertTrue(manifest.getValue("complete").jsonPrimitive.boolean)
        assertEquals(2, manifest.getValue("filesSaved").jsonPrimitive.int)
        assertEquals(JsonArray(emptyList()), manifest["filesFailed"])
        assertEquals(2, manifest.getValue("documents").jsonObject.getValue("transactions").jsonPrimitive.int)
        assertEquals(0, manifest.getValue("documents").jsonObject.getValue("repairs").jsonPrimitive.int)
        assertEquals(4, run.summary.documentTotal)
        assertEquals(2, run.summary.fileCount)
        assertTrue(run.summary.isComplete)
    }

    @Test
    fun aFileThatCannotBeLoaded_isReported_andTheRestIsStillSaved() {
        val run = backup(loader = { ref -> if (ref.fileId == "f1") Result.failure(AppException(AppError.FILE_CORRUPT)) else load(ref) })
        assertFalse(run.entries.containsKey("files/f1/Beleg.pdf"))
        assertTrue(run.entries.containsKey("files/f2/a.jpg"))
        assertEquals(listOf("Beleg.pdf (f1)"), run.summary.failedFiles)
        assertEquals(1, run.summary.fileCount)
        assertFalse(run.summary.isComplete)
        val manifest = run.json("manifest.json")
        assertFalse(manifest.getValue("complete").jsonPrimitive.boolean)
        assertEquals(JsonArray(listOf(JsonPrimitive("Beleg.pdf (f1)"))), manifest["filesFailed"])
    }

    @Test
    fun anUnreadableReference_makesTheBackupIncomplete() {
        val run = backup(docs = documents + BackupDocument("documents", "d1", mapOf("file" to "kaputt")))
        assertEquals(listOf("Verweis in documents/d1"), run.summary.failedFiles)
        assertFalse(run.json("manifest.json").getValue("complete").jsonPrimitive.boolean)
    }

    @Test
    fun progress_endsWithAllFilesDone() {
        val progress = backup().progress
        assertEquals(BackupProgress(BackupStep.SAVE_FILES, 0, 2), progress.first())
        assertEquals(BackupProgress(BackupStep.SAVE_FILES, 2, 2), progress.last())
    }

    @Test
    fun noFiles_stillProducesDataAndManifest() {
        val run = backup(docs = listOf(BackupDocument("repairs", "r1", mapOf("title" to "Dichtung"))))
        assertEquals(listOf("data.json", "manifest.json"), run.entries.keys.toList())
        assertTrue(run.summary.isComplete)
    }

    @Test
    fun aWriteFailure_isReportedAsStorageError_notAsNetworkError() {
        val broken = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("Speicher voll")
            }
        }
        try {
            runBlocking { BackupArchive.write(broken, createdAt, documents, { load(it) }) { } }
            fail("Erwartet: AppException")
        } catch (e: AppException) {
            assertEquals(AppError.STORAGE, e.error)
        }
    }

    @Test
    fun theStreamIsClosed() {
        var closed = false
        val out = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        runBlocking { BackupArchive.write(out, createdAt, documents, { load(it) }) { } }
        assertTrue(closed)
    }
}
