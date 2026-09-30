package de.hagi089.obelix.data.calendar

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonColorsTest {

    private fun channel(value: Long): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun hue(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        if (d == 0.0) return 0.0
        val h = when (mx) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60
        return if (h < 0) h + 360 else h
    }

    @Test
    fun palette_colorsAreDistinct() {
        assertEquals(PersonColors.palette.size, PersonColors.palette.map { it.background }.toSet().size)
    }

    @Test
    fun palette_contentIsReadableOnBackground() {
        PersonColors.palette.forEach {
            assertTrue("Kontrast zu gering: ${it.background.toString(16)}", contrast(it.background, it.content) >= 4.5)
        }
    }

    @Test
    fun palette_hasNoRedPinkOrOrange_redIsReservedForOverlap() {
        // Rot, Rosa und Orange liegen bei Farbtönen unter 45° bzw. über 300°.
        PersonColors.palette.forEach {
            val h = hue(it.background)
            assertTrue("Farbton ${h}° nicht erlaubt: ${it.background.toString(16)}", h in 45.0..300.0)
        }
    }

    @Test
    fun assign_isIndependentOfOrderAndSameOnEveryDevice() {
        val a = PersonColors.assign(listOf("u3", "u1", "u2"), emptyList())
        val b = PersonColors.assign(listOf("u2", "u3", "u1"), emptyList())
        assertEquals(a, b)
        assertNotEquals(a["u1"], a["u2"])
        assertNotEquals(a["u2"], a["u3"])
    }

    @Test
    fun assign_sortsByUid() {
        val colors = PersonColors.assign(listOf("b", "a"), emptyList())
        assertEquals(PersonColors.palette[0], colors["a"])
        assertEquals(PersonColors.palette[1], colors["b"])
    }

    @Test
    fun assign_includesEntryPersonsMissingInUserList() {
        val colors = PersonColors.assign(listOf("a"), listOf("a", "gone"))
        assertEquals(setOf("a", "gone"), colors.keys)
    }

    @Test
    fun assign_withoutUsers_stillColorsEntryPersons() {
        val colors = PersonColors.assign(emptyList(), listOf("x", "y", "x"))
        assertEquals(setOf("x", "y"), colors.keys)
        assertNotEquals(colors["x"], colors["y"])
    }

    @Test
    fun assign_moreThanPaletteSize_wrapsAround() {
        val uids = (1..PersonColors.palette.size + 1).map { "u%02d".format(it) }
        val colors = PersonColors.assign(uids, emptyList())
        assertEquals(uids.size, colors.size)
        assertEquals(colors[uids.first()], colors[uids.last()])
    }

    @Test
    fun assign_noPersons_isEmpty() {
        assertTrue(PersonColors.assign(emptyList(), emptyList()).isEmpty())
    }
}
