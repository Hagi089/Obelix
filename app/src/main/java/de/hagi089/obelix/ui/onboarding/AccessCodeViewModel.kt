package de.hagi089.obelix.ui.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.validation.AuthValidator
import de.hagi089.obelix.data.auth.AuthRepository
import de.hagi089.obelix.data.auth.RegistrationHandoff
import de.hagi089.obelix.data.user.AccessCode
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccessCodeUiState(
    val isLoading: Boolean = false,
    /** true, solange der Code aus dem Registrierungsformular verarbeitet wird (falscher Code löscht dann das Konto). */
    val fromRegistration: Boolean = false,
    @param:StringRes val codeError: Int? = null,
    val error: AppError? = null,
    val done: Boolean = false,
)

/**
 * Angemeldet, aber noch ohne Benutzerdokument: den Zugangscode einlösen.
 * Kommt der Code aus der Registrierung, wird er sofort eingelöst; ist er falsch, wird das neue Konto
 * wieder gelöscht und die Anmeldeseite zeigt den Grund.
 */
class AccessCodeViewModel(
    private val users: UserRepository,
    private val auth: AuthRepository,
    private val handoff: RegistrationHandoff,
    private val uid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AccessCodeUiState())
    val state: StateFlow<AccessCodeUiState> = _state.asStateFlow()

    init {
        val pending = handoff.pendingCode.value
        if (pending != null) {
            handoff.pendingCode.value = null
            _state.value = AccessCodeUiState(fromRegistration = true)
            redeem(pending)
        }
    }

    fun submit(input: String) {
        if (_state.value.isLoading) return
        val error = AuthValidator.accessCode(input)
        if (error != null) {
            _state.update { it.copy(codeError = error, error = null) }
            return
        }
        redeem(AccessCode.normalize(input))
    }

    fun consumeDone() {
        _state.value = AccessCodeUiState()
    }

    private fun redeem(code: String) {
        _state.update { it.copy(isLoading = true, error = null, codeError = null) }
        viewModelScope.launch {
            users.register(uid, displayName(), code).fold(
                onSuccess = {
                    handoff.pendingName.value = null
                    _state.update { it.copy(isLoading = false, done = true) }
                },
                onFailure = { fail((it as? AppException)?.error ?: AppError.UNKNOWN) },
            )
        }
    }

    private suspend fun fail(error: AppError) {
        if (error == AppError.INVALID_ACCESS_CODE && _state.value.fromRegistration) {
            auth.deleteAccount().fold(
                onSuccess = {
                    handoff.pendingName.value = null
                    handoff.notice.value = AppError.INVALID_ACCESS_CODE
                    auth.signOut()
                },
                onFailure = { e ->
                    // Konto konnte nicht gelöscht werden (z. B. offline). Ohne Benutzerdokument hat es keinen Datenzugriff.
                    _state.update {
                        it.copy(isLoading = false, fromRegistration = false, error = (e as? AppException)?.error ?: error)
                    }
                },
            )
        } else {
            _state.update { it.copy(isLoading = false, fromRegistration = false, error = error) }
        }
    }

    private fun displayName(): String {
        val current = auth.currentUser
        val name = handoff.pendingName.value?.takeIf { it.isNotBlank() }
            ?: current?.displayName?.takeIf { it.isNotBlank() }
            ?: current?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: FALLBACK_NAME
        return name.trim().take(AuthValidator.MAX_NAME_LENGTH)
    }

    private companion object {
        const val FALLBACK_NAME = "Benutzer"
    }
}
