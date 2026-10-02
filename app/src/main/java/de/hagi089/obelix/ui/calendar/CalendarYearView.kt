package de.hagi089.obelix.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.hagi089.obelix.R
import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarMonth
import de.hagi089.obelix.data.calendar.CalendarYear
import de.hagi089.obelix.data.calendar.PersonColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Breite, ab der drei statt zwei Monate nebeneinander stehen (Tablet, Querformat). */
private val THREE_COLUMNS_FROM = 600.dp

/**
 * Inhalt der Jahresübersicht: Jahreswechsel, Kennzahlen, zwölf Monate, Legende und die Liste aller Einträge des
 * Jahres. Die Monatsansicht wird nicht berührt; ein Tipp auf einen Monat öffnet sie für diesen Monat.
 */
internal fun LazyListScope.yearItems(
    state: CalendarState,
    viewModel: CalendarViewModel,
    colors: Map<String, PersonColor>,
    legend: List<Pair<String, PersonColor>>,
    onOpen: (String) -> Unit,
) {
    item { YearHeader(year = state.year, viewModel = viewModel) }
    item {
        val occupantsByMonth = remember(state.entries, state.year) { state.yearOccupants }
        // Ein Listeneintrag mit mehreren Bausteinen: in einer Spalte, sonst würden sie übereinanderliegen.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            YearSummary(occupiedDays = CalendarYear.occupiedDays(occupantsByMonth), totalDays = CalendarYear.daysInYear(state.year))
            YearGrid(
                year = state.year,
                occupantsByMonth = occupantsByMonth,
                colors = colors,
                today = state.today,
                onOpenMonth = viewModel::openMonth,
                onOpenEntry = onOpen,
            )
        }
    }
    item { Text(text = stringResource(R.string.calendar_legend), style = MaterialTheme.typography.bodySmall) }
    if (legend.isNotEmpty()) item { PersonLegend(legend) }
    item {
        Text(
            text = stringResource(R.string.calendar_year_list_title, state.year),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
    val yearEntries = state.yearEntries
    if (yearEntries.isEmpty()) {
        item {
            Text(
                text = stringResource(if (state.entries.isEmpty()) R.string.calendar_empty else R.string.calendar_empty_year),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    } else {
        items(yearEntries, key = { it.id }) { entry ->
            EntryRow(entry = entry, color = colors[entry.personUid], onClick = { onOpen(entry.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun YearHeader(year: Int, viewModel: CalendarViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::previousYear) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev_year))
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = viewModel::showToday) { Text(stringResource(R.string.calendar_today)) }
        IconButton(onClick = viewModel::nextYear) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_year))
        }
    }
}

/** „Belegt: 112 von 365 Tagen (31 %)“ mit Fortschrittsbalken und der Zahl der freien Tage. */
@Composable
private fun YearSummary(occupiedDays: Int, totalDays: Int) {
    val percent = if (totalDays == 0) 0 else Math.round(occupiedDays * 100f / totalDays)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.calendar_year_occupied, occupiedDays, totalDays, percent),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.calendar_year_free, totalDays - occupiedDays, totalDays - occupiedDays),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { if (totalDays == 0) 0f else occupiedDays.toFloat() / totalDays },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )
    }
}

/** Zwölf kleine Monatsraster, auf Smartphones zwei, auf breiten Displays drei nebeneinander. */
@Composable
private fun YearGrid(
    year: Int,
    occupantsByMonth: List<Map<LocalDate, List<CalendarEntry>>>,
    colors: Map<String, PersonColor>,
    today: LocalDate,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= THREE_COLUMNS_FROM) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..12).chunked(columns).forEach { months ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    months.forEach { m ->
                        MiniMonth(
                            month = YearMonth.of(year, m),
                            occupants = occupantsByMonth[m - 1],
                            colors = colors,
                            today = today,
                            onOpenMonth = onOpenMonth,
                            onOpenEntry = onOpenEntry,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    occupants: Map<LocalDate, List<CalendarEntry>>,
    colors: Map<String, PersonColor>,
    today: LocalDate,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(month) { CalendarMonth.weeks(month) }
    val weekdays = stringArrayResource(R.array.calendar_weekdays_short)
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    val description = pluralStringResource(R.plurals.calendar_year_month_description, occupants.size, name, month.year, occupants.size)
    val openLabel = stringResource(R.string.calendar_year_open_month, name)
    OutlinedCard(
        onClick = { onOpenMonth(month) },
        // Für Screenreader ist der Monat eine Einheit; die einzelnen Einträge stehen in der Liste darunter.
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            onClick(label = openLabel) { onOpenMonth(month); true }
        },
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { day ->
                    Text(
                        text = day.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val onDay = date?.let { occupants[it] }.orEmpty()
                        MiniDay(
                            date = date,
                            onDay = onDay,
                            colors = colors,
                            isToday = date == today,
                            onClick = when {
                                onDay.size == 1 -> ({ onOpenEntry(onDay.single().id) })
                                // Mehrfach belegte Tage: Monatsübersicht zeigt, welche Einträge sich treffen.
                                onDay.size >= 2 -> ({ onOpenMonth(month) })
                                else -> null
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDay(
    date: LocalDate?,
    onDay: List<CalendarEntry>,
    colors: Map<String, PersonColor>,
    isToday: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier = modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val personColor = onDay.singleOrNull()?.let { colors[it.personUid] }
    // Gleiche Farblogik wie die Monatsansicht: Person = Personenfarbe, mehrere Einträge = Fehlerfarbe (rot).
    val background: Color
    val foreground: Color
    when {
        onDay.size >= 2 -> { background = scheme.errorContainer; foreground = scheme.onErrorContainer }
        onDay.size == 1 && personColor != null -> { background = personColor.asColor(); foreground = Color(personColor.content) }
        onDay.size == 1 -> { background = scheme.primaryContainer; foreground = scheme.onPrimaryContainer }
        else -> { background = Color.Transparent; foreground = scheme.onSurface }
    }
    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (isToday) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (onDay.size >= 2) FontWeight.Bold else FontWeight.Normal,
            color = foreground,
            maxLines = 1,
        )
    }
}
