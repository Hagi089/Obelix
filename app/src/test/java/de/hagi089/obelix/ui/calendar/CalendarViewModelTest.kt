package de.hagi089.obelix.ui.calendar

import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarInput
import de.hagi089.obelix.data.calendar.CalendarRepository
import de.hagi089.obelix.data.user.Role
import de.hagi089.obelix.data.user.UserRepository
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Umschaltung Jahres-/Monatsübersicht sowie Jahres- und Monatswechsel (ohne Laden, daher ohne Firebase). */
class CalendarViewModelTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun entry(id: String, start: String, end: String) = CalendarEntry(
        id = id, startDate = start, endDate = end, personUid = "u1", personName = "Anna",
        destination = null, comment = "", createdBy = "u1",
    )

    private class FakeCalendar : CalendarRepository {
        override suspend fun loadAll() = Result.success(emptyList<CalendarEntry>())
        override suspend fun get(id: String) = Result.success<CalendarEntry?>(null)
        override suspend fun findOverlaps(startDate: String, endDate: String, excludeId: String?) = Result.success(emptyList<CalendarEntry>())
        override suspend fun create(input: CalendarInput, uid: String) = Result.success(Unit)
        override suspend fun update(id: String, input: CalendarInput, uid: String) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
    }

    private class FakeUsers : UserRepository {
        override suspend fun loadProfile(uid: String) = Result.success<de.hagi089.obelix.data.user.UserProfile?>(null)
        override suspend fun register(uid: String, displayName: String, accessCode: String) = Result.success(Unit)
        override suspend fun loadUsers() = Result.success(emptyList<de.hagi089.obelix.data.user.UserProfile>())
        override suspend fun setRole(uid: String, role: Role) = Result.success(Unit)
        override suspend fun removeUser(uid: String) = Result.success(Unit)
        override suspend fun loadAccessCode() = Result.success<String?>(null)
        override suspend fun renewAccessCode(adminUid: String) = Result.success("")
    }

    private fun viewModel(now: LocalDate = today) = CalendarViewModel(FakeCalendar(), FakeUsers(), clock = { now })

    @Test
    fun opensWithYearViewOfCurrentYear() {
        val state = viewModel().state.value
        assertEquals(CalendarViewMode.YEAR, state.viewMode)
        assertEquals(2026, state.year)
        assertEquals(YearMonth.of(2026, 10), state.month)
    }

    @Test
    fun yearChange_updatesYear_andDoesNotTouchMonth() {
        val vm = viewModel()
        vm.nextYear()
        assertEquals(2027, vm.state.value.year)
        vm.previousYear(); vm.previousYear()
        assertEquals(2025, vm.state.value.year)
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
    }

    @Test
    fun showToday_inYearView_returnsToCurrentYear() {
        val vm = viewModel()
        vm.nextYear(); vm.nextYear()
        vm.showToday()
        assertEquals(2026, vm.state.value.year)
    }

    @Test
    fun showToday_inMonthView_keepsOldBehaviour() {
        val vm = viewModel()
        vm.showMonthView()
        vm.nextMonth(); vm.nextMonth()
        assertEquals(YearMonth.of(2026, 12), vm.state.value.month)
        vm.showToday()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(CalendarViewMode.MONTH, vm.state.value.viewMode)
    }

    @Test
    fun openMonth_switchesToMonthViewOfThatMonth() {
        val vm = viewModel()
        vm.openMonth(YearMonth.of(2026, 3))
        val state = vm.state.value
        assertEquals(CalendarViewMode.MONTH, state.viewMode)
        assertEquals(YearMonth.of(2026, 3), state.month)
    }

    @Test
    fun showMonthView_afterYearChange_showsMonthInSelectedYear() {
        val vm = viewModel()
        vm.nextYear()
        vm.showMonthView()
        assertEquals(YearMonth.of(2027, 1), vm.state.value.month) // anderes Jahr: Januar
        vm.showYearView()
        vm.previousYear() // 2026
        vm.showMonthView()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month) // aktuelles Jahr: aktueller Monat
    }

    @Test
    fun showMonthView_inSameYear_keepsMonth() {
        val vm = viewModel()
        vm.openMonth(YearMonth.of(2026, 5))
        vm.showYearView()
        vm.showMonthView()
        assertEquals(YearMonth.of(2026, 5), vm.state.value.month)
    }

    @Test
    fun showYearView_takesYearFromMonthView() {
        val vm = viewModel()
        vm.showMonthView()
        repeat(3) { vm.nextMonth() } // Januar 2027
        vm.showYearView()
        assertEquals(CalendarViewMode.YEAR, vm.state.value.viewMode)
        assertEquals(2027, vm.state.value.year)
    }

    @Test
    fun monthNavigation_unchangedByYearView() {
        val vm = viewModel()
        vm.nextYear()
        vm.showMonthView()
        vm.previousMonth() // Dezember 2026
        assertEquals(YearMonth.of(2026, 12), vm.state.value.month)
    }

    @Test
    fun yearEntries_followTheSelectedYear_monthEntriesStayUnchanged() {
        val entries = listOf(entry("a", "2026-10-10", "2026-10-18"), entry("b", "2027-03-01", "2027-03-05"))
        val state = CalendarState(entries = entries, year = 2026, month = YearMonth.of(2026, 10))
        assertEquals(listOf("a"), state.yearEntries.map { it.id })
        assertEquals(listOf("a"), state.monthEntries.map { it.id })
        val next = state.copy(year = 2027)
        assertEquals(listOf("b"), next.yearEntries.map { it.id })
        assertEquals(listOf("a"), next.monthEntries.map { it.id })
    }

    @Test
    fun yearOccupants_haveTwelveMonthsForTheSelectedYear() {
        val state = CalendarState(entries = listOf(entry("a", "2026-10-10", "2026-10-11")), year = 2026)
        assertEquals(12, state.yearOccupants.size)
        assertEquals(setOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)), state.yearOccupants[9].keys)
    }
}
