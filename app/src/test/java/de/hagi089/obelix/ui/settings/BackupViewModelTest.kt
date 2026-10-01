package de.hagi089.obelix.ui.settings

import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.backup.BackupProgress
import de.hagi089.obelix.data.backup.BackupRepository
import de.hagi089.obelix.data.backup.BackupStep
import de.hagi089.obelix.data.backup.BackupSummary
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupViewModelTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val complete = BackupSummary(mapOf("transactions" to 3), 2, emptyList())

    private class FakeRepository(var result: Result<BackupSummary>) : BackupRepository {
        var calls = 0
        var stream: OutputStream? = null
        var createdAt: Instant? = null
        var reportProgress: BackupProgress? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun write(out: OutputStream, createdAt: Instant, onProgress: (BackupProgress) -> Unit): Result<BackupSummary> {
            calls++
            stream = out
            this.createdAt = createdAt
            reportProgress?.let(onProgress)
            gate?.await()
            return result
        }
    }

    private fun viewModel(repo: FakeRepository) = BackupViewModel(
        repository = repo,
        clock = { now },
        ioDispatcher = Dispatchers.Unconfined,
        scopeOverride = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun initially_nothingHappened() {
        val s = viewModel(FakeRepository(Result.success(complete))).state.value
        assertFalse(s.isRunning)
        assertNull(s.result)
        assertNull(s.error)
        assertNull(s.progress)
    }

    @Test
    fun success_showsTheResult_andPassesTheChosenStreamAndTime() {
        val repo = FakeRepository(Result.success(complete))
        val vm = viewModel(repo)
        val target = ByteArrayOutputStream()
        vm.start { target }
        val s = vm.state.value
        assertEquals(complete, s.result)
        assertFalse(s.isRunning)
        assertNull(s.error)
        assertSame(target, repo.stream)
        assertEquals(now, repo.createdAt)
    }

    @Test
    fun anIncompleteBackup_isNotPresentedAsSuccess() {
        val incomplete = BackupSummary(mapOf("transactions" to 3), 1, listOf("Beleg.pdf (f1)"))
        val vm = viewModel(FakeRepository(Result.success(incomplete)))
        vm.start { ByteArrayOutputStream() }
        assertFalse(vm.state.value.result!!.isComplete)
        assertNull(vm.state.value.error)
    }

    @Test
    fun failure_showsTheMappedError_andNoResult() {
        val vm = viewModel(FakeRepository(Result.failure(AppException(AppError.NETWORK))))
        vm.start { ByteArrayOutputStream() }
        assertEquals(AppError.NETWORK, vm.state.value.error)
        assertNull(vm.state.value.result)
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun failureWithoutMapping_becomesUnknown() {
        val vm = viewModel(FakeRepository(Result.failure(IllegalStateException("x"))))
        vm.start { ByteArrayOutputStream() }
        assertEquals(AppError.UNKNOWN, vm.state.value.error)
    }

    @Test
    fun targetCannotBeOpened_isAStorageError_andNothingIsRead() {
        val repo = FakeRepository(Result.success(complete))
        val vm = viewModel(repo)
        vm.start { null }
        assertEquals(AppError.STORAGE, vm.state.value.error)
        assertEquals(0, repo.calls)
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun targetThrowsWhileOpening_isAStorageError() {
        val repo = FakeRepository(Result.success(complete))
        val vm = viewModel(repo)
        vm.start { throw SecurityException("kein Zugriff") }
        assertEquals(AppError.STORAGE, vm.state.value.error)
        assertEquals(0, repo.calls)
    }

    @Test
    fun whileRunning_aSecondStartIsIgnored_andProgressIsVisible() {
        val repo = FakeRepository(Result.success(complete)).apply {
            gate = CompletableDeferred()
            reportProgress = BackupProgress(BackupStep.SAVE_FILES, 1, 4)
        }
        val vm = viewModel(repo)
        vm.start { ByteArrayOutputStream() }
        assertTrue(vm.state.value.isRunning)
        assertEquals(BackupProgress(BackupStep.SAVE_FILES, 1, 4), vm.state.value.progress)

        vm.start { ByteArrayOutputStream() }
        assertEquals(1, repo.calls)

        repo.gate!!.complete(Unit)
        assertFalse(vm.state.value.isRunning)
        assertEquals(complete, vm.state.value.result)
    }

    @Test
    fun aNewStart_clearsTheEarlierError() {
        val repo = FakeRepository(Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(repo)
        vm.start { ByteArrayOutputStream() }
        assertEquals(AppError.NETWORK, vm.state.value.error)

        repo.result = Result.success(complete)
        vm.start { ByteArrayOutputStream() }
        assertNull(vm.state.value.error)
        assertEquals(complete, vm.state.value.result)
    }
}
