package de.hagi089.obelix.data.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCalculatorTest {

    private var next = 0

    private fun b(type: BookingType, cents: Long, settlement: Settlement, payer: String? = "a") = Booking(
        id = "b${next++}",
        type = type,
        date = "2026-01-01",
        amountCents = cents,
        categoryId = "c",
        paidByUid = payer,
        settlement = settlement,
        description = "x",
        comment = "",
        importRef = null,
    )

    private fun income(cents: Long) = b(BookingType.INCOME, cents, Settlement.SETTLED)
    private fun expense(cents: Long, s: Settlement, payer: String = "a") = b(BookingType.EXPENSE, cents, s, payer)

    @Test
    fun empty_isAllZero() {
        val s = FinanceCalculator.summarize(emptyList())
        assertEquals(0L, s.balanceCents)
        assertEquals(0L, s.afterSettlementCents)
        assertEquals(0L, s.incomeTotalCents)
        assertEquals(0L, s.expenseTotalCents)
        assertEquals(emptyMap<String, Long>(), s.openByPayerCents)
    }

    @Test
    fun balance_countsOnlySettledBookings() {
        val s = FinanceCalculator.summarize(
            listOf(
                income(5_000_000),
                expense(2_300_000, Settlement.SETTLED),
                expense(9_900, Settlement.OPEN, "robert"),
                expense(2_000, Settlement.SPONSORED),
            ),
        )
        assertEquals(2_700_000L, s.balanceCents)
    }

    @Test
    fun openClaims_areGroupedByPayer_andReduceTheBalanceAfterSettlement() {
        val s = FinanceCalculator.summarize(
            listOf(
                income(10_717),
                expense(9_900, Settlement.OPEN, "robert"),
                expense(100, Settlement.OPEN, "tobias"),
                expense(50, Settlement.OPEN, "robert"),
            ),
        )
        assertEquals(mapOf("robert" to 9_950L, "tobias" to 100L), s.openByPayerCents)
        assertEquals(10_050L, s.openTotalCents)
        assertEquals(10_717L, s.balanceCents)
        assertEquals(667L, s.afterSettlementCents)
    }

    @Test
    fun sponsored_isInTotalExpenses_butNotInBalance() {
        val s = FinanceCalculator.summarize(listOf(income(1_000), expense(300, Settlement.SPONSORED), expense(200, Settlement.SETTLED)))
        assertEquals(800L, s.balanceCents)
        assertEquals(500L, s.expenseTotalCents)
        assertEquals(300L, s.sponsoredCents)
    }

    @Test
    fun totals_includeEveryStatus() {
        val s = FinanceCalculator.summarize(
            listOf(income(700), income(300), expense(100, Settlement.OPEN), expense(200, Settlement.SETTLED), expense(400, Settlement.SPONSORED)),
        )
        assertEquals(1_000L, s.incomeTotalCents)
        assertEquals(700L, s.expenseTotalCents)
    }

    @Test
    fun plannedButNotBought_hasNoEffect_becauseOnlyBookingsCount() {
        // Geplante Ausgaben (Phase 5) sind keine Buchungen und beeinflussen den Kontostand nicht.
        assertEquals(1_000L, FinanceCalculator.summarize(listOf(income(1_000))).balanceCents)
    }
}
