package de.hagi089.obelix.data.files

import de.hagi089.obelix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileValidatorTest {

    @Test
    fun check_acceptsJpegAndPdfUpToTheLimit() {
        assertNull(FileValidator.check(1, FileLimits.MIME_JPEG))
        assertNull(FileValidator.check(FileLimits.MAX_FILE_BYTES.toLong(), FileLimits.MIME_PDF))
    }

    @Test
    fun check_rejectsTooLargeEmptyAndWrongType() {
        assertEquals(R.string.error_file_too_large, FileValidator.check(FileLimits.MAX_FILE_BYTES.toLong() + 1, FileLimits.MIME_PDF))
        assertEquals(R.string.error_file_empty, FileValidator.check(0, FileLimits.MIME_JPEG))
        assertEquals(R.string.error_file_type, FileValidator.check(100, "image/png"))
        assertEquals(R.string.error_file_type, FileValidator.check(100, "application/zip"))
        assertEquals(R.string.error_file_type, FileValidator.check(100, null))
    }

    @Test
    fun cleanName_removesPathAndControlCharacters() {
        assertEquals("rechnung.pdf", FileValidator.cleanName("rechnung.pdf", "Beleg.pdf"))
        assertEquals("beleg.jpg", FileValidator.cleanName("/storage/emulated/0/DCIM/beleg.jpg", "Beleg"))
        assertEquals("beleg.jpg", FileValidator.cleanName("C:\\Users\\x\\beleg.jpg", "Beleg"))
        assertEquals("a b.pdf", FileValidator.cleanName("a\nb.pdf", "Beleg"))
    }

    @Test
    fun cleanName_usesFallbackForMissingOrBlankNames() {
        assertEquals("Beleg", FileValidator.cleanName(null, "Beleg"))
        assertEquals("Beleg", FileValidator.cleanName("   ", "Beleg"))
        assertEquals("Beleg", FileValidator.cleanName("/", "Beleg"))
    }

    @Test
    fun cleanName_shortensLongNamesButKeepsTheExtension() {
        val long = "x".repeat(300) + ".pdf"
        val cleaned = FileValidator.cleanName(long, "Beleg")
        assertEquals(FileLimits.MAX_NAME_LENGTH, cleaned.length)
        assertTrue(cleaned.endsWith(".pdf"))
    }

    @Test
    fun jpegName_replacesTheExtension() {
        assertEquals("foto.jpg", FileValidator.jpegName("foto.png"))
        assertEquals("foto.jpg", FileValidator.jpegName("foto.jpg"))
        assertEquals("foto.jpg", FileValidator.jpegName("foto.heic"))
        assertEquals("Beleg.jpg", FileValidator.jpegName("Beleg"))
        assertEquals("a.b.jpg", FileValidator.jpegName("a.b.png"))
    }

    @Test
    fun jpegName_neverExceedsTheLimit() {
        assertTrue(FileValidator.jpegName("x".repeat(198) + ".p").length <= FileLimits.MAX_NAME_LENGTH)
    }
}
