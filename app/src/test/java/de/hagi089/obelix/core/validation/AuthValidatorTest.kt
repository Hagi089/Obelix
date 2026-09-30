package de.hagi089.obelix.core.validation

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun email_empty_isRequired() {
        assertEquals(R.string.error_email_required, AuthValidator.email(""))
        assertEquals(R.string.error_email_required, AuthValidator.email("   "))
    }

    @Test
    fun email_invalidFormats_areRejected() {
        listOf("abc", "abc@", "@example.de", "a b@example.de", "abc@example", "abc@@example.de", "abc@example..").forEach {
            assertEquals("Erwartet ungültig: $it", R.string.error_email_invalid, AuthValidator.email(it))
        }
    }

    @Test
    fun email_validFormats_areAccepted() {
        listOf("a@b.de", "max.mustermann@example.com", "a+b@sub.example.org", " a@b.de ").forEach {
            assertNull("Erwartet gültig: $it", AuthValidator.email(it))
        }
    }

    @Test
    fun loginPassword_onlyRequiresInput() {
        assertEquals(R.string.error_password_required, AuthValidator.loginPassword(""))
        assertNull(AuthValidator.loginPassword("x"))
    }

    @Test
    fun newPassword_enforcesMinimumLength() {
        assertEquals(R.string.error_password_required, AuthValidator.newPassword(""))
        assertEquals(R.string.error_password_too_short, AuthValidator.newPassword("1234567"))
        assertNull(AuthValidator.newPassword("12345678"))
    }

    @Test
    fun name_mustNotBeBlankOrTooLong() {
        assertEquals(R.string.error_name_required, AuthValidator.name(" "))
        assertEquals(R.string.error_name_too_long, AuthValidator.name("x".repeat(51)))
        assertNull(AuthValidator.name("Tobias"))
        assertNull(AuthValidator.name("x".repeat(50)))
    }

    @Test
    fun accessCode_empty_isRequired() {
        assertEquals(R.string.error_access_code_required, AuthValidator.accessCode(""))
        assertEquals(R.string.error_access_code_required, AuthValidator.accessCode("  - "))
    }

    @Test
    fun accessCode_wrongFormat_isRejected() {
        assertEquals(R.string.error_access_code_format, AuthValidator.accessCode("ABC"))
        assertEquals(R.string.error_access_code_format, AuthValidator.accessCode("ABCDEFGH23456781"))
    }

    @Test
    fun accessCode_formattedInput_isAccepted() {
        assertNull(AuthValidator.accessCode("abcd-efgh-2345-6789"))
        assertNull(AuthValidator.accessCode("ABCDEFGH23456789"))
    }
}
