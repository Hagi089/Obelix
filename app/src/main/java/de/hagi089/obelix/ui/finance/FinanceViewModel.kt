package de.hagi089.obelix.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.CategoryRepository
import de.hagi089.obelix.data.finance.Category
import de.hagi089.obelix.data.finance.FinanceRepository
import de.hagi089.obelix.data.finance.FinanceSummary
import de.hagi089.obelix.data.finance.FinanceCalculator
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filter der Buchungsliste. Der Zeitraum ist ein Kalenderjahr. */
data class FinanceFilter(
    val type: BookingType? = null,
    val categoryId: String? = null,
    val paidByUid: String? = null,
    val year: Int? = null,
) {
    val isActive: Boolean get() = type != null || categoryId != null || paidByUid != null || year != null

    fun matches(booking: Booking): Boolean =
        (type == null || booking.type == type) &&
            (categoryId == null || booking.categoryId == categoryId) &&
            (paidByUid == null || booking.paidByUid == paidByUid) &&
            (year == null || booking.date.startsWith("$year-"))
}

data class FinanceUiState(
    val isLoading: Boolean = true,
    /** true, sobald Buchungen, Kategorien und Benutzer einmal vollständig vom Server geladen wurden. */
    val hasLoaded: Boolean = false,
    val bookings: List<Booking> = emptyList(),
    val categories: List<Category> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val filter: FinanceFilter = FinanceFilter(),
    val error: AppError? = null,
) {
    /** Kennzahlen über alle Buchungen, unabhängig vom Filter. */
    val summary: FinanceSummary get() = FinanceCalculator.summarize(bookings)
    val visible: List<Booking> get() = bookings.filter(filter::matches)
    val years: List<Int> get() = bookings.mapNotNull { it.date.take(4).toIntOrNull() }.distinct().sortedDescending()

    fun categoryName(id: String): String? = categories.firstOrNull { it.id == id }?.name
    fun userName(uid: String?): String? = users.firstOrNull { it.uid == uid }?.displayName
}

/** Übersicht und Liste des Finanzbereichs. Lädt einmalig je Öffnen (keine dauerhaften Listener, Anforderung 37). */
class FinanceViewModel(
    private val finance: FinanceRepository,
    private val categories: CategoryRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FinanceUiState())
    val state: StateFlow<FinanceUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val results = coroutineScope {
                val b = async { finance.loadAll() }
                val c = async { categories.loadAll() }
                val u = async { users.loadUsers() }
                Triple(b.await(), c.await(), u.await())
            }
            val (bookings, cats, userList) = results
            val failure = bookings.exceptionOrNull() ?: cats.exceptionOrNull() ?: userList.exceptionOrNull()
            if (failure != null) {
                // Bisher geladene Daten bleiben sichtbar; es wird nie eine „leere Liste" statt eines Fehlers gezeigt.
                _state.update { it.copy(isLoading = false, error = errorOf(failure)) }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        bookings = bookings.getOrThrow(),
                        categories = cats.getOrThrow(),
                        users = userList.getOrThrow(),
                        error = null,
                    )
                }
            }
        }
    }

    fun setFilter(filter: FinanceFilter) = _state.update { it.copy(filter = filter) }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
