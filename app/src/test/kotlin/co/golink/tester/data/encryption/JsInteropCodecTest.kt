package co.golink.tester.data.encryption

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interop com o codec da Web (resources/js/services/e2eFileCodec.js).
 *
 * As fixtures foram produzidas pelo JS com chave e prefix FIXOS, por isso o teste
 * verifica as duas direções em bytes:
 *   - o Kotlin decifra o ciphertext do JS;
 *   - o Kotlin, com o mesmo prefix, produz bytes idênticos aos do JS.
 *
 * Se este teste passar, mover a cripto da Web para um Web Worker não muda nada
 * para o Android — o formato no fio é o mesmo.
 */
class JsInteropCodecTest {

    private val key = ByteArray(32) { 7 }
    private val prefix = ByteArray(8) { 3 }

    private fun fixture(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    @Test
    fun `kotlin decifra ciphertext produzido pelo js`() {
        val plain = EncryptedFileCodec.decryptFull(fixture("js2.bin"), key)
        assertArrayEquals(fixture("plain2.bin"), plain)
    }

    @Test
    fun `kotlin produz os mesmos bytes que o js`() {
        val ct = EncryptedFileCodec.encrypt(fixture("plain2.bin"), key, 64, prefix)
        assertArrayEquals(fixture("js2.bin"), ct)
    }

    @Test
    fun `adulteracao da tag e rejeitada`() {
        val bad = fixture("js2.bin").copyOf()
        bad[bad.size - 1] = (bad[bad.size - 1].toInt() xor 0xff).toByte()
        val failed = runCatching { EncryptedFileCodec.decryptFull(bad, key) }.isFailure
        assertTrue("ciphertext adulterado foi aceite", failed)
    }
}
