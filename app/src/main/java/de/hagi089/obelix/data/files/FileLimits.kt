package de.hagi089.obelix.data.files

/**
 * Grenzen der Dateiablage (Phase 6, Option F: Dateien in Stücken in Firestore).
 * Muss zu firebase/firestore.rules (validFile, chunks) passen: 900 KiB je Stück, höchstens 10 Stücke,
 * höchstens 8 MiB je Datei, nur JPEG und PDF.
 */
object FileLimits {
    /** Größe eines Stücks. Firestore erlaubt 1 MiB je Dokument; der Rest ist Reserve für Pfad und Feldnamen. */
    const val CHUNK_SIZE_BYTES = 900 * 1024

    /** Höchstgröße einer gespeicherten Datei (unter dem Limit von 10 MiB je Schreibanfrage). */
    const val MAX_FILE_BYTES = 8 * 1024 * 1024

    /** 8 MiB in Stücken zu 900 KiB: neun volle Stücke und ein Rest. */
    const val MAX_CHUNKS = 10

    /** Lange Seite eines gespeicherten Bildes (Anforderung 5: ungefähr 1600 bis 2000 Pixel). */
    const val IMAGE_MAX_SIDE_PX = 1800
    const val JPEG_QUALITY = 80

    /** Bilder werden vor dem Verkleinern gelesen; größere Quelldateien werden abgelehnt (Speicherschutz). */
    const val MAX_SOURCE_IMAGE_BYTES = 30 * 1024 * 1024

    const val MIME_JPEG = "image/jpeg"
    const val MIME_PDF = "application/pdf"

    const val MAX_NAME_LENGTH = 200

    /** Dateivorgänge brauchen länger als ein normaler Schreibvorgang (Firestore erlaubt Transaktionen bis 270 s). */
    const val TIMEOUT_MS = 120_000L

    /** Anzahl Stücke für eine Datei der Größe [sizeBytes] (mindestens 1). */
    fun chunkCountFor(sizeBytes: Long): Int =
        ((sizeBytes + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt().coerceAtLeast(1)

    /** Gespeichert werden nur JPEG und PDF (Bilder werden beim Hochladen in JPEG umgewandelt). */
    fun isStoredType(contentType: String?): Boolean = contentType == MIME_JPEG || contentType == MIME_PDF
}
