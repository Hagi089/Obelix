package de.hagi089.obelix.data.dashboard

import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.CalendarMonth
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.FinanceCalculator
import java.time.LocalDate

/**
 * Reine Rechenlogik des Dashboards (Phase 11). Alles stützt sich auf dieselben Berechnungen wie die
 * Detailbereiche (`FinanceCalculator`, `CalendarMonth`, `RepairLogic`, `PlannedCalculator`), damit die Zahlen
 * dort und hier übereinstimmen.
 */
object DashboardLogic {

    /** Einträge, die am [today] laufen („Aktuell in Nutzung“), nach Start, Ende und Kennung sortiert. */
    fun currentEntries(entries: List<CalendarEntry>, today: LocalDate): List<CalendarEntry> =
        CalendarMonth.current(entries, today)

    /**
     * Der nächste Termin: der Eintrag mit dem frühesten Start **nach** [today]. Läuft heute ein Eintrag, zählt er
     * als „aktuell“, nicht als „nächster“. Bei gleichem Start entscheiden Ende und Kennung (stabil).
     * Liegt nichts in der Zukunft, ist das Ergebnis null (kein erfundener Termin).
     */
    fun nextEntry(entries: List<CalendarEntry>, today: LocalDate): CalendarEntry? {
        val todayText = today.toString()
        return entries
            .filter { it.startDate > todayText }
            .minWithOrNull(compareBy<CalendarEntry> { it.startDate }.thenBy { it.endDate }.thenBy { it.id })
    }

    /**
     * Ausgaben des Kalenderjahres [year] in Cent (Entscheidung 44: „aktueller Zeitraum“ = laufendes Jahr, wie der
     * Jahresfilter im Finanzbereich). Wie „Ausgaben gesamt“ dort zählen auch gesponserte und offene Ausgaben mit.
     * Einnahmen zählen nicht. Maßgeblich ist das Buchungsdatum.
     */
    fun expensesOfYearCents(bookings: List<Booking>, year: Int): Long {
        val prefix = "$year-"
        val ofYear = bookings.filter { it.type == BookingType.EXPENSE && it.date.startsWith(prefix) }
        return FinanceCalculator.summarize(ofYear).expenseTotalCents
    }
}
