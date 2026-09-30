package de.hagi089.obelix.data.repairs

import androidx.annotation.StringRes
import de.hagi089.obelix.R

/**
 * Eingabeprüfung für Auffälligkeiten (Anforderung 35). Datum und Kommentar prüft
 * [de.hagi089.obelix.data.finance.BookingValidator]. Muss zu firebase/firestore.rules (validRepair) passen.
 */
object RepairValidator {

    const val MAX_TITLE = 200
    const val MAX_DESCRIPTION = 2000

    @StringRes
    fun title(text: String): Int? = when {
        text.isBlank() -> R.string.error_repair_title_required
        text.trim().length > MAX_TITLE -> R.string.error_repair_title_too_long
        else -> null
    }

    @StringRes
    fun description(text: String): Int? = when {
        text.isBlank() -> R.string.error_repair_description_required
        text.trim().length > MAX_DESCRIPTION -> R.string.error_repair_description_too_long
        else -> null
    }
}
