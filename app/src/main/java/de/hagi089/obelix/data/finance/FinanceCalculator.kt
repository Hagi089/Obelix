package de.hagi089.obelix.data.finance

/**
 * Kennzahlen des Finanzbereichs. Alles in Cent.
 *
 * @property balanceCents Kontostand = Einnahmen − Ausgaben, jeweils nur mit Status SETTLED
 *   (entspricht der Excel-Formel „Kontostand aktuell").
 * @property openByPayerCents offene Forderungen je Zahler (Summe der Ausgaben mit Status OPEN).
 * @property afterSettlementCents Kontostand nach Begleichung der offenen Forderungen.
 * @property incomeTotalCents alle Einnahmen.
 * @property expenseTotalCents alle Ausgaben, auch gesponserte und offene.
 * @property sponsoredCents Teil der Ausgaben mit Status SPONSORED.
 */
data class FinanceSummary(
    val balanceCents: Long,
    val openByPayerCents: Map<String, Long>,
    val afterSettlementCents: Long,
    val incomeTotalCents: Long,
    val expenseTotalCents: Long,
    val sponsoredCents: Long,
) {
    val openTotalCents: Long get() = openByPayerCents.values.sum()
}

object FinanceCalculator {

    fun summarize(bookings: List<Booking>): FinanceSummary {
        var balance = 0L
        var income = 0L
        var expense = 0L
        var sponsored = 0L
        val open = linkedMapOf<String, Long>()
        for (b in bookings) {
            when (b.type) {
                BookingType.INCOME -> {
                    income += b.amountCents
                    if (b.settlement == Settlement.SETTLED) balance += b.amountCents
                }
                BookingType.EXPENSE -> {
                    expense += b.amountCents
                    when (b.settlement) {
                        Settlement.SETTLED -> balance -= b.amountCents
                        Settlement.SPONSORED -> sponsored += b.amountCents
                        Settlement.OPEN -> b.paidByUid?.let { open[it] = (open[it] ?: 0L) + b.amountCents }
                    }
                }
            }
        }
        return FinanceSummary(
            balanceCents = balance,
            openByPayerCents = open,
            afterSettlementCents = balance - open.values.sum(),
            incomeTotalCents = income,
            expenseTotalCents = expense,
            sponsoredCents = sponsored,
        )
    }
}
