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
    fun plannedUntil_isOptional() {
        assertNull(RepairValidator.plannedUntil(null))
    }

    @Test
    fun plannedUntil_acceptsValidDay() {
        assertNull(RepairValidator.plannedUntil("2026-10-31"))
        assertNull(RepairValidator.plannedUntil("2028-02-29")) // Schaltjahr
    }

    @Test
    fun plannedUntil_rejectsInvalidDay() {
        assertEquals(R.string.error_repair_planned_until_invalid, RepairValidator.plannedUntil(""))
        assertEquals(R.string.error_repair_planned_until_invalid, RepairValidator.plannedUntil("31.10.2026"))
        assertEquals(R.string.error_repair_planned_until_invalid, RepairValidator.plannedUntil("2026-02-30"))
    }

    @Test
    fun validText_isAccepted() {
        assertNull(RepairValidator.title("Wasserhahn tropft"))
        assertNull(RepairValidator.description("Der Hahn in der Küche tropft."))
    }
}
