package de.hagi089.obelix.data.campsites

import java.net.URLEncoder
import java.util.Locale

/**
 * Darstellung und Weitergabe von Koordinaten. Alle Formatierungen sind von der Gerätesprache unabhängig, wo ein
 * Programm sie liest (Adressen für andere Apps, Route): dort gilt immer der Dezimalpunkt.
 */
object GeoFormat {

    /** Anzeige für Menschen, deutsch: „48,13715° N, 11,57612° O“ (Komma als Dezimaltrennzeichen, N/S und O/W). */
    fun display(latitude: Double, longitude: Double): String {
        val ns = if (latitude >= 0) "N" else "S"
        val eo = if (longitude >= 0) "O" else "W"
        return String.format(Locale.GERMANY, "%.5f° %s, %.5f° %s", Math.abs(latitude), ns, Math.abs(longitude), eo)
    }

    /** Koordinaten als Text mit Dezimalpunkt, sechs Nachkommastellen (etwa 0,1 m). */
    fun plain(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)

    /** Direkt in der Navigation von Google Maps starten (nur dort verständlich). */
    fun navigationUri(latitude: Double, longitude: Double): String = "google.navigation:q=${plain(latitude, longitude)}&mode=d"

    /**
     * Ziel für jede Karten- oder Navigations-App (Android-Standard `geo:`). [label] erscheint als Bezeichnung des Ziels;
     * Klammern werden entfernt (sie begrenzen die Bezeichnung), Sonderzeichen werden codiert.
     */
    fun geoUri(latitude: Double, longitude: Double, label: String?): String {
        val point = plain(latitude, longitude)
        val clean = label?.replace("(", " ")?.replace(")", " ")?.trim().orEmpty()
        if (clean.isEmpty()) return "geo:0,0?q=$point"
        return "geo:0,0?q=$point(${URLEncoder.encode(clean, "UTF-8").replace("+", "%20")})"
    }

    /** Position als Text für die Route („Breite,Länge,Genauigkeit“, Dezimalpunkt); [decode] ist die Umkehrung. */
    fun encode(position: GeoPosition): String {
        val accuracy = position.accuracyMeters
        return if (accuracy == null) plain(position.latitude, position.longitude)
        else String.format(Locale.ROOT, "%.6f,%.6f,%.1f", position.latitude, position.longitude, accuracy)
    }

    /** Liest [encode] zurück; null bei jedem ungültigen Text oder ungültiger Position. */
    fun decode(text: String?): GeoPosition? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(',')
        if (parts.size != 2 && parts.size != 3) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        val accuracy = if (parts.size == 3) (parts[2].toFloatOrNull() ?: return null) else null
        if (!CampsiteValidator.isValidPosition(latitude, longitude)) return null
        if (accuracy != null && (!accuracy.isFinite() || accuracy < 0f)) return null
        return GeoPosition(latitude, longitude, accuracy)
    }
}
