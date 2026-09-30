package de.hagi089.obelix.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarOverlapTest {

    private fun entry(id: String, start: String, end: String, name: String = "Tobias") = CalendarEntry(
        id = id, startDate = start, endDate = end, personUid = "u-$name", personName = name,
        destination = null, comment = "", createdBy = "u-$name",
    )

    // Bestehender Eintrag in allen Fällen: 10.10. bis 18.10.2026
    private fun existing() = listOf(entry("a", "2026-10-10", "2026-10-18"))

    private fun found(start: String, end: String, excludeId: String? = null) =
        CalendarOverlap.find(existing(), start, end, excludeId).map { it.id }

    @Test
    fun identicalPeriod_overlaps() = assertEquals(listOf("a"), found("2026-10-10", "2026-10-18"))

    @Test
    fun sameSingleDay_overlaps() {
        assertEquals(listOf("a"), found("2026-10-14", "2026-10-14"))
        assertEquals(listOf("a"), found("2026-10-10", "2026-10-10"))
        assertEquals(listOf("a"), found("2026-10-18", "2026-10-18"))
    }

    @Test
    fun sharedBoundaryDay_overlaps() {
        // Ende = Start des anderen: der Tag wird von beiden belegt
        assertEquals(listOf("a"), found("2026-10-18", "2026-10-20"))
        assertEquals(listOf("a"), found("2026-10-05", "2026-10-10"))
    }

    @Test
    fun directlyConsecutiveDays_doNotOverlap() {
        assertEquals(emptyList<String>(), found("2026-10-19", "2026-10-25"))
        assertEquals(emptyList<String>(), found("2026-10-01", "2026-10-09"))
    }

    @Test
    fun enclosingAndEnclosedPeriods_overlap() {
        assertEquals(listOf("a"), found("2026-10-01", "2026-10-31")) // umschließt
        assertEquals(listOf("a"), found("2026-10-12", "2026-10-15")) // liegt darin
    }

    @Test
    fun partialOverlapOnEitherSide_overlaps() {
        assertEquals(listOf("a"), found("2026-10-05", "2026-10-12"))
        assertEquals(listOf("a"), found("2026-10-16", "2026-10-25"))
    }

    @Test
    fun disjointPeriods_doNotOverlap() {
        assertEquals(emptyList<String>(), found("2026-11-01", "2026-11-05"))
        assertEquals(emptyList<String>(), found("2026-09-01", "2026-09-05"))
    }

    @Test
    fun overlapAcrossYearEnd_isDetected() {
        val entries = listOf(entry("j", "2026-12-28", "2027-01-04"))
        assertEquals(listOf("j"), CalendarOverlap.find(entries, "2027-01-02", "2027-01-03").map { it.id })
        assertEquals(listOf("j"), CalendarOverlap.find(entries, "2026-12-20", "2026-12-28").map { it.id })
        assertEquals(emptyList<String>(), CalendarOverlap.find(entries, "2027-01-05", "2027-01-06").map { it.id })
    }

    @Test
    fun whenEditing_theEntryItselfIsNotACollision() {
        assertEquals(emptyList<String>(), found("2026-10-12", "2026-10-20", excludeId = "a"))
        assertEquals(listOf("a"), found("2026-10-12", "2026-10-20", excludeId = "andere"))
    }

    @Test
    fun severalCollisions_areAllReturnedSortedByStart() {
        val entries = listOf(
            entry("c", "2026-10-20", "2026-10-22", "Anna"),
            entry("a", "2026-10-10", "2026-10-18"),
            entry("b", "2026-10-15", "2026-10-21", "Robert"),
            entry("x", "2026-12-01", "2026-12-03"),
        )
        val result = CalendarOverlap.find(entries, "2026-10-17", "2026-10-20")
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(listOf("Tobias", "Robert", "Anna"), result.map { it.personName })
    }

    @Test
    fun emptyList_hasNoCollision() = assertTrue(CalendarOverlap.find(emptyList(), "2026-10-01", "2026-10-02").isEmpty())

    @Test
    fun overlapsIsSymmetric() {
        assertTrue(CalendarOverlap.overlaps("2026-10-10", "2026-10-18", "2026-10-18", "2026-10-20"))
        assertTrue(CalendarOverlap.overlaps("2026-10-18", "2026-10-20", "2026-10-10", "2026-10-18"))
        assertFalse(CalendarOverlap.overlaps("2026-10-10", "2026-10-18", "2026-10-19", "2026-10-20"))
        assertFalse(CalendarOverlap.overlaps("2026-10-19", "2026-10-20", "2026-10-10", "2026-10-18"))
    }
}
