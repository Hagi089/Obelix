package de.hagi089.obelix.data.auth

import de.hagi089.obelix.core.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Übergabe zwischen Registrierungsformular und Codebildschirm (nur im Arbeitsspeicher).
 * Das Formular legt Code und Name ab, bevor das Konto entsteht; der Codebildschirm legt damit das Benutzerdokument an.
 * Wird ein Konto wegen eines ungültigen Codes wieder gelöscht, steht der Grund in [notice].
 */
class RegistrationHandoff {
    val pendingCode = MutableStateFlow<String?>(null)
    val pendingName = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<AppError?>(null)
}
