package de.hagi089.obelix.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarRepository
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.CampsiteRepository
import de.hagi089.obelix.data.dashboard.DashboardLogic
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.FinanceCalculator
import de.hagi089.obelix.data.finance.FinanceRepository
import de.hagi089.obelix.data.finance.FinanceSummary
import de.hagi089.obelix.data.planned.PlannedCalculator
import de.hagi089.obelix.data.planned.PlannedExpense
import de.hagi089.obelix.data.planned.PlannedExpenseRepository
import de.hagi089.obelix.data.planned.PlannedSummary
import de.hagi089.obelix.data.repairs.Repair
import de.hagi089.obelix.data.repairs.RepairLogic
import de.hagi089.obelix.data.repairs.RepairRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Eine Datenquelle des Dashboards. [value] ist null, solange sie noch nie vollständig geladen wurde; [error] steht,
 * wenn der **letzte** Ladeversuch gescheitert ist. Ein früher geladener Wert bleibt dann stehen, wird aber nie
 * als frisch ausgegeben (das Dashboard zeigt den Fehler zusätzlich). Es gibt nie eine „0“ statt eines Fehlers.
 */
data class Source<T>(val value: T? = null, val error: AppError? = null) {
    fun after(result: Result<T>): Source<T> = result.fold(
        onSuccess = { Source(value = it) },
        onFailure = { Source(value = value, error = (it as? AppException)?.error ?: AppError.UNKNOWN) },
    )
}

data class DashboardState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val bookings: Source<List<Booking>> = Source(),
    val calendar: Source<List<CalendarEntry>> = Source(),
    val repairs: Source<List<Repair>> = Source(),
    val planned: Source<List<PlannedExpense>> = Source(),
    val campsites: Source<List<Campsite>> = Source(),
) {
    /** Erster Fehler des letzten Ladens; null, wenn alle Quellen zuletzt erfolgreich waren. */
    val error: AppError? get() =
        listOf(bookings.error, calendar.error, repairs.error, planned.error, campsites.error).firstOrNull { it != null }

    // Finanzen: null, solange die Buchungen nicht geladen sind (dann zeigt die Oberfläche keine Zahl).
    val financeSummary: FinanceSummary? get() = bookings.value?.let(FinanceCalculator::summarize)
    val hasBookings: Boolean get() = bookings.value?.isNotEmpty() == true
    val expensesOfYearCents: Long? get() = bookings.value?.let { DashboardLogic.expensesOfYearCents(it, today.year) }

    // Kalender
    val currentEntries: List<CalendarEntry>? get() = calendar.value?.let { DashboardLogic.currentEntries(it, today) }
    val nextEntry: CalendarEntry? get() = calendar.value?.let { DashboardLogic.nextEntry(it, today) }

    // Zähler
    val openRepairs: Int? get() = repairs.value?.let(RepairLogic::openCount)
    val plannedSummary: PlannedSummary? get() = planned.value?.let(PlannedCalculator::summarize)
    val campsiteCount: Int? get() = campsites.value?.size
}

/**
 * Dashboard (Phase 11): Kennzahlen aus den echten Daten. Lädt einmalig je Öffnen und bei der Rückkehr in die App
 * (keine dauerhaften Listener, Anforderung 37). Jede Quelle hat ihren eigenen Zustand; eine fehlgeschlagene
 * Quelle zeigt „nicht geladen“ statt einer Null, die übrigen Kacheln bleiben sichtbar.
 */
class DashboardViewModel(
    private val finance: FinanceRepository,
    private val calendar: CalendarRepository,
    private val repairs: RepairRepository,
    private val planned: PlannedExpenseRepository,
    private val campsites: CampsiteRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
    /** Nur für Tests: ein eigener Bereich statt [viewModelScope] (der braucht auf der JVM den Android-Hauptthread). */
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(DashboardState(today = clock()))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        // Ein laufendes Laden wird ersetzt, damit ein älteres Ergebnis kein neueres überschreibt.
        job?.cancel()
        _state.update { it.copy(isLoading = true, today = clock()) }
        job = scope.launch {
            coroutineScope {
                val b = async { finance.loadAll() }
                val c = async { calendar.loadAll() }
                val r = async { repairs.loadAll() }
                val p = async { planned.loadAll() }
                val s = async { campsites.loadAll() }
                val bookings = b.await()
                val entries = c.await()
                val repairList = r.await()
                val plans = p.await()
                val sites = s.await()
                _state.update {
                    it.copy(
                        isLoading = false,
                        bookings = it.bookings.after(bookings),
                        calendar = it.calendar.after(entries),
                        repairs = it.repairs.after(repairList),
                        planned = it.planned.after(plans),
                        campsites = it.campsites.after(sites),
                    )
                }
            }
        }
    }
}
