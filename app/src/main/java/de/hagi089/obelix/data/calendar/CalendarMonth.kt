package de.hagi089.obelix.data.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Berechnungen für die Monatsansicht (reine Logik, ohne Oberfläche). */
object CalendarMonth {

    /**
     * Wochen des Monats für ein Raster, Woche beginnt am Montag. Jede Woche hat 7 Felder; Tage außerhalb des
     * Monats sind null. Je Monat 4 bis 6 Wochen.
     */
    fun weeks(month: YearMonth): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value // Montag = 0 … Sonntag = 6
        val cells = buildList<LocalDate?> {
            repeat(leading) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
            while (size % 7 != 0) add(null)
        }
        return cells.chunked(7)
    }

    /** Einträge, die mindestens einen Tag des Monats belegen, nach Start, Ende und Kennung sortiert. */
    fun entriesIn(entries: List<CalendarEntry>, month: YearMonth): List<CalendarEntry> =
        CalendarOverlap.find(entries, month.atDay(1).toString(), month.atEndOfMonth().toString())

    /** Anzahl der Einträge je Tag des Monats (nur belegte Tage). Ab 2 Einträgen überschneiden sich Nutzungen. */
    fun occupancy(entries: List<CalendarEntry>, month: YearMonth): Map<LocalDate, Int> {
        val counts = HashMap<LocalDate, Int>()
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val iso = date.toString()
            val count = entries.count { it.startDate <= iso && iso <= it.endDate }
            if (count > 0) counts[date] = count
        }
        return counts
    }

    /** Einträge, die am [today] laufen („aktuelle Nutzung“). */
    fun current(entries: List<CalendarEntry>, today: LocalDate): List<CalendarEntry> =
        CalendarOverlap.find(entries, today.toString(), today.toString())
}
