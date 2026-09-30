package de.hagi089.obelix.data.files

/** Eine Datei, die neu gespeichert werden soll (Inhalt bereits geprüft und ggf. verkleinert). */
class NewFile(
    val name: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

/**
 * Verweis auf eine gespeicherte Datei (Sammlung `files`). Steht z. B. als `receipt` in einer Ausgabe.
 * Muss zu firebase/firestore.rules (validReceiptRef) passen.
 */
data class FileRef(
    val fileId: String,
    val name: String,
    val contentType: String,
    val sizeBytes: Long,
) {
    val isPdf: Boolean get() = contentType == FileLimits.MIME_PDF

    fun toMap(): Map<String, Any> = mapOf(
        "fileId" to fileId,
        "name" to name,
        "contentType" to contentType,
        "sizeBytes" to sizeBytes,
    )

    companion object {
        /** Liest einen Verweis aus dem Firestore-Feld (Map); null, wenn das Feld fehlt oder unvollständig ist. */
        fun fromMap(map: Map<*, *>?): FileRef? {
            if (map == null) return null
            return FileRef(
                fileId = map["fileId"] as? String ?: return null,
                name = map["name"] as? String ?: return null,
                contentType = map["contentType"] as? String ?: return null,
                sizeBytes = (map["sizeBytes"] as? Number)?.toLong() ?: return null,
            )
        }
    }
}
