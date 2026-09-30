package de.hagi089.obelix.data.files

import java.util.Locale

/** Lesbare Dateigröße für die Anzeige (deutsches Komma): „512 Byte", „412 KB", „1,5 MB". */
object FileSize {
    fun format(bytes: Long): String {
        if (bytes < 1024L) return "$bytes Byte"
        val kilobytes = Math.round(bytes / 1024.0)
        if (kilobytes < 1024L) return "$kilobytes KB"
        return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
