package de.hagi089.obelix.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.household.InviteType
import de.hagi089.obelix.data.household.PARTY_A
import de.hagi089.obelix.data.household.PARTY_B
import de.hagi089.obelix.ui.components.OfflineBanner

/**
 * Angemeldet, aber noch ohne Haushalt: Zugangscode eingeben, dann Partei wählen (Beitritt)
 * oder Haushalt einrichten (Start-Code).
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    isOnline: Boolean,
    onFinished: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.consumeDone()
            onFinished()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isOnline) OfflineBanner()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val invite = state.invite
                when {
                    invite == null -> CodeStep(state, viewModel::submitCode, onSignOut)
                    invite.type == InviteType.JOIN -> JoinStep(
                        state = state,
                        parties = invite.parties.map { it.id to it.name },
                        onJoin = viewModel::join,
                        onBack = viewModel::backToCode,
                    )
                    else -> CreateStep(state, viewModel::createHousehold, viewModel::backToCode)
                }
            }
        }
    }
}

@Composable
private fun CodeStep(state: OnboardingUiState, onSubmit: (String) -> Unit, onSignOut: () -> Unit) {
    var code by rememberSaveable(state.initialCode) { mutableStateOf(state.initialCode) }
    val submit = { onSubmit(code) }

    Text(stringResource(R.string.onboarding_code_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_code_intro), style = MaterialTheme.typography.bodyMedium)
    ErrorText(state)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text(stringResource(R.string.auth_access_code)) },
        singleLine = true,
        enabled = !state.isLoading,
        isError = state.codeError != null,
        supportingText = state.codeError?.let { res -> @Composable { Text(stringResource(res)) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton(R.string.onboarding_code_button, state.isLoading, submit)
    TextButton(onClick = onSignOut, enabled = !state.isLoading) {
        Text(stringResource(R.string.action_sign_out))
    }
}

@Composable
private fun JoinStep(
    state: OnboardingUiState,
    parties: List<Pair<String, String>>,
    onJoin: (partyId: String) -> Unit,
    onBack: () -> Unit,
) {
    var partyId by rememberSaveable { mutableStateOf(parties.firstOrNull()?.first ?: PARTY_A) }

    Text(stringResource(R.string.onboarding_join_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_join_intro), style = MaterialTheme.typography.bodyMedium)
    ErrorText(state)
    PartyChoice(parties = parties, selected = partyId, enabled = !state.isLoading, onSelect = { partyId = it })
    SubmitButton(R.string.onboarding_join_button, state.isLoading) { onJoin(partyId) }
    TextButton(onClick = onBack, enabled = !state.isLoading) {
        Text(stringResource(R.string.onboarding_other_code))
    }
}

@Composable
private fun CreateStep(
    state: OnboardingUiState,
    onCreate: (householdName: String, partyOne: String, partyTwo: String, ownPartyId: String) -> Unit,
    onBack: () -> Unit,
) {
    var householdName by rememberSaveable { mutableStateOf("") }
    var partyOne by rememberSaveable { mutableStateOf("") }
    var partyTwo by rememberSaveable { mutableStateOf("") }
    var ownParty by rememberSaveable { mutableStateOf(PARTY_A) }

    Text(stringResource(R.string.onboarding_create_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_create_intro), style = MaterialTheme.typography.bodyMedium)
    ErrorText(state)
    TextInput(householdName, { householdName = it }, R.string.onboarding_household_name, state.householdNameError, state.isLoading)
    TextInput(partyOne, { partyOne = it }, R.string.onboarding_party_one, state.partyOneError, state.isLoading)
    TextInput(partyTwo, { partyTwo = it }, R.string.onboarding_party_two, state.partyTwoError, state.isLoading)
    Text(stringResource(R.string.onboarding_own_party), style = MaterialTheme.typography.titleSmall)
    PartyChoice(
        parties = listOf(
            PARTY_A to partyOne.ifBlank { stringResource(R.string.onboarding_party_one) },
            PARTY_B to partyTwo.ifBlank { stringResource(R.string.onboarding_party_two) },
        ),
        selected = ownParty,
        enabled = !state.isLoading,
        onSelect = { ownParty = it },
    )
    SubmitButton(R.string.onboarding_create_button, state.isLoading) {
        onCreate(householdName, partyOne, partyTwo, ownParty)
    }
    TextButton(onClick = onBack, enabled = !state.isLoading) {
        Text(stringResource(R.string.onboarding_other_code))
    }
}

@Composable
private fun PartyChoice(
    parties: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        parties.forEach { (id, name) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected == id,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(id) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == id, onClick = null, enabled = enabled)
                Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    @StringRes errorRes: Int?,
    isLoading: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        enabled = !isLoading,
        isError = errorRes != null,
        supportingText = errorRes?.let { res -> @Composable { Text(stringResource(res)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(state: OnboardingUiState) {
    val error = state.error ?: return
    Text(
        text = stringResource(error.messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun SubmitButton(@StringRes labelRes: Int, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(labelRes))
        }
    }
}
