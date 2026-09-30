package de.hagi089.obelix.ui.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.validation.HouseholdValidator
import de.hagi089.obelix.data.auth.AuthRepository
import de.hagi089.obelix.data.auth.RegistrationHandoff
import de.hagi089.obelix.data.household.AccessCode
import de.hagi089.obelix.data.household.HouseholdRepository
import de.hagi089.obelix.data.household.InviteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoading: Boolean = false,
    /** true, wenn der Code aus dem Registrierungsformular kommt (ungültiger Code löscht dann das Konto). */
    val fromRegistration: Boolean = false,
    val initialCode: String = "",
    @param:StringRes val codeError: Int? = null,
    @param:StringRes val householdNameError: Int? = null,
    @param:StringRes val partyOneError: Int? = null,
    @param:StringRes val partyTwoError: Int? = null,
    val error: AppError? = null,
    /** Geprüfter Code; null = noch im Schritt „Code eingeben". */
    val invite: InviteInfo? = null,
    val done: Boolean = false,
)

/**
 * Einrichtung nach der Anmeldung: Code prüfen, dann entweder Partei wählen und beitreten
 * oder (Start-Code) den Haushalt anlegen.
 */
class OnboardingViewModel(
    private val households: HouseholdRepository,
    private val auth: AuthRepository,
    private val handoff: RegistrationHandoff,
    private val uid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        val pending = handoff.pendingCode.value
        if (pending != null) {
            handoff.pendingCode.value = null
            _state.value = OnboardingUiState(fromRegistration = true, initialCode = pending)
            lookup(pending)
        }
    }

    fun submitCode(input: String) {
        val error = HouseholdValidator.accessCode(input)
        if (error != null) {
            _state.update { it.copy(codeError = error, error = null) }
            return
        }
        lookup(AccessCode.normalize(input))
    }

    fun backToCode() = _state.update { it.copy(invite = null, error = null, codeError = null) }

    fun join(partyId: String) {
        val invite = _state.value.invite ?: return
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            households.join(uid, displayName(), invite, partyId).fold(
                onSuccess = { finish() },
                onFailure = { fail(it) },
            )
        }
    }

    fun createHousehold(householdName: String, partyOne: String, partyTwo: String, ownPartyId: String) {
        val invite = _state.value.invite ?: return
        if (_state.value.isLoading) return
        val nameError = HouseholdValidator.householdName(householdName)
        val oneError = HouseholdValidator.partyName(partyOne)
        val twoError = HouseholdValidator.partyName(partyTwo)
        if (nameError != null || oneError != null || twoError != null) {
            _state.update {
                it.copy(householdNameError = nameError, partyOneError = oneError, partyTwoError = twoError, error = null)
            }
            return
        }
        _state.update {
            it.copy(isLoading = true, error = null, householdNameError = null, partyOneError = null, partyTwoError = null)
        }
        viewModelScope.launch {
            households.createHousehold(
                uid = uid,
                displayName = displayName(),
                invite = invite,
                householdName = householdName.trim(),
                partyNames = listOf(partyOne.trim(), partyTwo.trim()),
                ownPartyId = ownPartyId,
            ).fold(
                onSuccess = { finish() },
                onFailure = { fail(it) },
            )
        }
    }

    /** Nach Abschluss zurücksetzen, damit der Zustand bei einem späteren Durchlauf nicht „fertig" meldet. */
    fun consumeDone() {
        _state.value = OnboardingUiState()
    }

    fun signOut() = auth.signOut()

    private fun lookup(code: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, codeError = null) }
        viewModelScope.launch {
            households.lookupCode(code).fold(
                onSuccess = { invite -> _state.update { it.copy(isLoading = false, invite = invite) } },
                onFailure = { fail(it) },
            )
        }
    }

    private fun finish() {
        handoff.pendingName.value = null
        _state.update { it.copy(isLoading = false, done = true) }
    }

    private suspend fun fail(throwable: Throwable) {
        val error = (throwable as? AppException)?.error ?: AppError.UNKNOWN
        if (error == AppError.INVALID_ACCESS_CODE && _state.value.fromRegistration) {
            abortRegistration()
        } else {
            _state.update { it.copy(isLoading = false, error = error, invite = if (error == AppError.INVALID_ACCESS_CODE) null else it.invite) }
        }
    }

    /** Ungültiger Code bei der Registrierung: Konto wieder löschen und zur Anmeldung zurückkehren. */
    private suspend fun abortRegistration() {
        auth.deleteAccount().fold(
            onSuccess = {
                handoff.pendingName.value = null
                handoff.notice.value = AppError.INVALID_ACCESS_CODE
                auth.signOut()
            },
            onFailure = { e ->
                // Konto konnte nicht gelöscht werden (z. B. offline). Ohne Haushalt hat es keinen Datenzugriff.
                val error = (e as? AppException)?.error ?: AppError.UNKNOWN
                _state.update { it.copy(isLoading = false, fromRegistration = false, invite = null, error = error) }
            },
        )
    }

    private fun displayName(): String {
        val current = auth.currentUser
        val name = handoff.pendingName.value?.takeIf { it.isNotBlank() }
            ?: current?.displayName?.takeIf { it.isNotBlank() }
            ?: current?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: FALLBACK_NAME
        return name.trim().take(MAX_NAME_LENGTH)
    }

    private companion object {
        const val FALLBACK_NAME = "Mitglied"
        const val MAX_NAME_LENGTH = 50
    }
}
