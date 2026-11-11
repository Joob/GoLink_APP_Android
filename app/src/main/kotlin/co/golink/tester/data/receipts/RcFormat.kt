package co.golink.tester.data.receipts

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Porte de services/receipts/format.js. Dinheiro sempre em formato português
 * (€1.758,80) — é o do e-Fatura e do IRS, seja qual for a língua da interface.
 */
object RcFormat {
    private fun group(n: Long): String = n.toString().reversed().chunked(3).joinToString(".").reversed()

    /** 175880 → "€1.758,80"; whole → "€1.759"; sign → "+€…" para positivos. */
    fun money(cents: Long, whole: Boolean = false, sign: Boolean = false): String {
        val neg = cents < 0
        val a = abs(cents)
        val body = if (whole) group((a + 50) / 100) else "${group(a / 100)},${(a % 100).toString().padStart(2, '0')}"
        val prefix = if (neg) "−" else if (sign && cents > 0) "+" else ""
        return "$prefix€$body"
    }

    /** Texto do utilizador ("1.234,56", "1234.56", "12,5") → cêntimos ou null. */
    fun parseMoney(text: String?): Long? {
        var s = (text ?: "").trim().replace(Regex("[€\\s]"), "")
        if (s.isEmpty()) return null
        s = when {
            Regex(",\\d{1,2}$").containsMatchIn(s) -> s.replace(".", "").replace(',', '.')
            Regex("\\.\\d{1,2}$").containsMatchIn(s) -> s.replace(",", "")
            else -> s.replace(Regex("[.,]"), "")
        }
        // Number() do JS não aceita sufixos do Java ("1d", "0x1p3").
        if (s.any { it.isLetter() && it != 'e' && it != 'E' }) return null
        val n = s.toDoubleOrNull() ?: return null
        if (n.isNaN() || n.isInfinite() || n < 0) return null
        return (n * 100).roundToLong()
    }

    /** Cêntimos → "80,00" para inputs. */
    fun moneyInput(cents: Long?): String = if (cents == null) "" else money(cents).replace("€", "")

    fun pad(n: Int): String = n.toString().padStart(2, '0')

    fun ymd(d: LocalDate): String = "${d.year}-${pad(d.monthValue)}-${pad(d.dayOfMonth)}"

    private val YMD = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

    fun parseYmd(s: String?): LocalDate? {
        val m = YMD.find(s ?: "") ?: return null
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }

    fun ym(d: LocalDate): String = "${d.year}-${pad(d.monthValue)}"

    /** m = 0..11 (como no JS). */
    fun daysInMonth(y: Int, m: Int): Int = YearMonth.of(y, m + 1).lengthOfMonth()

    /** NIF mascarado: "2•• ••• •19". */
    fun maskNif(nif: String?): String {
        val s = (nif ?: "").filter { it.isDigit() }
        if (s.length != 9) return s
        return "${s[0]}•• ••• •${s.substring(7)}"
    }

    /** "514778210" → "514 778 210". */
    fun spacedNif(nif: String?): String {
        val s = (nif ?: "").filter { it.isDigit() }
        return if (s.length == 9) "${s.substring(0, 3)} ${s.substring(3, 6)} ${s.substring(6)}" else s
    }

    fun pct(part: Long, total: Long): Int = if (total > 0) Math.round(part.toDouble() / total * 100).toInt() else 0
}
