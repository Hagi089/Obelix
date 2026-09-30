package de.hagi089.obelix.ui.calendar

import de.hagi089.obelix.data.calendar.CalendarEntry
import de.hagi089.obelix.data.calendar.PersonColors
import de.hagi089.obelix.data.user.Role
import de.hagi089.obelix.data.user.UserProfile
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalendarStateTest {

    private fun entry(id: String, uid: String, name: String, start: String, end: String) = CalendarEntry(
        id = id, startDate = start, endDate = end, personUid = uid, personName = name,
        destination = null, comment = "", createdBy = uid,
    )

    private val users = listOf(
        UserProfile("u2", "Zoe", Role.MEMBER),
        UserProfile("u1", "Anna", Role.ADMIN),
    )

    @Test
    fun colors_sameUserAlwaysSameColor_andDifferentUsersDiffer() {
        val state = CalendarState(
            users = users,
            entries = listOf(
                entry("e1", "u1", "Anna", "2026-10-01", "2026-10-03"),
                entry("e2", "u2", "Zoe", "2026-10-10", "2026-10-12"),
                entry("e3", "u1", "Anna", "2026-10-20", "2026-10-22"),
            ),
        )
        assertEquals(PersonColors.palette[0], state.colors["u1"])
        assertEquals(PersonColors.palette[1], state.colors["u2"])
        assertNotEquals(state.colors["u1"], state.colors["u2"])
    }

    @Test
    fun colors_withoutUserList_areDerivedFromEntries() {
        val state = CalendarState(entries = listOf(entry("e1", "u2", "Zoe", "2026-10-01", "2026-10-01")))
        assertEquals(setOf("u2"), state.colors.keys)
    }

    @Test
    fun legend_listsEachPersonOnceSortedByName() {
        val state = CalendarState(
            users = users,
            entries = listOf(
                entry("e1", "u2", "Zoe", "2026-10-01", "2026-10-01"),
                entry("e2", "u2", "Zoe", "2026-10-05", "2026-10-05"),
            ),
        )
        assertEquals(listOf("Anna", "Zoe"), state.legend.map { it.first })
    }

    @Test
    fun legend_usesCurrentUserNameOverStoredEntryName() {
        val state = CalendarState(
            users = listOf(UserProfile("u1", "Anna Neu", Role.MEMBER)),
            entries = listOf(entry("e1", "u1", "Anna Alt", "2026-10-01", "2026-10-01")),
        )
        assertEquals(listOf("Anna Neu"), state.legend.map { it.first })
    }

    @Test
    fun occupants_overlapDayHasBothEntries() {
        val state = CalendarState(
            month = YearMonth.of(2026, 10),
            entries = listOf(
                entry("e1", "u1", "Anna", "2026-10-01", "2026-10-05"),
                entry("e2", "u2", "Zoe", "2026-10-05", "2026-10-08"),
            ),
        )
        val byDay = state.occupants.mapKeys { it.key.dayOfMonth }
        assertEquals(listOf("e1"), byDay[4]?.map { it.id })
        assertEquals(listOf("e1", "e2"), byDay[5]?.map { it.id })
        assertEquals(listOf("e2"), byDay[6]?.map { it.id })
        assertEquals(null, byDay[9])
    }
}
