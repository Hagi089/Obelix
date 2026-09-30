package de.hagi089.obelix.data.planned

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannedValidatorTest {

    @Test
    fun title_isRequired() {
        assertEquals(R.string.error_planned_title_required, PlannedValidator.title(""))
        assertEquals(R.string.error_planned_title_required, PlannedValidator.title("   "))
        assertNull(PlannedValidator.title("Neue Batterie"))
    }

    @Test
    fun title_maxLengthIs200() {
        assertNull(PlannedValidator.title("x".repeat(200)))
        assertEquals(R.string.error_planned_title_too_long, PlannedValidator.title("x".repeat(201)))
    }

    @Test
    fun link_isOptional() {
        assertNull(PlannedValidator.link(""))
        assertNull(PlannedValidator.link("   "))
    }

    @Test
    fun link_httpAndHttpsAreValid() {
        listOf("https://example.org", "http://example.org/batterie?x=1&y=2", "  https://example.org/a  ").forEach {
            assertNull("Erwartet gültig: $it", PlannedValidator.link(it))
        }
    }

    @Test
    fun link_otherSchemesSpacesAndTextAreInvalid() {
        listOf("ftp://example.org", "javascript:alert(1)", "example.org", "https://exa mple.org", "https://", "HTTPS://example.org").forEach {
            assertEquals("Erwartet ungültig: $it", R.string.error_planned_link_invalid, PlannedValidator.link(it))
        }
    }

    @Test
    fun link_maxLengthIs500() {
        val prefix = "https://example.org/"
        assertNull(PlannedValidator.link(prefix + "a".repeat(500 - prefix.length)))
        assertEquals(R.string.error_planned_link_invalid, PlannedValidator.link(prefix + "a".repeat(501 - prefix.length)))
    }
}
