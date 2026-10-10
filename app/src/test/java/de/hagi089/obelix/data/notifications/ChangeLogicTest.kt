package de.hagi089.obelix.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A-55: Zusammenfassung der Änderungen anderer Benutzer je Bereich, neuer Stand, Zeilen (Entscheidung 53). */
class ChangeLogicTest {

    private val since = Stamp(1_000, 500)
    private val before = Stamp(999, 0)
    private val after = Stamp(1_000, 501)
    private val later = Stamp(2_000, 0)

    private fun rec(
        collection: String,
        createdAt: Stamp?,
        createdBy: String?,
        updatedAt: Stamp? = null,
        updatedBy: String? = null,
        id: String = "d",
    ) = ChangeRecord(collection, id, createdAt, createdBy, updatedAt, updatedBy)

    @Test
    fun `neu angelegt von anderen zaehlt als eingetragen, eigene Aenderungen nie`() {
        val result = ChangeLogic.summarize(
            listOf(
                rec("calendarEntries", after, "anna", id = "1"),
                rec("calendarEntries", after, "ich", id = "2"),
                rec("repairs", before, "anna", after, "ich", id = "3"),
            ),
            since,
            myUid = "ich",
        )
        assertEquals(listOf(AreaChange(ChangeArea.CALENDAR, setOf("anna"), emptySet())), result)
    }

    @Test
    fun `geaendert zaehlt mit updatedBy, nicht mit dem Ersteller`() {
        val result = ChangeLogic.summarize(listOf(rec("repairs", before, "ich", after, "bert")), since, "ich")
        assertEquals(listOf(AreaChange(ChangeArea.TASKS, emptySet(), setOf("bert"))), result)
    }

    @Test
    fun `selbst angelegt und gleich wieder geaendert ist nur eingetragen, fremd geaendert zusaetzlich geaendert`() {
        val same = ChangeLogic.summarize(listOf(rec("campsites", after, "anna", later, "anna")), since, "ich")
        assertEquals(listOf(AreaChange(ChangeArea.CAMPSITES, setOf("anna"), emptySet())), same)
        val other = ChangeLogic.summarize(listOf(rec("campsites", after, "anna", later, "bert")), since, "ich")
        assertEquals(listOf(AreaChange(ChangeArea.CAMPSITES, setOf("anna"), setOf("bert"))), other)
    }

    @Test
    fun `Buchungen und geplante Ausgaben sind Finanzen, Dokumente zaehlen, Reihenfolge wie die Navigation`() {
        val result = ChangeLogic.summarize(
            listOf(
                rec("campsites", after, "a", id = "1"),
                rec("plannedExpenses", after, "b", id = "2"),
                rec("transactions", after, "c", id = "3"),
                rec("repairs", after, "d", id = "4"),
                rec("calendarEntries", after, "e", id = "5"),
                rec("documents", before, "a", after, "f", id = "6"),
            ),
            since,
            "ich",
        )
        assertEquals(
            listOf(ChangeArea.CALENDAR, ChangeArea.FINANCE, ChangeArea.TASKS, ChangeArea.CAMPSITES, ChangeArea.DOCUMENTS),
            result.map { it.area },
        )
        assertEquals(setOf("b", "c"), result[1].created)
        assertEquals(AreaChange(ChangeArea.DOCUMENTS, emptySet(), setOf("f")), result[4])
    }

    @Test
    fun `nicht gepruefte Sammlungen und Zeitstempel bis einschliesslich Stand zaehlen nicht`() {
        val result = ChangeLogic.summarize(
            listOf(
                rec("categories", after, "anna", id = "1"),
                rec("users", after, "anna", id = "2"),
                rec("calendarEntries", since, "anna", since, "bert", id = "3"),
                rec("calendarEntries", null, "anna", null, "bert", id = "4"),
                rec("calendarEntries", after, null, id = "5"),
            ),
            since,
            "ich",
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `Nanosekunden zaehlen beim Vergleich`() {
        assertTrue(Stamp(1_000, 501) > since)
        assertTrue(Stamp(1_000, 500) <= since)
        assertTrue(Stamp(1_001, 0) > Stamp(1_000, 999_999_999))
    }

    @Test
    fun `neuer Stand ist der juengste gesehene Zeitstempel, auch eigener, nie aelter als der alte`() {
        val records = listOf(
            rec("repairs", after, "ich", id = "1"),
            rec("repairs", before, "anna", later, "anna", id = "2"),
        )
        assertEquals(later, ChangeLogic.watermark(records, since))
        assertEquals(since, ChangeLogic.watermark(emptyList(), since))
        assertEquals(since, ChangeLogic.watermark(listOf(rec("repairs", before, "a")), since))
    }

    @Test
    fun `Zeilen nach Namen sortiert, Aktion je Person, unbekannter Name zuletzt`() {
        val change = AreaChange(ChangeArea.CALENDAR, created = setOf("u1", "u2", "u9"), updated = setOf("u2", "u3"))
        val lines = ChangeLogic.personChanges(change, mapOf("u1" to "Zora", "u2" to "anton", "u3" to "Bea", "u9" to " "))
        assertEquals(
            listOf(
                PersonChange("u2", "anton", ChangeAction.CREATED_AND_UPDATED),
                PersonChange("u3", "Bea", ChangeAction.UPDATED),
                PersonChange("u1", "Zora", ChangeAction.CREATED),
                PersonChange("u9", null, ChangeAction.CREATED),
            ),
            lines,
        )
    }

    @Test
    fun `Stand als Text hin und zurueck, ungueltiger Text ergibt null`() {
        val stamp = Stamp(1_760_000_000, 123_456_789)
        assertEquals(stamp, Stamp.decode(stamp.encode()))
        assertNull(Stamp.decode(null))
        assertNull(Stamp.decode(""))
        assertNull(Stamp.decode("12"))
        assertNull(Stamp.decode("a:b"))
        assertNull(Stamp.decode("1:1000000000"))
        assertEquals(Stamp(1, 234_000_000), Stamp.ofMillis(1_234))
        assertEquals(Stamp(-1, 999_000_000), Stamp.ofMillis(-1))
    }
}
