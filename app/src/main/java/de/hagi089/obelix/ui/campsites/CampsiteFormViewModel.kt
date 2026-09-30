package de.hagi089.obelix.ui.campsites

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.R
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.CampsiteInput
import de.hagi089.obelix.data.campsites.CampsiteLogic
import de.hagi089.obelix.data.campsites.CampsitePhotoSet
import de.hagi089.obelix.data.campsites.CampsiteRepository
import de.hagi089.obelix.data.campsites.CampsiteValidator
import de.hagi089.obelix.data.campsites.GeoPosition
import de.hagi089.obelix.data.files.FileReadException
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.LocalFileReader
import de.hagi089.obelix.data.files.NewFile
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampsiteFormState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: AppError? = null,
    val saveError: AppError? = null,
    /** Bei „Bearbeiten“: der gespeicherte Stellplatz. */
    val existing: Campsite? = null,
    /** Neu: die beim Tippen auf „Aktuellen Standort speichern“ ermittelte Position; bearbeiten: die gespeicherte. */
    val position: GeoPosition? = null,
    val date: String = LocalDate.now().toString(),
    val comment: String = "",
    val name: String = "",
    val address: String = "",
    val note: String = "",
    val rating: Int? = null,
    val photos: CampsitePhotoSet = CampsitePhotoSet(),
    val isReadingPhoto: Boolean = false,
    @param:StringRes val photoError: Int? = null,
    @param:StringRes val commentError: Int? = null,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val addressError: Int? = null,
    @param:StringRes val noteError: Int? = null,
    /** true, sobald gespeichert oder gelöscht wurde: der Bildschirm schließt sich. */
    val finished: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
    val canAddPhoto: Boolean get() = photos.canAdd && !isReadingPhoto && !isSaving
}

/**
 * Formular eines Stellplatzes: neu (campsiteId = null, mit der ermittelten [initialPosition]) oder bearbeiten.
 * Fotos werden erst beim Speichern übertragen; die Zahl ist auf drei begrenzt (in der Oberfläche und in den Regeln).
 */
