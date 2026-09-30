package de.hagi089.obelix.data.planned

import de.hagi089.obelix.data.finance.Settlement

/** Status einer geplanten Ausgabe (Oberfläche: GEPLANT / GEKAUFT). */
enum class PlannedStatus {
    PLANNED,
    PURCHASED,
    ;

    companion object {
        fun from(value: String?): PlannedStatus? = entries.firstOrNull { it.name == value }
    }
}

/** Optionale Priorität einer Planung. */
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {
        fun from(value: String?): Priority? = entries.firstOrNull { it.name == value }
    }
}

/** Geplante Ausgabe in Firestore (Sammlung `plannedExpenses`). Kalendertag als `yyyy-MM-dd`. */
data class PlannedExpense(
    val id: String,
    val title: String,
    /** Geschätzter Betrag in Cent. Er zählt nirgends im Kontostand (Anforderung 16). */
    val estimatedAmountCents: Long,
    val plannedDate: String,
    val status: PlannedStatus,
    val priority: Priority?,
    val link: String?,
    val comment: String,
    /** Nur bei GEKAUFT: die Buchung, die beim Kauf entstanden ist. */
    val purchasedTransactionId: String?,
    /** Benutzer, der die Planung angelegt hat. */
    val createdBy: String,
)

/** Eingabe zum Anlegen oder Ändern einer Planung. */
data class PlannedInput(
    val title: String,
    val estimatedAmountCents: Long,
    val plannedDate: String,
    val priority: Priority?,
    val link: String?,
    val comment: String,
)

/** Angaben beim Kauf („Gekauft"): die tatsächlichen Werte, aus denen die Ausgabe entsteht. */
data class PurchaseInput(
    val actualAmountCents: Long,
    val date: String,
    val paidByUid: String,
    val categoryId: String,
    val settlement: Settlement,
)
