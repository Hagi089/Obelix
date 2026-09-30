package de.hagi089.obelix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.household.HouseholdRepository
import de.hagi089.obelix.data.household.Member
import de.hagi089.obelix.data.household.Membership
import de.hagi089.obelix.data.household.PARTY_A
import de.hagi089.obelix.data.household.PARTY_B
import de.hagi089.obelix.data.household.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    /** Frisch vom Server geladen (Rolle und Zugangscode können sich geändert haben). */
    val membership: Membership? = null,
    val members: List<Member> = emptyList(),
    val error: AppError? = null,
)

/** Haushalt, Zugangscode und Mitgliederverwaltung. Die Berechtigungen prüft der Server (Firestore-Regeln). */
class SettingsViewModel(
    private val households: HouseholdRepository,
    private val uid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { load() }
    }

    fun renewCode() = action { membership ->
        households.renewInviteCode(uid, membership.household).map { }
    }

    fun setRole(member: Member, role: Role) = action { membership ->
        households.updateMember(membership.household.id, member.uid, role, member.partyId)
    }

    fun switchParty(member: Member) = action { membership ->
        val other = if (member.partyId == PARTY_A) PARTY_B else PARTY_A
        households.updateMember(membership.household.id, member.uid, member.role, other)
    }

    fun remove(member: Member) = action { membership ->
        households.removeMember(membership.household.id, member.uid)
    }

    private fun action(block: suspend (Membership) -> Result<Unit>) {
        val membership = _state.value.membership ?: return
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = block(membership)
            // Nach jeder Aktion neu vom Server laden: angezeigt wird nur, was der Server bestätigt hat.
            load(actionError = result.exceptionOrNull()?.let { errorOf(it) })
        }
    }

    private suspend fun load(actionError: AppError? = null) {
        val membershipResult = households.loadMembership(uid)
        val membership = membershipResult.getOrNull()
        if (membership == null) {
            val error = membershipResult.exceptionOrNull()?.let { errorOf(it) } ?: AppError.NOT_FOUND
            _state.update { it.copy(isLoading = false, error = actionError ?: error) }
            return
        }
        households.loadMembers(membership.household.id).fold(
            onSuccess = { members ->
                _state.value = SettingsUiState(
                    isLoading = false,
                    membership = membership,
                    members = members,
                    error = actionError,
                )
            },
            onFailure = { e -> _state.update { it.copy(isLoading = false, membership = membership, error = actionError ?: errorOf(e)) } },
        )
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
