package de.hagi089.obelix.data.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarYearTest {

    private fun entry(id: String, start: String, end: String) = CalendarEntry(
        id = id, startDate = start, endDate = end, personUid = "u", personName = "Tobias",
        destination = null, comment = "", createdBy = "u",
    )

    @Test
    fun entriesIn_includesEntriesReachingIntoOrOutOfTheYear_sortedChronologically() {
        val entries = listOf(
            entry("spaet", "2026-12-28", "2027-01-03"),
            entry("vor", "2025-12-20", "2025-12-31"), // endet am Vortag: nicht 2026
            entry("mitte", "2026-07-06", "2026-07-12"),
            entry("rein", "2025-12-30", "2026-01-02"),
            entry("nach", "2027-01-01", "2027-01-05"),
        )
        assertEquals(listOf("rein", "mitte", "spaet"), CalendarYear.entriesIn(entries, 2026).map { it.id })
    }

    @Test
    fun entriesIn_yearBoundaryEntryAppearsInBothYears() {
        val entries = listOf(entry("silvester", "2026-12-30", "2027-01-02"))
        assertEquals(listOf("silvester"), CalendarYear.entriesIn(entries, 2026).map { it.id })
        assertEquals(listOf("silvester"), CalendarYear.entriesIn(entries, 2027).map { it.id })
        assertTrue(CalendarYear.entriesIn(entries, 2028).isEmpty())
    }

    @Test
    fun occupantsByMonth_hasTwelveMonths_andOnlyDaysOfThatMonth() {
        val entries = listOf(entry("e1", "2026-01-30", "2026-02-02"))
        val months = CalendarYear.occupantsByMonth(entries, 2026)
        assertEquals(12, months.size)
        assertEquals(setOf(30, 31), months[0].keys.map { it.dayOfMonth }.toSet())
        assertEquals(setOf(1, 2), months[1].keys.map { it.dayOfMonth }.toSet())
        assertTrue(months[2].isEmpty())
    }

    @Test
    fun occupiedDays_countsOverlapOnlyOnce_andIgnoresOtherYears() {
        val entries = listOf(
            entry("a", "2026-10-01", "2026-10-05"), // 5 Tage
            entry("b", "2026-10-05", "2026-10-08"), // 5. doppelt, 6.–8. neu: zusammen 8 Tage
            entry("c", "2026-12-30", "2027-01-02"), // 2 Tage in 2026
            entry("d", "2025-12-30", "2026-01-01"), // 1 Tag in 2026
        )
        assertEquals(11, CalendarYear.occupiedDays(CalendarYear.occupantsByMonth(entries, 2026)))
    }

    @Test
    fun occupiedDays_emptyYear_isZero() {
        assertEquals(0, CalendarYear.occupiedDays(CalendarYear.occupantsByMonth(emptyList(), 2026)))
    }

    @Test
    fun occupiedDays_fullLeapYear_equalsDaysInYear() {
        val entries = listOf(entry("all", "2028-01-01", "2028-12-31"))
        assertEquals(366, CalendarYear.daysInYear(2028))
        assertEquals(366, CalendarYear.occupiedDays(CalendarYear.occupantsByMonth(entries, 2028)))
        assertEquals(365, CalendarYear.daysInYear(2027))
    }

    @Test
    fun occupantsByMonth_overlapDayHasBothEntries() {
        val entries = listOf(entry("a", "2026-06-01", "2026-06-05"), entry("b", "2026-06-05", "2026-06-07"))
        val june = CalendarYear.occupantsByMonth(entries, 2026)[5]
        assertEquals(listOf("a", "b"), june[LocalDate.of(2026, 6, 5)]?.map { it.id })
    }
}
