package de.hagi089.obelix.ui.planned

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
import de.hagi089.obelix.data.planned.PlannedExpense
import de.hagi089.obelix.data.planned.PlannedStatus
import de.hagi089.obelix.data.planned.PlannedSummary
import de.hagi089.obelix.data.planned.Priority
import de.hagi089.obelix.ui.finance.formatDay

@StringRes
fun priorityLabel(priority: Priority): Int = when (priority) {
    Priority.LOW -> R.string.priority_low
    Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high
}

/** Geplante Ausgaben: Summe der offenen Planungen, Filter (Standard „Geplant“) und Liste. */
@Composable
fun PlannedListScreen(
    viewModel: PlannedListViewModel,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Beim Öffnen und nach der Rückkehr aus dem Formular neu vom Server laden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !state.hasLoaded && state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(state.error!!.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                Button(onClick = viewModel::refresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
            }
            !state.hasLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PlannedContent(state = state, viewModel = viewModel, onOpen = onOpen)
        }
        if (state.hasLoaded) {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.planned_add))
            }
        }
    }
}

@Composable
private fun PlannedContent(state: PlannedListState, viewModel: PlannedListViewModel, onOpen: (String) -> Unit) {
    val summary = remember(state.plans) { state.summary }
    val visible = remember(state.plans, state.filter) { state.visible }
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
        item { SummaryCard(summary) }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == PlannedFilter.PLANNED,
                    onClick = { viewModel.setFilter(PlannedFilter.PLANNED) },
                    label = { Text(stringResource(R.string.planned_filter_planned)) },
                )
                FilterChip(
                    selected = state.filter == PlannedFilter.PURCHASED,
                    onClick = { viewModel.setFilter(PlannedFilter.PURCHASED) },
                    label = { Text(stringResource(R.string.planned_filter_purchased)) },
                )
                FilterChip(
                    selected = state.filter == PlannedFilter.ALL,
                    onClick = { viewModel.setFilter(PlannedFilter.ALL) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }
        }
        if (visible.isEmpty()) {
            val messageRes = when {
                state.plans.isEmpty() -> R.string.planned_empty
                state.filter == PlannedFilter.PURCHASED -> R.string.planned_empty_purchased
                else -> R.string.planned_empty_planned
            }
            item {
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        } else {
            items(visible, key = { it.id }) { plan ->
                PlannedRow(plan = plan, state = state, onClick = { onOpen(plan.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: PlannedSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.planned_summary_open_count), style = MaterialTheme.typography.bodyMedium)
                Text(text = summary.openCount.toString(), style = MaterialTheme.typography.bodyMedium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.planned_summary_estimated), style = MaterialTheme.typography.titleSmall)
                Text(text = Money.format(summary.openEstimatedCents), style = MaterialTheme.typography.titleSmall)
            }
            Text(text = stringResource(R.string.planned_summary_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlannedRow(plan: PlannedExpense, state: PlannedListState, onClick: () -> Unit) {
    val creator = state.userName(plan.createdBy)
    val purchasedText = stringResource(R.string.planned_row_purchased)
    val details = buildList {
        add(formatDay(plan.plannedDate))
        plan.priority?.let { add(stringResource(priorityLabel(it))) }
        if (creator != null) add(stringResource(R.string.planned_created_by, creator))
        if (plan.status == PlannedStatus.PURCHASED) add(purchasedText)
    }.joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(plan.title) },
        supportingContent = { Text(details) },
        trailingContent = { Text(text = Money.format(plan.estimatedAmountCents), style = MaterialTheme.typography.bodyLarge) },
    )
}
