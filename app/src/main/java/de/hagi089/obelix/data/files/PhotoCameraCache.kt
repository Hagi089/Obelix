package de.hagi089.obelix.data.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Zielordner für Kamerafotos (Stellplätze, Phase 9). Die Kamera-App schreibt das Foto über den FileProvider in diese
 * Datei; die App liest sie danach wie eine Galerie-Auswahl. Dafür braucht die App **keine** Kamera-Berechtigung.
 * Liegt im privaten Cache-Ordner; es bleibt nur die jeweils letzte Aufnahme erhalten (Ordner in res/xml/file_paths.xml).
 */
class PhotoCameraCache(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, DIRECTORY)

    /** Legt eine leere Zieldatei an (ältere Aufnahmen werden gelöscht) und liefert die Adresse für die Kamera-App. */
    fun newCaptureUri(): Uri {
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, "aufnahme-${System.currentTimeMillis()}.jpg")
        target.createNewFile()
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", target)
    }

    companion object {
        const val DIRECTORY = "camera"
    }
}
