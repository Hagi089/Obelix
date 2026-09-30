package de.hagi089.obelix.data.calendar

/**
 * Nutzung des Wohnmobils in Firestore (Sammlung `calendarEntries`). Die Tage stehen als `yyyy-MM-dd`;
 * Start- und Endtag zählen beide zur Nutzung (ein Eintrag mit gleichem Start und Ende belegt einen Tag).
 */
data class CalendarEntry(
    val id: String,
    val startDate: String,
    val endDate: String,
    /** Benutzer, der das Wohnmobil nutzt. */
    val personUid: String,
    /** Name dieses Benutzers zum Zeitpunkt des Eintrags (bleibt lesbar, auch wenn das Konto entfernt wird). */
    val personName: String,
    val destination: String?,
    val comment: String,
    /** Benutzer, der den Eintrag angelegt hat. */
    val createdBy: String,
)

/** Eingabe zum Anlegen oder Ändern eines Eintrags. */
data class CalendarInput(
    val startDate: String,
    val endDate: String,
    val personUid: String,
    val personName: String,
    val destination: String?,
    val comment: String,
)
