package co.golink.tester.data.legacy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.random.Random

/**
 * Caminho do `contribute`: as partes dos guardiões (a do próprio + as relays)
 * reconstroem K_vault. O isVaultKey (secretbox nativo) não corre na JVM, por
 * isso aqui compara-se com a chave original.
 */
class LegacyVaultSharesTest {
    private val kVault = Random(42).nextBytes(32)

    @Test
    fun `3 guardioes com threshold 2 - qualquer par reconstroi K_vault`() {
        val shares = LegacyCrypto.splitVaultKey(kVault, 2, 3)
        assertEquals(3, shares.size)
        for (a in shares.indices) for (b in shares.indices) {
            if (a == b) continue
            assertArrayEquals("partes $a+$b", kVault, LegacyCrypto.combineShares(listOf(shares[a], shares[b]), 2))
        }
        // Todas juntas também (o contribute pode receber relays a mais).
        assertArrayEquals(kVault, LegacyCrypto.combineShares(shares, 2))
    }

    @Test
    fun `parte trocada nao reconstroi a chave`() {
        val shares = LegacyCrypto.splitVaultKey(kVault, 2, 3)
        val other = LegacyCrypto.splitVaultKey(Random(7).nextBytes(32), 2, 3)
        // Pode dar lixo ou falhar (x repetido); nunca a chave.
        val mixed = runCatching { LegacyCrypto.combineShares(listOf(shares[0], other[1]), 2) }.getOrNull()
        assertFalse(mixed != null && kVault.contentEquals(mixed))
    }

    @Test
    fun `1 de n e uma copia com o numero da parte no fim, como na web`() {
        val shares = LegacyCrypto.splitVaultKey(kVault, 1, 3)
        shares.forEachIndexed { i, s ->
            assertEquals(33, s.size)
            assertEquals((i + 1).toByte(), s[32])
            assertArrayEquals(kVault, LegacyCrypto.combineShares(listOf(s), 1))
        }
    }
}
