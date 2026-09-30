package de.hagi089.obelix.data.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChunkerTest {

    private val chunk = FileLimits.CHUNK_SIZE_BYTES

    /** Erkennbare Testdaten (kein Nullfeld), damit vertauschte Stücke auffallen. */
    private fun bytes(size: Int) = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun smallFile_isOneChunk() {
        val parts = FileChunker.split(bytes(1))
        assertEquals(1, parts.size)
        assertEquals(1, parts[0].size)
    }

    @Test
    fun exactlyOneChunk_staysOneChunk_andOneByteMoreIsTwo() {
        assertEquals(1, FileChunker.split(bytes(chunk)).size)
        val two = FileChunker.split(bytes(chunk + 1))
        assertEquals(2, two.size)
        assertEquals(chunk, two[0].size)
        assertEquals(1, two[1].size)
    }

    @Test
    fun largestFile_isTenChunks_allButLastFull() {
        val parts = FileChunker.split(bytes(FileLimits.MAX_FILE_BYTES))
        assertEquals(FileLimits.MAX_CHUNKS, parts.size)
        parts.dropLast(1).forEach { assertEquals(chunk, it.size) }
        assertEquals(FileLimits.MAX_FILE_BYTES - 9 * chunk, parts.last().size)
        assertTrue(parts.all { it.size in 1..chunk })
    }

    @Test
    fun chunkCount_matchesSplit_forBoundarySizes() {
        listOf(1, 2, chunk - 1, chunk, chunk + 1, 2 * chunk, 2 * chunk + 1, FileLimits.MAX_FILE_BYTES).forEach { size ->
            assertEquals("Größe $size", FileLimits.chunkCountFor(size.toLong()), FileChunker.split(bytes(size)).size)
        }
    }

    @Test
    fun splitThenJoin_returnsTheSameBytes() {
        listOf(1, 1000, chunk, chunk + 1, 3 * chunk + 7, FileLimits.MAX_FILE_BYTES).forEach { size ->
            val original = bytes(size)
            val parts = FileChunker.split(original)
            val joined = FileChunker.join(parts, parts.size, original.size.toLong())
            assertNotNull("Größe $size", joined)
            assertArrayEquals("Größe $size", original, joined)
        }
    }

    @Test
    fun join_detectsMissingOrWrongChunks() {
        val original = bytes(2 * chunk + 5)
        val parts = FileChunker.split(original)
        assertNull("fehlendes Stück", FileChunker.join(parts.dropLast(1), 3, original.size.toLong()))
        assertNull("falsche Stückzahl in den Metadaten", FileChunker.join(parts, 2, original.size.toLong()))
        assertNull("falsche Größe in den Metadaten", FileChunker.join(parts, 3, original.size.toLong() + 1))
        assertNull("Größe über dem Limit", FileChunker.join(parts, 3, FileLimits.MAX_FILE_BYTES.toLong() + 1))
    }

    @Test
    fun split_rejectsEmptyAndTooLargeFiles() {
        assertThrows(IllegalArgumentException::class.java) { FileChunker.split(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { FileChunker.split(ByteArray(FileLimits.MAX_FILE_BYTES + 1)) }
    }

    @Test
    fun limits_fitFirestoreAndRules() {
        // Firestore: 1 MiB je Dokument; 8 MiB passen in höchstens 10 Stücke (firestore.rules: chunkCount <= 10).
        assertTrue(FileLimits.CHUNK_SIZE_BYTES < 1024 * 1024)
        assertEquals(FileLimits.MAX_CHUNKS, FileLimits.chunkCountFor(FileLimits.MAX_FILE_BYTES.toLong()))
        assertEquals(1, FileLimits.chunkCountFor(0))
    }

    @Test
    fun onlyJpegAndPdfAreStored() {
        assertTrue(FileLimits.isStoredType("image/jpeg"))
        assertTrue(FileLimits.isStoredType("application/pdf"))
        assertTrue(!FileLimits.isStoredType("image/png"))
        assertTrue(!FileLimits.isStoredType("text/html"))
        assertTrue(!FileLimits.isStoredType(null))
    }
}
