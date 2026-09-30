package de.hagi089.obelix.data.finance

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
)

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
)

/** Finanzkategorie (Sammlung `categories`). Wird nie gelöscht, nur deaktiviert. */
data class Category(val id: String, val name: String, val active: Boolean)
