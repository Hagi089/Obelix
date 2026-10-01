package de.hagi089.obelix.ui.documents

import android.net.Uri
import de.hagi089.obelix.R
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.documents.Document
import de.hagi089.obelix.data.documents.DocumentCategory
import de.hagi089.obelix.data.documents.DocumentInput
import de.hagi089.obelix.data.documents.DocumentRepository
import de.hagi089.obelix.data.files.FileReadException
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.LocalFileReader
import de.hagi089.obelix.data.files.NewFile
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ablauf des Dokument-Formulars: hochladen, bearbeiten, löschen (Fakes, ohne Android). */
class DocumentFormViewModelTest {

    private val today = LocalDate.of(2026, 10, 1)
    private val ref = FileRef("f1", "Vertrag.pdf", "application/pdf", 100)

    private fun existing() = Document("d1", "Vertrag", DocumentCategory.INSURANCE, ref, "2026-09-01", "anna")

    private fun pdf(name: String = "Versicherung 2026.pdf") = NewFile(name, "application/pdf", ByteArray(10) { 1 })

    private class Created(val input: DocumentInput, val date: String, val file: NewFile, val uid: String)

    private class FakeDocuments(
        var existing: Document? = null,
        var getResult: Result<Unit> = Result.success(Unit),
        var writeResult: Result<Unit> = Result.success(Unit),
    ) : DocumentRepository {
        val created = mutableListOf<Created>()
        val updated = mutableListOf<Pair<String, DocumentInput>>()
        val deleted = mutableListOf<String>()

        override suspend fun loadAll() = Result.success(emptyList<Document>())
        override suspend fun get(id: String): Result<Document?> = getResult.map { existing?.takeIf { it.id == id } }
        override suspend fun create(input: DocumentInput, date: String, file: NewFile, uid: String): Result<Unit> {
            if (writeResult.isSuccess) created += Created(input, date, file, uid)
            return writeResult
        }
        override suspend fun update(id: String, input: DocumentInput, uid: String): Result<Unit> {
            if (writeResult.isSuccess) updated += id to input
            return writeResult
        }
        override suspend fun delete(id: String): Result<Unit> {
            if (writeResult.isSuccess) deleted += id
            return writeResult
        }
    }

    private object UnusedReader : LocalFileReader {
        override suspend fun read(uri: Uri): Result<NewFile> = error("nicht benutzt")
        override suspend fun readPhoto(uri: Uri): Result<NewFile> = error("nicht benutzt")
    }

