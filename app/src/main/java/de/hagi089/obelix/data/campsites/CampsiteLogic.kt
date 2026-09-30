package de.hagi089.obelix.data.campsites

import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile

/** Reine Logik zu Stellplätzen (ohne Android, damit sie sich testen lässt). */
object CampsiteLogic {

    /** Neueste zuerst (nach Datum), bei gleichem Datum nach Kennung, damit die Reihenfolge stabil bleibt. */
    fun sorted(campsites: List<Campsite>): List<Campsite> =
        campsites.sortedWith(compareByDescending<Campsite> { it.date }.thenBy { it.id })

    /** Leere oder nur aus Leerzeichen bestehende optionale Texte werden nicht gespeichert. */
    fun blankToNull(text: String): String? = text.trim().ifEmpty { null }
}

/**
 * Fotos im Formular: bereits gespeicherte ([saved], davon einige zum Entfernen markiert) und neu gewählte.
 * Insgesamt höchstens [MAX_CAMPSITE_PHOTOS]; entfernte zählen nicht mehr mit.
 */
data class CampsitePhotoSet(
    val saved: List<FileRef> = emptyList(),
    val removedIds: Set<String> = emptySet(),
    val added: List<NewFile> = emptyList(),
) {
    /** Gespeicherte Fotos, die erhalten bleiben. */
    val kept: List<FileRef> get() = saved.filter { it.fileId !in removedIds }

    val count: Int get() = kept.size + added.size

    val canAdd: Boolean get() = count < MAX_CAMPSITE_PHOTOS

    /** Hängt ein Foto an; bei voller Liste bleibt alles unverändert (null). */
    fun withAdded(file: NewFile): CampsitePhotoSet? = if (canAdd) copy(added = added + file) else null

    fun withoutAdded(index: Int): CampsitePhotoSet =
        if (index in added.indices) copy(added = added.filterIndexed { i, _ -> i != index }) else this

    /** Markiert ein gespeichertes Foto zum Entfernen (beim Speichern); unbekannte Kennungen werden ignoriert. */
    fun withRemoved(fileId: String): CampsitePhotoSet =
        if (saved.any { it.fileId == fileId }) copy(removedIds = removedIds + fileId) else this
}
