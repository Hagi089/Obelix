package de.hagi089.obelix.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarMonth
import de.hagi089.obelix.ui.finance.formatDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Zeitraum als Text: „10.10.2026 – 18.10.2026“, bei einem Tag nur „10.10.2026“. */
@Composable
internal fun periodText(startDate: String, endDate: String): String =
    if (startDate == endDate) {
        formatDay(startDate)
    } else {
        stringResource(R.string.calendar_row_period, formatDay(startDate), formatDay(endDate))
    }

/** Zeitraum und optional Ziel eines Eintrags, z. B. „10.10.2026 – 18.10.2026 · Italien“. */
@Composable
internal fun entryDetails(entry: CalendarEntry): String =
    listOfNotNull(periodText(entry.startDate, entry.endDate), entry.destination).joinToString(" · ")

private fun monthTitle(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + month.year

/** Kalender der Wohnmobil-Nutzung: Monatsraster mit belegten Tagen und die Liste der Einträge des Monats. */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
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
            else -> CalendarContent(state = state, viewModel = viewModel, onOpen = onOpen)
        }
        if (state.hasLoaded) {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.calendar_add))
            }
        }
    }
}

@Composable
private fun CalendarContent(state: CalendarState, viewModel: CalendarViewModel, onOpen: (String) -> Unit) {
    val monthEntries = remember(state.entries, state.month) { state.monthEntries }
    val occupancy = remember(state.entries, state.month) { state.occupancy }
    val current = remember(state.entries, state.today) { state.current }
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
        if (current.isNotEmpty()) item { CurrentUsageCard(current, onOpen) }
        item { MonthHeader(state = state, viewModel = viewModel) }
        item { MonthGrid(state = state, occupancy = occupancy) }
        item { Text(text = stringResource(R.string.calendar_legend), style = MaterialTheme.typography.bodySmall) }
        item {
            Text(
                text = stringResource(R.string.calendar_list_title, monthTitle(state.month)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
        }
        if (monthEntries.isEmpty()) {
            item {
                Text(
                    text = stringResource(if (state.entries.isEmpty()) R.string.calendar_empty else R.string.calendar_empty_month),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        } else {
            items(monthEntries, key = { it.id }) { entry ->
                EntryRow(entry = entry, onClick = { onOpen(entry.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CurrentUsageCard(current: List<CalendarEntry>, onOpen: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.calendar_current_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            current.forEach { entry ->
                Text(
                    text = stringResource(R.string.calendar_row_person_period, entry.personName, entryDetails(entry)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(entry.id) }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(state: CalendarState, viewModel: CalendarViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::previousMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev_month))
        }
        Text(
            text = monthTitle(state.month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = viewModel::showToday) { Text(stringResource(R.string.calendar_today)) }
        IconButton(onClick = viewModel::nextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month))
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarState, occupancy: Map<LocalDate, Int>) {
    val weeks = remember(state.month) { CalendarMonth.weeks(state.month) }
    val weekdays = stringArrayResource(R.array.calendar_weekdays_short)
    val occupiedText = stringResource(R.string.calendar_day_status_occupied)
    val overlapText = stringResource(R.string.calendar_day_status_overlap)
    val todayText = stringResource(R.string.calendar_day_status_today)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val count = date?.let { occupancy[it] } ?: 0
                    DayCell(
                        date = date,
                        count = count,
                        isToday = date == state.today,
                        occupiedText = occupiedText,
                        overlapText = overlapText,
                        todayText = todayText,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    count: Int,
    isToday: Boolean,
    occupiedText: String,
    overlapText: String,
    todayText: String,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier = modifier)
        return
    }
    val colors = MaterialTheme.colorScheme
    val background: Color
    val foreground: Color
    when {
        count >= 2 -> { background = colors.errorContainer; foreground = colors.onErrorContainer }
        count == 1 -> { background = colors.primaryContainer; foreground = colors.onPrimaryContainer }
        else -> { background = Color.Transparent; foreground = colors.onSurface }
    }
    // Der Zustand steht auch im Text für den Screenreader, nicht nur in der Farbe.
    val description = listOfNotNull(
        formatDay(date.toString()),
        when {
            count >= 2 -> overlapText
            count == 1 -> occupiedText
            else -> null
        },
        if (isToday) todayText else null,
    ).joinToString(", ")
    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (isToday) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (count >= 2) FontWeight.Bold else FontWeight.Normal,
            color = foreground,
        )
    }
}

@Composable
private fun EntryRow(entry: CalendarEntry, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(entry.personName) },
        supportingContent = { Text(entryDetails(entry)) },
    )
}
