package de.hagi089.obelix.data.planned

import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.FinanceCalculator
import de.hagi089.obelix.data.finance.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchasePlannerTest {

    private fun plan(id: String = "p1", cents: Long = 50_000, status: PlannedStatus = PlannedStatus.PLANNED, comment: String = "") =
        PlannedExpense(
            id = id,
            title = "Neue Batterie",
            estimatedAmountCents = cents,
            plannedDate = "2026-09-30",
            status = status,
            priority = null,
            link = null,
            comment = comment,
            purchasedTransactionId = if (status == PlannedStatus.PURCHASED) "b1" else null,
            createdBy = "u1",
        )

    private fun purchase(cents: Long, settlement: Settlement = Settlement.SETTLED) = PurchaseInput(
        actualAmountCents = cents,
        date = "2026-10-05",
        paidByUid = "u2",
        categoryId = "cat-elektro",
        settlement = settlement,
    )

    private fun asBooking(input: de.hagi089.obelix.data.finance.BookingInput) = Booking(
        id = "b1",
        type = input.type,
        date = input.date,
        amountCents = input.amountCents,
        categoryId = input.categoryId,
        paidByUid = input.paidByUid,
        settlement = input.settlement,
        description = input.description,
        comment = input.comment,
        importRef = input.importRef,
        plannedExpenseId = input.plannedExpenseId,
    )

    @Test
    fun purchase_usesTheActualAmountNotTheEstimate() {
        // 500 € geplant, 472 € gekauft ⇒ Ausgabe 472 €, nicht 500 €.
        val booking = PurchasePlanner.toBooking(plan(cents = 50_000), purchase(47_200))
        assertEquals(47_200L, booking.amountCents)
    }

    @Test
    fun purchase_takesAllFieldsFromThePurchaseDialog() {
        val booking = PurchasePlanner.toBooking(plan(), purchase(47_200, Settlement.OPEN))
        assertEquals(BookingType.EXPENSE, booking.type)
        assertEquals("2026-10-05", booking.date)
        assertEquals("u2", booking.paidByUid)
        assertEquals("cat-elektro", booking.categoryId)
        assertEquals(Settlement.OPEN, booking.settlement)
    }

    @Test
    fun purchase_titleBecomesDescription_commentIsKept_andLinksBackToThePlan() {
        val booking = PurchasePlanner.toBooking(plan(id = "p7", comment = "AGM 95 Ah"), purchase(47_200))
        assertEquals("Neue Batterie", booking.description)
        assertEquals("AGM 95 Ah", booking.comment)
        assertEquals("p7", booking.plannedExpenseId)
        assertNull(booking.importRef)
    }

    @Test
    fun plansNeverChangeTheBalance_onlyThePurchaseDoes() {
        val before = FinanceCalculator.summarize(emptyList())
        // Planungen sind keine Buchungen: Sie tauchen im Kontostand nicht auf, egal wie viele es gibt.
        assertEquals(0L, before.balanceCents)
        assertEquals(0L, before.expenseTotalCents)

        val afterSettled = FinanceCalculator.summarize(listOf(asBooking(PurchasePlanner.toBooking(plan(cents = 50_000), purchase(47_200, Settlement.SETTLED)))))
        assertEquals(-47_200L, afterSettled.balanceCents)
        assertEquals(47_200L, afterSettled.expenseTotalCents)

        // Offen (privat ausgelegt): Kontostand bleibt, es entsteht eine Forderung über den tatsächlichen Betrag.
        val afterOpen = FinanceCalculator.summarize(listOf(asBooking(PurchasePlanner.toBooking(plan(cents = 50_000), purchase(47_200, Settlement.OPEN)))))
        assertEquals(0L, afterOpen.balanceCents)
        assertEquals(mapOf("u2" to 47_200L), afterOpen.openByPayerCents)
        assertEquals(47_200L, afterOpen.expenseTotalCents)
    }

    @Test
    fun summary_countsOnlyOpenPlans() {
        val plans = listOf(
            plan(id = "a", cents = 50_000),
            plan(id = "b", cents = 12_345),
            plan(id = "c", cents = 99_999, status = PlannedStatus.PURCHASED),
        )
        val summary = PlannedCalculator.summarize(plans)
        assertEquals(2, summary.openCount)
        assertEquals(62_345L, summary.openEstimatedCents)
    }

    @Test
    fun summary_afterPurchase_planIsNoLongerOpen() {
        val open = plan(id = "a", cents = 50_000)
        assertEquals(1, PlannedCalculator.summarize(listOf(open)).openCount)
        val bought = open.copy(status = PlannedStatus.PURCHASED, purchasedTransactionId = "b1")
        val after = PlannedCalculator.summarize(listOf(bought))
        assertEquals(0, after.openCount)
        assertEquals(0L, after.openEstimatedCents)
    }

    @Test
    fun summary_emptyList_isZero() {
        val summary = PlannedCalculator.summarize(emptyList())
        assertEquals(0, summary.openCount)
        assertEquals(0L, summary.openEstimatedCents)
    }
}
