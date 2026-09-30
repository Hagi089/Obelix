package de.hagi089.obelix.ui.planned

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.core.money.Money
import de.hagi089.obelix.data.finance.Settlement
import de.hagi089.obelix.data.planned.Priority
import de.hagi089.obelix.ui.components.SelectorField
import de.hagi089.obelix.ui.components.SelectorOption
import de.hagi089.obelix.ui.finance.DateDialog
import de.hagi089.obelix.ui.finance.formatDay
import de.hagi089.obelix.ui.finance.settlementLabel

/**
 * Formular einer geplanten Ausgabe: anlegen, bearbeiten, „Gekauft“ (Dialog), löschen.
 * Eine bereits gekaufte Planung wird nur noch angezeigt (Verweis auf die Buchung, Löschen).
 */
@Composable
fun PlannedFormScreen(
    viewModel: PlannedFormViewModel,
    onFinished: () -> Unit,
    onOpenBooking: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    var confirmDelete by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var purchaseDatePickerOpen by remember { mutableStateOf(false) }

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
            if (state.isPurchased) {
                PurchasedInfo(state = state, onOpenBooking = onOpenBooking)
            } else {
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
                    value = state.amountText,
                    onValueChange = viewModel::setAmount,
                    label = { Text(stringResource(R.string.field_estimated_amount)) },
                    suffix = { Text("€") },
                    isError = state.amountError != null,
                    supportingText = { state.amountError?.let { Text(stringResource(it)) } },
                    singleLine = true,
                    enabled = editable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateButton(label = stringResource(R.string.field_planned_date), iso = state.date, enabled = editable) { datePickerOpen = true }
                state.dateError?.let { FieldError(stringResource(it)) }

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
                    value = state.link,
                    onValueChange = viewModel::setLink,
                    label = { Text(stringResource(R.string.field_link)) },
                    isError = state.linkError != null,
                    supportingText = { state.linkError?.let { Text(stringResource(it)) } },
                    singleLine = true,
                    enabled = editable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
            }

            if (state.purchase == null) {
                state.saveError?.let {
                    Text(
                        text = stringResource(it.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }

            if (!state.isPurchased) {
                Button(onClick = viewModel::save, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_save))
                }
            }
            if (state.isEdit && !state.isPurchased) {
                OutlinedButton(onClick = viewModel::startPurchase, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_purchased))
                }
                OpenLinkButton(link = state.existing?.link)
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

    if (datePickerOpen) {
        DateDialog(
            initialIso = state.date,
            onSelected = { viewModel.setDate(it); datePickerOpen = false },
            onDismiss = { datePickerOpen = false },
        )
    }
    val draft = state.purchase
    if (draft != null) {
        PurchaseDialog(
            state = state,
            draft = draft,
            viewModel = viewModel,
            onPickDate = { purchaseDatePickerOpen = true },
        )
        if (purchaseDatePickerOpen) {
            DateDialog(
                initialIso = draft.date,
                onSelected = { viewModel.setPurchaseDate(it); purchaseDatePickerOpen = false },
                onDismiss = { purchaseDatePickerOpen = false },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.planned_delete_title)) },
            text = {
                Text(stringResource(if (state.isPurchased) R.string.planned_delete_purchased_text else R.string.planned_delete_text))
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
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

/** Öffnet den gespeicherten Link im Browser (nur wenn es einen gibt). */
@Composable
private fun OpenLinkButton(link: String?) {
    if (link.isNullOrBlank()) return
    val uriHandler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { failed = !runCatching { uriHandler.openUri(link) }.isSuccess },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.planned_link_open)) }
    if (failed) {
        Text(
            text = stringResource(R.string.planned_link_open_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

/** Anzeige einer gekauften Planung: Plan, tatsächlicher Kauf, Verweis auf die Buchung. */
@Composable
private fun PurchasedInfo(state: PlannedFormState, onOpenBooking: (String) -> Unit) {
    val plan = state.existing ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = plan.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.field_estimated_amount) + ": " + Money.format(plan.estimatedAmountCents),
                style = MaterialTheme.typography.bodyMedium,
            )
            val booking = state.purchasedBooking
            if (booking != null) {
                Text(
                    text = stringResource(R.string.planned_purchased_info, formatDay(booking.date), Money.format(booking.amountCents)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = stringResource(R.string.planned_purchased_booking_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(text = stringResource(R.string.planned_purchased_locked), style = MaterialTheme.typography.bodySmall)
        }
    }
    val bookingId = plan.purchasedTransactionId
    if (state.purchasedBooking != null && bookingId != null) {
        OutlinedButton(onClick = { onOpenBooking(bookingId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.planned_purchased_view_booking))
        }
    }
    OpenLinkButton(link = plan.link)
}

/** Dialog „Gekauft“: tatsächlicher Betrag, Kaufdatum, Bezahlt von, Kategorie, Abrechnung. */
@Composable
private fun PurchaseDialog(
    state: PlannedFormState,
    draft: PurchaseDraft,
    viewModel: PlannedFormViewModel,
    onPickDate: () -> Unit,
) {
    val editable = !state.isSaving
    AlertDialog(
        onDismissRequest = viewModel::cancelPurchase,
        title = { Text(stringResource(R.string.purchase_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.purchase_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = viewModel::setPurchaseAmount,
                    label = { Text(stringResource(R.string.field_actual_amount)) },
                    suffix = { Text("€") },
                    isError = draft.amountError != null,
                    supportingText = { draft.amountError?.let { Text(stringResource(it)) } },
                    singleLine = true,
                    enabled = editable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateButton(label = stringResource(R.string.field_purchase_date), iso = draft.date, enabled = editable, onClick = onPickDate)
                draft.dateError?.let { FieldError(stringResource(it)) }
                SelectorField(
                    label = stringResource(R.string.field_paid_by),
                    value = state.users.firstOrNull { it.uid == draft.paidByUid }?.displayName ?: stringResource(R.string.select_please),
                    options = state.users.map { SelectorOption(it.uid, it.displayName) },
                    onSelect = viewModel::setPurchasePayer,
                    errorText = draft.payerError?.let { stringResource(it) },
                    enabled = editable,
                )
                SelectorField(
                    label = stringResource(R.string.field_category),
                    value = state.categories.firstOrNull { it.id == draft.categoryId }?.name ?: stringResource(R.string.select_please),
                    options = state.categories.filter { it.active }.map { SelectorOption(it.id, it.name) },
                    onSelect = viewModel::setPurchaseCategory,
                    errorText = draft.categoryError?.let { stringResource(it) },
                    enabled = editable,
                )
                Text(stringResource(R.string.field_settlement), style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Settlement.entries.forEach { option ->
                        FilterChip(
                            selected = draft.settlement == option,
                            onClick = { viewModel.setPurchaseSettlement(option) },
                            enabled = editable,
                            label = { Text(stringResource(settlementLabel(option))) },
                        )
                    }
                }
                Text(stringResource(R.string.settlement_help), style = MaterialTheme.typography.bodySmall)
                state.saveError?.let {
                    Text(
                        text = stringResource(it.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                if (state.isSaving) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmPurchase, enabled = editable) { Text(stringResource(R.string.purchase_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelPurchase, enabled = editable) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
