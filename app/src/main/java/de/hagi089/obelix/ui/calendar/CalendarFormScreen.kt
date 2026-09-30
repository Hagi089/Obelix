package de.hagi089.obelix.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.ui.components.SelectorField
import de.hagi089.obelix.ui.components.SelectorOption
import de.hagi089.obelix.ui.finance.DateDialog
import de.hagi089.obelix.ui.finance.formatDay

/**
 * Formular eines Kalendereintrags: anlegen, bearbeiten, löschen. Beim Speichern prüft die App zuerst auf
 * Überschneidungen und zeigt sie in einem Dialog; gespeichert wird erst nach „Trotzdem speichern“.
 */
@Composable
fun CalendarFormScreen(
    viewModel: CalendarFormViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    var confirmDelete by remember { mutableStateOf(false) }
    var startPickerOpen by remember { mutableStateOf(false) }
    var endPickerOpen by remember { mutableStateOf(false) }

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
            DateButton(label = stringResource(R.string.field_calendar_start), iso = state.startDate, enabled = editable) { startPickerOpen = true }
            state.startError?.let { FieldError(stringResource(it)) }
            DateButton(label = stringResource(R.string.field_calendar_end), iso = state.endDate, enabled = editable) { endPickerOpen = true }
            state.endError?.let { FieldError(stringResource(it)) }

            val removedText = state.existing?.let { stringResource(R.string.calendar_person_removed, it.personName) }
            val personOptions = buildList {
                state.users.forEach { add(SelectorOption(it.uid, it.displayName)) }
                // Person des Eintrags, deren Konto es nicht mehr gibt: bleibt wählbar und lesbar.
                val existing = state.existing
                if (existing != null && removedText != null && state.users.none { it.uid == existing.personUid }) {
                    add(SelectorOption(existing.personUid, removedText))
                }
            }
            SelectorField(
                label = stringResource(R.string.field_calendar_person),
                value = personOptions.firstOrNull { it.key == state.personUid }?.text.orEmpty(),
                options = personOptions,
                onSelect = viewModel::setPerson,
                errorText = state.personError?.let { stringResource(it) },
                enabled = editable,
            )
            OutlinedTextField(
                value = state.destination,
                onValueChange = viewModel::setDestination,
                label = { Text(stringResource(R.string.field_calendar_destination)) },
                isError = state.destinationError != null,
                supportingText = { state.destinationError?.let { Text(stringResource(it)) } },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
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
                TextButton(onClick = { confirmDelete = true }, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.isSaving) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    if (startPickerOpen) {
        DateDialog(
            initialIso = state.startDate,
            onSelected = { viewModel.setStartDate(it); startPickerOpen = false },
            onDismiss = { startPickerOpen = false },
        )
    }
    if (endPickerOpen) {
        DateDialog(
            initialIso = state.endDate,
            onSelected = { viewModel.setEndDate(it); endPickerOpen = false },
            onDismiss = { endPickerOpen = false },
        )
    }
    state.overlaps?.let { overlaps ->
        OverlapDialog(
            startDate = state.startDate,
            endDate = state.endDate,
            overlaps = overlaps,
            onConfirm = viewModel::confirmOverlap,
            onBack = viewModel::dismissOverlap,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.calendar_delete_title)) },
            text = { Text(stringResource(R.string.calendar_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Warnung vor dem Speichern (Entscheidung 5): Sie nennt jeden kollidierenden Eintrag mit Person, Zeitraum und Ziel.
 * „Trotzdem speichern“ ist die ausdrückliche Bestätigung; ein Tippen daneben oder „Zurück“ speichert nichts.
 */
@Composable
private fun OverlapDialog(
    startDate: String,
    endDate: String,
    overlaps: List<CalendarEntry>,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBack,
        icon = { Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.calendar_overlap_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.calendar_overlap_text, periodText(startDate, endDate)))
                overlaps.forEach { entry ->
                    Text(
                        text = "• " + stringResource(R.string.calendar_row_person_period, entry.personName, entryDetails(entry)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(text = stringResource(R.string.calendar_overlap_question), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.calendar_overlap_confirm)) } },
        dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.calendar_overlap_back)) } },
    )
}

@Composable
private fun DateButton(label: String, iso: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatDay(iso), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FieldError(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp))
}
