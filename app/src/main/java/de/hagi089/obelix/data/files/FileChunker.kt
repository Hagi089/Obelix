package de.hagi089.obelix.data.files

/** Teilt Dateien in Stücke für Firestore und setzt sie wieder zusammen. */
object FileChunker {

    /**
     * Teilt [bytes] in Stücke zu höchstens [FileLimits.CHUNK_SIZE_BYTES]. Alle Stücke außer dem letzten sind voll.
     * Leere oder zu große Dateien sind ein Programmfehler (vorher mit [FileValidator] prüfen).
     */
    fun split(bytes: ByteArray): List<ByteArray> {
        require(bytes.isNotEmpty()) { "Leere Datei" }
        require(bytes.size <= FileLimits.MAX_FILE_BYTES) { "Datei zu groß" }
        val chunkSize = FileLimits.CHUNK_SIZE_BYTES
        return (bytes.indices step chunkSize).map { start ->
            bytes.copyOfRange(start, minOf(start + chunkSize, bytes.size))
        }
    }

    /**
     * Setzt die Stücke (in der richtigen Reihenfolge) zusammen. Stimmen Anzahl oder Gesamtgröße nicht mit den
     * Metadaten überein, ist die Datei unvollständig gespeichert und das Ergebnis ist null.
     */
    fun join(parts: List<ByteArray>, expectedChunks: Int, expectedSize: Long): ByteArray? {
        if (parts.size != expectedChunks) return null
        if (parts.sumOf { it.size.toLong() } != expectedSize) return null
        if (expectedSize > FileLimits.MAX_FILE_BYTES) return null
        val result = ByteArray(expectedSize.toInt())
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }
}
