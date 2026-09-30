package de.hagi089.obelix.data.campsites

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteValidatorTest {

    @Test
    fun comment_isRequired_andLimited() {
        assertEquals(R.string.error_campsite_comment_required, CampsiteValidator.comment(""))
        assertEquals(R.string.error_campsite_comment_required, CampsiteValidator.comment("   "))
        assertNull(CampsiteValidator.comment("Ruhig, am See"))
        assertNull(CampsiteValidator.comment("x".repeat(500)))
        assertNull(CampsiteValidator.comment("  " + "x".repeat(500) + "  ")) // nach dem Trimmen gezählt
        assertEquals(R.string.error_comment_too_long, CampsiteValidator.comment("x".repeat(501)))
    }

    @Test
    fun optionalTexts_haveLimits() {
        assertNull(CampsiteValidator.name(""))
        assertNull(CampsiteValidator.name("x".repeat(100)))
        assertEquals(R.string.error_campsite_name_too_long, CampsiteValidator.name("x".repeat(101)))
        assertNull(CampsiteValidator.address("x".repeat(200)))
        assertEquals(R.string.error_campsite_address_too_long, CampsiteValidator.address("x".repeat(201)))
        assertNull(CampsiteValidator.note("x".repeat(500)))
        assertEquals(R.string.error_campsite_note_too_long, CampsiteValidator.note("x".repeat(501)))
    }

    @Test
    fun rating_isOptional_andOneToFive() {
        assertTrue(CampsiteValidator.isValidRating(null))
        for (value in 1..5) assertTrue(CampsiteValidator.isValidRating(value))
        assertFalse(CampsiteValidator.isValidRating(0))
        assertFalse(CampsiteValidator.isValidRating(6))
        assertFalse(CampsiteValidator.isValidRating(-1))
    }

    @Test
    fun position_mustBeInRange_andFinite() {
        assertTrue(CampsiteValidator.isValidPosition(48.137154, 11.576124))
        assertTrue(CampsiteValidator.isValidPosition(90.0, 180.0))
        assertTrue(CampsiteValidator.isValidPosition(-90.0, -180.0))
        assertTrue(CampsiteValidator.isValidPosition(0.0, 0.0))
        assertFalse(CampsiteValidator.isValidPosition(90.0001, 0.0))
        assertFalse(CampsiteValidator.isValidPosition(-90.0001, 0.0))
        assertFalse(CampsiteValidator.isValidPosition(0.0, 180.0001))
        assertFalse(CampsiteValidator.isValidPosition(0.0, -180.0001))
        assertFalse(CampsiteValidator.isValidPosition(Double.NaN, 0.0))
        assertFalse(CampsiteValidator.isValidPosition(0.0, Double.POSITIVE_INFINITY))
    }
}
