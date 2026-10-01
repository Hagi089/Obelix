package de.hagi089.obelix.data.documents

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentValidatorTest {

    @Test
    fun name_required_trimmedAndLimited() {
        assertEquals(R.string.error_document_name_required, DocumentValidator.name(""))
        assertEquals(R.string.error_document_name_required, DocumentValidator.name("   "))
        assertNull(DocumentValidator.name("Versicherung"))
        assertNull(DocumentValidator.name("x".repeat(100)))
        assertNull(DocumentValidator.name("  " + "x".repeat(100) + "  "))
        assertEquals(R.string.error_document_name_too_long, DocumentValidator.name("x".repeat(101)))
    }

    @Test
    fun category_isRequired() {
        assertEquals(R.string.error_document_category_required, DocumentValidator.category(null))
        DocumentCategory.entries.forEach { assertNull(DocumentValidator.category(it)) }
    }

    @Test
    fun file_isRequiredForNewDocuments() {
        assertEquals(R.string.error_document_file_required, DocumentValidator.file(false))
        assertNull(DocumentValidator.file(true))
    }

    @Test
    fun category_from_knowsAllSixAndRejectsUnknown() {
        assertEquals(6, DocumentCategory.entries.size)
        DocumentCategory.entries.forEach { assertEquals(it, DocumentCategory.from(it.name)) }
        assertNull(DocumentCategory.from("INVALID"))
        assertNull(DocumentCategory.from(null))
    }
}
