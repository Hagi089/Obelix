package de.hagi089.obelix.data.documents

/** Reine Logik zu Dokumenten (ohne Android, damit sie sich testen lässt). */
object DocumentLogic {

    /** Neueste zuerst (nach Datum), bei gleichem Datum nach Name und Kennung, damit die Reihenfolge stabil bleibt. */
    fun sorted(documents: List<Document>): List<Document> =
        documents.sortedWith(compareByDescending<Document> { it.date }.thenBy { it.name.lowercase() }.thenBy { it.id })

    /** Filter der Liste: null zeigt alle Dokumente, sonst nur die der Kategorie. */
    fun filtered(documents: List<Document>, category: DocumentCategory?): List<Document> =
        if (category == null) documents else documents.filter { it.category == category }

    /**
     * Vorschlag für den Namen aus dem Dateinamen: ohne Endung (nur wenn sie wie eine Endung aussieht: Punkt nicht am
     * Anfang, höchstens 10 Zeichen), getrimmt, höchstens [DocumentValidator.MAX_NAME] Zeichen. Leer, wenn nichts bleibt.
     */
    fun suggestedName(fileName: String): String {
        val trimmed = fileName.trim()
        val dot = trimmed.lastIndexOf('.')
        val stem = if (dot > 0 && trimmed.length - dot <= MAX_EXTENSION_LENGTH) trimmed.substring(0, dot) else trimmed
        return stem.trim().take(DocumentValidator.MAX_NAME).trim()
    }

    private const val MAX_EXTENSION_LENGTH = 10
}
