package de.hagi089.obelix.data.finance

import androidx.annotation.StringRes
import de.hagi089.obelix.R
import de.hagi089.obelix.core.money.Money
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Eingabeprüfung für Buchungen (Anforderung 35). Liefert die deutsche Fehlermeldung oder null.
 * Muss zu firebase/firestore.rules (validBooking) passen.
 */
object BookingValidator {

    const val MAX_DESCRIPTION = 200
    const val MAX_COMMENT = 500
    const val MAX_CATEGORY_NAME = 50

    /** Der Betrag muss eine Zahl mit höchstens 2 Nachkommastellen sein, größer als 0 und höchstens [Money.MAX_CENTS]. */
    @StringRes
    fun amount(text: String): Int? {
        val cents = Money.parse(text) ?: return if (text.isBlank()) R.string.error_amount_required else R.string.error_amount_invalid
        return when {
            cents <= 0L -> R.string.error_amount_positive
            cents > Money.MAX_CENTS -> R.string.error_amount_too_high
            else -> null
        }
    }

    /** Kalendertag im Format yyyy-MM-dd, der wirklich existiert. */
    @StringRes
    fun date(iso: String): Int? = try {
        LocalDate.parse(iso)
        null
    } catch (e: DateTimeParseException) {
        R.string.error_date_required
    }

    @StringRes
    fun category(categoryId: String?): Int? = if (categoryId.isNullOrBlank()) R.string.error_category_required else null

    /** Bei Ausgaben ist der Zahler Pflicht, bei Einnahmen optional. */
    @StringRes
    fun payer(type: BookingType, uid: String?): Int? =
        if (type == BookingType.EXPENSE && uid.isNullOrBlank()) R.string.error_payer_required else null

    @StringRes
    fun description(text: String): Int? = when {
        text.isBlank() -> R.string.error_description_required
        text.trim().length > MAX_DESCRIPTION -> R.string.error_description_too_long
        else -> null
    }

    @StringRes
    fun comment(text: String): Int? = if (text.trim().length > MAX_COMMENT) R.string.error_comment_too_long else null

    @StringRes
    fun categoryName(text: String): Int? = when {
        text.isBlank() -> R.string.error_category_name_required
        text.trim().length > MAX_CATEGORY_NAME -> R.string.error_category_name_too_long
        else -> null
    }
}
