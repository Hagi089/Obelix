package de.hagi089.obelix.data.files

import androidx.annotation.StringRes
import de.hagi089.obelix.R

/** Eingabeprüfung für Dateien (Anforderung 35: sinnvolle Dateigröße und Dateitypen). Liefert die Fehlermeldung oder null. */
object FileValidator {

    /** Prüft Größe und Typ einer **fertig aufbereiteten** Datei (so, wie sie gespeichert würde). */
    @StringRes
    fun check(sizeBytes: Long, contentType: String?): Int? = when {
        !FileLimits.isStoredType(contentType) -> R.string.error_file_type
        sizeBytes <= 0L -> R.string.error_file_empty
        sizeBytes > FileLimits.MAX_FILE_BYTES -> R.string.error_file_too_large
        else -> null
    }

    /** Bereinigt einen Dateinamen: ohne Pfad und Zeilenumbrüche, höchstens 200 Zeichen, nie leer. */
    fun cleanName(raw: String?, fallback: String): String {
        val base = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\r\\n\\t]"), " ").trim()
        if (base.isEmpty()) return fallback
        if (base.length <= FileLimits.MAX_NAME_LENGTH) return base
        val dot = base.lastIndexOf('.')
        val extension = if (dot > 0 && base.length - dot <= 10) base.substring(dot) else ""
        return base.substring(0, FileLimits.MAX_NAME_LENGTH - extension.length) + extension
    }

    /** Bilder werden als JPEG gespeichert: die Endung wird zu `.jpg` (aus „foto.png" wird „foto.jpg"). */
    fun jpegName(name: String): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        return "$stem.jpg".let { if (it.length > FileLimits.MAX_NAME_LENGTH) it.takeLast(FileLimits.MAX_NAME_LENGTH) else it }
    }
}
