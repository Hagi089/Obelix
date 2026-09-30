package de.hagi089.obelix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.data.auth.AuthRepository
import de.hagi089.obelix.data.auth.AuthUser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    /** Firebase hat den gespeicherten Anmeldezustand noch nicht gemeldet. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: AuthUser) : SessionState
}

class SessionViewModel(private val repository: AuthRepository) : ViewModel() {

    val session: StateFlow<SessionState> = repository.authState
        .map { user -> if (user == null) SessionState.SignedOut else SessionState.SignedIn(user) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    fun signOut() = repository.signOut()
}
