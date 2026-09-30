package de.hagi089.obelix.data.calendar

import androidx.annotation.StringRes
import de.hagi089.obelix.R
import de.hagi089.obelix.data.finance.BookingValidator
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Eingabeprüfung für Kalendereinträge (Anforderung 35). Kommentar und einzelne Tage prüft
 * [BookingValidator]. Muss zu firebase/firestore.rules (validCalendar) passen.
 */
object CalendarValidator {

    const val MAX_DESTINATION = 100

    /** Meldung, wenn das Ende vor dem Start liegt; sonst null. Ist ein Tag ungültig, prüft das [BookingValidator.date]. */
    @StringRes
    fun range(startDate: String, endDate: String): Int? {
        val start = parse(startDate) ?: return null
        val end = parse(endDate) ?: return null
        return if (end.isBefore(start)) R.string.error_calendar_end_before_start else null
    }

    @StringRes
    fun person(uid: String?): Int? = if (uid.isNullOrBlank()) R.string.error_calendar_person_required else null

    /** Das Ziel ist optional. */
    @StringRes
    fun destination(text: String): Int? =
        if (text.trim().length > MAX_DESTINATION) R.string.error_calendar_destination_too_long else null

    private fun parse(iso: String): LocalDate? = try {
        LocalDate.parse(iso)
    } catch (e: DateTimeParseException) {
        null
    }
}
