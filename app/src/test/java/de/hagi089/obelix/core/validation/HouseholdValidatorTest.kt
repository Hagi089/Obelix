package de.hagi089.obelix.core.validation

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HouseholdValidatorTest {

    @Test
    fun accessCode_empty_isRequired() {
        assertEquals(R.string.error_access_code_required, HouseholdValidator.accessCode(""))
        assertEquals(R.string.error_access_code_required, HouseholdValidator.accessCode("  - "))
    }

    @Test
    fun accessCode_wrongFormat_isRejected() {
        assertEquals(R.string.error_access_code_format, HouseholdValidator.accessCode("ABC"))
        assertEquals(R.string.error_access_code_format, HouseholdValidator.accessCode("ABCDEFGH23456781"))
    }

    @Test
    fun accessCode_formattedInput_isAccepted() {
        assertNull(HouseholdValidator.accessCode("abcd-efgh-2345-6789"))
        assertNull(HouseholdValidator.accessCode("ABCDEFGH23456789"))
    }

    @Test
    fun householdName_requiredAndLimited() {
        assertEquals(R.string.error_household_name_required, HouseholdValidator.householdName(" "))
        assertEquals(R.string.error_text_too_long, HouseholdValidator.householdName("x".repeat(61)))
        assertNull(HouseholdValidator.householdName("x".repeat(60)))
    }

    @Test
    fun partyName_requiredAndLimited() {
        assertEquals(R.string.error_party_name_required, HouseholdValidator.partyName(""))
        assertEquals(R.string.error_text_too_long, HouseholdValidator.partyName("x".repeat(41)))
        assertNull(HouseholdValidator.partyName("x".repeat(40)))
    }
}
