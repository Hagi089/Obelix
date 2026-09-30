package de.hagi089.obelix.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeTest {
    @Test
    fun formatsBytesKilobytesAndMegabytesWithGermanComma() {
        assertEquals("1 Byte", FileSize.format(1))
        assertEquals("512 Byte", FileSize.format(512))
        assertEquals("1023 Byte", FileSize.format(1023))
        assertEquals("1 KB", FileSize.format(1024))
        assertEquals("412 KB", FileSize.format(412L * 1024))
        assertEquals("1,0 MB", FileSize.format(1024L * 1024))
        assertEquals("1,5 MB", FileSize.format(1536L * 1024))
        assertEquals("8,0 MB", FileSize.format(FileLimits.MAX_FILE_BYTES.toLong()))
    }

    @Test
    fun justBelowOneMegabyte_doesNotShow1024KB() {
        assertEquals("1,0 MB", FileSize.format(1024L * 1024 - 1))
    }
}
