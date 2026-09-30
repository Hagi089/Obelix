package de.hagi089.obelix.ui.planned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.planned.PlannedCalculator
import de.hagi089.obelix.data.planned.PlannedExpense
import de.hagi089.obelix.data.planned.PlannedExpenseRepository
import de.hagi089.obelix.data.planned.PlannedStatus
import de.hagi089.obelix.data.planned.PlannedSummary
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filter der Liste: Standard ist „Geplant“, damit gekaufte Planungen nicht als offene Planung erscheinen. */
enum class PlannedFilter {
    PLANNED,
    PURCHASED,
    ALL,
    ;

    fun matches(plan: PlannedExpense): Boolean = when (this) {
        PLANNED -> plan.status == PlannedStatus.PLANNED
        PURCHASED -> plan.status == PlannedStatus.PURCHASED
        ALL -> true
    }
}

data class PlannedListState(
    val isLoading: Boolean = true,
    /** true, sobald Planungen und Benutzer einmal vollständig vom Server geladen wurden. */
    val hasLoaded: Boolean = false,
    val plans: List<PlannedExpense> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val filter: PlannedFilter = PlannedFilter.PLANNED,
    val error: AppError? = null,
) {
    val visible: List<PlannedExpense> get() = plans.filter(filter::matches)
    val summary: PlannedSummary get() = PlannedCalculator.summarize(plans)
    fun userName(uid: String?): String? = users.firstOrNull { it.uid == uid }?.displayName
}

/** Liste der geplanten Ausgaben. Lädt einmalig je Öffnen (keine dauerhaften Listener, Anforderung 37). */
class PlannedListViewModel(
    private val planned: PlannedExpenseRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannedListState())
    val state: StateFlow<PlannedListState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val (plans, userList) = coroutineScope {
                val p = async { planned.loadAll() }
                val u = async { users.loadUsers() }
                p.await() to u.await()
            }
            val failure = plans.exceptionOrNull() ?: userList.exceptionOrNull()
            if (failure != null) {
                // Bisher geladene Daten bleiben sichtbar; nie eine „leere Liste“ statt eines Fehlers.
                _state.update { it.copy(isLoading = false, error = (failure as? AppException)?.error ?: AppError.UNKNOWN) }
            } else {
                _state.update {
                    it.copy(isLoading = false, hasLoaded = true, plans = plans.getOrThrow(), users = userList.getOrThrow(), error = null)
                }
            }
        }
    }

    fun setFilter(filter: PlannedFilter) = _state.update { it.copy(filter = filter) }
}
