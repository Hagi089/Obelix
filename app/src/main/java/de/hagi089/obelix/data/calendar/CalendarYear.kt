package de.hagi089.obelix.data.calendar

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/** Berechnungen für die Jahresübersicht (reine Logik, baut auf [CalendarMonth] und [CalendarOverlap] auf). */
object CalendarYear {

    /** Einträge, die mindestens einen Tag des Jahres belegen, nach Start, Ende und Kennung sortiert. */
    fun entriesIn(entries: List<CalendarEntry>, year: Int): List<CalendarEntry> =
        CalendarOverlap.find(entries, LocalDate.of(year, 1, 1).toString(), LocalDate.of(year, 12, 31).toString())

    /** Einträge je belegtem Tag für alle zwölf Monate; Index 0 ist der Januar. */
    fun occupantsByMonth(entries: List<CalendarEntry>, year: Int): List<Map<LocalDate, List<CalendarEntry>>> =
        (1..12).map { CalendarMonth.occupants(entries, YearMonth.of(year, it)) }

    /** Anzahl der Tage des Jahres, an denen mindestens ein Eintrag läuft (Überschneidungen zählen einfach). */
    fun occupiedDays(occupantsByMonth: List<Map<LocalDate, List<CalendarEntry>>>): Int =
        occupantsByMonth.sumOf { it.size }

    fun daysInYear(year: Int): Int = Year.of(year).length()
}
