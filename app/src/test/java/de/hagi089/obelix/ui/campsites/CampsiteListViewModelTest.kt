package de.hagi089.obelix.ui.campsites

import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.CampsiteInput
import de.hagi089.obelix.data.campsites.CampsiteRepository
import de.hagi089.obelix.data.campsites.GeoPosition
import de.hagi089.obelix.data.campsites.LocationException
import de.hagi089.obelix.data.campsites.LocationProblem
import de.hagi089.obelix.data.campsites.LocationProvider
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile
import de.hagi089.obelix.data.user.Role
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Liste, Umschalter Liste/Karte und „Aktuellen Standort speichern“ (mit Fakes, ohne Android). */
class CampsiteListViewModelTest {

    private fun campsite(id: String, date: String, by: String = "anna") =
        Campsite(id, 48.0, 11.0, date, "Kommentar", null, null, null, null, emptyList(), by)

    private class FakeCampsites(var result: Result<List<Campsite>>) : CampsiteRepository {
        var loads = 0
        override suspend fun loadAll(): Result<List<Campsite>> { loads++; return result }
        override suspend fun get(id: String) = Result.success<Campsite?>(null)
        override suspend fun create(position: GeoPosition, date: String, input: CampsiteInput, photos: List<NewFile>, uid: String) = Result.success(Unit)
        override suspend fun update(id: String, input: CampsiteInput, removedPhotoIds: Set<String>, addedPhotos: List<NewFile>, uid: String) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
        override suspend fun loadPhoto(ref: FileRef) = Result.success(ByteArray(0))
    }

    private class FakeUsers(var result: Result<List<UserProfile>>) : UserRepository {
        override suspend fun loadProfile(uid: String) = Result.success<UserProfile?>(null)
        override suspend fun register(uid: String, displayName: String, accessCode: String) = Result.success(Unit)
        override suspend fun loadUsers() = result
        override suspend fun setRole(uid: String, role: Role) = Result.success(Unit)
        override suspend fun removeUser(uid: String) = Result.success(Unit)
        override suspend fun loadAccessCode() = Result.success<String?>(null)
        override suspend fun renewAccessCode(adminUid: String) = Result.success("")
    }

    private class FakeLocation(var result: Result<GeoPosition>) : LocationProvider {
        var requests = 0
        override suspend fun currentLocation(): Result<GeoPosition> { requests++; return result }
    }

    private val anna = UserProfile("anna", "Anna", Role.MEMBER)

    private fun viewModel(
        campsites: FakeCampsites = FakeCampsites(Result.success(emptyList())),
        users: FakeUsers = FakeUsers(Result.success(listOf(anna))),
        location: FakeLocation = FakeLocation(Result.success(GeoPosition(48.1, 11.5, 10f))),
    ) = CampsiteListViewModel(campsites, users, location, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun refresh_loadsCampsites_andUsers() {
        val vm = viewModel(FakeCampsites(Result.success(listOf(campsite("a", "2026-09-01"), campsite("b", "2026-10-01")))))
        vm.refresh()
        val s = vm.state.value
        assertTrue(s.hasLoaded)
        assertFalse(s.isLoading)
        assertEquals(2, s.campsites.size)
        assertEquals("Anna", s.userName("anna"))
    }

    @Test
    fun refresh_empty_isLoadedNotAnError() {
        val vm = viewModel()
        vm.refresh()
        assertTrue(vm.state.value.hasLoaded)
        assertTrue(vm.state.value.campsites.isEmpty())
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun refresh_failure_isAnError_notAnEmptyList() {
        val vm = viewModel(FakeCampsites(Result.failure(AppException(AppError.NETWORK))))
        vm.refresh()
        assertFalse(vm.state.value.hasLoaded)
        assertEquals(AppError.NETWORK, vm.state.value.error)
    }

    @Test
    fun refresh_failureAfterSuccess_keepsEarlierData() {
        val campsites = FakeCampsites(Result.success(listOf(campsite("a", "2026-09-01"))))
        val vm = viewModel(campsites)
        vm.refresh()
        campsites.result = Result.failure(AppException(AppError.NETWORK))
        vm.refresh()
        assertEquals(1, vm.state.value.campsites.size)
        assertEquals(AppError.NETWORK, vm.state.value.error)
        assertTrue(vm.state.value.hasLoaded)
    }

    @Test
    fun refresh_userLoadFailure_isAnError() {
        val vm = viewModel(users = FakeUsers(Result.failure(AppException(AppError.UNAVAILABLE))))
        vm.refresh()
        assertEquals(AppError.UNAVAILABLE, vm.state.value.error)
    }

    @Test
    fun mode_switchesBetweenListAndMap() {
        val vm = viewModel()
        assertEquals(CampsiteMode.LIST, vm.state.value.mode)
        vm.setMode(CampsiteMode.MAP)
        assertEquals(CampsiteMode.MAP, vm.state.value.mode)
        vm.setMode(CampsiteMode.LIST)
        assertEquals(CampsiteMode.LIST, vm.state.value.mode)
    }

    @Test
    fun locate_success_foundThenConsumed() {
        val location = FakeLocation(Result.success(GeoPosition(48.1, 11.5, 10f)))
        val vm = viewModel(location = location)
        vm.locate()
        assertEquals(LocateState.Found(GeoPosition(48.1, 11.5, 10f)), vm.state.value.locate)
        vm.consumeLocation()
        assertEquals(LocateState.Idle, vm.state.value.locate)
        assertEquals(1, location.requests)
    }

    @Test
    fun locate_problems_areReported() {
        for (problem in LocationProblem.entries) {
            val vm = viewModel(location = FakeLocation(Result.failure(LocationException(problem))))
            vm.locate()
            assertEquals(LocateState.Failed(problem), vm.state.value.locate)
        }
    }

    @Test
    fun locate_unknownFailure_isUnavailable() {
        val vm = viewModel(location = FakeLocation(Result.failure(IllegalStateException("x"))))
        vm.locate()
        assertEquals(LocateState.Failed(LocationProblem.UNAVAILABLE), vm.state.value.locate)
    }

    @Test
    fun permissionDenied_showsTheProblem_andNoPositionIsRequested() {
        val location = FakeLocation(Result.success(GeoPosition(1.0, 1.0)))
        val vm = viewModel(location = location)
        vm.permissionDenied()
        assertEquals(LocateState.Failed(LocationProblem.PERMISSION_DENIED), vm.state.value.locate)
        assertEquals(0, location.requests)
        vm.dismissLocationProblem()
        assertEquals(LocateState.Idle, vm.state.value.locate)
    }

    @Test
    fun consume_andDismiss_doNotTouchOtherStates() {
        val vm = viewModel(location = FakeLocation(Result.failure(LocationException(LocationProblem.UNAVAILABLE))))
        vm.locate()
        vm.consumeLocation() // nur Found wird zurückgesetzt
        assertEquals(LocateState.Failed(LocationProblem.UNAVAILABLE), vm.state.value.locate)
        vm.dismissLocationProblem()
        assertEquals(LocateState.Idle, vm.state.value.locate)
    }

    @Test
    fun locate_canBeRepeatedAfterAFailure() {
        val location = FakeLocation(Result.failure(LocationException(LocationProblem.UNAVAILABLE)))
        val vm = viewModel(location = location)
        vm.locate()
        location.result = Result.success(GeoPosition(48.0, 11.0))
        vm.locate()
        assertTrue(vm.state.value.locate is LocateState.Found)
        assertEquals(2, location.requests)
    }
}
