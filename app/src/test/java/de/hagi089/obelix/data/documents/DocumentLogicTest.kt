package de.hagi089.obelix.data.documents

import de.hagi089.obelix.data.files.FileRef
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentLogicTest {

    private val file = FileRef("f", "a.pdf", "application/pdf", 10)

    private fun doc(id: String, name: String, date: String, category: DocumentCategory = DocumentCategory.OTHER) =
        Document(id, name, category, file, date, "anna")

    @Test
    fun sorted_newestFirst_thenNameThenId() {
        val list = listOf(
            doc("3", "b", "2026-09-01"),
            doc("1", "Zebra", "2026-10-01"),
            doc("2", "apfel", "2026-10-01"),
            doc("4", "b", "2026-09-01"),
        )
        assertEquals(listOf("2", "1", "3", "4"), DocumentLogic.sorted(list).map { it.id })
    }

    @Test
    fun filtered_nullShowsAll_otherwiseOnlyCategory() {
        val list = listOf(
            doc("1", "a", "2026-01-01", DocumentCategory.INSURANCE),
            doc("2", "b", "2026-01-01", DocumentCategory.MANUAL),
        )
        assertEquals(2, DocumentLogic.filtered(list, null).size)
        assertEquals(listOf("2"), DocumentLogic.filtered(list, DocumentCategory.MANUAL).map { it.id })
        assertEquals(emptyList<Document>(), DocumentLogic.filtered(list, DocumentCategory.VEHICLE))
    }

    @Test
    fun suggestedName_stripsExtensionOnly() {
        assertEquals("Versicherung 2026", DocumentLogic.suggestedName("Versicherung 2026.pdf"))
        assertEquals("scan.v2", DocumentLogic.suggestedName("scan.v2.jpg"))
        assertEquals("", DocumentLogic.suggestedName("  "))
        assertEquals(".pdf", DocumentLogic.suggestedName(".pdf"))
        assertEquals("Beleg", DocumentLogic.suggestedName(" Beleg "))
        assertEquals("a.langeEndungXYZ", DocumentLogic.suggestedName("a.langeEndungXYZ"))
    }

    @Test
    fun suggestedName_isLimitedTo100Characters() {
        assertEquals(100, DocumentLogic.suggestedName("x".repeat(150) + ".pdf").length)
    }
}
