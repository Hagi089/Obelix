package de.hagi089.obelix.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.ui.components.OfflineBanner

/** Angemeldet, aber noch ohne gültigen Zugangscode: Code eingeben oder abmelden. */
@Composable
fun AccessCodeScreen(
    viewModel: AccessCodeViewModel,
    isOnline: Boolean,
    onFinished: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    val submit = { viewModel.submit(code) }

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
                if (state.fromRegistration) {
                    // Die Registrierung läuft noch: der Code wird gerade geprüft.
                    CircularProgressIndicator()
                } else {
                    CodeForm(
                        code = code,
                        onCodeChange = { code = it },
                        state = state,
                        onSubmit = submit,
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeForm(
    code: String,
    onCodeChange: (String) -> Unit,
    state: AccessCodeUiState,
    onSubmit: () -> Unit,
    onSignOut: () -> Unit,
) {
    Text(stringResource(R.string.access_code_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.access_code_intro), style = MaterialTheme.typography.bodyMedium)
    state.error?.let { error ->
        Text(
            text = stringResource(error.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text(stringResource(R.string.auth_access_code)) },
        singleLine = true,
        enabled = !state.isLoading,
        isError = state.codeError != null,
        supportingText = state.codeError?.let { res -> @Composable { Text(stringResource(res)) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSubmit, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.access_code_button))
        }
    }
    TextButton(onClick = onSignOut, enabled = !state.isLoading) {
        Text(stringResource(R.string.action_sign_out))
    }
}
