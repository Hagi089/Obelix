package de.hagi089.obelix.data.files

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zwischenspeicher für PDF-Belege, die in einer externen PDF-App geöffnet werden (die App kann PDFs nicht selbst
 * anzeigen). Liegt im privaten Cache-Ordner der App; nur der zuletzt geöffnete Beleg bleibt erhalten.
 * Der Ordner ist in res/xml/file_paths.xml für den FileProvider freigegeben.
 */
class ReceiptCache(context: Context) {

    private val directory = File(context.applicationContext.cacheDir, DIRECTORY)

    suspend fun writePdf(fileId: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        require(fileId.isNotEmpty() && fileId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Ungültige Datei-ID" }
        directory.mkdirs()
        val target = File(directory, "$fileId.pdf")
        directory.listFiles()?.filter { it != target }?.forEach { it.delete() }
        target.writeBytes(bytes)
        target
    }

    companion object {
        const val DIRECTORY = "receipts"
    }
}
