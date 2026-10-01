package de.hagi089.obelix.data.documents

import de.hagi089.obelix.data.files.FileRef

/**
 * Kategorie eines Dokuments. Die sechs Beispiele aus Anforderung 27 (Fahrzeug, Versicherung, Rechnung, Garantie,
 * Bedienungsanleitung, Sonstiges). Muss zu firebase/firestore.rules (validDocument) passen.
 */
enum class DocumentCategory {
    VEHICLE,
    INSURANCE,
    INVOICE,
    WARRANTY,
    MANUAL,
    OTHER,
    ;

    companion object {
        fun from(value: String?): DocumentCategory? = entries.firstOrNull { it.name == value }
    }
}

/** Wichtige Unterlage des Wohnmobils mit genau einer Datei (Sammlung `documents`). Kalendertag als `yyyy-MM-dd`. */
data class Document(
    val id: String,
    val name: String,
    val category: DocumentCategory,
    /** Verweis auf die Datei in der Dateiablage (`files`), wie beim Beleg. */
    val file: FileRef,
    /** Tag des Hochladens. */
    val date: String,
    /** Benutzer, der das Dokument hochgeladen hat. */
    val createdBy: String,
)

/** Eingabe zum Anlegen oder Ändern (Name und Kategorie; die Datei ist nach dem Anlegen unveränderlich). */
data class DocumentInput(
    val name: String,
    val category: DocumentCategory,
)
