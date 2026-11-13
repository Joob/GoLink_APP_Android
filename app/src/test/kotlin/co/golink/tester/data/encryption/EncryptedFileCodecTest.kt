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

    @Test
    fun blockLayoutOpensAnyBlock() {
        val plain = ByteArray(250) { it.toByte() }
        val obj = EncryptedFileCodec.encrypt(plain, key, 100, prefix)
        val layout = EncryptedFileCodec.blockLayout(obj.copyOf(36), obj.size.toLong(), key)

        assertEquals(250L, layout.plainLength)
        assertEquals(2, layout.lastIndex)
        val block = (0..2).map { i ->
            val off = layout.offsetOf(i).toInt()
            layout.open(obj.copyOfRange(off, off + layout.lengthOf(i)), i)
        }
        assertArrayEquals(plain, block[0] + block[1] + block[2])
    }

    @Test
    fun blockLayoutReadsDuplicatedHeader() {
        val plain = ByteArray(120) { it.toByte() }
        val obj = EncryptedFileCodec.encrypt(plain, key, 100, prefix)
        val dup = obj.copyOf(EncryptedFileCodec.HEADER_LEN) + obj
        val layout = EncryptedFileCodec.blockLayout(dup.copyOf(36), dup.size.toLong(), key)

        assertEquals(120L, layout.plainLength)
        val off = layout.offsetOf(1).toInt()
        assertArrayEquals(plain.copyOfRange(100, 120), layout.open(dup.copyOfRange(off, off + layout.lengthOf(1)), 1))
    }

    @Test
    fun blockLayoutHandlesObjectsOver2Gb() {
        val head = EncryptedFileCodec.encrypt(ByteArray(10), key, 65536, prefix).copyOf(36)
        val blocks = 80_000L
        val size = EncryptedFileCodec.HEADER_LEN + blocks * (65536 + EncryptedFileCodec.TAG_LEN)
        val layout = EncryptedFileCodec.blockLayout(head, size, key)

        assertEquals(blocks * 65536, layout.plainLength)
        assertEquals(EncryptedFileCodec.HEADER_LEN + 79_999L * 65552, layout.offsetOf(79_999))
    }
}
