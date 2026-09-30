package de.hagi089.obelix.ui.finance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.Settlement
import de.hagi089.obelix.ui.components.SelectorField
import de.hagi089.obelix.ui.components.SelectorOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Formular: Einnahme oder Ausgabe anlegen, bearbeiten, als erstattet markieren, löschen. */
@Composable
fun BookingFormScreen(
    viewModel: BookingFormViewModel,
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
            // Eine Buchung aus einer geplanten Ausgabe bleibt eine Ausgabe (Regeln erzwingen das).
            val fromPlanned = state.existing?.plannedExpenseId != null
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.type == BookingType.EXPENSE,
                    onClick = { viewModel.setType(BookingType.EXPENSE) },
                    enabled = editable && !fromPlanned,
                    label = { Text(stringResource(R.string.type_expense)) },
                )
                FilterChip(
                    selected = state.type == BookingType.INCOME,
                    onClick = { viewModel.setType(BookingType.INCOME) },
                    enabled = editable && !fromPlanned,
                    label = { Text(stringResource(R.string.type_income)) },
                )
            }
            if (fromPlanned) Text(stringResource(R.string.booking_from_planned_hint), style = MaterialTheme.typography.bodySmall)

            OutlinedButton(
                onClick = { datePickerOpen = true },
                enabled = editable,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.field_date), style = MaterialTheme.typography.labelSmall)
                    Text(formatDay(state.date), style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.dateError?.let { FieldError(stringResource(it)) }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text(stringResource(R.string.field_amount)) },
                suffix = { Text("€") },
                isError = state.amountError != null,
                supportingText = { state.amountError?.let { Text(stringResource(it)) } },
                singleLine = true,
                enabled = editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            val categoryOptions = state.categories
                .filter { it.active || it.id == state.existing?.categoryId }
                .map { SelectorOption(it.id, it.name) }
            SelectorField(
                label = stringResource(R.string.field_category),
                value = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: stringResource(R.string.select_please),
                options = categoryOptions,
                onSelect = viewModel::setCategory,
                errorText = state.categoryError?.let { stringResource(it) },
                enabled = editable,
            )

            val noneText = stringResource(R.string.select_none)
            val payerOptions = buildList {
                if (state.type == BookingType.INCOME) add(SelectorOption(null, noneText))
                addAll(state.users.map { SelectorOption(it.uid, it.displayName) })
            }
            SelectorField(
                label = stringResource(if (state.type == BookingType.INCOME) R.string.field_paid_in_by else R.string.field_paid_by),
                value = state.users.firstOrNull { it.uid == state.paidByUid }?.displayName
                    ?: if (state.type == BookingType.INCOME) noneText else stringResource(R.string.select_please),
                options = payerOptions,
                onSelect = viewModel::setPayer,
                errorText = state.payerError?.let { stringResource(it) },
                enabled = editable,
            )

            if (state.type == BookingType.EXPENSE) {
                Text(stringResource(R.string.field_settlement), style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Settlement.entries.forEach { option ->
                        FilterChip(
                            selected = state.settlement == option,
                            onClick = { viewModel.setSettlement(option) },
                            enabled = editable,
                            label = { Text(stringResource(settlementLabel(option))) },
                        )
                    }
                }
                Text(stringResource(R.string.settlement_help), style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.field_description)) },
                isError = state.descriptionError != null,
                supportingText = { state.descriptionError?.let { Text(stringResource(it)) } },
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
            val existing = state.existing
            if (existing != null) {
                if (existing.type == BookingType.EXPENSE && existing.settlement == Settlement.OPEN) {
                    OutlinedButton(onClick = viewModel::markSettled, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_mark_settled))
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
            title = { Text(stringResource(R.string.finance_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (state.existing?.plannedExpenseId != null) R.string.finance_delete_text_planned else R.string.finance_delete_text,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun FieldError(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(initialIso: String, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val initialMillis = try {
        LocalDate.parse(initialIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: java.time.format.DateTimeParseException) {
        null
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}
