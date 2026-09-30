package de.hagi089.obelix.data.repairs

import de.hagi089.obelix.data.planned.Priority

/** Filter der Liste. Standard ist „Offen“, damit offene Auffälligkeiten gut sichtbar sind (Anforderung 19). */
enum class RepairFilter {
    OPEN,
    DONE,
    ALL,
    ;

    fun matches(repair: Repair): Boolean = when (this) {
        OPEN -> repair.status == RepairStatus.OPEN
        DONE -> repair.status == RepairStatus.DONE
        ALL -> true
    }
}

/** Reine Logik der Liste (ohne Oberfläche). */
object RepairLogic {

    fun openCount(repairs: List<Repair>): Int = repairs.count { it.status == RepairStatus.OPEN }

    /**
     * Sortierung: offene vor erledigten, dann nach Priorität (hoch zuerst, ohne Priorität zuletzt), dann das
     * neueste Datum zuerst, dann nach Titel und Kennung (stabil).
     */
    fun sorted(repairs: List<Repair>): List<Repair> = repairs.sortedWith(
        compareBy<Repair> { it.status.ordinal }
            .thenBy { rank(it.priority) }
            .thenByDescending { it.date }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id },
    )

    private fun rank(priority: Priority?): Int = when (priority) {
        Priority.HIGH -> 0
        Priority.MEDIUM -> 1
        Priority.LOW -> 2
        null -> 3
    }
}
