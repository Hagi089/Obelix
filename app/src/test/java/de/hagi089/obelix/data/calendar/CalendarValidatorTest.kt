package de.hagi089.obelix.data.calendar

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarValidatorTest {

    @Test
    fun range_endBeforeStart_isRejected() {
        assertEquals(R.string.error_calendar_end_before_start, CalendarValidator.range("2026-10-18", "2026-10-17"))
        assertEquals(R.string.error_calendar_end_before_start, CalendarValidator.range("2027-01-03", "2026-12-30"))
    }

    @Test
    fun range_sameDayAndLaterEnd_areValid() {
        assertNull(CalendarValidator.range("2026-10-10", "2026-10-10"))
        assertNull(CalendarValidator.range("2026-10-10", "2026-10-18"))
        assertNull(CalendarValidator.range("2026-12-30", "2027-01-03"))
    }

    @Test
    fun range_leavesInvalidDatesToTheDateCheck() {
        assertNull(CalendarValidator.range("", "2026-10-10"))
        assertNull(CalendarValidator.range("2026-10-10", "kein Datum"))
        assertNull(CalendarValidator.range("2026-02-30", "2026-02-01"))
    }

    @Test
    fun person_isRequired() {
        assertEquals(R.string.error_calendar_person_required, CalendarValidator.person(null))
        assertEquals(R.string.error_calendar_person_required, CalendarValidator.person(""))
        assertEquals(R.string.error_calendar_person_required, CalendarValidator.person("  "))
        assertNull(CalendarValidator.person("uid-1"))
    }

    @Test
    fun destination_isOptionalAndLimitedTo100() {
        assertNull(CalendarValidator.destination(""))
        assertNull(CalendarValidator.destination("Italien"))
        assertNull(CalendarValidator.destination("x".repeat(100)))
        assertEquals(R.string.error_calendar_destination_too_long, CalendarValidator.destination("x".repeat(101)))
    }
}
