package de.hagi089.obelix.data.dashboard

import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.FinanceCalculator
import de.hagi089.obelix.data.finance.Settlement
import de.hagi089.obelix.ui.finance.FinanceFilter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardLogicTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun entry(id: String, start: String, end: String, person: String = "Anna") =
        CalendarEntry(id, start, end, "u-$person", person, null, "", "u-$person")

    private fun booking(
        id: String,
        type: BookingType,
        date: String,
        cents: Long,
        settlement: Settlement = Settlement.SETTLED,
        by: String? = "u1",
    ) = Booking(id, type, date, cents, "cat", by, settlement, "", "", null)

    // ---- nextEntry / currentEntries ----

    @Test
    fun nextEntry_isNull_withoutEntries() {
        assertNull(DashboardLogic.nextEntry(emptyList(), today))
    }

    @Test
    fun nextEntry_ignoresPastAndRunningEntries() {
        val entries = listOf(
            entry("past", "2026-09-01", "2026-09-10"),
            entry("running", "2026-09-28", "2026-10-03"),
        )
        assertNull(DashboardLogic.nextEntry(entries, today))
    }

    @Test
    fun nextEntry_startingToday_isCurrentNotNext() {
        val entries = listOf(entry("a", "2026-10-01", "2026-10-05"))
        assertNull(DashboardLogic.nextEntry(entries, today))
        assertEquals(listOf("a"), DashboardLogic.currentEntries(entries, today).map { it.id })
    }

    @Test
    fun nextEntry_startingTomorrow_isNext() {
        val entries = listOf(entry("a", "2026-10-02", "2026-10-05"))
        assertEquals("a", DashboardLogic.nextEntry(entries, today)?.id)
    }

    @Test
    fun nextEntry_picksEarliestStart_regardlessOfListOrder() {
        val entries = listOf(
            entry("late", "2026-12-24", "2027-01-02"),
            entry("soon", "2026-10-10", "2026-10-18"),
            entry("mid", "2026-11-01", "2026-11-03"),
        )
        assertEquals("soon", DashboardLogic.nextEntry(entries, today)?.id)
    }

    @Test
    fun nextEntry_sameStart_usesEndThenId() {
        val entries = listOf(
            entry("b", "2026-10-10", "2026-10-12"),
            entry("a", "2026-10-10", "2026-10-12"),
            entry("c", "2026-10-10", "2026-10-11"),
        )
        assertEquals("c", DashboardLogic.nextEntry(entries, today)?.id)
        assertEquals("a", DashboardLogic.nextEntry(entries.filter { it.id != "c" }, today)?.id)
    }

    @Test
    fun nextEntry_acrossYearBoundary() {
        val entries = listOf(entry("ny", "2027-01-01", "2027-01-05"))
        assertEquals("ny", DashboardLogic.nextEntry(entries, LocalDate.of(2026, 12, 31))?.id)
    }

    @Test
    fun currentEntries_includeStartAndEndDay_andOverlaps() {
        val entries = listOf(
            entry("endsToday", "2026-09-25", "2026-10-01"),
            entry("startsToday", "2026-10-01", "2026-10-04", "Tobias"),
            entry("tomorrow", "2026-10-02", "2026-10-04"),
        )
        assertEquals(setOf("endsToday", "startsToday"), DashboardLogic.currentEntries(entries, today).map { it.id }.toSet())
    }

    // ---- expensesOfYearCents ----

    @Test
    fun expensesOfYear_isZero_withoutBookings() {
        assertEquals(0L, DashboardLogic.expensesOfYearCents(emptyList(), 2026))
    }

    @Test
    fun expensesOfYear_countsOnlyExpensesOfThatYear() {
        val bookings = listOf(
            booking("1", BookingType.EXPENSE, "2026-01-01", 1_000),
            booking("2", BookingType.EXPENSE, "2026-12-31", 2_500),
            booking("3", BookingType.EXPENSE, "2025-12-31", 9_999),
            booking("4", BookingType.EXPENSE, "2027-01-01", 7_777),
            booking("5", BookingType.INCOME, "2026-06-01", 50_000),
        )
        assertEquals(3_500L, DashboardLogic.expensesOfYearCents(bookings, 2026))
    }

    @Test
    fun expensesOfYear_includesSponsoredAndOpen_likeFinanceTotal() {
        val bookings = listOf(
            booking("1", BookingType.EXPENSE, "2026-03-01", 1_000, Settlement.SETTLED),
            booking("2", BookingType.EXPENSE, "2026-03-02", 2_000, Settlement.OPEN),
            booking("3", BookingType.EXPENSE, "2026-03-03", 4_000, Settlement.SPONSORED),
        )
        assertEquals(7_000L, DashboardLogic.expensesOfYearCents(bookings, 2026))
        // Dieselbe Zahl wie „Ausgaben gesamt“ des Finanzbereichs, wenn alle Buchungen im Jahr liegen.
        assertEquals(FinanceCalculator.summarize(bookings).expenseTotalCents, DashboardLogic.expensesOfYearCents(bookings, 2026))
    }

    @Test
    fun expensesOfYear_yearPrefixIsExact() {
        // „2026-“ darf nicht „20260“ o. Ä. treffen; ein Datum in einem anderen Jahrhundert zählt nicht.
        val bookings = listOf(
            booking("1", BookingType.EXPENSE, "2026-05-05", 100),
            booking("2", BookingType.EXPENSE, "1026-05-05", 900),
        )
        assertEquals(100L, DashboardLogic.expensesOfYearCents(bookings, 2026))
    }

    @Test
    fun expensesOfYear_matchesFinanceFilterForExpensesOfThatYear() {
        // Der Finanzbereich filtert „Ausgabe“ + Jahr nach demselben Datumspräfix.
        val bookings = listOf(
            booking("1", BookingType.EXPENSE, "2026-02-01", 1_234),
            booking("2", BookingType.EXPENSE, "2025-02-01", 555),
            booking("3", BookingType.INCOME, "2026-02-01", 8_000),
            booking("4", BookingType.EXPENSE, "2026-11-30", 66, Settlement.SPONSORED),
        )
        val viaFilter = bookings
            .filter(FinanceFilter(type = BookingType.EXPENSE, year = 2026)::matches)
            .sumOf { it.amountCents }
        assertEquals(viaFilter, DashboardLogic.expensesOfYearCents(bookings, 2026))
    }
}
