package de.hagi089.obelix.data.calendar

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthTest {

    private fun entry(id: String, start: String, end: String) = CalendarEntry(
        id = id, startDate = start, endDate = end, personUid = "u", personName = "Tobias",
        destination = null, comment = "", createdBy = "u",
    )

    @Test
    fun weeks_startOnMonday_andHaveSevenCells() {
        // Oktober 2026 beginnt an einem Donnerstag
        val weeks = CalendarMonth.weeks(YearMonth.of(2026, 10))
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(listOf(null, null, null, LocalDate.of(2026, 10, 1)), weeks.first().take(4))
        assertEquals(LocalDate.of(2026, 10, 31), weeks.last().filterNotNull().last())
        assertEquals(31, weeks.flatten().filterNotNull().size)
    }

    @Test
    fun weeks_monthStartingOnMonday_hasNoLeadingGap() {
        // Juni 2026 beginnt an einem Montag
        val weeks = CalendarMonth.weeks(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), weeks.first().first())
        assertEquals(5, weeks.size)
    }

    @Test
    fun weeks_monthEndingOnSunday_hasNoTrailingGap() {
        // Mai 2026 beginnt an einem Freitag und endet an einem Sonntag
        val weeks = CalendarMonth.weeks(YearMonth.of(2026, 5))
        assertEquals(LocalDate.of(2026, 5, 31), weeks.last().last())
    }

    @Test
    fun weeks_februaryLeapAndNormalYear() {
        assertEquals(29, CalendarMonth.weeks(YearMonth.of(2028, 2)).flatten().filterNotNull().size)
        assertEquals(28, CalendarMonth.weeks(YearMonth.of(2027, 2)).flatten().filterNotNull().size)
        // Februar 2027 beginnt an einem Montag und hat 28 Tage: genau vier volle Wochen
        assertEquals(4, CalendarMonth.weeks(YearMonth.of(2027, 2)).size)
    }

    @Test
    fun entriesIn_includesEntriesReachingIntoOrOutOfTheMonth() {
        val entries = listOf(
            entry("vor", "2026-09-25", "2026-09-30"), // endet am Vortag: nicht im Oktober
            entry("rein", "2026-09-28", "2026-10-02"),
            entry("mitten", "2026-10-10", "2026-10-18"),
            entry("raus", "2026-10-30", "2026-11-03"),
            entry("nach", "2026-11-01", "2026-11-05"),
        )
        assertEquals(listOf("rein", "mitten", "raus"), CalendarMonth.entriesIn(entries, YearMonth.of(2026, 10)).map { it.id })
    }

    @Test
    fun occupancy_countsEntriesPerDay_andMarksOverlaps() {
        val entries = listOf(entry("a", "2026-10-10", "2026-10-12"), entry("b", "2026-10-12", "2026-10-13"))
        val occupancy = CalendarMonth.occupancy(entries, YearMonth.of(2026, 10))
        assertEquals(1, occupancy[LocalDate.of(2026, 10, 10)])
        assertEquals(1, occupancy[LocalDate.of(2026, 10, 11)])
        assertEquals(2, occupancy[LocalDate.of(2026, 10, 12)]) // beide Einträge belegen den 12.
        assertEquals(1, occupancy[LocalDate.of(2026, 10, 13)])
        assertNull(occupancy[LocalDate.of(2026, 10, 9)])
        assertNull(occupancy[LocalDate.of(2026, 10, 14)])
    }

    @Test
    fun occupancy_onlyCountsDaysInsideTheShownMonth() {
        val entries = listOf(entry("a", "2026-09-28", "2026-10-02"))
        val occupancy = CalendarMonth.occupancy(entries, YearMonth.of(2026, 10))
        assertEquals(setOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), occupancy.keys)
    }

    @Test
    fun current_returnsEntriesRunningToday_includingFirstAndLastDay() {
        val entries = listOf(entry("a", "2026-10-10", "2026-10-18"), entry("b", "2026-10-20", "2026-10-25"))
        assertEquals(listOf("a"), CalendarMonth.current(entries, LocalDate.of(2026, 10, 10)).map { it.id })
        assertEquals(listOf("a"), CalendarMonth.current(entries, LocalDate.of(2026, 10, 18)).map { it.id })
        assertTrue(CalendarMonth.current(entries, LocalDate.of(2026, 10, 19)).isEmpty())
    }
}
