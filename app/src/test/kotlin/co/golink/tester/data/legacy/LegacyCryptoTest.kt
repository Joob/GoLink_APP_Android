package co.golink.tester.data.legacy

import co.golink.tester.domain.legacy.LegacyGuardian
import co.golink.tester.domain.legacy.LegacyGuardianDto
import co.golink.tester.domain.legacy.LegacyMeta
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyCryptoTest {
    @Test
    fun `normalizeCode tolera minusculas, espacos e confusoes ao ditar como a web`() {
        val code = "0123456789ABCDEFGHJKMNPQR"
        assertEquals("01234-56789-ABCDE-FGHJK-MNPQR", LegacyCrypto.normalizeCode(code))
        assertEquals("01234-56789-ABCDE-FGHJK-MNPQR", LegacyCrypto.normalizeCode("o1234 56789-abcde fghjk-mnpqr"))
        assertEquals("11111-11111-11111-11111-11111", LegacyCrypto.normalizeCode("ILIL1 lilil 11111 11111 11111"))
    }

    @Test
    fun `normalizeCode recusa tamanho errado ou letras fora do alfabeto`() {
        assertNull(LegacyCrypto.normalizeCode("01234-56789"))
        assertNull(LegacyCrypto.normalizeCode("U1234-56789-ABCDE-FGHJK-MNPQR"))
        assertNull(LegacyCrypto.normalizeCode(null))
    }

    @Test
    fun `csvCell neutraliza formulas e escapa aspas`() {
        assertEquals("\"'=SUM(A1)\"", LegacyCrypto.csvCell("=SUM(A1)"))
        assertEquals("\"'@x\"", LegacyCrypto.csvCell("@x"))
        assertEquals("\"diz \"\"olá\"\"\"", LegacyCrypto.csvCell("diz \"olá\""))
        assertEquals("\"\"", LegacyCrypto.csvCell(null))
    }

    private fun guardian(status: String, publicKey: String?, meta: Map<String, String>) = LegacyGuardian(
        LegacyGuardianDto(id = "g1", email = "a@b.c", status = status, public_key = publicKey),
        LegacyMeta(JsonObject(meta.mapValues { JsonPrimitive(it.value) })),
    )

    @Test
    fun `keyState so confia na chave fixada no meta da propria linha`() {
        val pinned = mapOf("id" to "g1", "email" to "a@b.c", "pinned_key" to "K1")
        assertNull(LegacyCrypto.keyState(guardian("invited", "K1", pinned)))
        assertNull(LegacyCrypto.keyState(guardian("accepted", null, pinned)))
        assertEquals("pinned", LegacyCrypto.keyState(guardian("accepted", "K1", pinned)))
        assertEquals("changed", LegacyCrypto.keyState(guardian("accepted", "K2", pinned)))
        assertEquals("unverified", LegacyCrypto.keyState(guardian("accepted", "K1", emptyMap())))
        // Pin de outra linha (o servidor trocou o meta_enc): não conta.
        assertEquals("unverified", LegacyCrypto.keyState(guardian("accepted", "K1", pinned + ("id" to "g2"))))
    }
}
