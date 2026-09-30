package de.hagi089.obelix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.auth.AuthRepository
import de.hagi089.obelix.data.auth.AuthUser
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Stand des Benutzerdokuments eines angemeldeten Kontos. */
sealed interface ProfileState {
    data object Loading : ProfileState

    /** Angemeldet, aber noch ohne Benutzerdokument: Zugangscode einlösen. */
    data object None : ProfileState
    data class Failed(val error: AppError) : ProfileState
    data class Ready(val profile: UserProfile) : ProfileState
}

sealed interface SessionState {
    /** Firebase hat den gespeicherten Anmeldezustand noch nicht gemeldet. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: AuthUser, val profile: ProfileState) : SessionState
}

class SessionViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val reloadTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<SessionState> = authRepository.authState
        .flatMapLatest { user ->
            if (user == null) {
                flowOf<SessionState>(SessionState.SignedOut)
            } else {
                reloadTrigger.flatMapLatest {
                    flow<SessionState> {
                        emit(SessionState.SignedIn(user, ProfileState.Loading))
                        val state = userRepository.loadProfile(user.uid).fold(
                            onSuccess = { profile -> if (profile == null) ProfileState.None else ProfileState.Ready(profile) },
                            onFailure = { ProfileState.Failed((it as? AppException)?.error ?: AppError.UNKNOWN) },
                        )
                        emit(SessionState.SignedIn(user, state))
                    }
                }
            }
        }
        // Eagerly statt WhileSubscribed: Kehrt man nach mehr als 5 s aus einer anderen App zurück (z. B. Dateiauswahl
        // für einen Beleg), darf das Benutzerdokument nicht neu geladen werden. Sonst ersetzt der Ladebildschirm
        // (oder bei einem Netzfehler die Fehlerseite) den Hauptbereich und verwirft das offene Formular.
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    /** Benutzerdokument neu vom Server laden (nach Einlösen des Codes oder bei „Erneut versuchen"). */
    fun reloadProfile() = reloadTrigger.update { it + 1 }

    fun signOut() = authRepository.signOut()
}
