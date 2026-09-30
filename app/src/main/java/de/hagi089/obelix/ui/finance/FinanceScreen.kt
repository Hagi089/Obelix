package de.hagi089.obelix.ui.finance

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.core.money.Money
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.FinanceSummary
import de.hagi089.obelix.data.finance.Settlement
import de.hagi089.obelix.ui.components.SelectorField
import de.hagi089.obelix.ui.components.SelectorOption

/** Finanzen: Übersicht (Kontostand, Forderungen), Filter und Buchungsliste. */
@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Beim Öffnen und nach der Rückkehr aus dem Formular neu vom Server laden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !state.hasLoaded && state.error != null -> ErrorState(
                messageRes = state.error!!.messageRes,
                onRetry = viewModel::refresh,
            )
            !state.hasLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> FinanceContent(state = state, viewModel = viewModel, onOpen = onOpen)
        }
        if (state.hasLoaded) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.finance_add))
            }
        }
    }
}

@Composable
private fun ErrorState(@StringRes messageRes: Int, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun FinanceContent(state: FinanceUiState, viewModel: FinanceViewModel, onOpen: (String) -> Unit) {
    val summary = remember(state.bookings) { state.summary }
    val visible = remember(state.bookings, state.filter) { state.visible }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.error?.let { error ->
            item {
                Text(
                    text = stringResource(error.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        item { OverviewCard(summary = summary, state = state) }
        item { Filters(state = state, onChange = viewModel::setFilter) }
        if (state.bookings.isEmpty()) {
            item { EmptyText(R.string.finance_empty) }
        } else if (visible.isEmpty()) {
            item { EmptyText(R.string.finance_empty_filtered) }
        } else {
            items(visible, key = { it.id }) { booking ->
                BookingRow(booking = booking, state = state, onClick = { onOpen(booking.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyText(@StringRes messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
private fun OverviewCard(summary: FinanceSummary, state: FinanceUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.finance_balance), style = MaterialTheme.typography.labelLarge)
            Text(text = Money.format(summary.balanceCents), style = MaterialTheme.typography.headlineMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(text = stringResource(R.string.finance_open_claims), style = MaterialTheme.typography.labelLarge)
            if (summary.openByPayerCents.isEmpty()) {
                Text(text = stringResource(R.string.finance_no_open_claims), style = MaterialTheme.typography.bodyMedium)
            } else {
                summary.openByPayerCents.forEach { (uid, cents) ->
                    val name = state.userName(uid) ?: stringResource(R.string.finance_unknown_user)
                    AmountLine(label = stringResource(R.string.finance_paid_out_by, name), cents = cents)
                }
            }
            AmountLine(label = stringResource(R.string.finance_after_settlement), cents = summary.afterSettlementCents, emphasized = true)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            AmountLine(label = stringResource(R.string.finance_income_total), cents = summary.incomeTotalCents)
            AmountLine(label = stringResource(R.string.finance_expense_total), cents = summary.expenseTotalCents)
            if (summary.sponsoredCents > 0) {
                AmountLine(label = stringResource(R.string.finance_sponsored_part), cents = summary.sponsoredCents)
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, cents: Long, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.format(cents),
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Filters(state: FinanceUiState, onChange: (FinanceFilter) -> Unit) {
    val filter = state.filter
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter.type == null, onClick = { onChange(filter.copy(type = null)) }, label = { Text(stringResource(R.string.filter_all)) })
            FilterChip(
                selected = filter.type == BookingType.INCOME,
                onClick = { onChange(filter.copy(type = BookingType.INCOME)) },
                label = { Text(stringResource(R.string.filter_income)) },
            )
            FilterChip(
                selected = filter.type == BookingType.EXPENSE,
                onClick = { onChange(filter.copy(type = BookingType.EXPENSE)) },
                label = { Text(stringResource(R.string.filter_expense)) },
            )
        }
        val all = stringResource(R.string.filter_all)
        SelectorField(
            label = stringResource(R.string.field_category),
            value = filter.categoryId?.let { state.categoryName(it) } ?: all,
            options = listOf(SelectorOption(null, all)) + state.categories.map { SelectorOption(it.id, it.name) },
            onSelect = { onChange(filter.copy(categoryId = it)) },
        )
        SelectorField(
            label = stringResource(R.string.filter_payer),
            value = filter.paidByUid?.let { state.userName(it) } ?: all,
            options = listOf(SelectorOption(null, all)) + state.users.map { SelectorOption(it.uid, it.displayName) },
            onSelect = { onChange(filter.copy(paidByUid = it)) },
        )
        SelectorField(
            label = stringResource(R.string.filter_year),
            value = filter.year?.toString() ?: all,
            options = listOf(SelectorOption(null, all)) + state.years.map { SelectorOption(it.toString(), it.toString()) },
            onSelect = { onChange(filter.copy(year = it?.toIntOrNull())) },
        )
    }
}

@Composable
private fun BookingRow(booking: Booking, state: FinanceUiState, onClick: () -> Unit) {
    val category = state.categoryName(booking.categoryId) ?: stringResource(R.string.finance_unknown_category)
    val payer = state.userName(booking.paidByUid)
    val isIncome = booking.type == BookingType.INCOME
    val details = buildList {
        add(formatDay(booking.date))
        add(category)
        if (payer != null) add(payer)
        if (booking.settlement != Settlement.SETTLED) add(stringResource(settlementLabel(booking.settlement)))
    }.joinToString(" · ")
    val amount = (if (isIncome) "+" else "−") + Money.format(booking.amountCents)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(booking.description) },
        supportingContent = { Text(details) },
        trailingContent = {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}
