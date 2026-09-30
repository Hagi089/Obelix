package de.hagi089.obelix.ui.repairs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.planned.Priority
import de.hagi089.obelix.ui.components.SelectorField
import de.hagi089.obelix.ui.components.SelectorOption
import de.hagi089.obelix.ui.finance.DateDialog
import de.hagi089.obelix.ui.finance.formatDay
import de.hagi089.obelix.ui.planned.priorityLabel

/** Formular einer Auffälligkeit: anlegen, bearbeiten, als erledigt markieren, wieder öffnen, löschen. */
@Composable
fun RepairFormScreen(
    viewModel: RepairFormViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    var confirmDelete by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    when {
        state.isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.loadError != null -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(state.loadError!!.messageRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Button(onClick = viewModel::load, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
        }
        else -> Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val editable = !state.isSaving
            if (state.isEdit) {
                Text(
                    text = stringResource(if (state.isDone) R.string.repair_status_done else R.string.repair_status_open),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.field_title)) },
                isError = state.titleError != null,
                supportingText = { state.titleError?.let { Text(stringResource(it)) } },
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.field_description)) },
                isError = state.descriptionError != null,
                supportingText = { state.descriptionError?.let { Text(stringResource(it)) } },
                minLines = 3,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { datePickerOpen = true }, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.field_repair_date), style = MaterialTheme.typography.labelSmall)
                    Text(formatDay(state.date), style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.dateError?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            val noneText = stringResource(R.string.select_none)
            SelectorField(
                label = stringResource(R.string.field_priority),
                value = state.priority?.let { stringResource(priorityLabel(it)) } ?: noneText,
                options = buildList {
                    add(SelectorOption(null, noneText))
                    Priority.entries.forEach { add(SelectorOption(it.name, stringResource(priorityLabel(it)))) }
                },
                onSelect = { viewModel.setPriority(Priority.from(it)) },
                enabled = editable,
            )
            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::setComment,
                label = { Text(stringResource(R.string.field_comment)) },
                isError = state.commentError != null,
                supportingText = { state.commentError?.let { Text(stringResource(it)) } },
                minLines = 2,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            state.saveError?.let {
                Text(
                    text = stringResource(it.messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Button(onClick = viewModel::save, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_save))
            }
            if (state.isEdit) {
                if (state.isDone) {
                    OutlinedButton(onClick = viewModel::reopen, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.repair_action_reopen))
                    }
                } else {
                    FilledTonalButton(onClick = viewModel::markDone, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.repair_action_done))
                    }
                }
                TextButton(onClick = { confirmDelete = true }, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.isSaving) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    if (datePickerOpen) {
        DateDialog(
            initialIso = state.date,
            onSelected = { viewModel.setDate(it); datePickerOpen = false },
            onDismiss = { datePickerOpen = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.repair_delete_title)) },
            text = { Text(stringResource(R.string.repair_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
