package de.hagi089.obelix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.auth.AuthUser
import de.hagi089.obelix.data.user.AccessCode
import de.hagi089.obelix.data.user.Role
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.ui.settings.CategoriesViewModel
import de.hagi089.obelix.ui.settings.SettingsUiState
import de.hagi089.obelix.ui.settings.SettingsViewModel

private sealed interface Confirm {
    data object RenewCode : Confirm
    data class Remove(val user: UserProfile) : Confirm
}

/** Einstellungen: Konto; für ADMIN zusätzlich Zugangscode und Benutzerverwaltung. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    categoriesViewModel: CategoriesViewModel,
    user: AuthUser,
    onSignOut: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val profile = state.profile

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(R.string.settings_account)
        (profile?.displayName ?: user.displayName)?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
        user.email?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        profile?.let {
            Text(
                text = stringResource(R.string.settings_your_role, stringResource(roleLabel(it.role))),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_sign_out))
        }

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            if (profile == null) {
                Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (profile != null) {
            HorizontalDivider()
            CategorySection(viewModel = categoriesViewModel, isAdmin = profile.isAdmin)
        }
        if (profile?.isAdmin == true) {
            HorizontalDivider()
            SectionTitle(R.string.settings_import)
            Text(text = stringResource(R.string.settings_import_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenImport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_import_open))
            }
            HorizontalDivider()
            CodeSection(state, onRenew = { confirm = Confirm.RenewCode })
            HorizontalDivider()
            SectionTitle(R.string.settings_users)
            state.users.forEach { other ->
                val isSelf = other.uid == profile.uid
                UserRow(
                    user = other,
                    isSelf = isSelf,
                    canManage = !isSelf && !state.isLoading,
                    onToggleRole = { viewModel.toggleRole(other) },
                    onRemove = { confirm = Confirm.Remove(other) },
                )
            }
        }
    }

    when (val pending = confirm) {
        Confirm.RenewCode -> ConfirmDialog(
            title = R.string.settings_code_renew_title,
            text = stringResource(R.string.settings_code_renew_text),
            confirmLabel = R.string.settings_code_renew_confirm,
            onConfirm = { confirm = null; viewModel.renewCode() },
            onDismiss = { confirm = null },
        )
        is Confirm.Remove -> ConfirmDialog(
            title = R.string.settings_user_remove_title,
            text = stringResource(R.string.settings_user_remove_text, pending.user.displayName),
            confirmLabel = R.string.settings_user_remove_confirm,
            onConfirm = { confirm = null; viewModel.remove(pending.user) },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun CodeSection(state: SettingsUiState, onRenew: () -> Unit) {
    val context = LocalContext.current
    val code = state.accessCode
    SectionTitle(R.string.settings_code)
    Text(text = stringResource(R.string.settings_code_intro), style = MaterialTheme.typography.bodyMedium)
    if (code == null) {
        Text(text = stringResource(R.string.settings_code_none), style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(text = AccessCode.format(code), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, code) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_copy))
            }
            OutlinedButton(onClick = { shareText(context, code) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_share))
            }
        }
    }
    Button(onClick = onRenew, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(if (code == null) R.string.settings_code_create else R.string.settings_code_renew))
    }
}

@Composable
private fun UserRow(
    user: UserProfile,
    isSelf: Boolean,
    canManage: Boolean,
    onToggleRole: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = if (isSelf) stringResource(R.string.settings_user_you, user.displayName) else user.displayName
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(stringResource(roleLabel(user.role))) },
        trailingContent = if (canManage) {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.settings_user_actions, user.displayName),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (user.role == Role.ADMIN) R.string.settings_make_member else R.string.settings_make_admin,
                                    ),
                                )
                            },
                            onClick = { menuOpen = false; onToggleRole() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_user_remove)) },
                            onClick = { menuOpen = false; onRemove() },
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun ConfirmDialog(
    @StringRes title: Int,
    text: String,
    @StringRes confirmLabel: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmLabel)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@StringRes
private fun roleLabel(role: Role): Int = if (role == Role.ADMIN) R.string.role_admin else R.string.role_member

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_code), code))
}

private fun shareText(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_code_share_text, AccessCode.format(code)))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share)))
}
