package co.golink.tester.data.legacy

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

/** Vetores gerados com shamir-secret-sharing 0.0.4 (a lib da web). */
class ShamirTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private data class Vec(val t: Int, val secret: String, val shares: List<String>)

    private val vectors = listOf(
        Vec(2, "183d6287acd1f61b40658aafd4f91e43688db2d7fc21466b90b5daff24496e93", listOf("3d9a9e8a9f4649da2103b705b248331db8557f7b2fd23e6aa71200a50246021641", "b5a04258b31045b8305b09edead6a93b37c80613e055d0a38228148aca1dc1ed18")),
        Vec(2, "23486d92b7dc01264b7095badf04294e7398bde2072c51769bc0e50a2f54799e", listOf("6157f2e6cdc4fde793fc3e6ee7888ad0eb50ec7b09d419df367526135d1c0ac8b1", "a8f9415b9d916e5c5846b3027e323446a3fd2c16e4d3a9186573d7633eac4cf323", "7adee7bb201573929085388811f1c362a6cd9b91b9f0188ccf5d0665ff1d0fe733")),
        Vec(3, "3a5f84a9cef3183d6287acd1f61b40658aafd4f91e43688db2d7fc21466b90b5", listOf("bdd995576ad8a0d0a7bf67acc49b852d99969a19bca2de7a35eeed7d5c3cb41123", "3bd3c449114e663524d16798c7a2082ade87c3830638695ba3920ae4860d2ce112", "ddae1ec6d9c77d78b22a112e7d2641acb15b228a12f2b7e616d0f0e5fabc479333", "9bbbe59bc58d05a16668df3706d992bd6bbe62089d65ac94588e50a45e3ec9470f", "144570765bd7281200b977761a54e2d02de55a8331780e6082f315d95aad9635b6")),
        Vec(7, "54799ec3e80d32577ca1c6eb10355a7fa4c9ee13385d82a7ccf1163b6085aacf", listOf("9da9c539c6d7afbe7dee06cdf215e160c935fd831bc9f908147ed399c9d8aaec93", "2a0a8348817b5bcc56b6532c69036dae20402e139b418dabbba9ffc750ea660e8c", "0db4b363cd899fb2f043835e844c6f9ba5e6bb75f9486ea5a1cd8719ec8fdbc590", "822fe301e91e1a896b103f810b6510461e437d74e668eaf8c931e125279d197b75", "98b7f4485b73ec3a955b5462b0153645d6f393b70812b2ccd345646e8da8db42e5", "df64d39cb91bacffaad5478b1ac9c567cf1bb8989db09c756f4cb9fab48082889e", "b24199eb1a1a5e374cb54fb28fb873685f4845516f02f9d3b6d4b0acce343d9030")),
    )

    private fun <T> subsets(list: List<T>, k: Int): List<List<T>> =
        if (k == 0) listOf(emptyList()) else list.indices.flatMap { i -> subsets(list.drop(i + 1), k - 1).map { listOf(list[i]) + it } }

    @Test
    fun combinesWebShares() {
        for (v in vectors) {
            for (pick in subsets(v.shares, v.t)) assertArrayEquals(hex(v.secret), Shamir.combine(pick.map(::hex)))
        }
    }

    @Test
    fun splitRoundTrip() {
        val rnd = Random(7)
        val secret = rnd.nextBytes(32)
        val shares = Shamir.split(secret, 5, 3) { rnd.nextBytes(it) }
        for (pick in subsets(shares, 3)) assertArrayEquals(secret, Shamir.combine(pick))
    }

    @Test
    fun oneOfNIsCopy() {
        val secret = Random(1).nextBytes(32)
        val shares = LegacyCrypto.splitVaultKey(secret, 1, 3)
        assertArrayEquals(secret, LegacyCrypto.combineShares(listOf(shares[2]), 1))
    }
}