class CampsiteFormViewModel(
    private val campsiteId: String?,
    private val initialPosition: GeoPosition?,
    private val uid: String,
    private val campsites: CampsiteRepository,
    private val fileReader: LocalFileReader,
    private val clock: () -> LocalDate = { LocalDate.now() },
    /** Nur für Tests: ein eigener Bereich statt [viewModelScope] (der braucht auf der JVM den Android-Hauptthread). */
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(CampsiteFormState(date = clock().toString(), position = initialPosition))
    val state: StateFlow<CampsiteFormState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        scope.launch {
            if (campsiteId == null) {
                // Ohne Position gibt es nichts zu speichern (die Position kommt vom Knopf „Aktuellen Standort speichern“).
                _state.update { it.copy(isLoading = false, loadError = if (initialPosition == null) AppError.UNKNOWN else null) }
                return@launch
            }
            val result = campsites.get(campsiteId)
            val failure = result.exceptionOrNull()
            val existing = result.getOrNull()
            when {
                failure != null -> _state.update { it.copy(isLoading = false, loadError = errorOf(failure)) }
                existing == null -> _state.update { it.copy(isLoading = false, loadError = AppError.NOT_FOUND) }
                else -> _state.update {
                    it.copy(
                        isLoading = false,
                        existing = existing,
                        position = existing.position,
                        date = existing.date,
                        comment = existing.comment,
                        name = existing.name.orEmpty(),
                        address = existing.address.orEmpty(),
                        note = existing.note.orEmpty(),
                        rating = existing.rating,
                        photos = CampsitePhotoSet(saved = existing.photos),
                    )
                }
            }
        }
    }

    fun setComment(text: String) = _state.update { it.copy(comment = text, commentError = null) }
    fun setName(text: String) = _state.update { it.copy(name = text, nameError = null) }
    fun setAddress(text: String) = _state.update { it.copy(address = text, addressError = null) }
    fun setNote(text: String) = _state.update { it.copy(note = text, noteError = null) }

    /** Bewertung 1 bis 5 oder null (keine Angabe). Ungültige Werte werden ignoriert. */
    fun setRating(rating: Int?) {
        if (CampsiteValidator.isValidRating(rating)) _state.update { it.copy(rating = rating) }
    }

    /** Der Benutzer hat ein Foto gewählt oder aufgenommen: lesen, verkleinern, prüfen. Erst „Speichern“ überträgt es. */
    fun addPhoto(uri: Uri) = addPhotoFrom { fileReader.readPhoto(uri) }

    /** Wie [addPhoto], mit beliebiger Quelle (so lässt sich der Ablauf ohne Android prüfen). */
    internal fun addPhotoFrom(read: suspend () -> Result<NewFile>) {
        val s = _state.value
        if (s.isSaving || s.isReadingPhoto || s.isLoading) return
        if (!s.photos.canAdd) {
            _state.update { it.copy(photoError = R.string.error_photo_limit) }
            return
        }
        _state.update { it.copy(isReadingPhoto = true, photoError = null) }
        scope.launch {
            val result = read()
            _state.update { current ->
                result.fold(
                    onSuccess = { file ->
                        val next = current.photos.withAdded(file)
                        if (next == null) {
                            current.copy(isReadingPhoto = false, photoError = R.string.error_photo_limit)
                        } else {
                            current.copy(isReadingPhoto = false, photos = next)
                        }
                    },
                    onFailure = { error ->
                        val message = (error as? FileReadException)?.messageRes ?: R.string.error_file_unreadable
                        current.copy(isReadingPhoto = false, photoError = message)
                    },
                )
            }
        }
    }

    /** Verwirft ein neu gewähltes, noch nicht gespeichertes Foto. */
    fun removeNewPhoto(index: Int) = _state.update { it.copy(photos = it.photos.withoutAdded(index), photoError = null) }

    /** Markiert ein gespeichertes Foto zum Entfernen; die Datei wird beim Speichern gelöscht. */
    fun removeSavedPhoto(fileId: String) = _state.update { it.copy(photos = it.photos.withRemoved(fileId), photoError = null) }

    /** Bilddaten eines gespeicherten Fotos (nur beim Anzeigen geladen). */
    suspend fun loadPhoto(ref: FileRef): Result<ByteArray> = campsites.loadPhoto(ref)

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving || s.isReadingPhoto) return
        val position = s.position ?: return
        val errors = s.copy(
            commentError = CampsiteValidator.comment(s.comment),
            nameError = CampsiteValidator.name(s.name),
            addressError = CampsiteValidator.address(s.address),
            noteError = CampsiteValidator.note(s.note),
            saveError = null,
        )
        if (listOf(errors.commentError, errors.nameError, errors.addressError, errors.noteError).any { it != null } ||
            !CampsiteValidator.isValidPosition(position.latitude, position.longitude)
        ) {
            _state.value = errors
            return
        }
        val input = CampsiteInput(
            comment = s.comment.trim(),
            name = CampsiteLogic.blankToNull(s.name),
            address = CampsiteLogic.blankToNull(s.address),
            note = CampsiteLogic.blankToNull(s.note),
            rating = s.rating,
        )
        _state.value = errors.copy(isSaving = true)
        scope.launch {
            val existing = s.existing
            val result = if (existing == null) {
                campsites.create(position, s.date, input, s.photos.added, uid)
            } else {
                campsites.update(existing.id, input, s.photos.removedIds, s.photos.added, uid)
            }
            finish(result)
        }
    }

    fun delete() {
        val s = _state.value
        val existing = s.existing ?: return
        if (s.isLoading || s.isSaving) return
        _state.value = s.copy(isSaving = true, saveError = null)
        scope.launch { finish(campsites.delete(existing.id)) }
    }

    private fun finish(result: Result<Unit>) {
        val error = result.exceptionOrNull()
        _state.update {
            if (error == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, saveError = errorOf(error))
        }
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
