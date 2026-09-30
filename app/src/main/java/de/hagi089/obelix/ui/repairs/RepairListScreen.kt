package de.hagi089.obelix.ui.repairs

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.planned.Priority
import de.hagi089.obelix.data.repairs.Repair
import de.hagi089.obelix.data.repairs.RepairFilter
import de.hagi089.obelix.data.repairs.RepairStatus
import de.hagi089.obelix.ui.finance.formatDay
import de.hagi089.obelix.ui.planned.priorityLabel

/** Auffälligkeiten: Zähler der offenen, Filter (Standard „Offen“) und Liste. */
@Composable
fun RepairListScreen(
    viewModel: RepairListViewModel,
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
            else -> RepairContent(state = state, viewModel = viewModel, onOpen = onOpen)
        }
        if (state.hasLoaded) {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.repair_add))
            }
        }
    }
}

@Composable
private fun RepairContent(state: RepairListState, viewModel: RepairListViewModel, onOpen: (String) -> Unit) {
    val visible = remember(state.repairs, state.filter) { state.visible }
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
        item { OpenCountCard(state.openCount) }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == RepairFilter.OPEN,
                    onClick = { viewModel.setFilter(RepairFilter.OPEN) },
                    label = { Text(stringResource(R.string.repair_filter_open)) },
                )
                FilterChip(
                    selected = state.filter == RepairFilter.DONE,
                    onClick = { viewModel.setFilter(RepairFilter.DONE) },
                    label = { Text(stringResource(R.string.repair_filter_done)) },
                )
                FilterChip(
                    selected = state.filter == RepairFilter.ALL,
                    onClick = { viewModel.setFilter(RepairFilter.ALL) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }
        }
        if (visible.isEmpty()) {
            val messageRes = when {
                state.repairs.isEmpty() -> R.string.repair_empty
                state.filter == RepairFilter.DONE -> R.string.repair_empty_done
                else -> R.string.repair_empty_open
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
            items(visible, key = { it.id }) { repair ->
                RepairRow(repair = repair, state = state, onClick = { onOpen(repair.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OpenCountCard(openCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.repair_summary_open), style = MaterialTheme.typography.titleSmall)
            Text(text = openCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RepairRow(repair: Repair, state: RepairListState, onClick: () -> Unit) {
    val creator = state.userName(repair.createdBy)
    val details = buildList {
        add(formatDay(repair.date))
        if (creator != null) add(stringResource(R.string.repair_created_by, creator))
        if (repair.status == RepairStatus.DONE) add(stringResource(R.string.repair_row_done))
    }.joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(repair.title) },
        supportingContent = { Text(details) },
        trailingContent = repair.priority?.let { priority ->
            {
                Text(
                    text = stringResource(priorityLabel(priority)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (priority == Priority.HIGH) FontWeight.Bold else FontWeight.Normal,
                    color = if (priority == Priority.HIGH && repair.status == RepairStatus.OPEN) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        },
    )
}
