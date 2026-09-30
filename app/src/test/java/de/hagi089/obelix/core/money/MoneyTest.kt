package de.hagi089.obelix.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parse_acceptsGermanAndPlainInput() {
        val cases = mapOf(
            "12" to 1200L,
            "12,5" to 1250L,
            "12,50" to 1250L,
            "0,05" to 5L,
            "0,5" to 50L,
            "1.234,56" to 123456L,
            "1.234" to 123400L,
            "12.50" to 1250L,
            "12.5" to 1250L,
            "1.000.000,00" to 100000000L,
            "12,50 €" to 1250L,
            " 12,50€ " to 1250L,
            "1234,56" to 123456L,
            "12.345,6" to 1234560L,
        )
        cases.forEach { (input, cents) -> assertEquals("Eingabe „$input“", cents, Money.parse(input)) }
    }

    @Test
    fun parse_rejectsNonAmounts() {
        listOf("", " ", "abc", "12,345", "1,2,3", "-5", "+5", "12,", ",5", "1.23.4", "1e3", "99999999999999999")
            .forEach { assertNull("Erwartet ungültig: „$it“", Money.parse(it)) }
    }

    @Test
    fun format_usesGermanNotationWithSign() {
        assertEquals("0,00 €", Money.format(0))
        assertEquals("0,07 €", Money.format(7))
        assertEquals("12,50 €", Money.format(1250))
        assertEquals("1.234,56 €", Money.format(123456))
        assertEquals("25.000,00 €", Money.format(2500000))
        assertEquals("1.000.000,00 €", Money.format(100000000))
        assertEquals("−99,00 €", Money.format(-9900))
    }

    @Test
    fun formatInput_hasNoGroupingAndRoundTrips() {
        assertEquals("1234,56", Money.formatInput(123456))
        assertEquals("0,05", Money.formatInput(5))
        listOf(1L, 5L, 99L, 100L, 123456L, 100000000L).forEach {
            assertEquals(it, Money.parse(Money.formatInput(it)))
            assertEquals(it, Money.parse(Money.format(it)))
        }
    }
}
