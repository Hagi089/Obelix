package de.hagi089.obelix.ui.campsites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.CampsiteRepository
import de.hagi089.obelix.data.campsites.GeoPosition
import de.hagi089.obelix.data.campsites.LocationException
import de.hagi089.obelix.data.campsites.LocationProblem
import de.hagi089.obelix.data.campsites.LocationProvider
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CampsiteMode { LIST, MAP }

/** Stand der Ermittlung des aktuellen Standorts („Aktuellen Standort speichern“). */
sealed interface LocateState {
    data object Idle : LocateState
    data object Locating : LocateState
    data class Failed(val problem: LocationProblem) : LocateState

    /** Die Position liegt vor; der Bildschirm öffnet das Formular und ruft danach [CampsiteListViewModel.consumeLocation] auf. */
    data class Found(val position: GeoPosition) : LocateState
}

data class CampsiteListState(
    val isLoading: Boolean = true,
    /** true, sobald Stellplätze und Benutzer einmal vollständig vom Server geladen wurden. */
    val hasLoaded: Boolean = false,
    val campsites: List<Campsite> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val error: AppError? = null,
    val mode: CampsiteMode = CampsiteMode.LIST,
    val locate: LocateState = LocateState.Idle,
) {
    fun userName(uid: String?): String? = users.firstOrNull { it.uid == uid }?.displayName
}

/**
 * Liste und Karte der Stellplätze. Lädt einmalig je Öffnen (keine dauerhaften Listener, Anforderung 37). Der Standort
 * wird nur auf Knopfdruck ermittelt (Anforderung 23).
 */
class CampsiteListViewModel(
    private val campsites: CampsiteRepository,
    private val users: UserRepository,
    private val location: LocationProvider,
    /** Nur für Tests: ein eigener Bereich statt [viewModelScope] (der braucht auf der JVM den Android-Hauptthread). */
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(CampsiteListState())
    val state: StateFlow<CampsiteListState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            val (list, userList) = coroutineScope {
                val c = async { campsites.loadAll() }
                val u = async { users.loadUsers() }
                c.await() to u.await()
            }
            val failure = list.exceptionOrNull() ?: userList.exceptionOrNull()
            if (failure != null) {
                // Bisher geladene Daten bleiben sichtbar; nie eine „leere Liste“ statt eines Fehlers.
                _state.update { it.copy(isLoading = false, error = (failure as? AppException)?.error ?: AppError.UNKNOWN) }
            } else {
                _state.update {
                    it.copy(isLoading = false, hasLoaded = true, campsites = list.getOrThrow(), users = userList.getOrThrow(), error = null)
                }
            }
        }
    }

    fun setMode(mode: CampsiteMode) = _state.update { it.copy(mode = mode) }

    /** Ermittelt die aktuelle Position (die Berechtigung hat der Bildschirm vorher eingeholt). */
    fun locate() {
        if (_state.value.locate == LocateState.Locating) return
        _state.update { it.copy(locate = LocateState.Locating) }
        scope.launch {
            val result = location.currentLocation()
            val position = result.getOrNull()
            _state.update {
                if (position != null) {
                    it.copy(locate = LocateState.Found(position))
                } else {
                    val problem = (result.exceptionOrNull() as? LocationException)?.problem ?: LocationProblem.UNAVAILABLE
                    it.copy(locate = LocateState.Failed(problem))
                }
            }
        }
    }

    /** Der Benutzer hat die Standortberechtigung abgelehnt. */
    fun permissionDenied() = _state.update { it.copy(locate = LocateState.Failed(LocationProblem.PERMISSION_DENIED)) }

    /** Die gefundene Position wurde übernommen (Formular geöffnet). */
    fun consumeLocation() = _state.update { if (it.locate is LocateState.Found) it.copy(locate = LocateState.Idle) else it }

    /** Blendet die Meldung zur Standortermittlung aus. */
    fun dismissLocationProblem() = _state.update { if (it.locate is LocateState.Failed) it.copy(locate = LocateState.Idle) else it }
}
