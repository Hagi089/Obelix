package de.hagi089.obelix.data.campsites

import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteLogicTest {

    private fun campsite(id: String, date: String) =
        Campsite(id, 48.0, 11.0, date, "Kommentar", null, null, null, null, emptyList(), "anna")

    private fun ref(id: String) = FileRef(id, "foto.jpg", "image/jpeg", 1000)
    private fun file(name: String = "neu.jpg") = NewFile(name, "image/jpeg", ByteArray(10))

    @Test
    fun sorted_newestFirst_thenById() {
        val list = listOf(campsite("b", "2026-09-01"), campsite("a", "2026-10-01"), campsite("c", "2026-09-01"))
        assertEquals(listOf("a", "b", "c"), CampsiteLogic.sorted(list).map { it.id })
    }

    @Test
    fun sorted_keepsEverything_andHandlesEmptyList() {
        assertTrue(CampsiteLogic.sorted(emptyList()).isEmpty())
        val list = (1..5).map { campsite("id$it", "2026-10-0$it") }
        assertEquals(5, CampsiteLogic.sorted(list).size)
    }

    @Test
    fun blankToNull_trims_andDropsBlank() {
        assertNull(CampsiteLogic.blankToNull(""))
        assertNull(CampsiteLogic.blankToNull("   "))
        assertEquals("See", CampsiteLogic.blankToNull("  See "))
    }

    @Test
    fun photoSet_limitIsThree_acrossSavedAndNew() {
        var set = CampsitePhotoSet(saved = listOf(ref("a"), ref("b")))
        assertEquals(2, set.count)
        assertTrue(set.canAdd)
        set = set.withAdded(file())!!
        assertEquals(3, set.count)
        assertFalse(set.canAdd)
        assertNull(set.withAdded(file())) // das vierte Foto wird abgelehnt, der Stand bleibt
        assertEquals(3, set.count)
    }

    @Test
    fun photoSet_onlyNewPhotos_upToThree() {
        var set = CampsitePhotoSet()
        repeat(3) { set = set.withAdded(file("f$it.jpg"))!! }
        assertEquals(3, set.added.size)
        assertNull(set.withAdded(file()))
    }

    @Test
    fun photoSet_removingSavedPhoto_freesASlot_andKeepsTheRest() {
        val full = CampsitePhotoSet(saved = listOf(ref("a"), ref("b"), ref("c")))
        assertFalse(full.canAdd)
        val freed = full.withRemoved("b")
        assertEquals(listOf("a", "c"), freed.kept.map { it.fileId })
        assertEquals(setOf("b"), freed.removedIds)
        assertTrue(freed.canAdd)
        assertNotNull(freed.withAdded(file()))
        assertEquals(3, freed.withAdded(file())!!.count)
    }

    @Test
    fun photoSet_removeUnknownId_doesNothing() {
        val set = CampsitePhotoSet(saved = listOf(ref("a")))
        assertEquals(set, set.withRemoved("gibt-es-nicht"))
    }

    @Test
    fun photoSet_removeNewPhoto_byIndex() {
        var set = CampsitePhotoSet().withAdded(file("1.jpg"))!!.withAdded(file("2.jpg"))!!
        set = set.withoutAdded(0)
        assertEquals(listOf("2.jpg"), set.added.map { it.name })
        assertEquals(set, set.withoutAdded(5)) // ungültiger Index ändert nichts
        assertEquals(set, set.withoutAdded(-1))
    }
}
