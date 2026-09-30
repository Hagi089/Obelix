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
import de.hagi089.obelix.data.household.AccessCode
import de.hagi089.obelix.data.household.Member
import de.hagi089.obelix.data.household.Membership
import de.hagi089.obelix.data.household.Role
import de.hagi089.obelix.ui.settings.SettingsViewModel

private sealed interface Confirm {
    data object RenewCode : Confirm
    data class Remove(val member: Member) : Confirm
}

/** Einstellungen: Konto, Haushalt, Zugangscode und Mitglieder (Verwaltung nur für ADMIN). */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    user: AuthUser,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val membership = state.membership

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(R.string.settings_account)
        (membership?.member?.displayName ?: user.displayName)?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
        user.email?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_sign_out))
        }

        HorizontalDivider()

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            if (membership == null) {
                Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (membership != null) {
            HouseholdSection(membership)
            CodeSection(membership, busy = state.isLoading, onRenew = { confirm = Confirm.RenewCode })
            HorizontalDivider()
            SectionTitle(R.string.settings_members)
            state.members.forEach { member ->
                val isSelf = member.uid == membership.member.uid
                MemberRow(
                    member = member,
                    membership = membership,
                    isSelf = isSelf,
                    canManage = membership.isAdmin && !isSelf && !state.isLoading,
                    onToggleRole = {
                        viewModel.setRole(member, if (member.role == Role.ADMIN) Role.MEMBER else Role.ADMIN)
                    },
                    onSwitchParty = { viewModel.switchParty(member) },
                    onRemove = { confirm = Confirm.Remove(member) },
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
            title = R.string.settings_member_remove_title,
            text = stringResource(R.string.settings_member_remove_text, pending.member.displayName),
            confirmLabel = R.string.settings_member_remove_confirm,
            onConfirm = { confirm = null; viewModel.remove(pending.member) },
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
private fun HouseholdSection(membership: Membership) {
    SectionTitle(R.string.settings_household)
    Text(text = membership.household.name, style = MaterialTheme.typography.bodyLarge)
    Text(
        text = stringResource(
            R.string.settings_your_role,
            stringResource(roleLabel(membership.member.role)),
            membership.partyName(membership.member.partyId),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CodeSection(membership: Membership, busy: Boolean, onRenew: () -> Unit) {
    val context = LocalContext.current
    val code = membership.household.inviteCode
    SectionTitle(R.string.settings_code)
    Text(
        text = stringResource(R.string.settings_code_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
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
    if (membership.isAdmin) {
        Button(onClick = onRenew, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (code == null) R.string.settings_code_create else R.string.settings_code_renew))
        }
    }
}

@Composable
private fun MemberRow(
    member: Member,
    membership: Membership,
    isSelf: Boolean,
    canManage: Boolean,
    onToggleRole: () -> Unit,
    onSwitchParty: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = if (isSelf) stringResource(R.string.settings_member_you, member.displayName) else member.displayName
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_member_details,
                    membership.partyName(member.partyId),
                    stringResource(roleLabel(member.role)),
                ),
            )
        },
        trailingContent = if (canManage) {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.settings_member_actions, member.displayName),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (member.role == Role.ADMIN) R.string.settings_make_member else R.string.settings_make_admin,
                                    ),
                                )
                            },
                            onClick = { menuOpen = false; onToggleRole() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_switch_party)) },
                            onClick = { menuOpen = false; onSwitchParty() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_member_remove)) },
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
