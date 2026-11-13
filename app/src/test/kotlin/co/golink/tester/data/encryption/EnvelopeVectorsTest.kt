package co.golink.tester.data.encryption

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vetores gerados com o WebCrypto (o mesmo `crypto.subtle.deriveBits` PBKDF2 de
 * resources/js/services/e2e.js) e com o formatRecovery da web. Se mudar, a
 * passphrase deixa de abrir na web o cofre criado no Android (e vice-versa).
 *
 * O sealed box e o secretbox (libsodium nativo do Android) não correm na JVM:
 * ficam para um teste instrumentado.
 */
class EnvelopeVectorsTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun pbkdf2MatchesWebCrypto() {
        assertArrayEquals(
            hex("02169e674d80c0fc7292e8610c8f17be140df062af9aa33ea85eaabecffdf08d"),
            Envelope.deriveKey("correct horse battery", salt, 1000, 0, Envelope.KDF_ALG_PBKDF2),
        )
        // Não-ASCII: a password entra em UTF-8 (o PBEKeySpec do Android usava Latin-1).
        assertArrayEquals(
            hex("10f19c73062c1f5a8f74655bb5b605739cc6f93627067ae2b9a3b4519b49881d"),
            Envelope.deriveKey("pässwörd 🔑 ação", salt, 1000, 0, Envelope.KDF_ALG_PBKDF2),
        )
    }

    @Test
    fun pbkdf2DefaultIterationsMatchWebCrypto() {
        assertEquals(Envelope.KDF_ALG_PBKDF2, Envelope.defaultKdfAlg())
        assertEquals(600_000L, Envelope.defaultKdfOps())
        assertArrayEquals(
            hex("bb06c8c0b1dd5bfd4e40f4e297a2d0e64da7ef94b4b8ec20989021c8b41536ad"),
            Envelope.deriveKey("correct horse battery", salt, Envelope.defaultKdfOps(), Envelope.defaultKdfMem(), Envelope.defaultKdfAlg()),
        )
    }

    @Test
    fun recoveryKeyFormatMatchesWeb() {
        val key = ByteArray(32) { ((it * 37 + 5) and 0xff).toByte() }
        val text = "052A-4F74-99BE-E308-2D52-779C-C1E6-0B30-557A-9FC4-E90E-3358-7DA2-C7EC-1136-5B80"
        assertEquals(text, Envelope.formatRecovery(key))
        assertArrayEquals(key, Envelope.parseRecovery(text))
        // O utilizador pode escrever em minúsculas, com espaços ou sem hífens.
        assertArrayEquals(key, Envelope.parseRecovery(" " + text.lowercase().replace("-", " ") + "\n"))
    }
}
