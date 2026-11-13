package co.golink.tester.data.legacy

import co.golink.tester.data.encryption.Envelope

/**
 * Porta de shamir-secret-sharing 0.0.4 (a lib da web): GF(256) com x⁸+x⁴+x³+x+1 e
 * gerador 0xe5. Parte = y-bytes || x, com o x no último byte e tirado de 1..255
 * baralhado. As partes são intercambiáveis com as da web (ver ShamirTest).
 */
object Shamir {
    private val EXP = IntArray(255)
    private val LOG = IntArray(256)

    init {
        var v = 1
        for (i in 0 until 255) {
            EXP[i] = v
            LOG[v] = i
            v = mulSlow(v, 0xe5)
        }
    }

    private fun mulSlow(a: Int, b: Int): Int {
        var x = a; var y = b; var r = 0
        while (y != 0) {
            if (y and 1 != 0) r = r xor x
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11b
            y = y shr 1
        }
        return r
    }

    private fun mult(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP[(LOG[a] + LOG[b]) % 255]

    private fun div(a: Int, b: Int): Int {
        require(b != 0) { "cannot divide by zero" }
        return if (a == 0) 0 else EXP[(LOG[a] - LOG[b] + 255) % 255]
    }

    private fun interpolate(xs: IntArray, ys: IntArray): Int {
        var result = 0
        for (i in xs.indices) {
            var basis = 1
            for (j in xs.indices) {
                if (i == j) continue
                basis = mult(basis, div(xs[j], xs[i] xor xs[j])) // x = 0: num = 0 ^ xs[j]
            }
            result = result xor mult(ys[i], basis)
        }
        return result
    }

    private fun evaluate(coefficients: IntArray, x: Int): Int {
        var result = coefficients.last()
        for (i in coefficients.size - 2 downTo 0) result = mult(result, x) xor coefficients[i]
        return result
    }

    private fun coordinates(random: (Int) -> ByteArray): IntArray {
        val c = IntArray(255) { it + 1 }
        val idx = random(255)
        for (i in 0 until 255) {
            val j = (idx[i].toInt() and 0xff) % 255
            val t = c[i]; c[i] = c[j]; c[j] = t
        }
        return c
    }

    fun split(secret: ByteArray, shares: Int, threshold: Int, random: (Int) -> ByteArray = Envelope::randomBytes): List<ByteArray> {
        require(secret.isNotEmpty()) { "secret cannot be empty" }
        require(shares in 2..255 && threshold in 2..255 && shares >= threshold) { "bad shares/threshold" }
        val xs = coordinates(random)
        val out = List(shares) { ByteArray(secret.size + 1).also { s -> s[secret.size] = xs[it].toByte() } }
        val degree = threshold - 1
        for (i in secret.indices) {
            val rnd = random(degree)
            val coefficients = IntArray(degree + 1) { if (it == 0) secret[i].toInt() and 0xff else rnd[it - 1].toInt() and 0xff }
            for (j in 0 until shares) out[j][i] = evaluate(coefficients, xs[j]).toByte()
        }
        return out
    }

    fun combine(shares: List<ByteArray>): ByteArray {
        require(shares.size in 2..255) { "shares must have at least 2 and at most 255 elements" }
        val len = shares[0].size
        require(shares.all { it.size == len && it.size >= 2 }) { "bad share length" }
        val xs = IntArray(shares.size) { shares[it][len - 1].toInt() and 0xff }
        require(xs.toSet().size == xs.size) { "shares must contain unique values but a duplicate was found" }
        val ys = IntArray(shares.size)
        return ByteArray(len - 1) { i ->
            for (j in shares.indices) ys[j] = shares[j][i].toInt() and 0xff
            interpolate(xs, ys).toByte()
        }
    }
}
