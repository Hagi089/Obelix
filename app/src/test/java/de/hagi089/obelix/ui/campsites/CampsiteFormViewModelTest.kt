package de.hagi089.obelix.ui.campsites

import de.hagi089.obelix.R
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.CampsiteInput
import de.hagi089.obelix.data.campsites.CampsiteRepository
import de.hagi089.obelix.data.campsites.GeoPosition
import de.hagi089.obelix.data.files.FileReadException
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.LocalFileReader
import de.hagi089.obelix.data.files.NewFile
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
 * Ablauf des Formulars „Stellplatz“ (mit Fakes, ohne Android): anlegen mit Position, bis zu drei Fotos, bearbeiten,
 * einzelne Fotos entfernen, löschen. Die Fakes antworten sofort, deshalb läuft alles mit [Dispatchers.Unconfined] synchron.
 */
class CampsiteFormViewModelTest {

    private val today = LocalDate.of(2026, 10, 1)
    private val position = GeoPosition(48.137154, 11.576124, 8.0f)

    private fun file(name: String = "foto.jpg") = NewFile(name, "image/jpeg", ByteArray(100))
    private fun ref(id: String) = FileRef(id, "$id.jpg", "image/jpeg", 100)

    private fun campsite(photos: List<FileRef> = emptyList()) = Campsite(
        id = "s1", latitude = 47.5, longitude = 10.5, date = "2026-09-20", comment = "Am See",
        name = "Seeblick", address = "Seestraße 1", note = "Zufahrt schmal", rating = 4, photos = photos, createdBy = "anna",
    )

    private class Created(val position: GeoPosition, val date: String, val input: CampsiteInput, val photos: List<NewFile>)
    private class Updated(val id: String, val input: CampsiteInput, val removed: Set<String>, val added: List<NewFile>)

    private class FakeCampsites(
        var existing: Campsite? = null,
        var getResult: Result<Unit> = Result.success(Unit),
        var writeResult: Result<Unit> = Result.success(Unit),
    ) : CampsiteRepository {
        val created = mutableListOf<Created>()
        val updated = mutableListOf<Updated>()
        val deleted = mutableListOf<String>()
        var photoResult: Result<ByteArray> = Result.success(ByteArray(3))

        override suspend fun loadAll() = Result.success(emptyList<Campsite>())
        override suspend fun get(id: String): Result<Campsite?> = getResult.map { existing?.takeIf { it.id == id } }
        override suspend fun create(position: GeoPosition, date: String, input: CampsiteInput, photos: List<NewFile>, uid: String): Result<Unit> {
            if (writeResult.isSuccess) created += Created(position, date, input, photos)
            return writeResult
        }
        override suspend fun update(id: String, input: CampsiteInput, removedPhotoIds: Set<String>, addedPhotos: List<NewFile>, uid: String): Result<Unit> {
            if (writeResult.isSuccess) updated += Updated(id, input, removedPhotoIds, addedPhotos)
            return writeResult
        }
        override suspend fun delete(id: String): Result<Unit> {
            if (writeResult.isSuccess) deleted += id
            return writeResult
        }
        override suspend fun loadPhoto(ref: FileRef): Result<ByteArray> = photoResult
    }

    /** Wird nie benutzt: Die Tests geben die Fotos über addPhotoFrom, ohne android.net.Uri, hinein. */
    private object UnusedReader : LocalFileReader {
        override suspend fun read(uri: android.net.Uri): Result<NewFile> = error("nicht benutzt")
        override suspend fun readPhoto(uri: android.net.Uri): Result<NewFile> = error("nicht benutzt")
    }

