package de.hagi089.obelix.data.repairs

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepairValidatorTest {

    @Test
    fun title_blankIsRequired() {
        assertEquals(R.string.error_repair_title_required, RepairValidator.title(""))
        assertEquals(R.string.error_repair_title_required, RepairValidator.title("   "))
    }

    @Test
    fun title_limit200AfterTrim() {
        assertNull(RepairValidator.title("x".repeat(200)))
        assertNull(RepairValidator.title("  " + "x".repeat(200) + "  "))
        assertEquals(R.string.error_repair_title_too_long, RepairValidator.title("x".repeat(201)))
    }

    @Test
    fun description_blankIsRequired() {
        assertEquals(R.string.error_repair_description_required, RepairValidator.description(""))
        assertEquals(R.string.error_repair_description_required, RepairValidator.description(" \n "))
    }

    @Test
    fun description_limit2000() {
        assertNull(RepairValidator.description("x".repeat(2000)))
        assertEquals(R.string.error_repair_description_too_long, RepairValidator.description("x".repeat(2001)))
    }

    @Test
    fun validText_isAccepted() {
        assertNull(RepairValidator.title("Wasserhahn tropft"))
        assertNull(RepairValidator.description("Der Hahn in der Küche tropft."))
    }
}
