package de.hagi089.obelix.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.finance.Category
import de.hagi089.obelix.ui.settings.CategoriesViewModel

private sealed interface NameDialog {
    data object Add : NameDialog
    data class Rename(val category: Category) : NameDialog
}

/** Finanzkategorien in den Einstellungen: alle legen an, nur ADMIN benennt um und (de)aktiviert. */
@Composable
fun CategorySection(viewModel: CategoriesViewModel, isAdmin: Boolean, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<NameDialog?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.settings_categories), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(if (isAdmin) R.string.settings_categories_intro_admin else R.string.settings_categories_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.error?.let {
            Text(
                text = stringResource(it.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.categories.forEach { category ->
            CategoryRow(
                category = category,
                canManage = isAdmin && !state.isLoading,
                onRename = { dialog = NameDialog.Rename(category) },
                onToggle = { viewModel.toggleActive(category) },
            )
        }
        Button(
            onClick = { dialog = NameDialog.Add },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_category_add)) }
    }

    val current = dialog
    if (current != null) {
        val initial = (current as? NameDialog.Rename)?.category?.name ?: ""
        NameInputDialog(
            title = if (current is NameDialog.Rename) R.string.settings_category_rename else R.string.settings_category_add,
            initial = initial,
            errorRes = state.nameError,
            onTextChange = viewModel::clearNameError,
            onConfirm = { name ->
                when (current) {
                    NameDialog.Add -> viewModel.add(name)
                    is NameDialog.Rename -> viewModel.rename(current.category, name)
                }
            },
            onDismiss = {
                viewModel.clearNameError()
                dialog = null
            },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, canManage: Boolean, onRename: () -> Unit, onToggle: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(category.name) },
        supportingContent = if (category.active) null else ({ Text(stringResource(R.string.settings_category_inactive)) }),
        trailingContent = if (canManage) {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.settings_category_actions, category.name),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_category_rename)) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (category.active) R.string.settings_category_deactivate else R.string.settings_category_activate,
                                    ),
                                )
                            },
                            onClick = { menuOpen = false; onToggle() },
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
private fun NameInputDialog(
    @StringRes title: Int,
    initial: String,
    @StringRes errorRes: Int?,
    onTextChange: () -> Unit,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onTextChange() },
                label = { Text(stringResource(R.string.field_category_name)) },
                isError = errorRes != null,
                supportingText = { errorRes?.let { Text(stringResource(it)) } },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (onConfirm(text)) onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
