package de.hagi089.obelix.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.validation.AuthValidator
import de.hagi089.obelix.core.validation.HouseholdValidator
import de.hagi089.obelix.data.auth.AuthRepository
import de.hagi089.obelix.data.auth.RegistrationHandoff
import de.hagi089.obelix.data.household.AccessCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val codeError: Int? = null,
    /** Fehler vom Server (Netzwerk, falsche Zugangsdaten, …). */
    val error: AppError? = null,
    /** Nach dem Anfordern des Passwort-Resets: neutrale Bestätigung. */
    val resetMailSent: Boolean = false,
)

/** Zustand und Aktionen der drei Anmeldeformulare (Login, Registrierung, Passwort zurücksetzen). */
class AuthViewModel(
    private val repository: AuthRepository,
    private val handoff: RegistrationHandoff,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())

    /** Zusätzlich der Grund, warum eine Registrierung zurückgenommen wurde (ungültiger Zugangscode). */
    val state: StateFlow<AuthUiState> = combine(_state, handoff.notice) { current, notice ->
        if (current.error == null && notice != null) current.copy(error = notice) else current
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthUiState())

    /** Beim Wechsel zwischen den Formularen alte Fehler verwerfen. */
    fun reset() {
        if (!_state.value.isLoading) {
            _state.value = AuthUiState()
            handoff.notice.value = null
        }
    }

    fun signIn(email: String, password: String) {
        val emailError = AuthValidator.email(email)
        val passwordError = AuthValidator.loginPassword(password)
        if (emailError != null || passwordError != null) {
            _state.value = AuthUiState(emailError = emailError, passwordError = passwordError)
            return
        }
        submit { repository.signIn(email, password) }
    }

    /**
     * Registrierung nur mit Zugangscode. Code und Name werden vor dem Anlegen des Kontos abgelegt;
     * die Einrichtung prüft den Code direkt danach und löscht das Konto bei ungültigem Code wieder.
     */
    fun register(name: String, email: String, password: String, accessCode: String) {
        val nameError = AuthValidator.name(name)
        val emailError = AuthValidator.email(email)
        val passwordError = AuthValidator.newPassword(password)
        val codeError = HouseholdValidator.accessCode(accessCode)
        if (nameError != null || emailError != null || passwordError != null || codeError != null) {
            _state.value = AuthUiState(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
                codeError = codeError,
            )
            return
        }
        if (_state.value.isLoading) return
        handoff.notice.value = null
        handoff.pendingCode.value = AccessCode.normalize(accessCode)
        handoff.pendingName.value = name.trim()
        submit(onFailure = {
            handoff.pendingCode.value = null
            handoff.pendingName.value = null
        }) { repository.register(name, email, password) }
    }

    fun sendPasswordReset(email: String) {
        val emailError = AuthValidator.email(email)
        if (emailError != null) {
            _state.value = AuthUiState(emailError = emailError)
            return
        }
        submit(onSuccess = { it.copy(resetMailSent = true) }) { repository.sendPasswordReset(email) }
    }

    private fun submit(
        onSuccess: (AuthUiState) -> AuthUiState = { it },
        onFailure: () -> Unit = {},
        action: suspend () -> Result<Unit>,
    ) {
        if (_state.value.isLoading) return
        _state.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            val result = action()
            _state.update { current ->
                result.fold(
                    onSuccess = { onSuccess(current.copy(isLoading = false)) },
                    onFailure = {
                        onFailure()
                        current.copy(isLoading = false, error = (it as? AppException)?.error ?: AppError.UNKNOWN)
                    },
                )
            }
        }
    }
}
