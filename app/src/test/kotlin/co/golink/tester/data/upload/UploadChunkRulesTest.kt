package co.golink.tester.data.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plano de chunks contra o StoreFileChunksAction (422 missing_chunks, 507). */
class UploadChunkRulesTest {
    private val mb5 = 5L * 1024 * 1024

    @Test
    fun chunkCountMatchesServerTotalChunks() {
        assertEquals(1, UploadManager.chunkCountFor(0L))
        assertEquals(1, UploadManager.chunkCountFor(1L))
        assertEquals(1, UploadManager.chunkCountFor(mb5))
        assertEquals(2, UploadManager.chunkCountFor(mb5 + 1))
        assertEquals(20, UploadManager.chunkCountFor(100L * 1024 * 1024))
    }

    @Test
    fun missingChunksListIsParsed() {
        assertEquals(listOf(3, 7), UploadManager.parseMissingChunks("""{"message":"missing_chunks","missing_chunks":[3,7]}"""))
        // Campos depois da lista não entram.
        assertEquals(listOf(1), UploadManager.parseMissingChunks("""{"message":"missing_chunks","missing_chunks":[1],"limit":1000}"""))
        assertEquals(emptyList<Int>(), UploadManager.parseMissingChunks("""{"message":"missing_chunks","missing_chunks":[]}"""))
        assertNull(UploadManager.parseMissingChunks("""{"message":"The name field is required."}"""))
        assertNull(UploadManager.parseMissingChunks(null))
    }

    @Test
    fun retryPolicy() {
        assertTrue(UploadManager.isRetryableChunkStatus(429))
        assertTrue(UploadManager.isRetryableChunkStatus(500))
        assertTrue(UploadManager.isRetryableChunkStatus(503))
        assertFalse(UploadManager.isRetryableChunkStatus(507)) // sem espaço
        assertFalse(UploadManager.isRetryableChunkStatus(422))
        assertFalse(UploadManager.isRetryableChunkStatus(413))
        assertFalse(UploadManager.isRetryableChunkStatus(403))
    }
}
