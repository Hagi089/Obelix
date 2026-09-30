package de.hagi089.obelix.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRefTest {

    private val ref = FileRef("abc123", "beleg.jpg", FileLimits.MIME_JPEG, 412_345L)

    @Test
    fun toMap_thenFromMap_roundtrips() {
        assertEquals(ref, FileRef.fromMap(ref.toMap()))
    }

    @Test
    fun toMap_hasExactlyTheFieldsTheRulesExpect() {
        // firestore.rules validReceiptRef: fileId, name, contentType, sizeBytes
        assertEquals(setOf("fileId", "name", "contentType", "sizeBytes"), ref.toMap().keys)
    }

    @Test
    fun fromMap_acceptsAnyNumberTypeForTheSize() {
        val map = ref.toMap().toMutableMap().also { it["sizeBytes"] = 1000 } // Int statt Long
        assertEquals(1000L, FileRef.fromMap(map)?.sizeBytes)
    }

    @Test
    fun fromMap_returnsNullForMissingOrIncompleteData() {
        assertNull(FileRef.fromMap(null))
        assertNull(FileRef.fromMap(emptyMap<String, Any>()))
        listOf("fileId", "name", "contentType", "sizeBytes").forEach { missing ->
            assertNull("ohne $missing", FileRef.fromMap(ref.toMap() - missing))
        }
        assertNull(FileRef.fromMap(ref.toMap() + ("sizeBytes" to "viel")))
    }

    @Test
    fun isPdf_dependsOnTheContentType() {
        assertTrue(ref.copy(contentType = FileLimits.MIME_PDF).isPdf)
        assertFalse(ref.isPdf)
    }

    @Test
    fun newFile_reportsItsSize() {
        assertEquals(3L, NewFile("a.pdf", FileLimits.MIME_PDF, byteArrayOf(1, 2, 3)).sizeBytes)
    }
}
