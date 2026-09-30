package de.hagi089.obelix.data.finance.importing

import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nur erfundene Testdaten – die echte Importdatei liegt nie im Repository. */
class ImportPlannerTest {

    private fun booking(row: Int, type: String, cents: Long, category: String, payer: String, settlement: String, description: String = "Test", comment: String = "") =
        """{"row":$row,"date":"2020-05-17","type":"$type","amountCents":$cents,"category":"$category","payer":"$payer","settlement":"$settlement","description":"$description","comment":"$comment"}"""

    // Einzahlung 1000,00 (P1), Einzahlung 500,00 (P2), Kauf 300,00 beglichen (P1), Auslage 99,00 offen (P2), gesponsert 20,00 (P2)
    private val bookings = listOf(
        booking(4, "INCOME", 100_000, "Einzahlung", "P1", "SETTLED"),
        booking(5, "INCOME", 50_000, "Einzahlung", "P2", "SETTLED"),
        booking(6, "EXPENSE", 30_000, "Inventar", "P1", "SETTLED", comment = "Verantwortung: P1"),
        booking(7, "EXPENSE", 9_900, "Inventar", "P2", "OPEN"),
        booking(8, "EXPENSE", 2_000, "Sonstiges", "P2", "SPONSORED"),
    )

    private fun json(
        version: Int = 1,
        bookingsJson: List<String> = bookings,
        categories: String = """["Einzahlung","Inventar","Sonstiges"]""",
        expected: String = """{"count":5,"incomeCents":150000,"expenseCents":41900,"balanceCents":120000,"openCents":{"P1":0,"P2":9900},"afterSettlementCents":110100}""",
    ) = """{"version":$version,"payers":["P1","P2"],"categories":$categories,"bookings":[${bookingsJson.joinToString(",")}],"skipped":[{"row":9,"description":"Gegenstand","reason":"Betrag 0"}],"expected":$expected}"""

    @Test
    fun parse_validFile() {
        val file = ImportPlanner.parse(json()).getOrThrow()
        assertEquals(5, file.bookings.size)
        assertEquals(1, file.skipped.size)
        assertEquals("xl-4", file.bookings[0].importRef)
    }

    @Test
    fun controlValues_matchExpected() {
        val file = ImportPlanner.parse(json()).getOrThrow()
        val s = ImportPlanner.controlValues(file)
        assertEquals(120_000L, s.balanceCents)
        assertEquals(mapOf("P2" to 9_900L), s.openByPayerCents)
        assertEquals(110_100L, s.afterSettlementCents)
        assertEquals(150_000L, s.incomeTotalCents)
        assertEquals(41_900L, s.expenseTotalCents)
        assertEquals(emptyList<String>(), ImportPlanner.mismatches(file))
    }

    @Test
    fun mismatches_reportEveryDeviation() {
        val wrong = """{"count":6,"incomeCents":1,"expenseCents":2,"balanceCents":3,"openCents":{"P2":4},"afterSettlementCents":5}"""
        val file = ImportPlanner.parse(json(expected = wrong)).getOrThrow()
        val list = ImportPlanner.mismatches(file)
        assertEquals(6, list.size)
        assertTrue(list.any { it.startsWith("Anzahl") })
        assertTrue(list.any { it.startsWith("Kontostand:") })
        assertTrue(list.any { it.startsWith("Offene Forderung P2") })
    }

    @Test
    fun toInputs_mapsPayerCategoryAndImportRef() {
        val file = ImportPlanner.parse(json()).getOrThrow()
        val inputs = ImportPlanner.toInputs(
            file,
            payerToUid = mapOf("P1" to "uid-1", "P2" to "uid-2"),
            categoryIds = mapOf("Einzahlung" to "c-e", "Inventar" to "c-i", "Sonstiges" to "c-s"),
        )
        assertEquals(5, inputs.size)
        assertEquals("uid-1", inputs[0].paidByUid)
        assertEquals(BookingType.INCOME, inputs[0].type)
        assertEquals("c-i", inputs[2].categoryId)
        assertEquals("Verantwortung: P1", inputs[2].comment)
        assertEquals(Settlement.OPEN, inputs[3].settlement)
        assertEquals(Settlement.SPONSORED, inputs[4].settlement)
        assertEquals("xl-7", inputs[3].importRef)
        assertEquals(9_900L, inputs[3].amountCents)
    }

    private fun assertRejected(text: String, contains: String) {
        val error = ImportPlanner.parse(text).exceptionOrNull()
        assertTrue("Erwartet abgelehnt: $contains", error is ImportFileException)
        assertTrue("Meldung „${(error as ImportFileException).reason}“ enthält nicht „$contains“", error.reason.contains(contains))
    }

    @Test
    fun parse_rejectsBrokenFiles() {
        assertRejected("kein json", "Format")
        assertRejected("""{"version":1}""", "Format")
        assertRejected(json(version = 2), "Dateiversion")
        assertRejected(json(bookingsJson = listOf(booking(4, "TRANSFER", 100, "Inventar", "P1", "SETTLED"))), "Art")
        assertRejected(json(bookingsJson = listOf(booking(4, "EXPENSE", 100, "Inventar", "P1", "PAID"))), "Status")
        assertRejected(json(bookingsJson = listOf(booking(4, "EXPENSE", 0, "Inventar", "P1", "SETTLED"))), "Betrag")
        assertRejected(json(bookingsJson = listOf(booking(4, "EXPENSE", -5, "Inventar", "P1", "SETTLED"))), "Betrag")
        assertRejected(json(bookingsJson = listOf(booking(4, "EXPENSE", 100, "Unbekannt", "P1", "SETTLED"))), "Kategorie")
        assertRejected(json(bookingsJson = listOf(booking(4, "EXPENSE", 100, "Inventar", "P9", "SETTLED"))), "Zahler")
        assertRejected(json(bookingsJson = listOf(booking(4, "EXPENSE", 100, "Inventar", "P1", "SETTLED", description = ""))), "Beschreibung")
        assertRejected(json(bookingsJson = listOf(booking(4, "INCOME", 100, "Einzahlung", "P1", "OPEN"))), "beglichen")
        assertRejected(json(bookingsJson = listOf(booking(4, "EXPENSE", 100, "Inventar", "P1", "SETTLED"), booking(4, "EXPENSE", 100, "Inventar", "P1", "SETTLED"))), "doppelt")
        assertRejected(json(categories = """["Einzahlung","einzahlung","Inventar","Sonstiges"]"""), "Duplikate")
    }

    @Test
    fun parse_rejectsInvalidDate() {
        val bad = booking(4, "EXPENSE", 100, "Inventar", "P1", "SETTLED").replace("2020-05-17", "2020-02-30")
        assertRejected(json(bookingsJson = listOf(bad)), "Datum")
    }
}
