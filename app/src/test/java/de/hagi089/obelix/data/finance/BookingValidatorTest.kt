package de.hagi089.obelix.data.finance

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingValidatorTest {

    @Test
    fun amount_valid() {
        listOf("0,01", "1", "12,50", "1.234,56", "1.000.000,00").forEach { assertNull("Erwartet gültig: $it", BookingValidator.amount(it)) }
    }

    @Test
    fun amount_emptyIsRequired() {
        assertEquals(R.string.error_amount_required, BookingValidator.amount(""))
        assertEquals(R.string.error_amount_required, BookingValidator.amount("  "))
    }

    @Test
    fun amount_notANumberOrTooManyDecimals_isInvalid() {
        listOf("abc", "12,345", "-5", "1,2,3").forEach { assertEquals("Erwartet ungültig: $it", R.string.error_amount_invalid, BookingValidator.amount(it)) }
    }

    @Test
    fun amount_zeroIsNotPositive() {
        assertEquals(R.string.error_amount_positive, BookingValidator.amount("0"))
        assertEquals(R.string.error_amount_positive, BookingValidator.amount("0,00"))
    }

    @Test
    fun amount_aboveLimit_isRejected() {
        assertEquals(R.string.error_amount_too_high, BookingValidator.amount("1.000.000,01"))
    }

    @Test
    fun date_mustBeARealDay() {
        assertNull(BookingValidator.date("2026-09-30"))
        assertNull(BookingValidator.date("2024-02-29"))
        listOf("", "30.09.2026", "2026-9-30", "2026-02-30", "2025-02-29", "abc").forEach {
            assertEquals("Erwartet ungültig: $it", R.string.error_date_required, BookingValidator.date(it))
        }
    }

    @Test
    fun category_required() {
        assertNull(BookingValidator.category("c1"))
        assertEquals(R.string.error_category_required, BookingValidator.category(null))
        assertEquals(R.string.error_category_required, BookingValidator.category(""))
    }

    @Test
    fun payer_requiredForExpense_optionalForIncome() {
        assertEquals(R.string.error_payer_required, BookingValidator.payer(BookingType.EXPENSE, null))
        assertEquals(R.string.error_payer_required, BookingValidator.payer(BookingType.EXPENSE, ""))
        assertNull(BookingValidator.payer(BookingType.EXPENSE, "u1"))
        assertNull(BookingValidator.payer(BookingType.INCOME, null))
        assertNull(BookingValidator.payer(BookingType.INCOME, "u1"))
    }

    @Test
    fun description_requiredAndLimited() {
        assertEquals(R.string.error_description_required, BookingValidator.description("  "))
        assertNull(BookingValidator.description("Diesel"))
        assertNull(BookingValidator.description("x".repeat(200)))
        assertEquals(R.string.error_description_too_long, BookingValidator.description("x".repeat(201)))
    }

    @Test
    fun comment_optionalButLimited() {
        assertNull(BookingValidator.comment(""))
        assertNull(BookingValidator.comment("x".repeat(500)))
        assertEquals(R.string.error_comment_too_long, BookingValidator.comment("x".repeat(501)))
    }

    @Test
    fun categoryName_requiredAndLimited() {
        assertEquals(R.string.error_category_name_required, BookingValidator.categoryName(" "))
        assertNull(BookingValidator.categoryName("Elektro"))
        assertEquals(R.string.error_category_name_too_long, BookingValidator.categoryName("x".repeat(51)))
    }
}
