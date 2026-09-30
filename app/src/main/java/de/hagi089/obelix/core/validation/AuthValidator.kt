package de.hagi089.obelix.core.validation

import androidx.annotation.StringRes
import de.hagi089.obelix.R
import de.hagi089.obelix.data.user.AccessCode

/**
 * Eingabeprüfung für die Anmeldeformulare. Liefert die Fehlermeldung (deutsch) oder null, wenn gültig.
 * Rein logisch und ohne Android-Abhängigkeit testbar.
 */
object AuthValidator {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NAME_LENGTH = 50

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    @StringRes
    fun email(value: String): Int? = when {
        value.isBlank() -> R.string.error_email_required
        !EMAIL_REGEX.matches(value.trim()) -> R.string.error_email_invalid
        else -> null
    }

    /** Beim Login wird nur geprüft, ob etwas eingegeben wurde (die Länge prüft der Server). */
    @StringRes
    fun loginPassword(value: String): Int? =
        if (value.isEmpty()) R.string.error_password_required else null

    /** Beim Registrieren gilt die Mindestlänge. */
    @StringRes
    fun newPassword(value: String): Int? = when {
        value.isEmpty() -> R.string.error_password_required
        value.length < MIN_PASSWORD_LENGTH -> R.string.error_password_too_short
        else -> null
    }

    @StringRes
    fun name(value: String): Int? = when {
        value.isBlank() -> R.string.error_name_required
        value.trim().length > MAX_NAME_LENGTH -> R.string.error_name_too_long
        else -> null
    }

    /** Zugangscode: 16 Zeichen; Bindestriche, Leerzeichen und Kleinbuchstaben sind bei der Eingabe erlaubt. */
    @StringRes
    fun accessCode(value: String): Int? {
        val normalized = AccessCode.normalize(value)
        return when {
            normalized.isEmpty() -> R.string.error_access_code_required
            !AccessCode.isValid(normalized) -> R.string.error_access_code_format
            else -> null
        }
    }
}
