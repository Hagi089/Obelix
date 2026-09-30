package de.hagi089.obelix.data.repairs

import de.hagi089.obelix.data.planned.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairLogicTest {

    private fun repair(
        id: String,
        status: RepairStatus = RepairStatus.OPEN,
        priority: Priority? = null,
        date: String = "2026-10-10",
        title: String = "Titel $id",
    ) = Repair(id, title, "Beschreibung", date, status, priority, "", "u")

    private val all = listOf(
        repair("a", RepairStatus.OPEN),
        repair("b", RepairStatus.DONE),
        repair("c", RepairStatus.OPEN),
    )

    @Test
    fun filter_openShowsOnlyOpen() {
        assertEquals(listOf("a", "c"), all.filter(RepairFilter.OPEN::matches).map { it.id })
    }

    @Test
    fun filter_doneShowsOnlyDone() {
        assertEquals(listOf("b"), all.filter(RepairFilter.DONE::matches).map { it.id })
    }

    @Test
    fun filter_allShowsEverything() {
        assertEquals(3, all.filter(RepairFilter.ALL::matches).size)
    }

    @Test
    fun openCount_countsOnlyOpen() {
        assertEquals(2, RepairLogic.openCount(all))
        assertEquals(0, RepairLogic.openCount(emptyList()))
    }

    @Test
    fun sorted_openBeforeDone_evenWithHigherPriorityOrNewerDate() {
        val list = listOf(
            repair("done", RepairStatus.DONE, Priority.HIGH, "2026-12-01"),
            repair("open", RepairStatus.OPEN, null, "2026-01-01"),
        )
        assertEquals(listOf("open", "done"), RepairLogic.sorted(list).map { it.id })
    }

    @Test
    fun sorted_priorityHighFirst_noPriorityLast() {
        val list = listOf(
            repair("none"),
            repair("low", priority = Priority.LOW),
            repair("high", priority = Priority.HIGH),
            repair("mid", priority = Priority.MEDIUM),
        )
        assertEquals(listOf("high", "mid", "low", "none"), RepairLogic.sorted(list).map { it.id })
    }

    @Test
    fun sorted_samePriority_newestDateFirst() {
        val list = listOf(
            repair("old", date = "2026-01-01"),
            repair("new", date = "2026-06-01"),
        )
        assertEquals(listOf("new", "old"), RepairLogic.sorted(list).map { it.id })
    }

    @Test
    fun sorted_isStableAndDeterministic() {
        val list = listOf(
            repair("2", title = "beta"),
            repair("1", title = "Beta"),
            repair("3", title = "alpha"),
        )
        val first = RepairLogic.sorted(list).map { it.id }
        assertEquals(first, RepairLogic.sorted(list.reversed()).map { it.id })
        assertEquals("3", first.first())
    }

    @Test
    fun sorted_doesNotLoseEntries() {
        assertTrue(RepairLogic.sorted(all).map { it.id }.toSet() == setOf("a", "b", "c"))
    }
}
