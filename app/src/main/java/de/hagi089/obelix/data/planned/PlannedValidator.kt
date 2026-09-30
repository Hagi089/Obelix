package de.hagi089.obelix.data.planned

import androidx.annotation.StringRes
import de.hagi089.obelix.R

/**
 * Eingabeprüfung für geplante Ausgaben (Anforderung 35). Betrag, Datum und Kommentar prüft
 * [de.hagi089.obelix.data.finance.BookingValidator]. Muss zu firebase/firestore.rules (validPlanned) passen.
 */
object PlannedValidator {

    const val MAX_TITLE = 200
    const val MAX_LINK = 500

    private val LINK = Regex("^https?://[^ ]+$")

    @StringRes
    fun title(text: String): Int? = when {
        text.isBlank() -> R.string.error_planned_title_required
        text.trim().length > MAX_TITLE -> R.string.error_planned_title_too_long
        else -> null
    }

    /** Der Link ist optional; wenn vorhanden, muss er mit http:// oder https:// beginnen und darf keine Leerzeichen enthalten. */
    @StringRes
    fun link(text: String): Int? {
        val value = text.trim()
        return when {
            value.isEmpty() -> null
            value.length > MAX_LINK -> R.string.error_planned_link_invalid
            !LINK.matches(value) -> R.string.error_planned_link_invalid
            else -> null
        }
    }
}
