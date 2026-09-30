package de.hagi089.obelix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.auth.AuthRepository
import de.hagi089.obelix.data.auth.AuthUser
import de.hagi089.obelix.data.household.HouseholdRepository
import de.hagi089.obelix.data.household.Membership
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Haushaltsstand eines angemeldeten Benutzers. */
sealed interface HouseholdState {
    data object Loading : HouseholdState

    /** Angemeldet, aber noch keinem Haushalt zugeordnet: Zugangscode einlösen. */
    data object None : HouseholdState
    data class Failed(val error: AppError) : HouseholdState
    data class Ready(val membership: Membership) : HouseholdState
}

sealed interface SessionState {
    /** Firebase hat den gespeicherten Anmeldezustand noch nicht gemeldet. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: AuthUser, val household: HouseholdState) : SessionState
}

class SessionViewModel(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
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
                        emit(SessionState.SignedIn(user, HouseholdState.Loading))
                        val state = householdRepository.loadMembership(user.uid).fold(
                            onSuccess = { membership ->
                                if (membership == null) HouseholdState.None else HouseholdState.Ready(membership)
                            },
                            onFailure = { HouseholdState.Failed((it as? AppException)?.error ?: AppError.UNKNOWN) },
                        )
                        emit(SessionState.SignedIn(user, state))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    /** Haushaltsstand neu vom Server laden (nach Einrichtung oder bei „Erneut versuchen"). */
    fun reloadHousehold() = reloadTrigger.update { it + 1 }

    fun signOut() = authRepository.signOut()
}
