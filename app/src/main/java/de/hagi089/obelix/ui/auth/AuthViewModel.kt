package de.hagi089.obelix.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.validation.AuthValidator
import de.hagi089.obelix.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    /** Fehler vom Server (Netzwerk, falsche Zugangsdaten, …). */
    val error: AppError? = null,
    /** Nach dem Anfordern des Passwort-Resets: neutrale Bestätigung. */
    val resetMailSent: Boolean = false,
)

/** Zustand und Aktionen der drei Anmeldeformulare (Login, Registrierung, Passwort zurücksetzen). */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Beim Wechsel zwischen den Formularen alte Fehler verwerfen. */
    fun reset() {
        if (!_state.value.isLoading) _state.value = AuthUiState()
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

    fun register(name: String, email: String, password: String) {
        val nameError = AuthValidator.name(name)
        val emailError = AuthValidator.email(email)
        val passwordError = AuthValidator.newPassword(password)
        if (nameError != null || emailError != null || passwordError != null) {
            _state.value = AuthUiState(nameError = nameError, emailError = emailError, passwordError = passwordError)
            return
        }
        submit { repository.register(name, email, password) }
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
        action: suspend () -> Result<Unit>,
    ) {
        if (_state.value.isLoading) return
        _state.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            val result = action()
            _state.update { current ->
                result.fold(
                    onSuccess = { onSuccess(current.copy(isLoading = false)) },
                    onFailure = { current.copy(isLoading = false, error = (it as? AppException)?.error ?: AppError.UNKNOWN) },
                )
            }
        }
    }
}
