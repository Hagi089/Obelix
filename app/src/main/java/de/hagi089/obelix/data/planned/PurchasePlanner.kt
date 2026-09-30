package de.hagi089.obelix.data.planned

import de.hagi089.obelix.data.finance.BookingInput
import de.hagi089.obelix.data.finance.BookingType

/**
 * Macht aus einer geplanten Ausgabe und den tatsächlichen Kaufdaten die echte Ausgabe (Anforderung 21).
 * Für die Finanzen zählt der **tatsächliche** Betrag, nie die Schätzung.
 */
object PurchasePlanner {

    fun toBooking(plan: PlannedExpense, purchase: PurchaseInput): BookingInput = BookingInput(
        type = BookingType.EXPENSE,
        date = purchase.date,
        amountCents = purchase.actualAmountCents,
        categoryId = purchase.categoryId,
        paidByUid = purchase.paidByUid,
        settlement = purchase.settlement,
        description = plan.title,
        comment = plan.comment,
        plannedExpenseId = plan.id,
    )
}

/** Kennzahlen der Planung. Alles in Cent. */
data class PlannedSummary(val openCount: Int, val openEstimatedCents: Long)

object PlannedCalculator {

    /** Summe und Anzahl der noch offenen (nicht gekauften) Planungen. Gekaufte zählen hier nicht mehr. */
    fun summarize(plans: List<PlannedExpense>): PlannedSummary {
        val open = plans.filter { it.status == PlannedStatus.PLANNED }
        return PlannedSummary(openCount = open.size, openEstimatedCents = open.sumOf { it.estimatedAmountCents })
    }
}