    private fun viewModel(repo: FakeCampsites, campsiteId: String? = null, initial: GeoPosition? = position) = CampsiteFormViewModel(
        campsiteId = campsiteId,
        initialPosition = initial,
        uid = "tobias",
        campsites = repo,
        fileReader = UnusedReader,
        clock = { today },
        scopeOverride = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun CampsiteFormViewModel.addPhotos(count: Int) = repeat(count) { addPhotoFrom { Result.success(file("f$it.jpg")) } }

    @Test
    fun newCampsite_startsWithPositionAndToday_noErrors() {
        val s = viewModel(FakeCampsites()).state.value
        assertFalse(s.isLoading)
        assertFalse(s.isEdit)
        assertEquals(position, s.position)
        assertEquals("2026-10-01", s.date)
        assertNull(s.loadError)
        assertTrue(s.canAddPhoto)
    }

    @Test
    fun newCampsite_withoutPosition_isAnErrorAndCannotBeSaved() {
        val repo = FakeCampsites()
        val vm = viewModel(repo, initial = null)
        assertNotNull(vm.state.value.loadError)
        vm.setComment("Kommentar")
        vm.save()
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun save_withoutComment_showsError_andWritesNothing() {
        val repo = FakeCampsites()
        val vm = viewModel(repo)
        vm.save()
        assertEquals(R.string.error_campsite_comment_required, vm.state.value.commentError)
        assertTrue(repo.created.isEmpty())
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun save_tooLongTexts_showErrors() {
        val repo = FakeCampsites()
        val vm = viewModel(repo)
        vm.setComment("x".repeat(501))
        vm.setName("x".repeat(101))
        vm.setAddress("x".repeat(201))
        vm.setNote("x".repeat(501))
        vm.save()
        val s = vm.state.value
        assertEquals(R.string.error_comment_too_long, s.commentError)
        assertEquals(R.string.error_campsite_name_too_long, s.nameError)
        assertEquals(R.string.error_campsite_address_too_long, s.addressError)
        assertEquals(R.string.error_campsite_note_too_long, s.noteError)
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun editingInput_clearsItsError() {
        val vm = viewModel(FakeCampsites())
        vm.save()
        vm.setComment("Ruhig")
        assertNull(vm.state.value.commentError)
    }

    @Test
    fun save_new_createsWithPosition_date_trimmedInput_andNoBlankOptionals() {
        val repo = FakeCampsites()
        val vm = viewModel(repo)
        vm.setComment("  Ruhig, am See ")
        vm.setName("   ")
        vm.setAddress(" Seestraße 1 ")
        vm.setRating(5)
        vm.save()
        val created = repo.created.single()
        assertEquals(position, created.position)
        assertEquals("2026-10-01", created.date)
        assertEquals(CampsiteInput("Ruhig, am See", null, "Seestraße 1", null, 5), created.input)
        assertTrue(created.photos.isEmpty())
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun photos_limitIsThree_fourthIsRejectedWithMessage() {
        val vm = viewModel(FakeCampsites())
        vm.addPhotos(3)
        assertEquals(3, vm.state.value.photos.count)
        assertFalse(vm.state.value.canAddPhoto)
        vm.addPhotoFrom { Result.success(file("vier.jpg")) }
        assertEquals(3, vm.state.value.photos.count)
        assertEquals(R.string.error_photo_limit, vm.state.value.photoError)
    }

    @Test
    fun save_withThreePhotos_passesAllThree() {
        val repo = FakeCampsites()
        val vm = viewModel(repo)
        vm.setComment("Mit Fotos")
        vm.addPhotos(3)
        vm.save()
        assertEquals(listOf("f0.jpg", "f1.jpg", "f2.jpg"), repo.created.single().photos.map { it.name })
    }

    @Test
    fun removingANewPhoto_freesASlot() {
        val vm = viewModel(FakeCampsites())
        vm.addPhotos(3)
        vm.removeNewPhoto(1)
        assertEquals(listOf("f0.jpg", "f2.jpg"), vm.state.value.photos.added.map { it.name })
        assertTrue(vm.state.value.canAddPhoto)
    }

    @Test
    fun photoReadFailure_showsItsMessage_andAddsNothing() {
        val vm = viewModel(FakeCampsites())
        vm.addPhotoFrom { Result.failure(FileReadException(R.string.error_photo_too_large)) }
        assertEquals(R.string.error_photo_too_large, vm.state.value.photoError)
        assertEquals(0, vm.state.value.photos.count)
        assertFalse(vm.state.value.isReadingPhoto)
        vm.addPhotoFrom { Result.failure(IllegalStateException("unbekannt")) }
        assertEquals(R.string.error_file_unreadable, vm.state.value.photoError)
    }

    @Test
    fun successfulPhoto_clearsEarlierPhotoError() {
        val vm = viewModel(FakeCampsites())
        vm.addPhotoFrom { Result.failure(FileReadException(R.string.error_photo_type)) }
        assertNotNull(vm.state.value.photoError)
        vm.addPhotoFrom { Result.success(file()) }
        assertNull(vm.state.value.photoError)
        assertEquals(1, vm.state.value.photos.count)
    }

    @Test
    fun saveFailure_keepsFormOpen_withTheError_andKeepsThePhotos() {
        val repo = FakeCampsites(writeResult = Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(repo)
        vm.setComment("Kommentar")
        vm.addPhotos(2)
        vm.save()
        val s = vm.state.value
        assertEquals(AppError.NETWORK, s.saveError)
        assertFalse(s.finished)
        assertFalse(s.isSaving)
        assertEquals(2, s.photos.count)
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun edit_loadsExisting_includingPhotos_andPosition() {
        val repo = FakeCampsites(existing = campsite(listOf(ref("a"), ref("b"))))
        val vm = viewModel(repo, "s1", initial = null)
        val s = vm.state.value
        assertTrue(s.isEdit)
        assertEquals(GeoPosition(47.5, 10.5), s.position) // gespeicherte Position, nicht die Route
        assertEquals("Am See", s.comment)
        assertEquals("Seeblick", s.name)
        assertEquals(4, s.rating)
        assertEquals(2, s.photos.count)
        assertTrue(s.canAddPhoto)
    }

    @Test
    fun edit_save_passesRemovedAndAddedPhotos_andClearedOptionalFields() {
        val repo = FakeCampsites(existing = campsite(listOf(ref("a"), ref("b"), ref("c"))))
        val vm = viewModel(repo, "s1", initial = null)
        assertFalse(vm.state.value.canAddPhoto)
        vm.removeSavedPhoto("b")
        assertTrue(vm.state.value.canAddPhoto)
        vm.addPhotoFrom { Result.success(file("neu.jpg")) }
        vm.setName("")
        vm.setRating(null)
        vm.save()
        val updated = repo.updated.single()
        assertEquals("s1", updated.id)
        assertEquals(setOf("b"), updated.removed)
        assertEquals(listOf("neu.jpg"), updated.added.map { it.name })
        assertNull(updated.input.name)
        assertNull(updated.input.rating)
        assertEquals("Seestraße 1", updated.input.address)
        assertTrue(repo.created.isEmpty())
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun edit_removeSavedPhoto_isNotASaveUntilSaved() {
        val repo = FakeCampsites(existing = campsite(listOf(ref("a"))))
        val vm = viewModel(repo, "s1", initial = null)
        vm.removeSavedPhoto("a")
        assertEquals(0, vm.state.value.photos.count)
        assertTrue(repo.updated.isEmpty()) // erst „Speichern“ überträgt
    }

    @Test
    fun edit_unknownCampsite_andLoadError() {
        val unknown = viewModel(FakeCampsites(existing = null), "s1", initial = null)
        assertEquals(AppError.NOT_FOUND, unknown.state.value.loadError)
        val broken = viewModel(
            FakeCampsites(existing = campsite(), getResult = Result.failure(AppException(AppError.NETWORK))),
            "s1",
            initial = null,
        )
        assertEquals(AppError.NETWORK, broken.state.value.loadError)
    }

    @Test
    fun delete_removesTheCampsite_andFinishes() {
        val repo = FakeCampsites(existing = campsite(listOf(ref("a"))))
        val vm = viewModel(repo, "s1", initial = null)
        vm.delete()
        assertEquals(listOf("s1"), repo.deleted)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun delete_onNewCampsite_doesNothing() {
        val repo = FakeCampsites()
        val vm = viewModel(repo)
        vm.delete()
        assertTrue(repo.deleted.isEmpty())
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun delete_failure_keepsFormOpen_withError() {
        val repo = FakeCampsites(existing = campsite(), writeResult = Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(repo, "s1", initial = null)
        vm.delete()
        assertEquals(AppError.NETWORK, vm.state.value.saveError)
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun rating_onlyOneToFiveOrNone() {
        val vm = viewModel(FakeCampsites())
        vm.setRating(3)
        assertEquals(3, vm.state.value.rating)
        vm.setRating(9)
        assertEquals(3, vm.state.value.rating) // ungültig: bleibt
        vm.setRating(0)
        assertEquals(3, vm.state.value.rating)
        vm.setRating(null)
        assertNull(vm.state.value.rating)
    }
}
