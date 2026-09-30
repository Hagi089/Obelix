package de.hagi089.obelix.data.campsites

import de.hagi089.obelix.data.files.FileLimits
import de.hagi089.obelix.data.files.PhotoCompression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCompressionTest {

    @Test
    fun photoLimit_isExactlyOneChunk() {
        // Die Regeln erlauben ein Foto nur bis 921.600 Byte (validPhotoRef): ein Stück, Regelbudget 19 von 20.
        assertEquals(921_600, FileLimits.MAX_PHOTO_BYTES)
        assertEquals(1, FileLimits.chunkCountFor(FileLimits.MAX_PHOTO_BYTES.toLong()))
        assertEquals(2, FileLimits.chunkCountFor(FileLimits.MAX_PHOTO_BYTES + 1L))
    }

    @Test
    fun fits_boundaries() {
        assertTrue(PhotoCompression.fits(1))
        assertTrue(PhotoCompression.fits(FileLimits.MAX_PHOTO_BYTES))
        assertFalse(PhotoCompression.fits(FileLimits.MAX_PHOTO_BYTES + 1))
        assertFalse(PhotoCompression.fits(0))
        assertFalse(PhotoCompression.fits(-5))
    }

    @Test
    fun steps_startWithTheReceiptSetting_andOnlyGetSmaller() {
        val steps = PhotoCompression.steps
        assertEquals(FileLimits.IMAGE_MAX_SIDE_PX, steps.first().maxSide)
        assertEquals(FileLimits.JPEG_QUALITY, steps.first().quality)
        for ((previous, next) in steps.zipWithNext()) {
            // Die Bildgröße wächst nie, und jede Stufe unterscheidet sich von der vorigen.
            assertTrue(next.maxSide <= previous.maxSide)
            assertTrue(next != previous)
        }
        assertTrue(steps.all { it.quality in 1..100 && it.maxSide > 0 })
        assertTrue(steps.last().maxSide < steps.first().maxSide) // am Ende sinkt auch die Bildgröße
        assertTrue(steps.all { it.maxSide <= FileLimits.IMAGE_MAX_SIDE_PX }) // nie größer als die Vorgabe (Anforderung 5)
    }
}
