package de.hagi089.obelix.data.documents

import androidx.annotation.StringRes
import de.hagi089.obelix.R

/**
 * Eingabeprüfung für Dokumente (Anforderung 35). Liefert die deutsche Fehlermeldung oder null.
 * Größe und Typ der Datei prüft [de.hagi089.obelix.data.files.FileValidator]. Muss zu firebase/firestore.rules
 * (validDocument) passen.
 */
object DocumentValidator {

    const val MAX_NAME = 100

    @StringRes
    fun name(text: String): Int? = when {
        text.isBlank() -> R.string.error_document_name_required
        text.trim().length > MAX_NAME -> R.string.error_document_name_too_long
        else -> null
    }

    @StringRes
    fun category(category: DocumentCategory?): Int? = if (category == null) R.string.error_document_category_required else null

    /** Ein neues Dokument braucht eine Datei. */
    @StringRes
    fun file(hasFile: Boolean): Int? = if (hasFile) null else R.string.error_document_file_required
}
