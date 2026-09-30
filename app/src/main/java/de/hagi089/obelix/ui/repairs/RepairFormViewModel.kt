package de.hagi089.obelix.ui.repairs

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.finance.BookingValidator
import de.hagi089.obelix.data.planned.Priority
import de.hagi089.obelix.data.repairs.Repair
import de.hagi089.obelix.data.repairs.RepairInput
import de.hagi089.obelix.data.repairs.RepairRepository
import de.hagi089.obelix.data.repairs.RepairStatus
import de.hagi089.obelix.data.repairs.RepairValidator
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepairFormState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: AppError? = null,
    val saveError: AppError? = null,
    /** Bei „Bearbeiten“: die gespeicherte Auffälligkeit. */
    val existing: Repair? = null,
    val title: String = "",
    val description: String = "",
    val date: String = LocalDate.now().toString(),
    val priority: Priority? = null,
    val comment: String = "",
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val descriptionError: Int? = null,
    @param:StringRes val dateError: Int? = null,
    @param:StringRes val commentError: Int? = null,
    /** true, sobald gespeichert, erledigt, wieder geöffnet oder gelöscht wurde: der Bildschirm schließt sich. */
    val finished: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
    val isDone: Boolean get() = existing?.status == RepairStatus.DONE
}

/** Formular zum Anlegen, Bearbeiten, Erledigen, Wiederöffnen und Löschen einer Auffälligkeit (repairId = null: neu). */
class RepairFormViewModel(
    private val repairId: String?,
    private val uid: String,
    private val repairs: RepairRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
    /** Nur für Tests: ein eigener Bereich statt [viewModelScope] (der braucht auf der JVM den Android-Hauptthread). */
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(RepairFormState(date = clock().toString()))
    val state: StateFlow<RepairFormState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        scope.launch {
            val result = repairId?.let { repairs.get(it) }
            val failure = result?.exceptionOrNull()
            val existing = result?.getOrNull()
            when {
                failure != null -> _state.update { it.copy(isLoading = false, loadError = errorOf(failure)) }
                repairId != null && existing == null -> _state.update { it.copy(isLoading = false, loadError = AppError.NOT_FOUND) }
                else -> _state.update { current ->
                    if (existing == null) {
                        current.copy(isLoading = false)
                    } else {
                        current.copy(
                            isLoading = false,
                            existing = existing,
                            title = existing.title,
                            description = existing.description,
                            date = existing.date,
                            priority = existing.priority,
                            comment = existing.comment,
                        )
                    }
                }
            }
        }
    }

    fun setTitle(text: String) = _state.update { it.copy(title = text, titleError = null) }
    fun setDescription(text: String) = _state.update { it.copy(description = text, descriptionError = null) }
    fun setDate(iso: String) = _state.update { it.copy(date = iso, dateError = null) }
    fun setPriority(priority: Priority?) = _state.update { it.copy(priority = priority) }
    fun setComment(text: String) = _state.update { it.copy(comment = text, commentError = null) }

    /** Speichert die Angaben; der Status bleibt, wie er ist (neu: OFFEN). */
    fun save() = submit(_state.value.existing?.status ?: RepairStatus.OPEN)

    /** „Als erledigt markieren“: speichert die Angaben zusammen mit dem Status ERLEDIGT. */
    fun markDone() {
        if (_state.value.existing != null) submit(RepairStatus.DONE)
    }

    /** „Wieder öffnen“: speichert die Angaben zusammen mit dem Status OFFEN. */
    fun reopen() {
        if (_state.value.existing != null) submit(RepairStatus.OPEN)
    }

    fun delete() {
        val s = _state.value
        val existing = s.existing ?: return
        if (s.isLoading || s.isSaving) return
        _state.value = s.copy(isSaving = true, saveError = null)
        scope.launch { finish(repairs.delete(existing.id)) }
    }

    private fun submit(status: RepairStatus) {
        val s = _state.value
        if (s.isLoading || s.isSaving) return
        val errors = s.copy(
            titleError = RepairValidator.title(s.title),
            descriptionError = RepairValidator.description(s.description),
            dateError = BookingValidator.date(s.date),
            commentError = BookingValidator.comment(s.comment),
            saveError = null,
        )
        if (listOf(errors.titleError, errors.descriptionError, errors.dateError, errors.commentError).any { it != null }) {
            _state.value = errors
            return
        }
        val input = RepairInput(
            title = s.title.trim(),
            description = s.description.trim(),
            date = s.date,
            priority = s.priority,
            comment = s.comment.trim(),
        )
        _state.value = errors.copy(isSaving = true)
        scope.launch {
            val existing = s.existing
            finish(if (existing == null) repairs.create(input, uid) else repairs.update(existing.id, input, status, uid))
        }
    }

    private fun finish(result: Result<Unit>) {
        val error = result.exceptionOrNull()
        _state.update {
            if (error == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, saveError = errorOf(error))
        }
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
