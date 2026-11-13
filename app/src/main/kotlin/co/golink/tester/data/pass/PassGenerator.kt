package co.golink.tester.data.pass

import java.security.SecureRandom
import kotlin.math.ln

/** Gerador local (SecureRandom). Espelho de resources/js/services/pass/generator.js + random.js. */
object PassGenerator {
    enum class Mode(val key: String) { Random("random"), Passphrase("passphrase"), Pin("pin") }

    data class RandomOpts(val length: Int = 18, val upper: Boolean = true, val digits: Boolean = true, val symbols: Boolean = true, val avoidLookalike: Boolean = true)
    data class PassphraseOpts(val words: Int = 5, val separator: String = "-", val capitalize: Boolean = true, val number: Boolean = true)
    data class PinOpts(val length: Int = 6)

    val RANDOM_LIMITS = 8..64
    val PASSPHRASE_LIMITS = 3..10
    val PIN_LIMITS = 4..12

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    const val SYMBOLS = "!@#$%^&*-_=+?"
    private const val LOOKALIKE = "Il1O0o"

    private val rng = SecureRandom()

    /** Inteiro uniforme em [0, max) (SecureRandom.nextInt já é sem enviesamento). */
    fun randomInt(max: Int): Int = if (max <= 0) 0 else rng.nextInt(max)

    private fun <T> MutableList<T>.shuffleSecure(): MutableList<T> {
        for (i in size - 1 downTo 1) {
            val j = randomInt(i + 1)
            val t = this[i]; this[i] = this[j]; this[j] = t
        }
        return this
    }

    private fun pick(set: String): Char = set[randomInt(set.length)]

    fun random(o: RandomOpts = RandomOpts()): String {
        fun strip(s: String) = if (o.avoidLookalike) s.filterNot { it in LOOKALIKE } else s
        val sets = mutableListOf(strip(LOWER))
        if (o.upper) sets += strip(UPPER)
        if (o.digits) sets += strip(DIGITS)
        if (o.symbols) sets += SYMBOLS
        val all = sets.joinToString("")
        val length = o.length.coerceIn(RANDOM_LIMITS)
        val chars = sets.map(::pick).toMutableList()
        while (chars.size < length) chars += pick(all)
        return chars.shuffleSecure().joinToString("")
    }

    fun passphrase(o: PassphraseOpts = PassphraseOpts()): String {
        val n = o.words.coerceIn(PASSPHRASE_LIMITS)
        val list = PassWordlist.words
        val words = MutableList(n) {
            val w = list[randomInt(list.size)]
            if (o.capitalize) w.replaceFirstChar { c -> c.uppercaseChar() } else w
        }
        if (o.number) {
            val i = randomInt(n)
            words[i] = words[i] + randomInt(10)
        }
        return words.joinToString(o.separator)
    }

    fun pin(o: PinOpts = PinOpts()): String {
        val n = o.length.coerceIn(PIN_LIMITS)
        return (1..n).joinToString("") { randomInt(10).toString() }
    }

    private fun log2(x: Double) = ln(x) / ln(2.0)

    /** Entropia exata do gerador (para a força mostrada no Generator). */
    fun entropy(mode: Mode, random: RandomOpts, phrase: PassphraseOpts, pin: PinOpts): Double = when (mode) {
        Mode.Pin -> pin.length * log2(10.0)
        Mode.Passphrase -> phrase.words * log2(PassWordlist.words.size.toDouble()) + if (phrase.number) log2(10.0 * phrase.words) else 0.0
        Mode.Random -> {
            val cut = if (random.avoidLookalike) 2 else 0
            var pool = 26 - cut
            if (random.upper) pool += 26 - cut
            if (random.digits) pool += 10 - cut
            if (random.symbols) pool += SYMBOLS.length
            random.length * log2(pool.toDouble())
        }
    }

    enum class CharKind { Letter, Digit, Symbol }

    fun charKind(c: Char): CharKind = when {
        c in '0'..'9' -> CharKind.Digit
        c in 'a'..'z' || c in 'A'..'Z' -> CharKind.Letter
        else -> CharKind.Symbol
    }
}
