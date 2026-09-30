package de.hagi089.obelix.ui.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarInput
import de.hagi089.obelix.data.calendar.CalendarRepository
import de.hagi089.obelix.data.calendar.CalendarValidator
import de.hagi089.obelix.data.finance.BookingValidator
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarFormState(
    val isLoading: Boolean = true,
    /** true während der Überschneidungsprüfung und während des Schreibens. */
    val isSaving: Boolean = false,
    val loadError: AppError? = null,
    val saveError: AppError? = null,
    /** Bei „Bearbeiten“: der gespeicherte Eintrag. */
    val existing: CalendarEntry? = null,
    val users: List<UserProfile> = emptyList(),
    val startDate: String = LocalDate.now().toString(),
    val endDate: String = LocalDate.now().toString(),
    val personUid: String? = null,
    val destination: String = "",
    val comment: String = "",
    @param:StringRes val startError: Int? = null,
    @param:StringRes val endError: Int? = null,
    @param:StringRes val personError: Int? = null,
    @param:StringRes val destinationError: Int? = null,
    @param:StringRes val commentError: Int? = null,
    /**
     * Nicht null, solange der Überschneidungsdialog offen ist: die Einträge, mit denen sich der Zeitraum überschneidet.
     * Gespeichert wird erst nach „Trotzdem speichern“ (Entscheidung 5).
     */
    val overlaps: List<CalendarEntry>? = null,
    /** true, sobald gespeichert oder gelöscht wurde: der Bildschirm schließt sich. */
    val finished: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
}

/**
 * Formular zum Anlegen, Bearbeiten und Löschen eines Kalendereintrags (entryId = null: neu).
 *
 * Ablauf beim Speichern: Eingabe prüfen → Überschneidungen **frisch vom Server** abfragen → bei Treffern den
 * Dialog zeigen und erst nach ausdrücklicher Bestätigung schreiben. Schlägt die Abfrage fehl (z. B. ohne
 * Verbindung), wird nicht gespeichert.
 */
