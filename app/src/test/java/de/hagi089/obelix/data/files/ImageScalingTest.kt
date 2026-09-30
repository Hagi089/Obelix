package de.hagi089.obelix.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageScalingTest {

    private val max = FileLimits.IMAGE_MAX_SIDE_PX

    @Test
    fun smallImage_isNotEnlarged() {
        assertEquals(800 to 600, ImageScaling.targetSize(800, 600, max))
        assertEquals(max to max, ImageScaling.targetSize(max, max, max))
    }

    @Test
    fun landscapeAndPortrait_longSideBecomesMax_ratioStays() {
        assertEquals(1800 to 1350, ImageScaling.targetSize(4000, 3000, max))
        assertEquals(1350 to 1800, ImageScaling.targetSize(3000, 4000, max))
        assertEquals(1800 to 900, ImageScaling.targetSize(3600, 1800, max))
    }

    @Test
    fun extremeRatio_keepsAtLeastOnePixel() {
        val (w, h) = ImageScaling.targetSize(100_000, 10, max)
        assertEquals(max, w)
        assertTrue(h >= 1)
    }

    @Test
    fun sampleSize_isAPowerOfTwo_andKeepsEnoughPixels() {
        assertEquals(1, ImageScaling.sampleSize(1000, 800, max))
        assertEquals(1, ImageScaling.sampleSize(max, 100, max))
        assertEquals(1, ImageScaling.sampleSize(3599, 2000, max))
        assertEquals(2, ImageScaling.sampleSize(3600, 2000, max))
        assertEquals(2, ImageScaling.sampleSize(4000, 3000, max)) // 12-MP-Foto: 2000 x 1500 beim Dekodieren
        assertEquals(4, ImageScaling.sampleSize(8000, 6000, max))
        listOf(1000, 1800, 3000, 4032, 6000, 12000).forEach { side ->
            val sample = ImageScaling.sampleSize(side, side / 2, max)
            assertTrue("Potenz von 2 bei $side", sample and (sample - 1) == 0)
            assertTrue("genug Pixel bei $side", side / sample >= minOf(side, max))
        }
    }

    @Test
    fun invalidSizes_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { ImageScaling.targetSize(0, 100, max) }
        assertThrows(IllegalArgumentException::class.java) { ImageScaling.sampleSize(100, -1, max) }
    }
}
