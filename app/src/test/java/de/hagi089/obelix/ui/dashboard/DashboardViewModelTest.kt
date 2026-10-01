package de.hagi089.obelix.ui.dashboard

import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarInput
import de.hagi089.obelix.data.calendar.CalendarRepository
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.CampsiteInput
import de.hagi089.obelix.data.campsites.CampsiteRepository
import de.hagi089.obelix.data.campsites.GeoPosition
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingInput
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.FinanceRepository
import de.hagi089.obelix.data.finance.ReceiptChange
import de.hagi089.obelix.data.finance.Settlement
import de.hagi089.obelix.data.planned.PlannedExpense
import de.hagi089.obelix.data.planned.PlannedExpenseRepository
import de.hagi089.obelix.data.planned.PlannedInput
import de.hagi089.obelix.data.planned.PlannedStatus
import de.hagi089.obelix.data.planned.PurchaseInput
import de.hagi089.obelix.data.repairs.Repair
import de.hagi089.obelix.data.repairs.RepairInput
import de.hagi089.obelix.data.repairs.RepairRepository
import de.hagi089.obelix.data.repairs.RepairStatus
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun failure(error: AppError = AppError.NETWORK) = Result.failure<Nothing>(AppException(error))

    private fun booking(id: String, type: BookingType, date: String, cents: Long, settlement: Settlement = Settlement.SETTLED) =
        Booking(id, type, date, cents, "cat", "u1", settlement, "", "", null)

    private fun entry(id: String, start: String, end: String) = CalendarEntry(id, start, end, "u1", "Anna", "Italien", "", "u1")

    private fun repair(id: String, status: RepairStatus) = Repair(id, "T$id", "D", "2026-09-01", status, null, "", "u1")

    private fun plan(id: String, cents: Long, status: PlannedStatus) =
        PlannedExpense(id, "P$id", cents, "2026-09-01", status, null, null, "", null, "u1")

    private fun site(id: String) = Campsite(id, 48.0, 11.0, "2026-09-01", "ok", null, null, null, null, emptyList(), "u1")

    private class FakeFinance(var result: Result<List<Booking>>) : FinanceRepository {
        var loads = 0
        override suspend fun loadAll(): Result<List<Booking>> { loads++; return result }
        override suspend fun get(id: String) = Result.success<Booking?>(null)
        override suspend fun create(input: BookingInput, uid: String, receipt: NewFile?) = Result.success(Unit)
        override suspend fun update(id: String, input: BookingInput, previous: Settlement, uid: String, receipt: ReceiptChange) =
            Result.success(Unit)
        override suspend fun markSettled(id: String, uid: String) = Result.success(Unit)
        override suspend fun delete(id: String, plannedExpenseId: String?, uid: String) = Result.success(Unit)
        override suspend fun importBookings(inputs: List<BookingInput>, uid: String, onProgress: (Int) -> Unit) = Result.success(0)
    }

    private class FakeCalendar(var result: Result<List<CalendarEntry>>) : CalendarRepository {
        override suspend fun loadAll() = result
        override suspend fun get(id: String) = Result.success<CalendarEntry?>(null)
        override suspend fun findOverlaps(startDate: String, endDate: String, excludeId: String?) = Result.success(emptyList<CalendarEntry>())
        override suspend fun create(input: CalendarInput, uid: String) = Result.success(Unit)
        override suspend fun update(id: String, input: CalendarInput, uid: String) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
    }

    private class FakeRepairs(var result: Result<List<Repair>>) : RepairRepository {
        override suspend fun loadAll() = result
        override suspend fun get(id: String) = Result.success<Repair?>(null)
        override suspend fun create(input: RepairInput, uid: String) = Result.success(Unit)
        override suspend fun update(id: String, input: RepairInput, status: RepairStatus, uid: String) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
    }

    private class FakePlanned(var result: Result<List<PlannedExpense>>) : PlannedExpenseRepository {
        override suspend fun loadAll() = result
        override suspend fun get(id: String) = Result.success<PlannedExpense?>(null)
        override suspend fun create(input: PlannedInput, uid: String) = Result.success(Unit)
        override suspend fun update(id: String, input: PlannedInput, uid: String) = Result.success(Unit)
        override suspend fun purchase(id: String, purchase: PurchaseInput, uid: String) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
    }

    private class FakeCampsites(var result: Result<List<Campsite>>) : CampsiteRepository {
        override suspend fun loadAll() = result
        override suspend fun get(id: String) = Result.success<Campsite?>(null)
        override suspend fun create(position: GeoPosition, date: String, input: CampsiteInput, photos: List<NewFile>, uid: String) =
            Result.success(Unit)
        override suspend fun update(
            id: String,
            input: CampsiteInput,
            removedPhotoIds: Set<String>,
            addedPhotos: List<NewFile>,
            uid: String,
        ) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
        override suspend fun loadPhoto(ref: FileRef) = Result.success(ByteArray(0))
    }

    private class Fakes(
        val finance: FakeFinance = FakeFinance(Result.success(emptyList())),
        val calendar: FakeCalendar = FakeCalendar(Result.success(emptyList())),
        val repairs: FakeRepairs = FakeRepairs(Result.success(emptyList())),
        val planned: FakePlanned = FakePlanned(Result.success(emptyList())),
        val campsites: FakeCampsites = FakeCampsites(Result.success(emptyList())),
    )

    private fun viewModel(f: Fakes, clockDay: LocalDate = today) = DashboardViewModel(
        finance = f.finance,
        calendar = f.calendar,
        repairs = f.repairs,
        planned = f.planned,
        campsites = f.campsites,
        clock = { clockDay },
        scopeOverride = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun beforeRefresh_nothingIsLoaded_andNoNumbersExist() {
        val s = viewModel(Fakes()).state.value
        assertTrue(s.isLoading)
        assertNull(s.financeSummary)
        assertNull(s.expensesOfYearCents)
        assertNull(s.currentEntries)
        assertNull(s.nextEntry)
        assertNull(s.openRepairs)
        assertNull(s.plannedSummary)
        assertNull(s.campsiteCount)
        assertNull(s.error)
    }

    @Test
    fun emptyDatabase_loadsAsEmpty_notAsError() {
        val vm = viewModel(Fakes())
        vm.refresh()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNull(s.error)
        assertFalse(s.hasBookings)
        assertEquals(emptyList<CalendarEntry>(), s.currentEntries)
        assertNull(s.nextEntry)
        assertEquals(0, s.openRepairs)
        assertEquals(0, s.plannedSummary?.openCount)
        assertEquals(0, s.campsiteCount)
    }

    @Test
    fun refresh_computesAllFiguresFromTheData() {
        val f = Fakes(
            finance = FakeFinance(
                Result.success(
                    listOf(
                        booking("1", BookingType.INCOME, "2026-01-05", 100_000),
                        booking("2", BookingType.EXPENSE, "2026-02-05", 30_000),
                        booking("3", BookingType.EXPENSE, "2025-02-05", 5_000),
                        booking("4", BookingType.EXPENSE, "2026-03-05", 2_000, Settlement.OPEN),
                    ),
                ),
            ),
            calendar = FakeCalendar(Result.success(listOf(entry("now", "2026-09-30", "2026-10-02"), entry("later", "2026-10-20", "2026-10-25")))),
            repairs = FakeRepairs(Result.success(listOf(repair("1", RepairStatus.OPEN), repair("2", RepairStatus.DONE), repair("3", RepairStatus.OPEN)))),
            planned = FakePlanned(Result.success(listOf(plan("1", 50_000, PlannedStatus.PLANNED), plan("2", 99_999, PlannedStatus.PURCHASED), plan("3", 1_500, PlannedStatus.PLANNED)))),
            campsites = FakeCampsites(Result.success(listOf(site("1"), site("2"), site("3"), site("4")))),
        )
        val vm = viewModel(f)
        vm.refresh()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNull(s.error)
        // Kontostand = nur beglichene: 100.000 − 30.000 − 5.000 = 65.000 (die offene Ausgabe zählt nicht)
        assertEquals(65_000L, s.financeSummary?.balanceCents)
        assertEquals(2_000L, s.financeSummary?.openTotalCents)
        // Ausgaben 2026: 30.000 + 2.000 (offene zählt zu den Ausgaben), nicht die aus 2025
        assertEquals(32_000L, s.expensesOfYearCents)
        assertEquals(listOf("now"), s.currentEntries?.map { it.id })
        assertEquals("later", s.nextEntry?.id)
        assertEquals(2, s.openRepairs)
        assertEquals(2, s.plannedSummary?.openCount)
        assertEquals(51_500L, s.plannedSummary?.openEstimatedCents)
        assertEquals(4, s.campsiteCount)
    }

    @Test
    fun plannedExpenses_neverChangeTheBalance() {
        val bookings = listOf(booking("1", BookingType.INCOME, "2026-01-05", 10_000))
        val without = viewModel(Fakes(finance = FakeFinance(Result.success(bookings))))
        val with = viewModel(
            Fakes(
                finance = FakeFinance(Result.success(bookings)),
                planned = FakePlanned(Result.success(listOf(plan("1", 7_777, PlannedStatus.PLANNED)))),
            ),
        )
        without.refresh()
        with.refresh()
        assertEquals(without.state.value.financeSummary?.balanceCents, with.state.value.financeSummary?.balanceCents)
        assertEquals(10_000L, with.state.value.financeSummary?.balanceCents)
    }

    @Test
    fun oneFailingSource_showsError_andOnlyThatSourceHasNoNumber() {
        val f = Fakes(
            finance = FakeFinance(failure(AppError.NETWORK)),
            repairs = FakeRepairs(Result.success(listOf(repair("1", RepairStatus.OPEN)))),
            campsites = FakeCampsites(Result.success(listOf(site("1")))),
        )
        val vm = viewModel(f)
        vm.refresh()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(AppError.NETWORK, s.error)
        // Keine „0“ statt eines Fehlers:
        assertNull(s.financeSummary)
        assertNull(s.expensesOfYearCents)
        assertFalse(s.hasBookings)
        assertEquals(AppError.NETWORK, s.bookings.error)
        // Die anderen Kacheln bleiben sichtbar:
        assertEquals(1, s.openRepairs)
        assertEquals(1, s.campsiteCount)
        assertNull(s.repairs.error)
    }

    @Test
    fun everySourceFailing_leavesNoNumberAtAll() {
        val f = Fakes(
            finance = FakeFinance(failure()),
            calendar = FakeCalendar(failure()),
            repairs = FakeRepairs(failure()),
            planned = FakePlanned(failure()),
            campsites = FakeCampsites(failure()),
        )
        val vm = viewModel(f)
        vm.refresh()
        val s = vm.state.value
        assertEquals(AppError.NETWORK, s.error)
        assertNull(s.financeSummary)
        assertNull(s.currentEntries)
        assertNull(s.nextEntry)
        assertNull(s.openRepairs)
        assertNull(s.plannedSummary)
        assertNull(s.campsiteCount)
    }

    @Test
    fun laterFailure_keepsEarlierValue_butReportsTheError() {
        val repairs = FakeRepairs(Result.success(listOf(repair("1", RepairStatus.OPEN))))
        val vm = viewModel(Fakes(repairs = repairs))
        vm.refresh()
        assertEquals(1, vm.state.value.openRepairs)
        repairs.result = failure(AppError.UNAVAILABLE)
        vm.refresh()
        val s = vm.state.value
        assertEquals(AppError.UNAVAILABLE, s.error)
        assertEquals(1, s.openRepairs)
        assertFalse(s.isLoading)
    }

    @Test
    fun retry_afterFailure_clearsTheError() {
        val finance = FakeFinance(failure())
        val vm = viewModel(Fakes(finance = finance))
        vm.refresh()
        assertEquals(AppError.NETWORK, vm.state.value.error)
        finance.result = Result.success(listOf(booking("1", BookingType.INCOME, "2026-01-05", 500)))
        vm.refresh()
        val s = vm.state.value
        assertNull(s.error)
        assertEquals(500L, s.financeSummary?.balanceCents)
    }

    @Test
    fun unknownExceptionBecomesUnknownError() {
        val vm = viewModel(Fakes(repairs = FakeRepairs(Result.failure(IllegalStateException("x")))))
        vm.refresh()
        assertEquals(AppError.UNKNOWN, vm.state.value.error)
        assertNull(vm.state.value.openRepairs)
    }

    @Test
    fun refresh_usesTheCurrentDay_forYearAndAppointments() {
        val f = Fakes(
            finance = FakeFinance(Result.success(listOf(booking("1", BookingType.EXPENSE, "2027-01-02", 800)))),
            calendar = FakeCalendar(Result.success(listOf(entry("a", "2027-01-10", "2027-01-12")))),
        )
        val vm = viewModel(f, clockDay = LocalDate.of(2027, 1, 3))
        vm.refresh()
        val s = vm.state.value
        assertEquals(LocalDate.of(2027, 1, 3), s.today)
        assertEquals(800L, s.expensesOfYearCents)
        assertEquals("a", s.nextEntry?.id)
    }

    @Test
    fun everyRefresh_loadsEachSourceOnce() {
        val f = Fakes()
        val vm = viewModel(f)
        vm.refresh()
        assertEquals(1, f.finance.loads)
        vm.refresh()
        assertEquals(2, f.finance.loads)
    }
}
