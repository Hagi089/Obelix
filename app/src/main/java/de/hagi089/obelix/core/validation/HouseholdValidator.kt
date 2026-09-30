package de.hagi089.obelix.core.validation

import androidx.annotation.StringRes
import de.hagi089.obelix.R
import de.hagi089.obelix.data.household.AccessCode

/** Eingabeprüfung für Zugangscode, Haushaltsname und Parteinamen. Grenzen passen zu den Firestore-Regeln. */
object HouseholdValidator {

    const val MAX_HOUSEHOLD_NAME_LENGTH = 60
    const val MAX_PARTY_NAME_LENGTH = 40

    @StringRes
    fun accessCode(value: String): Int? {
        val normalized = AccessCode.normalize(value)
        return when {
            normalized.isEmpty() -> R.string.error_access_code_required
            !AccessCode.isValid(normalized) -> R.string.error_access_code_format
            else -> null
        }
    }

    @StringRes
    fun householdName(value: String): Int? = when {
        value.isBlank() -> R.string.error_household_name_required
        value.trim().length > MAX_HOUSEHOLD_NAME_LENGTH -> R.string.error_text_too_long
        else -> null
    }

    @StringRes
    fun partyName(value: String): Int? = when {
        value.isBlank() -> R.string.error_party_name_required
        value.trim().length > MAX_PARTY_NAME_LENGTH -> R.string.error_text_too_long
        else -> null
    }
}
