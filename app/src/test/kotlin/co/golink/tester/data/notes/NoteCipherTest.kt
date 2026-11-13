package co.golink.tester.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCipherTest {
    private val key = ByteArray(32) { (it + 1).toByte() }

    /** Vetor gerado com WebCrypto (createCipher(K, '01HXNOTE', 'note') da web). */
    @Test
    fun decryptsWebBlob() {
        val cipher = NoteCipher(key, "01HXNOTE")
        val plain = cipher.decrypt("yMnKy8zNzs/Q0dLTmq8nUqDLWCEvW0mVJt+GpMj63+eIaqdd")
        assertEquals("olá yjs", String(plain, Charsets.UTF_8))
    }

    @Test
    fun roundTrip() {
        val cipher = NoteCipher(key, "n1")
        val data = ByteArray(1000) { it.toByte() }
        assertEquals(data.toList(), cipher.decrypt(cipher.encrypt(data)).toList())
    }
}
