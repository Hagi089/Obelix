package de.hagi089.obelix.ui.documents

import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.documents.Document
import de.hagi089.obelix.data.documents.DocumentCategory
import de.hagi089.obelix.data.documents.DocumentInput
import de.hagi089.obelix.data.documents.DocumentRepository
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile
import de.hagi089.obelix.data.user.Role
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentListViewModelTest {

    private val file = FileRef("f", "a.pdf", "application/pdf", 10)

    private fun doc(id: String, category: DocumentCategory, by: String = "anna") =
        Document(id, "Dok $id", category, file, "2026-10-01", by)

    private class FakeDocuments(var result: Result<List<Document>>) : DocumentRepository {
        var loads = 0
        override suspend fun loadAll(): Result<List<Document>> { loads++; return result }
        override suspend fun get(id: String) = Result.success<Document?>(null)
        override suspend fun create(input: DocumentInput, date: String, file: NewFile, uid: String) = Result.success(Unit)
        override suspend fun update(id: String, input: DocumentInput, uid: String) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
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

    private val anna = UserProfile("anna", "Anna", Role.MEMBER)

    private fun viewModel(
        documents: FakeDocuments = FakeDocuments(Result.success(emptyList())),
        users: FakeUsers = FakeUsers(Result.success(listOf(anna))),
    ) = DocumentListViewModel(documents, users, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun refresh_loadsDocumentsAndUsers() {
        val vm = viewModel(FakeDocuments(Result.success(listOf(doc("1", DocumentCategory.MANUAL), doc("2", DocumentCategory.OTHER)))))
        vm.refresh()
        val s = vm.state.value
        assertTrue(s.hasLoaded)
        assertFalse(s.isLoading)
        assertEquals(2, s.documents.size)
        assertEquals("Anna", s.userName("anna"))
        assertNull(s.userName("unbekannt"))
        assertNull(s.userName(null))
    }

    @Test
    fun categoryFilter_limitsVisible_andNullShowsAll() {
        val vm = viewModel(FakeDocuments(Result.success(listOf(doc("1", DocumentCategory.MANUAL), doc("2", DocumentCategory.OTHER)))))
        vm.refresh()
        vm.setCategory(DocumentCategory.MANUAL)
        assertEquals(listOf("1"), vm.state.value.visible.map { it.id })
        vm.setCategory(null)
        assertEquals(2, vm.state.value.visible.size)
    }

    @Test
    fun loadFailure_showsError_notEmptyList_andKeepsOldData() {
        val documents = FakeDocuments(Result.success(listOf(doc("1", DocumentCategory.MANUAL))))
        val vm = viewModel(documents)
        vm.refresh()
        documents.result = Result.failure(AppException(AppError.NETWORK))
        vm.refresh()
        val s = vm.state.value
        assertEquals(AppError.NETWORK, s.error)
        assertEquals(1, s.documents.size)
        assertTrue(s.hasLoaded)
        assertFalse(s.isLoading)
    }

    @Test
    fun userLoadFailure_isAlsoAnError_andNeverMarksLoaded() {
        val vm = viewModel(users = FakeUsers(Result.failure(AppException(AppError.NETWORK))))
        vm.refresh()
        assertEquals(AppError.NETWORK, vm.state.value.error)
        assertFalse(vm.state.value.hasLoaded)
    }

    @Test
    fun refresh_afterError_recovers() {
        val documents = FakeDocuments(Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(documents)
        vm.refresh()
        documents.result = Result.success(listOf(doc("1", DocumentCategory.OTHER)))
        vm.refresh()
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.hasLoaded)
        assertEquals(2, documents.loads)
    }
}
