package de.hagi089.obelix.ui.auth

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.ui.components.OfflineBanner

private enum class AuthMode { LOGIN, REGISTER, RESET }

/** Anmeldebereich: wechselt zwischen Login, Registrierung und Passwort zurücksetzen. */
@Composable
fun AuthScreens(viewModel: AuthViewModel, isOnline: Boolean) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    fun switchTo(newMode: AuthMode) {
        viewModel.reset()
        mode = newMode
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
                when (mode) {
                    AuthMode.LOGIN -> LoginForm(
                        state = state,
                        onSubmit = viewModel::signIn,
                        onRegister = { switchTo(AuthMode.REGISTER) },
                        onForgotPassword = { switchTo(AuthMode.RESET) },
                    )
                    AuthMode.REGISTER -> RegisterForm(
                        state = state,
                        onSubmit = viewModel::register,
                        onBack = { switchTo(AuthMode.LOGIN) },
                    )
                    AuthMode.RESET -> ResetForm(
                        state = state,
                        onSubmit = viewModel::sendPasswordReset,
                        onBack = { switchTo(AuthMode.LOGIN) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    state: AuthUiState,
    onSubmit: (email: String, password: String) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val submit = { onSubmit(email, password) }

    FormTitle(R.string.auth_login_title)
    ErrorMessage(state)
    EmailField(email, { email = it }, state.emailError, ImeAction.Next)
    PasswordField(
        value = password,
        onValueChange = { password = it },
        labelRes = R.string.auth_password,
        errorRes = state.passwordError,
        imeAction = ImeAction.Done,
        onDone = submit,
    )
    SubmitButton(R.string.auth_login_button, state.isLoading, submit)
    TextButton(onClick = onForgotPassword, enabled = !state.isLoading) {
        Text(stringResource(R.string.auth_forgot_password))
    }
    TextButton(onClick = onRegister, enabled = !state.isLoading) {
        Text(stringResource(R.string.auth_to_register))
    }
}

@Composable
private fun RegisterForm(
    state: AuthUiState,
    onSubmit: (name: String, email: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val submit = { onSubmit(name, email, password) }
    val focusManager = LocalFocusManager.current

    FormTitle(R.string.auth_register_title)
    ErrorMessage(state)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.auth_name)) },
        singleLine = true,
        isError = state.nameError != null,
        supportingText = state.nameError?.let { res -> @Composable { Text(stringResource(res)) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
    )
    EmailField(email, { email = it }, state.emailError, ImeAction.Next)
    PasswordField(
        value = password,
        onValueChange = { password = it },
        labelRes = R.string.auth_new_password,
        errorRes = state.passwordError,
        imeAction = ImeAction.Done,
        onDone = submit,
        hintRes = R.string.auth_password_hint,
    )
    SubmitButton(R.string.auth_register_button, state.isLoading, submit)
    TextButton(onClick = onBack, enabled = !state.isLoading) {
        Text(stringResource(R.string.auth_back_to_login))
    }
}

@Composable
private fun ResetForm(
    state: AuthUiState,
    onSubmit: (email: String) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    val submit = { onSubmit(email) }

    FormTitle(R.string.auth_reset_title)
    Text(
        text = stringResource(R.string.auth_reset_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    ErrorMessage(state)
    if (state.resetMailSent) {
        Text(
            text = stringResource(R.string.auth_reset_sent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    EmailField(email, { email = it }, state.emailError, ImeAction.Done, onDone = submit)
    SubmitButton(R.string.auth_reset_button, state.isLoading, submit)
    TextButton(onClick = onBack, enabled = !state.isLoading) {
        Text(stringResource(R.string.auth_back_to_login))
    }
}

@Composable
private fun FormTitle(@StringRes titleRes: Int) {
    Text(text = stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun ErrorMessage(state: AuthUiState) {
    val error = state.error ?: return
    Text(
        text = stringResource(error.messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes errorRes: Int?,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_email)) },
        singleLine = true,
        isError = errorRes != null,
        supportingText = errorRes?.let { res -> @Composable { Text(stringResource(res)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { onDone() },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    @StringRes errorRes: Int?,
    imeAction: ImeAction,
    onDone: () -> Unit,
    @StringRes hintRes: Int? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val supporting: (@Composable () -> Unit)? = when {
        errorRes != null -> { { Text(stringResource(errorRes)) } }
        hintRes != null -> { { Text(stringResource(hintRes)) } }
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        isError = errorRes != null,
        supportingText = supporting,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
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
