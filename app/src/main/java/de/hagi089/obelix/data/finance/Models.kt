package de.hagi089.obelix.data.finance

import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile

/** Einnahme oder Ausgabe. Der Betrag ist immer positiv; das Vorzeichen ergibt sich aus dem Typ. */
enum class BookingType {
    INCOME,
    EXPENSE,
    ;

    companion object {
        fun from(value: String?): BookingType? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Abrechnungsstatus (aus der Excel-Spalte „Kosten beglichen"):
 * OPEN = privat ausgelegt, noch nicht erstattet (offene Forderung des Zahlers),
 * SETTLED = beglichen (zählt zum Kontostand), SPONSORED = gesponsert (keine Erstattung, kein Kontostand).
 * Einnahmen sind immer SETTLED.
 */
enum class Settlement {
    OPEN,
    SETTLED,
    SPONSORED,
    ;

    companion object {
        fun from(value: String?): Settlement? = entries.firstOrNull { it.name == value }
    }
}

/** Buchung in Firestore (Sammlung `transactions`). Kalendertag als `yyyy-MM-dd`. */
data class Booking(
    val id: String,
    val type: BookingType,
    val date: String,
    val amountCents: Long,
    val categoryId: String,
    /** Zahler (Ausgabe) bzw. Einzahler (Einnahme, optional): uid des Benutzers. */
    val paidByUid: String?,
    val settlement: Settlement,
    val description: String,
    val comment: String,
    /** Nur bei importierten Buchungen: Herkunft (Excel-Zeile). */
    val importRef: String?,
    /** Nur bei Buchungen, die aus einer geplanten Ausgabe entstanden sind (Phase 5): Verweis auf die Planung. */
    val plannedExpenseId: String? = null,
    /** Beleg (Phase 6): Verweis auf die Datei in der Dateiablage; nur bei Ausgaben. */
    val receipt: FileRef? = null,
)

/** Was beim Speichern einer bestehenden Buchung mit ihrem Beleg geschehen soll (Phase 6). */
sealed interface ReceiptChange {
    /** Beleg unverändert lassen (auch wenn keiner vorhanden ist). */
    data object Keep : ReceiptChange

    /** Beleg entfernen; die Datei wird im selben Schritt gelöscht. */
    data object Remove : ReceiptChange

    /** Neuen Beleg speichern; ein vorhandener alter Beleg wird danach gelöscht. */
    class Replace(val file: NewFile) : ReceiptChange
}

/** Eingabe zum Anlegen oder Ändern einer Buchung. */
data class BookingInput(
    val type: BookingType,
    val date: String,
    val amountCents: Long,
    val categoryId: String,
    val paidByUid: String?,
    val settlement: Settlement,
    val description: String,
    val comment: String,
    val importRef: String? = null,
    /** Nur beim Kauf einer geplanten Ausgabe: Verweis auf die Planung. */
    val plannedExpenseId: String? = null,
)

/** Finanzkategorie (Sammlung `categories`). Wird nie gelöscht, nur deaktiviert. */
data class Category(val id: String, val name: String, val active: Boolean)
