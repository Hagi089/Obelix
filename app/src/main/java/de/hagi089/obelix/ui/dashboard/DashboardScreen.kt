package de.hagi089.obelix.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.core.money.Money
import de.hagi089.obelix.ui.calendar.entryDetails

/**
 * Dashboard: Kalender, Finanzen und Zähler aus den echten Daten (Phase 11). Jede Kachel öffnet ihren Bereich.
 * Fehlende Daten werden nie als Null dargestellt: Eine nicht geladene Quelle zeigt „Nicht geladen“.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenCalendar: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenPlanned: () -> Unit,
    onOpenRepairs: () -> Unit,
    onOpenCampsites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Beim Öffnen und nach der Rückkehr aus einem Bereich oder der App neu vom Server laden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            if (!state.isLoading) {
                Button(onClick = viewModel::refresh, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
        CalendarCard(state = state, onClick = onOpenCalendar)
        FinanceCard(state = state, onClick = onOpenFinance)
        CounterCard(
            title = stringResource(R.string.repair_summary_open),
            value = state.openRepairs?.toString(),
            hasError = state.repairs.error != null,
            isLoading = state.isLoading,
            onClick = onOpenRepairs,
        )
        PlannedCard(state = state, onClick = onOpenPlanned)
        CounterCard(
            title = stringResource(R.string.dashboard_campsites),
            value = state.campsiteCount?.toString(),
            hasError = state.campsites.error != null,
            isLoading = state.isLoading,
            onClick = onOpenCampsites,
        )
    }
}

@Composable
private fun CalendarCard(state: DashboardState, onClick: () -> Unit) {
    val current = state.currentEntries
    val next = state.nextEntry
    DashboardCard(title = stringResource(R.string.nav_calendar), onClick = onClick) {
        when {
            current == null -> Placeholder(hasError = state.calendar.error != null, isLoading = state.isLoading)
            current.isEmpty() && next == null -> Text(
                text = stringResource(R.string.dashboard_calendar_none),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                current.forEach { entry ->
                    Text(text = stringResource(R.string.calendar_current_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = stringResource(R.string.calendar_row_person_period, entry.personName, entryDetails(entry)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (next != null) {
                    Text(text = stringResource(R.string.dashboard_next_entry), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = stringResource(R.string.calendar_row_person_period, next.personName, entryDetails(next)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceCard(state: DashboardState, onClick: () -> Unit) {
    val summary = state.financeSummary
    val expenses = state.expensesOfYearCents
    DashboardCard(title = stringResource(R.string.nav_finance), onClick = onClick) {
        when {
            summary == null || expenses == null -> Placeholder(hasError = state.bookings.error != null, isLoading = state.isLoading)
            // Ohne Buchungen keine „0,00 €“: Das wäre eine Zahl ohne Daten.
            !state.hasBookings -> Text(text = stringResource(R.string.finance_empty), style = MaterialTheme.typography.bodyLarge)
            else -> {
                Text(text = stringResource(R.string.finance_balance), style = MaterialTheme.typography.labelLarge)
                Text(text = Money.format(summary.balanceCents), style = MaterialTheme.typography.headlineMedium)
                Line(label = stringResource(R.string.dashboard_expenses_year, state.today.year), value = Money.format(expenses))
                if (summary.openTotalCents > 0) {
                    Line(label = stringResource(R.string.finance_open_claims), value = Money.format(summary.openTotalCents))
                } else {
                    Text(text = stringResource(R.string.finance_no_open_claims), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PlannedCard(state: DashboardState, onClick: () -> Unit) {
    val summary = state.plannedSummary
    DashboardCard(title = stringResource(R.string.planned_title), onClick = onClick) {
        if (summary == null) {
            Placeholder(hasError = state.planned.error != null, isLoading = state.isLoading)
        } else {
            Line(label = stringResource(R.string.planned_summary_open_count), value = summary.openCount.toString(), emphasized = true)
            if (summary.openCount > 0) {
                Line(label = stringResource(R.string.planned_summary_estimated), value = Money.format(summary.openEstimatedCents))
            }
            Text(text = stringResource(R.string.planned_summary_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CounterCard(title: String, value: String?, hasError: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    DashboardCard(title = title, onClick = onClick) {
        if (value == null) {
            Placeholder(hasError = hasError, isLoading = isLoading)
        } else {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardCard(title: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

@Composable
private fun Placeholder(hasError: Boolean, isLoading: Boolean) {
    // Fehler hat Vorrang: Nach einem gescheiterten Laden steht „Nicht geladen“, nie eine Zahl.
    val textRes = if (hasError || !isLoading) R.string.dashboard_not_loaded else R.string.dashboard_loading
    Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Line(label: String, value: String, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
    }
}
