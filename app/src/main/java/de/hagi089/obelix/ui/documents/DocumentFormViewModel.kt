package de.hagi089.obelix.ui.documents

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.R
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.documents.Document
import de.hagi089.obelix.data.documents.DocumentCategory
import de.hagi089.obelix.data.documents.DocumentInput
import de.hagi089.obelix.data.documents.DocumentLogic
import de.hagi089.obelix.data.documents.DocumentRepository
import de.hagi089.obelix.data.documents.DocumentValidator
import de.hagi089.obelix.data.files.FileReadException
import de.hagi089.obelix.data.files.LocalFileReader
import de.hagi089.obelix.data.files.NewFile
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentFormState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: AppError? = null,
    val saveError: AppError? = null,
    /** Bei „Bearbeiten“: das gespeicherte Dokument (nur Name und Kategorie sind änderbar). */
    val existing: Document? = null,
    val name: String = "",
    val category: DocumentCategory? = null,
    /** Neu gewählte Datei (fertig aufbereitet), wird erst beim Speichern übertragen. */
    val pendingFile: NewFile? = null,
    /** Eine gewählte Datei wird gerade gelesen und, wenn es ein Bild ist, verkleinert. */
    val isReadingFile: Boolean = false,
    @param:StringRes val fileError: Int? = null,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val categoryError: Int? = null,
    /** true, sobald gespeichert oder gelöscht wurde: der Bildschirm schließt sich. */
    val finished: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
}

/** Formular zum Hochladen, Bearbeiten (Name, Kategorie) und Löschen eines Dokuments (documentId = null: neu). */
class DocumentFormViewModel(
    private val documentId: String?,
    private val uid: String,
    private val documents: DocumentRepository,
    private val fileReader: LocalFileReader,
    private val clock: () -> LocalDate = { LocalDate.now() },
    /** Nur für Tests: ein eigener Bereich statt [viewModelScope] (der braucht auf der JVM den Android-Hauptthread). */
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(DocumentFormState())
    val state: StateFlow<DocumentFormState> = _state.asStateFlow()

    /** Der zuletzt aus dem Dateinamen vorgeschlagene Name: Solange er unverändert ist, darf eine neue Datei ihn ersetzen. */
    private var suggestedName: String = ""

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        scope.launch {
            val result = documentId?.let { documents.get(it) }
            val failure = result?.exceptionOrNull()
            val existing = result?.getOrNull()
            when {
                failure != null -> _state.update { it.copy(isLoading = false, loadError = errorOf(failure)) }
                documentId != null && existing == null -> _state.update { it.copy(isLoading = false, loadError = AppError.NOT_FOUND) }
                else -> _state.update { current ->
                    if (existing == null) {
                        current.copy(isLoading = false)
                    } else {
                        current.copy(isLoading = false, existing = existing, name = existing.name, category = existing.category)
                    }
                }
            }
        }
    }

    fun setName(text: String) = _state.update { it.copy(name = text, nameError = null) }
    fun setCategory(category: DocumentCategory?) = _state.update { it.copy(category = category, categoryError = null) }

    /** Der Benutzer hat eine Datei gewählt: lesen, Bild verkleinern, prüfen. Erst „Speichern“ überträgt sie. */
    fun attachFile(uri: Uri) = attachFileFrom { fileReader.read(uri) }

    /** Wie [attachFile], mit beliebiger Quelle (so lässt sich der Ablauf ohne Android prüfen). */
    internal fun attachFileFrom(read: suspend () -> Result<NewFile>) {
        val s = _state.value
        // Die Datei eines gespeicherten Dokuments ist unveränderlich (Regeln); ersetzen heißt löschen und neu hochladen.
        if (s.isLoading || s.isSaving || s.isReadingFile || s.isEdit) return
        _state.update { it.copy(isReadingFile = true, fileError = null) }
        scope.launch {
            val result = read()
            val file = result.getOrNull()
            if (file == null) {
                val message = (result.exceptionOrNull() as? FileReadException)?.messageRes ?: R.string.error_file_unreadable
                _state.update { it.copy(isReadingFile = false, fileError = message) }
            } else {
                val suggestion = DocumentLogic.suggestedName(file.name)
                _state.update { current ->
                    // Den Namen aus dem Dateinamen vorschlagen, solange der Benutzer ihn nicht selbst geändert hat.
                    val adopt = current.name.isBlank() || current.name == suggestedName
                    current.copy(
                        isReadingFile = false,
                        pendingFile = file,
                        fileError = null,
                        name = if (adopt) suggestion else current.name,
                        nameError = if (adopt) null else current.nameError,
                    )
                }
                suggestedName = suggestion
            }
        }
    }

    /** Verwirft die neu gewählte Datei (der Name bleibt, wie er ist). */
    fun discardPendingFile() = _state.update { it.copy(pendingFile = null, fileError = null) }

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving || s.isReadingFile) return
        val errors = s.copy(
            nameError = DocumentValidator.name(s.name),
            categoryError = DocumentValidator.category(s.category),
            fileError = if (s.isEdit) null else DocumentValidator.file(s.pendingFile != null),
            saveError = null,
        )
        if (listOf(errors.nameError, errors.categoryError, errors.fileError).any { it != null }) {
            _state.value = errors
            return
        }
        val input = DocumentInput(name = s.name.trim(), category = requireNotNull(s.category))
        _state.value = errors.copy(isSaving = true)
        scope.launch {
            val existing = s.existing
            finish(
                if (existing == null) {
                    documents.create(input, clock().toString(), requireNotNull(s.pendingFile), uid)
                } else {
                    documents.update(existing.id, input, uid)
                },
            )
        }
    }

    fun delete() {
        val s = _state.value
        val existing = s.existing ?: return
        if (s.isLoading || s.isSaving) return
        _state.value = s.copy(isSaving = true, saveError = null)
        scope.launch { finish(documents.delete(existing.id)) }
    }

    private fun finish(result: Result<Unit>) {
        val error = result.exceptionOrNull()
        _state.update {
            if (error == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, saveError = errorOf(error))
        }
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
