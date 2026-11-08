package co.golink.tester.data.pass

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Estimativa de força local (sem zxcvbn). Espelho exato de
 * resources/js/services/pass/strength.js: entropia pelo alfabeto, com penalizações.
 */
object PassStrength {
    data class Result(val bits: Double, val score: Int)

    val SCORE_LABELS = listOf("very_weak", "weak", "fair", "good", "strong")

    private val COMMON = (
        "password passw0rd 123456 12345678 123456789 1234567890 qwerty qwertyuiop abc123 111111 123123 admin letmein welcome " +
            "monkey dragon iloveyou sunshine princess football baseball master shadow superman trustno1 login starwars " +
            "whatever freedom hello charlie donald qazwsx zaq12wsx 654321 000000 1q2w3e4r 1qaz2wsx senha benfica porto sporting " +
            "contraseña motdepasse azerty soleil"
        ).split(' ').toSet()

    private val SEQUENCES = listOf("abcdefghijklmnopqrstuvwxyz", "0123456789", "qwertyuiopasdfghjklzxcvbnm", "azertyuiopqsdfghjklmwxcvbn")
    private val SEQ_BOTH = SEQUENCES.flatMap { listOf(it, it.reversed()) }

    private fun poolSize(pw: String): Int {
        var pool = 0
        if (pw.any { it in 'a'..'z' }) pool += 26
        if (pw.any { it in 'A'..'Z' }) pool += 26
        if (pw.any { it in '0'..'9' }) pool += 10
        if (pw.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') }) pool += 33
        if (pw.any { it.code > 0x7f }) pool += 60
        return if (pool == 0) 1 else pool
    }

    private fun sequencePenalty(pw: String): Int {
        val low = pw.lowercase()
        var run = 0
        for (i in 0 until low.length - 2) {
            val tri = low.substring(i, i + 3)
            if (SEQ_BOTH.any { it.contains(tri) }) run++
        }
        return run
    }

    private val TRAILING = Regex("[0-9!@#$.]+$")
    private val TRIPLE = Regex("(.)\\1{2,}")
    private val REPEAT = Regex("^(.+?)\\1+$")
    private val DIGITS_ONLY = Regex("^[0-9]+$")

    fun entropy(pw: String): Double {
        if (pw.isEmpty()) return 0.0
        val lower = pw.lowercase()
        if (lower in COMMON || lower.replace(TRAILING, "") in COMMON) return 8.0
        // JS conta unidades UTF-16 (.length); String.length do Kotlin também.
        val squashed = pw.replace(TRIPLE, "$1$1")
        var effLen = squashed.length - sequencePenalty(squashed) * 0.7
        REPEAT.find(pw)?.let { effLen = min(effLen, it.groupValues[1].length + 1.0) }
        if (DIGITS_ONLY.matches(pw) && pw.length <= 8) effLen = min(effLen, pw.length * 0.8)
        return max(0.0, effLen) * (ln(poolSize(pw).toDouble()) / ln(2.0))
    }

    fun scoreFromEntropy(bits: Double): Int = when {
        bits < 28 -> 0
        bits < 40 -> 1
        bits < 60 -> 2
        bits < 80 -> 3
        else -> 4
    }

    fun of(pw: String): Result {
        val bits = entropy(pw)
        return Result(bits, scoreFromEntropy(bits))
    }

    /** Ataque offline rápido: 1e10 tentativas/s, metade do espaço em média. */
    fun crackTimeKey(bits: Double): String {
        val seconds = 2.0.pow(max(0.0, bits - 1)) / 1e10
        return when {
            seconds < 1 -> "instant"
            seconds < 3600 -> "minutes"
            seconds < 86400 * 30 -> "days"
            seconds < 86400.0 * 365 * 100 -> "years"
            seconds < 86400.0 * 365 * 1e6 -> "centuries"
            else -> "millions_years"
        }
    }
}
