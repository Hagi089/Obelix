package de.hagi089.obelix.data.repairs

import de.hagi089.obelix.data.planned.Priority

/** Status einer Auffälligkeit (Oberfläche: OFFEN / ERLEDIGT). */
enum class RepairStatus {
    OPEN,
    DONE,
    ;

    companion object {
        fun from(value: String?): RepairStatus? = entries.firstOrNull { it.name == value }
    }
}

/** Auffälligkeit oder offene Arbeit am Wohnmobil (Sammlung `repairs`). Kalendertag als `yyyy-MM-dd`. */
data class Repair(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val status: RepairStatus,
    val priority: Priority?,
    val comment: String,
    /** Benutzer, der die Auffälligkeit angelegt hat. */
    val createdBy: String,
    /** Optional: Kalendertag (`yyyy-MM-dd`), bis zu dem die Auffälligkeit erledigt sein soll („Geplant bis zum“). */
    val plannedUntil: String? = null,
)

/** Eingabe zum Anlegen oder Ändern. Der Status wird getrennt übergeben. */
data class RepairInput(
    val title: String,
    val description: String,
    val date: String,
    val priority: Priority?,
    val comment: String,
    /** Optional: „Geplant bis zum“ als `yyyy-MM-dd`; null = kein Datum. */
    val plannedUntil: String? = null,
)
