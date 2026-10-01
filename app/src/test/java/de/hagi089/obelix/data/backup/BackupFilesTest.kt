package de.hagi089.obelix.data.backup

import de.hagi089.obelix.data.files.FileRef
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFilesTest {

    private fun ref(id: String, name: String = "Beleg.pdf", type: String = "application/pdf", size: Long = 10) =
        mapOf("fileId" to id, "name" to name, "contentType" to type, "sizeBytes" to size)

    @Test
    fun collect_findsReceiptsPhotosAndDocumentFiles() {
        val collected = BackupFiles.collect(
            listOf(
                BackupDocument("transactions", "t1", mapOf("receipt" to ref("r1"))),
                BackupDocument("transactions", "t2", mapOf("description" to "ohne Beleg")),
                BackupDocument("campsites", "c1", mapOf("photos" to listOf(ref("p1", "a.jpg", "image/jpeg"), ref("p2", "b.jpg", "image/jpeg")))),
                BackupDocument("campsites", "c2", mapOf("photos" to emptyList<Any>())),
                BackupDocument("documents", "d1", mapOf("file" to ref("f1", "Schein.pdf"))),
                BackupDocument("users", "u1", mapOf("receipt" to ref("ignored"))),
            ),
        )
        assertEquals(listOf("r1", "p1", "p2", "f1"), collected.refs.map { it.fileId })
        assertTrue(collected.unreadable.isEmpty())
    }

    @Test
    fun collect_listsEachFileOnce() {
        val collected = BackupFiles.collect(
            listOf(
                BackupDocument("transactions", "t1", mapOf("receipt" to ref("same"))),
                BackupDocument("documents", "d1", mapOf("file" to ref("same"))),
            ),
        )
        assertEquals(1, collected.refs.size)
    }

    @Test
    fun collect_reportsUnreadableReferencesInsteadOfSkippingThem() {
        val collected = BackupFiles.collect(
            listOf(
                BackupDocument("transactions", "t3", mapOf("receipt" to mapOf("fileId" to "x"))),
                BackupDocument("documents", "d9", mapOf("file" to "kaputt")),
                BackupDocument("campsites", "c7", mapOf("photos" to listOf(ref("ok", "a.jpg", "image/jpeg"), 5))),
            ),
        )
        assertEquals(listOf("ok"), collected.refs.map { it.fileId })
        assertEquals(
            listOf("Verweis in transactions/t3", "Verweis in documents/d9", "Verweis in campsites/c7"),
            collected.unreadable,
        )
    }

    @Test
    fun entryName_hasNoPathCharactersInTheName() {
        val name = BackupFiles.entryName(FileRef("abc", "../../etc/passwd", "application/pdf", 1)).removePrefix("files/abc/")
        assertFalse(name.contains('/'))
        assertFalse(name.startsWith("."))
    }

    @Test
    fun entryName_keepsReadableNamesAndUmlauts() {
        assertEquals("files/f1/Rechnung Müller-2026.pdf", BackupFiles.entryName(FileRef("f1", "Rechnung Müller-2026.pdf", "application/pdf", 1)))
    }

    @Test
    fun entryName_usesFallbackForEmptyNames() {
        assertEquals("files/f1/datei", BackupFiles.entryName(FileRef("f1", "...", "application/pdf", 1)))
        assertEquals("files/f1/datei", BackupFiles.entryName(FileRef("f1", "   ", "application/pdf", 1)))
    }

    @Test
    fun entryName_isShortened() {
        val name = BackupFiles.entryName(FileRef("f1", "a".repeat(500) + ".pdf", "application/pdf", 1)).removePrefix("files/f1/")
        assertEquals(100, name.length)
    }

    @Test
    fun fileName_containsTheDate() {
        assertEquals("obelix-backup-2026-10-01.zip", BackupFiles.fileName(LocalDate.of(2026, 10, 1)))
    }
}
