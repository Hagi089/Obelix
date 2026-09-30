package de.hagi089.obelix.ui.repairs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.repairs.Repair
import de.hagi089.obelix.data.repairs.RepairFilter
import de.hagi089.obelix.data.repairs.RepairLogic
import de.hagi089.obelix.data.repairs.RepairRepository
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepairListState(
    val isLoading: Boolean = true,
    /** true, sobald Auffälligkeiten und Benutzer einmal vollständig vom Server geladen wurden. */
    val hasLoaded: Boolean = false,
    val repairs: List<Repair> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    /** Standard „Offen“: offene Auffälligkeiten sollen gut sichtbar sein. */
    val filter: RepairFilter = RepairFilter.OPEN,
    val error: AppError? = null,
) {
    val visible: List<Repair> get() = repairs.filter(filter::matches)
    val openCount: Int get() = RepairLogic.openCount(repairs)
    fun userName(uid: String?): String? = users.firstOrNull { it.uid == uid }?.displayName
}

/** Liste der Auffälligkeiten. Lädt einmalig je Öffnen (keine dauerhaften Listener, Anforderung 37). */
class RepairListViewModel(
    private val repairs: RepairRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RepairListState())
    val state: StateFlow<RepairListState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val (list, userList) = coroutineScope {
                val r = async { repairs.loadAll() }
                val u = async { users.loadUsers() }
                r.await() to u.await()
            }
            val failure = list.exceptionOrNull() ?: userList.exceptionOrNull()
            if (failure != null) {
                // Bisher geladene Daten bleiben sichtbar; nie eine „leere Liste“ statt eines Fehlers.
                _state.update { it.copy(isLoading = false, error = (failure as? AppException)?.error ?: AppError.UNKNOWN) }
            } else {
                _state.update {
                    it.copy(isLoading = false, hasLoaded = true, repairs = list.getOrThrow(), users = userList.getOrThrow(), error = null)
                }
            }
        }
    }

    fun setFilter(filter: RepairFilter) = _state.update { it.copy(filter = filter) }
}
