package co.golink.tester.data.encryption

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

/**
 * Interop do codec Android com PHP/JS. O VECTOR é o mesmo gerado pelo codec JS
 * (ops/e2e/js-selftest.mjs) e verificado em PHP — se o Android produzir/consumir
 * os mesmos bytes, os três lados são interoperáveis. Corre na JVM (javax.crypto).
 */
class EncryptedFileCodecTest {
    private val key = ByteArray(32) { it.toByte() }
    private val prefix = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val plaintext = "GoLink E2E interop vector 0123456789"
    private val vectorB64 =
        "R0xLMQEBAAAAEAECAwQFBgcIzXBi2eN81S6+jfQ0GoqqJMZmK/0lGk9aY0VfIsKQjlsK+8HFxqHEn9JMBJJV67WjCWP2Oi7dWXXAdW3TEFw2UrTOiG8C2QeRTOmPgqCJ847nsZZy"

    @Test
    fun decodesJsVector() {
        val obj = Base64.getDecoder().decode(vectorB64)
        assertEquals(plaintext, String(EncryptedFileCodec.decryptFull(obj, key)))
    }

    @Test
    fun producesSameBytesAsJs() {
        val obj = EncryptedFileCodec.encrypt(plaintext.toByteArray(), key, 16, prefix)
        assertEquals(vectorB64, Base64.getEncoder().encodeToString(obj))
    }

    @Test
    fun roundTrip() {
        for (size in intArrayOf(0, 1, 15, 16, 17, 48, 171, 4103)) {
            val p = ByteArray(size) { ((it * 7 + 3) and 0xff).toByte() }
            val obj = EncryptedFileCodec.encrypt(p, key, 16)
            assertArrayEquals(p, EncryptedFileCodec.decryptFull(obj, key))
            assertEquals(size, EncryptedFileCodec.plaintextLength(obj.size, 16))
        }
    }

    @Test
    fun rangeMatchesSubstring() {
        val p = ByteArray(200) { ((it * 13 + 1) and 0xff).toByte() }
        val obj = EncryptedFileCodec.encrypt(p, key, 16)
        val reader = { off: Int, len: Int -> obj.copyOfRange(off, off + len) }
        val ranges = listOf(0 to 1, 0 to 16, 0 to 17, 5 to 30, 16 to 16, 100 to 50, 190 to 10, 0 to 200, 199 to 1)
        for ((s, l) in ranges) {
            assertArrayEquals(p.copyOfRange(s, s + l), EncryptedFileCodec.decryptRange(reader, obj.size, key, s, l))
        }
    }

    @Test(expected = Exception::class)
    fun tamperIsDetected() {
        val obj = EncryptedFileCodec.encrypt("hello world tamper".toByteArray(), key, 16)
        obj[EncryptedFileCodec.HEADER_LEN + 3] = (obj[EncryptedFileCodec.HEADER_LEN + 3].toInt() xor 1).toByte()
        EncryptedFileCodec.decryptFull(obj, key)
    }
}
