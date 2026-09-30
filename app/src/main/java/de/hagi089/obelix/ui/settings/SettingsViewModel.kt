package de.hagi089.obelix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.user.Role
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    /** Eigenes Benutzerdokument, frisch vom Server (die Rolle kann sich geändert haben). */
    val profile: UserProfile? = null,
    /** Nur für ADMIN geladen. */
    val users: List<UserProfile> = emptyList(),
    /** Nur für ADMIN geladen; null = noch kein Code angelegt. */
    val accessCode: String? = null,
    val error: AppError? = null,
)

/** Konto, Zugangscode und Benutzerverwaltung. Die Berechtigungen prüft der Server (Firestore-Regeln). */
class SettingsViewModel(
    private val users: UserRepository,
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

    fun renewCode() = action { users.renewAccessCode(uid).map { } }

    fun toggleRole(user: UserProfile) = action {
        users.setRole(user.uid, if (user.role == Role.ADMIN) Role.MEMBER else Role.ADMIN)
    }

    fun remove(user: UserProfile) = action { users.removeUser(user.uid) }

    private fun action(block: suspend () -> Result<Unit>) {
        if (_state.value.isLoading || _state.value.profile?.isAdmin != true) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val error = block().exceptionOrNull()?.let(::errorOf)
            // Nach jeder Aktion neu vom Server laden: angezeigt wird nur, was der Server bestätigt hat.
            load(actionError = error)
        }
    }

    private suspend fun load(actionError: AppError? = null) {
        val profileResult = users.loadProfile(uid)
        val profile = profileResult.getOrNull()
        if (profile == null) {
            val error = profileResult.exceptionOrNull()?.let(::errorOf) ?: AppError.NOT_FOUND
            _state.update { it.copy(isLoading = false, error = actionError ?: error) }
            return
        }
        if (!profile.isAdmin) {
            _state.value = SettingsUiState(isLoading = false, profile = profile, error = actionError)
            return
        }
        val list = users.loadUsers()
        val code = users.loadAccessCode()
        _state.value = SettingsUiState(
            isLoading = false,
            profile = profile,
            users = list.getOrDefault(emptyList()),
            accessCode = code.getOrNull(),
            error = actionError
                ?: list.exceptionOrNull()?.let(::errorOf)
                ?: code.exceptionOrNull()?.let(::errorOf),
        )
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
