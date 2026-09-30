package de.hagi089.obelix.data.campsites

import androidx.annotation.StringRes
import de.hagi089.obelix.R

/**
 * Eingabeprüfung für Stellplätze (Anforderung 35). Das Datum prüft
 * [de.hagi089.obelix.data.finance.BookingValidator.date]. Muss zu firebase/firestore.rules (validCampsite) passen.
 */
object CampsiteValidator {

    const val MAX_COMMENT = 500
    const val MAX_NAME = 100
    const val MAX_ADDRESS = 200
    const val MAX_NOTE = 500
    const val MIN_RATING = 1
    const val MAX_RATING = 5

    /** Der Kommentar ist Pflicht (Anforderung 22). */
    @StringRes
    fun comment(text: String): Int? = when {
        text.isBlank() -> R.string.error_campsite_comment_required
        text.trim().length > MAX_COMMENT -> R.string.error_comment_too_long
        else -> null
    }

    @StringRes
    fun name(text: String): Int? = if (text.trim().length > MAX_NAME) R.string.error_campsite_name_too_long else null

    @StringRes
    fun address(text: String): Int? = if (text.trim().length > MAX_ADDRESS) R.string.error_campsite_address_too_long else null

    @StringRes
    fun note(text: String): Int? = if (text.trim().length > MAX_NOTE) R.string.error_campsite_note_too_long else null

    /** Die Bewertung ist optional; wenn vorhanden, dann 1 bis 5. */
    fun isValidRating(rating: Int?): Boolean = rating == null || rating in MIN_RATING..MAX_RATING

    /** Breite −90 bis 90 und Länge −180 bis 180, beide endlich (kein NaN, kein Unendlich). */
    fun isValidPosition(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
