package de.hagi089.obelix.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarMonth
import de.hagi089.obelix.data.calendar.CalendarRepository
import de.hagi089.obelix.data.calendar.CalendarYear
import de.hagi089.obelix.data.calendar.PersonColor
import de.hagi089.obelix.data.calendar.PersonColors
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Darstellung des Kalenders: Jahresübersicht (Standard) oder die bisherige Monatsübersicht. */
enum class CalendarViewMode { YEAR, MONTH }

data class CalendarState(
    val isLoading: Boolean = true,
    /** true, sobald die Einträge einmal vollständig vom Server geladen wurden. */
    val hasLoaded: Boolean = false,
    val entries: List<CalendarEntry> = emptyList(),
    /** Alle Benutzer, nur für die Zuordnung der Farben; leer, wenn sie nicht geladen werden konnten. */
    val users: List<UserProfile> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Der angezeigte Monat. */
    val month: YearMonth = YearMonth.from(LocalDate.now()),
    /** Aktive Darstellung; beim Öffnen die Jahresübersicht. */
    val viewMode: CalendarViewMode = CalendarViewMode.YEAR,
    /** Das angezeigte Jahr der Jahresübersicht. */
    val year: Int = LocalDate.now().year,
    val error: AppError? = null,
) {
    val monthEntries: List<CalendarEntry> get() = CalendarMonth.entriesIn(entries, month)
    val occupancy: Map<LocalDate, Int> get() = CalendarMonth.occupancy(entries, month)
    val occupants: Map<LocalDate, List<CalendarEntry>> get() = CalendarMonth.occupants(entries, month)
    val yearEntries: List<CalendarEntry> get() = CalendarYear.entriesIn(entries, year)
    val yearOccupants: List<Map<LocalDate, List<CalendarEntry>>> get() = CalendarYear.occupantsByMonth(entries, year)
    val colors: Map<String, PersonColor> get() = PersonColors.assign(users.map { it.uid }, entries.map { it.personUid })
    /** Personen mit Farbe für die Legende, nach Name sortiert. */
    val legend: List<Pair<String, PersonColor>> get() {
        val names = LinkedHashMap<String, String>()
        entries.forEach { names[it.personUid] = it.personName }
        users.forEach { names[it.uid] = it.displayName }
        val palette = colors
        return names.entries.sortedBy { it.value.lowercase() }.mapNotNull { (uid, name) -> palette[uid]?.let { name to it } }
    }
    val current: List<CalendarEntry> get() = CalendarMonth.current(entries, today)
}

/** Kalender der Wohnmobil-Nutzung. Lädt einmalig je Öffnen (keine dauerhaften Listener, Anforderung 37). */
class CalendarViewModel(
    private val calendar: CalendarRepository,
    private val users: UserRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarState(today = clock(), month = YearMonth.from(clock()), year = clock().year))
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null, today = clock()) }
        viewModelScope.launch {
            val (result, userResult) = coroutineScope {
                val entries = async { calendar.loadAll() }
                val people = async { users.loadUsers() }
                entries.await() to people.await()
            }
            val failure = result.exceptionOrNull()
            if (failure != null) {
                // Bisher geladene Einträge bleiben sichtbar; nie eine „leere Liste“ statt eines Fehlers.
                _state.update { it.copy(isLoading = false, error = (failure as? AppException)?.error ?: AppError.UNKNOWN) }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false, hasLoaded = true, entries = result.getOrThrow(),
                        // Ohne Benutzerliste bleiben die Farben aus den Einträgen ableitbar; kein Fehler.
                        users = userResult.getOrNull() ?: it.users,
                        error = null,
                    )
                }
            }
        }
    }

    fun previousMonth() = _state.update { it.copy(month = it.month.minusMonths(1)) }
    fun nextMonth() = _state.update { it.copy(month = it.month.plusMonths(1)) }
    fun showToday() = _state.update {
        when (it.viewMode) {
            CalendarViewMode.YEAR -> it.copy(year = clock().year)
            CalendarViewMode.MONTH -> it.copy(month = YearMonth.from(clock()))
        }
    }

    fun previousYear() = _state.update { it.copy(year = it.year - 1) }
    fun nextYear() = _state.update { it.copy(year = it.year + 1) }

    /** Wechsel zur Jahresübersicht: zeigt das Jahr des zuletzt betrachteten Monats. */
    fun showYearView() = _state.update { it.copy(viewMode = CalendarViewMode.YEAR, year = it.month.year) }

    /**
     * Wechsel zur Monatsübersicht. Liegt der zuletzt betrachtete Monat nicht im gewählten Jahr (Jahreswechsel in der
     * Jahresübersicht), wird der aktuelle Monat dieses Jahres bzw. sonst der Januar gezeigt.
     */
    fun showMonthView() = _state.update {
        val month = if (it.month.year == it.year) it.month
        else YearMonth.of(it.year, if (it.year == clock().year) clock().monthValue else 1)
        it.copy(viewMode = CalendarViewMode.MONTH, month = month)
    }

    /** Tippen auf einen Monat der Jahresübersicht öffnet die Monatsübersicht dieses Monats. */
    fun openMonth(month: YearMonth) = _state.update { it.copy(viewMode = CalendarViewMode.MONTH, month = month, year = month.year) }
}
