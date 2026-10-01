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
import kotlinx.coroutines.CompletableDeferred
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
        /** Gespeicherte Positionskorrekturen (Stellplatz, Position, Benutzer). */
        val positions = mutableListOf<Triple<String, GeoPosition, String>>()
        var positionResult: Result<Unit> = Result.success(Unit)
        /** Wenn gesetzt, wartet [updatePosition], bis der Test sie freigibt (so lässt sich der Zustand „wird gespeichert“ prüfen). */
        var positionGate: CompletableDeferred<Unit>? = null
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
        override suspend fun updatePosition(id: String, position: GeoPosition, uid: String): Result<Unit> {
            positionGate?.await()
            if (positionResult.isSuccess) positions += Triple(id, position, uid)
            return positionResult
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

    // ---- Marker verschieben: eine zentrale Position (Anzeige, Karte, Navigation lesen state.position) ----

    @Test
    fun moveMarker_onNewCampsite_changesThePosition_dropsAccuracy_andWritesNothingYet() {
        val repo = FakeCampsites()
        val vm = viewModel(repo)
        vm.movePosition(48.2, 11.7)
        val s = vm.state.value
        assertEquals(GeoPosition(48.2, 11.7), s.position) // ohne Messgenauigkeit: von Hand gesetzt
        assertNull(s.position?.accuracyMeters)
        assertTrue(repo.positions.isEmpty()) // es gibt noch kein Dokument
        assertFalse(s.isMovingPosition)
        // „Speichern“ legt den Stellplatz mit der korrigierten Position an
        vm.setComment("Kommentar")
        vm.save()
        assertEquals(GeoPosition(48.2, 11.7), repo.created.single().position)
    }

    @Test
    fun moveMarker_onExistingCampsite_savesImmediately_andKeepsTheOtherInput() {
        val repo = FakeCampsites(existing = campsite())
        val vm = viewModel(repo, "s1", initial = null)
        vm.setComment("Noch nicht gespeichert")
        vm.movePosition(47.6, 10.6)
        val s = vm.state.value
        assertEquals(GeoPosition(47.6, 10.6), s.position)
        assertEquals(GeoPosition(47.6, 10.6), s.existing?.position) // dieselbe Position im gespeicherten Stand
        assertEquals(Triple("s1", GeoPosition(47.6, 10.6), "tobias"), repo.positions.single())
        assertFalse(s.isMovingPosition)
        assertNull(s.positionError)
        assertEquals("Noch nicht gespeichert", s.comment) // Eingaben im Formular bleiben
        assertFalse(s.finished) // das Formular bleibt offen
        // Danach „Speichern“ schreibt die Position nicht noch einmal
        vm.save()
        assertEquals(1, repo.positions.size)
        assertEquals("Noch nicht gespeichert", repo.updated.single().input.comment)
    }

    @Test
    fun moveMarker_twice_savesTheLastPosition() {
        val repo = FakeCampsites(existing = campsite())
        val vm = viewModel(repo, "s1", initial = null)
        vm.movePosition(47.6, 10.6)
        vm.movePosition(47.7, 10.7)
        assertEquals(listOf(GeoPosition(47.6, 10.6), GeoPosition(47.7, 10.7)), repo.positions.map { it.second })
        assertEquals(GeoPosition(47.7, 10.7), vm.state.value.position)
    }

    @Test
    fun moveMarker_saveFailure_jumpsBackToTheSavedPosition_andReportsTheError() {
        val repo = FakeCampsites(existing = campsite())
        repo.positionResult = Result.failure(AppException(AppError.NETWORK))
        val vm = viewModel(repo, "s1", initial = null)
        vm.movePosition(47.6, 10.6)
        val s = vm.state.value
        assertEquals(GeoPosition(47.5, 10.5), s.position) // zurück auf den gespeicherten Wert, keine falsche Erfolgsanzeige
        assertEquals(GeoPosition(47.5, 10.5), s.existing?.position)
        assertEquals(AppError.NETWORK, s.positionError)
        assertFalse(s.isMovingPosition)
        assertTrue(repo.positions.isEmpty())
        // ein neuer Versuch ist möglich und löscht den Fehler
        repo.positionResult = Result.success(Unit)
        vm.movePosition(47.6, 10.6)
        assertNull(vm.state.value.positionError)
        assertEquals(GeoPosition(47.6, 10.6), vm.state.value.position)
    }

    @Test
    fun moveMarker_whileSavingThePosition_blocksFurtherMovesSaveAndDelete() {
        val repo = FakeCampsites(existing = campsite())
        val gate = CompletableDeferred<Unit>()
        repo.positionGate = gate
        val vm = viewModel(repo, "s1", initial = null)
        vm.movePosition(47.6, 10.6)
        assertTrue(vm.state.value.isMovingPosition)
        assertFalse(vm.state.value.canMoveMarker)
        assertEquals(GeoPosition(47.6, 10.6), vm.state.value.position) // sofort sichtbar
        vm.movePosition(40.0, 9.0) // ignoriert
        vm.setComment("Kommentar")
        vm.save() // ignoriert
        vm.delete() // ignoriert
        assertEquals(GeoPosition(47.6, 10.6), vm.state.value.position)
        assertTrue(repo.updated.isEmpty())
        assertTrue(repo.deleted.isEmpty())
        gate.complete(Unit)
        assertFalse(vm.state.value.isMovingPosition)
        assertTrue(vm.state.value.canMoveMarker)
        assertEquals(listOf(GeoPosition(47.6, 10.6)), repo.positions.map { it.second })
    }

    @Test
    fun moveMarker_invalidOrWrappedPositions() {
        val repo = FakeCampsites(existing = campsite())
        val vm = viewModel(repo, "s1", initial = null)
        vm.movePosition(Double.NaN, 10.0)
        vm.movePosition(91.0, 10.0)
        vm.movePosition(10.0, Double.POSITIVE_INFINITY)
        assertEquals(GeoPosition(47.5, 10.5), vm.state.value.position)
        assertTrue(repo.positions.isEmpty())
        vm.movePosition(10.0, 190.0) // über die Datumsgrenze verschoben: wird auf −170 zurückgerechnet
        assertEquals(-170.0, vm.state.value.position!!.longitude, 1e-9)
    }

    @Test
    fun moveMarker_notPossible_whileLoadingOrWithoutPosition() {
        val repo = FakeCampsites(existing = campsite(), getResult = Result.failure(AppException(AppError.NETWORK)))
        val vm = viewModel(repo, "s1", initial = null)
        assertFalse(vm.state.value.canMoveMarker)
        vm.movePosition(47.6, 10.6)
        assertNull(vm.state.value.position)
        assertTrue(repo.positions.isEmpty())
    }
}