    private fun viewModel(documents: FakeDocuments, documentId: String? = null) = DocumentFormViewModel(
        documentId = documentId,
        uid = "tobias",
        documents = documents,
        fileReader = UnusedReader,
        clock = { today },
        scopeOverride = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun DocumentFormViewModel.attach(file: NewFile = pdf()) = attachFileFrom { Result.success(file) }

    @Test
    fun newDocument_startsEmpty() {
        val s = viewModel(FakeDocuments()).state.value
        assertFalse(s.isLoading)
        assertFalse(s.isEdit)
        assertNull(s.pendingFile)
        assertEquals("", s.name)
        assertNull(s.category)
    }

    @Test
    fun attachFile_suggestsNameFromFileName() {
        val vm = viewModel(FakeDocuments())
        vm.attach()
        val s = vm.state.value
        assertEquals("Versicherung 2026", s.name)
        assertEquals("Versicherung 2026.pdf", s.pendingFile?.name)
        assertFalse(s.isReadingFile)
    }

    @Test
    fun attachFile_keepsNameTheUserTyped() {
        val vm = viewModel(FakeDocuments())
        vm.setName("Mein Name")
        vm.attach()
        assertEquals("Mein Name", vm.state.value.name)
    }

    @Test
    fun attachSecondFile_replacesSuggestionButNotUserEdit() {
        val vm = viewModel(FakeDocuments())
        vm.attach(pdf("Erste.pdf"))
        vm.attach(pdf("Zweite.pdf"))
        assertEquals("Zweite", vm.state.value.name)
        vm.setName("Eigener")
        vm.attach(pdf("Dritte.pdf"))
        assertEquals("Eigener", vm.state.value.name)
    }

    @Test
    fun attachFile_failure_showsMessage_andKeepsNoFile() {
        val vm = viewModel(FakeDocuments())
        vm.attachFileFrom { Result.failure(FileReadException(R.string.error_file_too_large)) }
        val s = vm.state.value
        assertEquals(R.string.error_file_too_large, s.fileError)
        assertNull(s.pendingFile)
        assertFalse(s.isReadingFile)
    }

    @Test
    fun attachFile_unknownFailure_usesUnreadableMessage() {
        val vm = viewModel(FakeDocuments())
        vm.attachFileFrom { Result.failure(IllegalStateException()) }
        assertEquals(R.string.error_file_unreadable, vm.state.value.fileError)
    }

    @Test
    fun discardPendingFile_removesFile() {
        val vm = viewModel(FakeDocuments())
        vm.attach()
        vm.discardPendingFile()
        assertNull(vm.state.value.pendingFile)
    }

    @Test
    fun save_new_createsTrimmedInputWithTodayAndFile() {
        val documents = FakeDocuments()
        val vm = viewModel(documents)
        vm.attach()
        vm.setName("  Police  ")
        vm.setCategory(DocumentCategory.INSURANCE)
        vm.save()
        val c = documents.created.single()
        assertEquals(DocumentInput("Police", DocumentCategory.INSURANCE), c.input)
        assertEquals("2026-10-01", c.date)
        assertEquals("tobias", c.uid)
        assertEquals("Versicherung 2026.pdf", c.file.name)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun save_new_withoutAnything_showsAllErrors_andWritesNothing() {
        val documents = FakeDocuments()
        val vm = viewModel(documents)
        vm.save()
        val s = vm.state.value
        assertEquals(R.string.error_document_name_required, s.nameError)
        assertEquals(R.string.error_document_category_required, s.categoryError)
        assertEquals(R.string.error_document_file_required, s.fileError)
        assertTrue(documents.created.isEmpty())
        assertFalse(s.finished)
    }

    @Test
    fun save_new_withoutFile_isRejected() {
        val documents = FakeDocuments()
        val vm = viewModel(documents)
        vm.setName("Name")
        vm.setCategory(DocumentCategory.OTHER)
        vm.save()
        assertEquals(R.string.error_document_file_required, vm.state.value.fileError)
        assertTrue(documents.created.isEmpty())
    }

    @Test
    fun save_tooLongName_isRejected() {
        val documents = FakeDocuments()
        val vm = viewModel(documents)
        vm.attach()
        vm.setName("x".repeat(101))
        vm.setCategory(DocumentCategory.OTHER)
        vm.save()
        assertEquals(R.string.error_document_name_too_long, vm.state.value.nameError)
        assertTrue(documents.created.isEmpty())
    }

    @Test
    fun editingInput_clearsItsError() {
        val vm = viewModel(FakeDocuments())
        vm.save()
        vm.setName("Neu")
        vm.setCategory(DocumentCategory.OTHER)
        assertNull(vm.state.value.nameError)
        assertNull(vm.state.value.categoryError)
    }

    @Test
    fun edit_loadsExisting_andSaveUpdatesNameAndCategoryOnly() {
        val documents = FakeDocuments(existing = existing())
        val vm = viewModel(documents, "d1")
        val s = vm.state.value
        assertTrue(s.isEdit)
        assertEquals("Vertrag", s.name)
        assertEquals(DocumentCategory.INSURANCE, s.category)
        vm.setName("Neuer Name")
        vm.setCategory(DocumentCategory.WARRANTY)
        vm.save()
        assertEquals("d1" to DocumentInput("Neuer Name", DocumentCategory.WARRANTY), documents.updated.single())
        assertTrue(documents.created.isEmpty())
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun edit_ignoresNewFile_becauseFileIsImmutable() {
        val documents = FakeDocuments(existing = existing())
        val vm = viewModel(documents, "d1")
        vm.attach()
        assertNull(vm.state.value.pendingFile)
        assertEquals("Vertrag", vm.state.value.name)
    }

    @Test
    fun delete_removesExistingOnly() {
        val documents = FakeDocuments(existing = existing())
        val vm = viewModel(documents, "d1")
        vm.delete()
        assertEquals(listOf("d1"), documents.deleted)
        assertTrue(vm.state.value.finished)

        val fresh = FakeDocuments()
        viewModel(fresh).delete()
        assertTrue(fresh.deleted.isEmpty())
    }

    @Test
    fun writeFailure_showsError_keepsFormOpen() {
        val documents = FakeDocuments(writeResult = Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(documents)
        vm.attach()
        vm.setName("Name")
        vm.setCategory(DocumentCategory.OTHER)
        vm.save()
        val s = vm.state.value
        assertEquals(AppError.NETWORK, s.saveError)
        assertFalse(s.finished)
        assertFalse(s.isSaving)
        assertTrue(documents.created.isEmpty())
        assertEquals("Versicherung 2026.pdf", s.pendingFile?.name)
    }

    @Test
    fun unknownId_showsNotFound() {
        assertEquals(AppError.NOT_FOUND, viewModel(FakeDocuments(), "missing").state.value.loadError)
    }

    @Test
    fun loadFailure_showsError() {
        val vm = viewModel(FakeDocuments(getResult = Result.failure(AppException(AppError.NETWORK))), "d1")
        assertEquals(AppError.NETWORK, vm.state.value.loadError)
    }
}
