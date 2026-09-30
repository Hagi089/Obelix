package de.hagi089.obelix.ui.repairs

import de.hagi089.obelix.R
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.planned.Priority
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

/**
 * Ablauf des Formulars „Auffälligkeit“: anlegen, bearbeiten, erledigen, wieder öffnen, löschen. Die Fakes antworten
 * sofort, deshalb läuft alles ohne Android mit [Dispatchers.Unconfined] synchron.
 */
class RepairFormViewModelTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun repair(status: RepairStatus = RepairStatus.OPEN, priority: Priority? = Priority.HIGH) =
        Repair("r1", "Wasserhahn tropft", "Küche", "2026-09-20", status, priority, "Kommentar", "anna")

    private class FakeRepairs(
        var existing: Repair? = null,
        var getResult: Result<Unit> = Result.success(Unit),
        var writeResult: Result<Unit> = Result.success(Unit),
    ) : RepairRepository {
        val created = mutableListOf<RepairInput>()
        val updated = mutableListOf<Triple<String, RepairInput, RepairStatus>>()
        val deleted = mutableListOf<String>()

        override suspend fun loadAll() = Result.success(emptyList<Repair>())
        override suspend fun get(id: String): Result<Repair?> =
            getResult.map { existing?.takeIf { it.id == id } }
        override suspend fun create(input: RepairInput, uid: String): Result<Unit> {
            if (writeResult.isSuccess) created += input
            return writeResult
        }
        override suspend fun update(id: String, input: RepairInput, status: RepairStatus, uid: String): Result<Unit> {
            if (writeResult.isSuccess) updated += Triple(id, input, status)
            return writeResult
        }
        override suspend fun delete(id: String): Result<Unit> {
            if (writeResult.isSuccess) deleted += id
            return writeResult
        }
    }

    private fun viewModel(repairs: FakeRepairs, repairId: String? = null) = RepairFormViewModel(
        repairId = repairId,
        uid = "tobias",
        repairs = repairs,
        clock = { today },
        scopeOverride = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun RepairFormViewModel.fill() {
        setTitle("  Dichtung prüfen ")
        setDescription(" Fenster hinten ")
        setComment(" bald ")
    }

    @Test
    fun newRepair_startsWithTodayAndNoErrors() {
        val s = viewModel(FakeRepairs()).state.value
        assertFalse(s.isLoading)
        assertEquals("2026-10-01", s.date)
        assertFalse(s.isEdit)
        assertFalse(s.isDone)
    }

    @Test
    fun save_newRepair_createsTrimmedInput() {
        val repairs = FakeRepairs()
        val vm = viewModel(repairs)
        vm.fill()
        vm.setPriority(Priority.MEDIUM)
        vm.save()
        assertEquals(
            listOf(RepairInput("Dichtung prüfen", "Fenster hinten", "2026-10-01", Priority.MEDIUM, "bald")),
            repairs.created,
        )
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun save_withoutTitleOrDescription_showsErrorsAndWritesNothing() {
        val repairs = FakeRepairs()
        val vm = viewModel(repairs)
        vm.save()
        val s = vm.state.value
        assertEquals(R.string.error_repair_title_required, s.titleError)
        assertEquals(R.string.error_repair_description_required, s.descriptionError)
        assertTrue(repairs.created.isEmpty())
        assertFalse(s.finished)
    }

    @Test
    fun save_tooLongComment_showsError() {
        val repairs = FakeRepairs()
        val vm = viewModel(repairs)
        vm.fill()
        vm.setComment("x".repeat(501))
        vm.save()
        assertEquals(R.string.error_comment_too_long, vm.state.value.commentError)
        assertTrue(repairs.created.isEmpty())
    }

    @Test
    fun editingInput_clearsItsError() {
        val vm = viewModel(FakeRepairs())
        vm.save()
        vm.setTitle("Neu")
        assertNull(vm.state.value.titleError)
    }

    @Test
    fun edit_loadsExisting_andSaveKeepsStatus() {
        val repairs = FakeRepairs(existing = repair(RepairStatus.DONE))
        val vm = viewModel(repairs, "r1")
        val s = vm.state.value
        assertTrue(s.isEdit)
        assertTrue(s.isDone)
        assertEquals("Wasserhahn tropft", s.title)
        assertEquals(Priority.HIGH, s.priority)
        vm.setTitle("Wasserhahn repariert?")
        vm.save()
        assertEquals(1, repairs.updated.size)
        assertEquals(RepairStatus.DONE, repairs.updated.single().third)
        assertEquals("Wasserhahn repariert?", repairs.updated.single().second.title)
        assertTrue(repairs.created.isEmpty())
    }

    @Test
    fun markDone_writesStatusDone_withCurrentInput() {
        val repairs = FakeRepairs(existing = repair(RepairStatus.OPEN))
        val vm = viewModel(repairs, "r1")
        vm.markDone()
        val (id, input, status) = repairs.updated.single()
        assertEquals("r1", id)
        assertEquals(RepairStatus.DONE, status)
        assertEquals("Wasserhahn tropft", input.title)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun reopen_writesStatusOpen() {
        val repairs = FakeRepairs(existing = repair(RepairStatus.DONE))
        val vm = viewModel(repairs, "r1")
        vm.reopen()
        assertEquals(RepairStatus.OPEN, repairs.updated.single().third)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun markDone_withInvalidInput_writesNothing() {
        val repairs = FakeRepairs(existing = repair())
        val vm = viewModel(repairs, "r1")
        vm.setTitle("")
        vm.markDone()
        assertTrue(repairs.updated.isEmpty())
        assertEquals(R.string.error_repair_title_required, vm.state.value.titleError)
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun markDoneAndReopen_doNothingForNewRepair() {
        val repairs = FakeRepairs()
        val vm = viewModel(repairs)
        vm.fill()
        vm.markDone()
        vm.reopen()
        assertTrue(repairs.created.isEmpty())
        assertTrue(repairs.updated.isEmpty())
    }

    @Test
    fun priority_canBeRemovedAgain() {
        val repairs = FakeRepairs(existing = repair(priority = Priority.HIGH))
        val vm = viewModel(repairs, "r1")
        vm.setPriority(null)
        vm.save()
        assertNull(repairs.updated.single().second.priority)
    }

    @Test
    fun delete_removesExistingOnly() {
        val repairs = FakeRepairs(existing = repair())
        val vm = viewModel(repairs, "r1")
        vm.delete()
        assertEquals(listOf("r1"), repairs.deleted)
        assertTrue(vm.state.value.finished)

        val fresh = FakeRepairs()
        viewModel(fresh).delete()
        assertTrue(fresh.deleted.isEmpty())
    }

    @Test
    fun writeFailure_showsError_keepsFormOpen_andNothingIsSaved() {
        val repairs = FakeRepairs(writeResult = Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(repairs)
        vm.fill()
        vm.save()
        val s = vm.state.value
        assertEquals(AppError.NETWORK, s.saveError)
        assertFalse(s.finished)
        assertFalse(s.isSaving)
        assertTrue(repairs.created.isEmpty())
    }

    @Test
    fun unknownId_showsNotFound() {
        val vm = viewModel(FakeRepairs(existing = null), "missing")
        assertEquals(AppError.NOT_FOUND, vm.state.value.loadError)
    }

    @Test
    fun loadFailure_showsError() {
        val vm = viewModel(FakeRepairs(getResult = Result.failure(AppException(AppError.NETWORK))), "r1")
        assertEquals(AppError.NETWORK, vm.state.value.loadError)
    }
}