class CalendarFormViewModel(
    private val entryId: String?,
    private val uid: String,
    private val calendar: CalendarRepository,
    private val users: UserRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
    /** Nur für Tests: ein eigener Bereich statt [viewModelScope] (der braucht auf der JVM den Android-Hauptthread). */
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(CalendarFormState(startDate = clock().toString(), endDate = clock().toString()))
    val state: StateFlow<CalendarFormState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        scope.launch {
            val (userList, entry) = coroutineScope {
                val u = async { users.loadUsers() }
                val e = async { entryId?.let { calendar.get(it) } }
                u.await() to e.await()
            }
            val failure = userList.exceptionOrNull() ?: entry?.exceptionOrNull()
            val existing = entry?.getOrNull()
            if (failure != null) {
                _state.update { it.copy(isLoading = false, loadError = errorOf(failure)) }
                return@launch
            }
            if (entryId != null && existing == null) {
                _state.update { it.copy(isLoading = false, loadError = AppError.NOT_FOUND) }
                return@launch
            }
            _state.update { current ->
                val base = current.copy(isLoading = false, users = userList.getOrThrow())
                if (existing == null) {
                    base.copy(personUid = current.personUid ?: uid)
                } else {
                    base.copy(
                        existing = existing,
                        startDate = existing.startDate,
                        endDate = existing.endDate,
                        personUid = existing.personUid,
                        destination = existing.destination.orEmpty(),
                        comment = existing.comment,
                    )
                }
            }
        }
    }

    /** Liegt der neue Start nach dem Ende, rückt das Ende mit (ein Tag), sonst gäbe es sofort einen Fehler. */
    fun setStartDate(iso: String) = _state.update {
        val end = if (iso > it.endDate) iso else it.endDate
        it.copy(startDate = iso, endDate = end, startError = null, endError = null)
    }

    fun setEndDate(iso: String) = _state.update { it.copy(endDate = iso, endError = null) }
    fun setPerson(uid: String?) = _state.update { it.copy(personUid = uid, personError = null) }
    fun setDestination(text: String) = _state.update { it.copy(destination = text, destinationError = null) }
    fun setComment(text: String) = _state.update { it.copy(comment = text, commentError = null) }

    /** Name für die Auswahl „Person“: aus den Benutzern, sonst der gespeicherte Name des Eintrags. */
    private fun nameOf(state: CalendarFormState, personUid: String): String? =
        state.users.firstOrNull { it.uid == personUid }?.displayName
            ?: state.existing?.takeIf { it.personUid == personUid }?.personName

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving) return
        // Person ohne auffindbaren Namen (Konto entfernt und Person gewechselt) gilt wie „nicht gewählt“.
        val personName = s.personUid?.let { nameOf(s, it) }
        val errors = s.copy(
            startError = BookingValidator.date(s.startDate),
            endError = BookingValidator.date(s.endDate) ?: CalendarValidator.range(s.startDate, s.endDate),
            personError = CalendarValidator.person(s.personUid) ?: if (personName == null) CalendarValidator.person(null) else null,
            destinationError = CalendarValidator.destination(s.destination),
            commentError = BookingValidator.comment(s.comment),
            saveError = null,
            overlaps = null,
        )
        val hasErrors = listOf(errors.startError, errors.endError, errors.personError, errors.destinationError, errors.commentError)
            .any { it != null }
        if (hasErrors) {
            _state.value = errors
            return
        }
        _state.value = errors.copy(isSaving = true)
        scope.launch {
            val found = calendar.findOverlaps(s.startDate, s.endDate, s.existing?.id)
            val failure = found.exceptionOrNull()
            if (failure != null) {
                // Ohne Prüfung wird nicht gespeichert (Entscheidung 5, Anforderung 8): Fehlermeldung, nichts geschrieben.
                _state.update { it.copy(isSaving = false, saveError = errorOf(failure)) }
                return@launch
            }
            val overlaps = found.getOrThrow()
            if (overlaps.isNotEmpty()) {
                _state.update { it.copy(isSaving = false, overlaps = overlaps) }
            } else {
                write()
            }
        }
    }

    /** „Trotzdem speichern“ im Überschneidungsdialog: die ausdrückliche Bestätigung des Benutzers. */
    fun confirmOverlap() {
        val s = _state.value
        if (s.overlaps == null || s.isSaving) return
        _state.value = s.copy(overlaps = null, isSaving = true, saveError = null)
        scope.launch { write() }
    }

    /** „Zurück“ im Überschneidungsdialog: nichts wird gespeichert, das Formular bleibt offen. */
    fun dismissOverlap() = _state.update { if (it.isSaving) it else it.copy(overlaps = null) }

    fun delete() {
        val s = _state.value
        val existing = s.existing ?: return
        if (s.isSaving) return
        _state.value = s.copy(isSaving = true, saveError = null)
        scope.launch { finish(calendar.delete(existing.id)) }
    }

    /** Schreibt den Eintrag. Setzt voraus, dass die Eingabe geprüft ist und [CalendarFormState.isSaving] gilt. */
    private suspend fun write() {
        val s = _state.value
        val personUid = s.personUid
        val personName = personUid?.let { nameOf(s, it) }
        if (personUid == null || personName == null) {
            _state.update { it.copy(isSaving = false, personError = CalendarValidator.person(null)) }
            return
        }
        val input = CalendarInput(
            startDate = s.startDate,
            endDate = s.endDate,
            personUid = personUid,
            personName = personName,
            destination = s.destination.trim().takeIf { it.isNotEmpty() },
            comment = s.comment.trim(),
        )
        val existing = s.existing
        finish(if (existing == null) calendar.create(input, uid) else calendar.update(existing.id, input, uid))
    }

    private fun finish(result: Result<Unit>) {
        val error = result.exceptionOrNull()
        _state.update {
            if (error == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, saveError = errorOf(error))
        }
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
