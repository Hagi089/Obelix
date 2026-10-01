package de.hagi089.obelix.data.campsites

import de.hagi089.obelix.data.files.FileRef

/** Höchstzahl Fotos je Stellplatz (Anforderung 24). Muss zu firebase/firestore.rules (validCampsite) passen. */
const val MAX_CAMPSITE_PHOTOS = 3

/** Eine ermittelte Position. [accuracyMeters] ist die vom Gerät gemeldete Genauigkeit (null, wenn unbekannt). */
data class GeoPosition(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null)

/** Gespeicherter Stellplatz (Sammlung `campsites`). Kalendertag als `yyyy-MM-dd`. Breite und Länge ändert nur die Positionskorrektur (Marker verschieben). */
data class Campsite(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val date: String,
    val comment: String,
    val name: String?,
    val address: String?,
    val note: String?,
    val rating: Int?,
    val photos: List<FileRef>,
    /** Benutzer, der den Stellplatz gespeichert hat. */
    val createdBy: String,
) {
    val position: GeoPosition get() = GeoPosition(latitude, longitude)
}

/**
 * Eingabe zum Anlegen oder Ändern (Text bereits getrimmt, leere optionale Felder als null).
 * Die Position steht beim Anlegen getrennt und ändert sich danach nur über die Positionskorrektur
 * ([CampsiteRepository.updatePosition]); das Datum ändert sich nie.
 */
data class CampsiteInput(
    val comment: String,
    val name: String?,
    val address: String?,
    val note: String?,
    val rating: Int?,
)
