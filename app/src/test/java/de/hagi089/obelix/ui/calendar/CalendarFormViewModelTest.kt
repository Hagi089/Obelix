package de.hagi089.obelix.ui.calendar

import de.hagi089.obelix.R
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarInput
import de.hagi089.obelix.data.calendar.CalendarRepository
import de.hagi089.obelix.data.user.Role
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Speicherablauf des Kalenderformulars (Entscheidung 5): Überschneidung vor dem Speichern anzeigen, Speichern nur
 * nach ausdrücklicher Bestätigung, bei fehlgeschlagener Prüfung gar nicht. Die Fakes antworten sofort, deshalb läuft
 * alles ohne Android mit [Dispatchers.Unconfined] synchron.
 */
class CalendarFormViewModelTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun entry(id: String, start: String, end: String, name: String = "Anna", uid: String = "anna", destination: String? = null) =
        CalendarEntry(id, start, end, uid, name, destination, "", uid)

    private class FakeCalendar(
        var existing: CalendarEntry? = null,
        var overlaps: Result<List<CalendarEntry>> = Result.success(emptyList()),
        var writeResult: Result<Unit> = Result.success(Unit),
    ) : CalendarRepository {
        val created = mutableListOf<CalendarInput>()
        val updated = mutableListOf<Pair<String, CalendarInput>>()
        val deleted = mutableListOf<String>()
        val overlapQueries = mutableListOf<Triple<String, String, String?>>()

        override suspend fun loadAll() = Result.success(emptyList<CalendarEntry>())
        override suspend fun get(id: String): Result<CalendarEntry?> = Result.success(existing?.takeIf { it.id == id })
        override suspend fun findOverlaps(startDate: String, endDate: String, excludeId: String?): Result<List<CalendarEntry>> {
            overlapQueries += Triple(startDate, endDate, excludeId)
            return overlaps
        }
        override suspend fun create(input: CalendarInput, uid: String): Result<Unit> {
            if (writeResult.isSuccess) created += input
            return writeResult
        }
        override suspend fun update(id: String, input: CalendarInput, uid: String): Result<Unit> {
            if (writeResult.isSuccess) updated += id to input
            return writeResult
        }
        override suspend fun delete(id: String): Result<Unit> {
            if (writeResult.isSuccess) deleted += id
            return writeResult
        }
    }

    private class FakeUsers(private val list: List<UserProfile>) : UserRepository {
        override suspend fun loadProfile(uid: String) = Result.success(list.firstOrNull { it.uid == uid })
        override suspend fun register(uid: String, displayName: String, accessCode: String) = Result.success(Unit)
        override suspend fun loadUsers() = Result.success(list)
        override suspend fun setRole(uid: String, role: Role) = Result.success(Unit)
        override suspend fun removeUser(uid: String) = Result.success(Unit)
        override suspend fun loadAccessCode() = Result.success<String?>(null)
        override suspend fun renewAccessCode(adminUid: String) = Result.success("")
    }

    private val users = FakeUsers(
        listOf(UserProfile("tobias", "Tobias", Role.ADMIN), UserProfile("anna", "Anna", Role.MEMBER)),
    )

    private fun viewModel(calendar: FakeCalendar, entryId: String? = null) = CalendarFormViewModel(
        entryId = entryId,
        uid = "tobias",
        calendar = calendar,
        users = users,
        clock = { today },
        scopeOverride = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun CalendarFormViewModel.fill(start: String = "2026-10-10", end: String = "2026-10-18") {
        setStartDate(start)
        setEndDate(end)
    }

    @Test
    fun newEntry_startsWithTodayAndTheCurrentUser() {
        val vm = viewModel(FakeCalendar())
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals("2026-10-01", s.startDate)
        assertEquals("2026-10-01", s.endDate)
        assertEquals("tobias", s.personUid)
        assertFalse(s.isEdit)
    }

    @Test
    fun noOverlap_savesDirectly() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.fill()
        vm.setDestination("  Italien ")
        vm.setComment(" Herbsturlaub ")
        vm.save()
        assertTrue(vm.state.value.finished)
        assertNull(vm.state.value.overlaps)
        assertEquals(
            listOf(CalendarInput("2026-10-10", "2026-10-18", "tobias", "Tobias", "Italien", "Herbsturlaub")),
            calendar.created,
        )
        assertEquals(listOf(Triple("2026-10-10", "2026-10-18", null)), calendar.overlapQueries)
    }

    @Test
    fun emptyDestination_isNotStored() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.fill()
        vm.setDestination("   ")
        vm.save()
        assertNull(calendar.created.single().destination)
    }

    @Test
    fun overlap_showsTheCollidingEntries_andSavesNothing() {
        val collision = entry("x", "2026-10-15", "2026-10-20", destination = "Kroatien")
        val calendar = FakeCalendar(overlaps = Result.success(listOf(collision)))
        val vm = viewModel(calendar)
        vm.fill()
        vm.save()
        val s = vm.state.value
        assertEquals(listOf(collision), s.overlaps)
        assertFalse(s.finished)
        assertFalse(s.isSaving)
        assertTrue("Ohne Bestätigung darf nichts geschrieben werden", calendar.created.isEmpty())
    }

    @Test
    fun overlap_savesOnlyAfterExplicitConfirmation() {
        val calendar = FakeCalendar(overlaps = Result.success(listOf(entry("x", "2026-10-15", "2026-10-20"))))
        val vm = viewModel(calendar)
        vm.fill()
        vm.save()
        assertTrue(calendar.created.isEmpty())
        vm.confirmOverlap()
        assertEquals(1, calendar.created.size)
        assertTrue(vm.state.value.finished)
        assertNull(vm.state.value.overlaps)
    }

    @Test
    fun overlap_backDismissesTheDialog_andSavesNothing() {
        val calendar = FakeCalendar(overlaps = Result.success(listOf(entry("x", "2026-10-15", "2026-10-20"))))
        val vm = viewModel(calendar)
        vm.fill()
        vm.save()
        vm.dismissOverlap()
        assertNull(vm.state.value.overlaps)
        assertFalse(vm.state.value.finished)
        assertTrue(calendar.created.isEmpty())
        // Das Formular ist unverändert; nach einer Korrektur wird erneut geprüft
        assertEquals("2026-10-10", vm.state.value.startDate)
        vm.save()
        assertNotNull(vm.state.value.overlaps)
        assertEquals(2, calendar.overlapQueries.size)
    }

    @Test
    fun confirmWithoutDialog_doesNothing() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.fill()
        vm.confirmOverlap()
        assertTrue(calendar.created.isEmpty())
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun failedOverlapCheck_savesNothing_andShowsTheError() {
        val calendar = FakeCalendar(overlaps = Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(calendar)
        vm.fill()
        vm.save()
        val s = vm.state.value
        assertEquals(AppError.NETWORK, s.saveError)
        assertNull(s.overlaps)
        assertFalse(s.finished)
        assertFalse(s.isSaving)
        assertTrue("Ohne Überschneidungsprüfung darf nicht gespeichert werden", calendar.created.isEmpty())
    }

    @Test
    fun failedWrite_showsTheError_andIsNotFinished() {
        val calendar = FakeCalendar(writeResult = Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(calendar)
        vm.fill()
        vm.save()
        assertEquals(AppError.NETWORK, vm.state.value.saveError)
        assertFalse(vm.state.value.finished)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun endBeforeStart_isRejected_withoutAnyServerCall() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.setStartDate("2026-10-10")
        vm.setEndDate("2026-10-09")
        vm.save()
        assertEquals(R.string.error_calendar_end_before_start, vm.state.value.endError)
        assertTrue(calendar.overlapQueries.isEmpty())
        assertTrue(calendar.created.isEmpty())
    }

    @Test
    fun movingTheStartBehindTheEnd_movesTheEndAlong() {
        val vm = viewModel(FakeCalendar())
        vm.fill("2026-10-10", "2026-10-12")
        vm.setStartDate("2026-10-20")
        assertEquals("2026-10-20", vm.state.value.endDate)
        vm.setStartDate("2026-10-05")
        assertEquals("2026-10-20", vm.state.value.endDate)
    }

    @Test
    fun missingPerson_isRejected() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.fill()
        vm.setPerson(null)
        vm.save()
        assertEquals(R.string.error_calendar_person_required, vm.state.value.personError)
        assertTrue(calendar.overlapQueries.isEmpty())
    }

    @Test
    fun tooLongDestination_isRejected() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.fill()
        vm.setDestination("x".repeat(101))
        vm.save()
        assertEquals(R.string.error_calendar_destination_too_long, vm.state.value.destinationError)
        assertTrue(calendar.overlapQueries.isEmpty())
    }

    @Test
    fun otherPerson_isStoredWithTheirName() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.fill()
        vm.setPerson("anna")
        vm.save()
        assertEquals("anna", calendar.created.single().personUid)
        assertEquals("Anna", calendar.created.single().personName)
    }

    @Test
    fun edit_loadsTheEntry_excludesItselfFromTheCheck_andUpdates() {
        val existing = entry("e1", "2026-10-10", "2026-10-18", destination = "Italien")
        val calendar = FakeCalendar(existing = existing)
        val vm = viewModel(calendar, entryId = "e1")
        val loaded = vm.state.value
        assertTrue(loaded.isEdit)
        assertEquals("2026-10-10", loaded.startDate)
        assertEquals("anna", loaded.personUid)
        assertEquals("Italien", loaded.destination)
        vm.setEndDate("2026-10-20")
        vm.save()
        assertEquals(listOf(Triple("2026-10-10", "2026-10-20", "e1")), calendar.overlapQueries)
        assertEquals("e1", calendar.updated.single().first)
        assertEquals("2026-10-20", calendar.updated.single().second.endDate)
        assertTrue(calendar.created.isEmpty())
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun edit_overlapNeedsConfirmationToo() {
        val existing = entry("e1", "2026-10-10", "2026-10-18")
        val calendar = FakeCalendar(existing = existing, overlaps = Result.success(listOf(entry("x", "2026-10-19", "2026-10-22"))))
        val vm = viewModel(calendar, entryId = "e1")
        vm.setEndDate("2026-10-20")
        vm.save()
        assertNotNull(vm.state.value.overlaps)
        assertTrue(calendar.updated.isEmpty())
        vm.confirmOverlap()
        assertEquals(1, calendar.updated.size)
    }

    @Test
    fun edit_keepsTheNameOfAPersonWhoseAccountWasRemoved() {
        val existing = entry("e1", "2026-10-10", "2026-10-18", name = "Robert", uid = "robert")
        val calendar = FakeCalendar(existing = existing)
        val vm = viewModel(calendar, entryId = "e1")
        vm.setComment("neu")
        vm.save()
        assertEquals("robert", calendar.updated.single().second.personUid)
        assertEquals("Robert", calendar.updated.single().second.personName)
    }

    @Test
    fun delete_removesTheEntry() {
        val calendar = FakeCalendar(existing = entry("e1", "2026-10-10", "2026-10-18"))
        val vm = viewModel(calendar, entryId = "e1")
        vm.delete()
        assertEquals(listOf("e1"), calendar.deleted)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun delete_onNewEntry_doesNothing() {
        val calendar = FakeCalendar()
        val vm = viewModel(calendar)
        vm.delete()
        assertTrue(calendar.deleted.isEmpty())
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun unknownEntry_isReportedAsNotFound() {
        val vm = viewModel(FakeCalendar(), entryId = "gibt-es-nicht")
        assertEquals(AppError.NOT_FOUND, vm.state.value.loadError)
    }
}
