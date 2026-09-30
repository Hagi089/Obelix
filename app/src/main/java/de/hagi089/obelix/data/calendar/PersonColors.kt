package de.hagi089.obelix.data.calendar

/** Farbe einer Person im Kalender als ARGB-Wert; [content] ist die gut lesbare Schriftfarbe darauf. */
data class PersonColor(val background: Long, val content: Long)

/**
 * Ordnet jeder Person eine feste Farbe zu, ohne dass etwas gespeichert wird. Die Zuordnung ergibt sich aus der
 * nach Benutzerkennung sortierten Personenliste und ist deshalb auf allen Geräten gleich. Die Palette enthält
 * bewusst keine roten, rosa oder orangen Töne: Rot ist im Kalender für Überschneidungen reserviert.
 */
object PersonColors {
    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L

    val palette: List<PersonColor> = listOf(
        PersonColor(0xFF1565C0, WHITE), // Blau
        PersonColor(0xFF2E7D32, WHITE), // Grün
        PersonColor(0xFF6A1B9A, WHITE), // Violett
        PersonColor(0xFFFDD835, BLACK), // Gelb
        PersonColor(0xFF00796B, WHITE), // Petrol
        PersonColor(0xFF827717, WHITE), // Oliv
        PersonColor(0xFF455A64, WHITE), // Schiefergrau
        PersonColor(0xFF3949AB, WHITE), // Indigo
    )

    /**
     * Farbe je Benutzerkennung. Berücksichtigt alle [userUids] und zusätzlich die [entryUids] der Einträge
     * (z. B. entfernte Benutzer), damit jeder Eintrag eine Farbe hat, auch wenn die Benutzerliste nicht
     * geladen werden konnte. Gibt es mehr Personen als Farben, beginnt die Palette von vorn.
     */
    fun assign(userUids: Collection<String>, entryUids: Collection<String>): Map<String, PersonColor> =
        (userUids + entryUids).toSortedSet().withIndex()
            .associate { (index, uid) -> uid to palette[index % palette.size] }
}
