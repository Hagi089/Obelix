package de.hagi089.obelix.data.calendar

/**
 * Überschneidung von Nutzungszeiträumen (Entscheidung 5: Überschneidung ist speicherbar, muss aber vor dem
 * Speichern angezeigt werden).
 *
 * Start- und Endtag zählen mit. Überschneidung heißt daher: Die Zeiträume haben mindestens einen Tag gemeinsam.
 * Gleicher Tag, „Ende = Start des anderen“ und umschließende Zeiträume überschneiden sich, direkt aufeinanderfolgende
 * Tage (Ende am 10., Start am 11.) nicht.
 *
 * Verglichen werden die Tage als Text `yyyy-MM-dd`: Bei diesem Format entspricht die Textreihenfolge der
 * zeitlichen Reihenfolge, und Firestore-Abfrage und Regeln vergleichen ebenso.
 */
object CalendarOverlap {

    fun overlaps(startA: String, endA: String, startB: String, endB: String): Boolean =
        startA <= endB && startB <= endA

    /**
     * Alle Einträge, die den Zeitraum [startDate] bis [endDate] berühren, nach Start, Ende und Kennung sortiert.
     * Beim Bearbeiten wird der Eintrag selbst ([excludeId]) nicht mitgezählt.
     */
    fun find(entries: List<CalendarEntry>, startDate: String, endDate: String, excludeId: String? = null): List<CalendarEntry> =
        entries
            .filter { it.id != excludeId && overlaps(it.startDate, it.endDate, startDate, endDate) }
            .sortedWith(compareBy<CalendarEntry> { it.startDate }.thenBy { it.endDate }.thenBy { it.id })
}
