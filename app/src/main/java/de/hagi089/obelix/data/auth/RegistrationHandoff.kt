package de.hagi089.obelix.data.auth

import de.hagi089.obelix.core.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Übergabe zwischen Registrierungsformular und Einrichtung des Haushalts (nur im Arbeitsspeicher).
 * Das Formular legt Code und Name ab, bevor das Konto entsteht; die Einrichtung übernimmt sie.
 * Wird ein Konto wegen eines ungültigen Codes wieder gelöscht, steht der Grund in [notice].
 */
class RegistrationHandoff {
    val pendingCode = MutableStateFlow<String?>(null)
    val pendingName = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<AppError?>(null)
}
