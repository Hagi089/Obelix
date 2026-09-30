package de.hagi089.obelix.data.household

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessCodeTest {

    @Test
    fun generate_hasCorrectLengthAndOnlyAllowedCharacters() {
        repeat(200) {
            val code = AccessCode.generate()
            assertEquals(AccessCode.LENGTH, code.length)
            assertTrue("Ungültiger Code: $code", AccessCode.isValid(code))
            assertFalse(code.any { it in "ILO01" })
        }
    }

    @Test
    fun generate_producesDifferentCodes() {
        val codes = List(100) { AccessCode.generate() }.toSet()
        assertEquals(100, codes.size)
    }

    @Test
    fun normalize_removesSeparatorsAndUppercases() {
        assertEquals("ABCDEFGH23456789", AccessCode.normalize(" abcd-efgh 2345-6789 "))
    }

    @Test
    fun isValid_rejectsWrongLengthAndForbiddenCharacters() {
        assertFalse(AccessCode.isValid(""))
        assertFalse(AccessCode.isValid("ABCDEFGH2345678")) // 15
        assertFalse(AccessCode.isValid("ABCDEFGH234567890")) // 17
        assertFalse(AccessCode.isValid("ABCDEFGH23456781")) // enthält 1
        assertFalse(AccessCode.isValid("ABCDEFGH2345678O")) // enthält O
        assertTrue(AccessCode.isValid("ABCDEFGH23456789"))
    }

    @Test
    fun format_groupsInFours() {
        assertEquals("ABCD-EFGH-2345-6789", AccessCode.format("ABCDEFGH23456789"))
    }

    @Test
    fun formattedCode_canBeNormalizedBackToTheSameCode() {
        val code = AccessCode.generate()
        assertEquals(code, AccessCode.normalize(AccessCode.format(code)))
    }
}
