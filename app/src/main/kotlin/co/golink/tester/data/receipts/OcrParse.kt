package co.golink.tester.data.receipts

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Campos lidos do texto de um talão (valores em cêntimos; null = não leu). */
data class OcrFields(
    val merchant: String = "",
    val merchantNif: String = "",
    val buyerNif: String = "",
    val date: String = "",
    val time: String = "",
    val total: Long? = null,
    val vat: Long? = null,
    val atcud: String = "",
    val docNo: String = "",
    val docType: String = "",
    val filled: List<String> = emptyList(),
)

/**
 * Talão sem QR → campos do formulário, a partir do texto do OCR (Tesseract, no
 * aparelho). Porte 1:1 de resources/js/services/receipts/ocrParse.js — as
 * fixtures (ReceiptsLogicTest) garantem que dão o mesmo.
 */
object OcrParse {
    private const val FINAL_CONSUMER = "999999990"
    private val RATES = setOf(4, 5, 6, 9, 12, 13, 16, 22, 23)

    private val WS = Regex("(?U)\\s+")
    private val WS1 = Regex("(?U)\\s")
    private val MARKS = Regex("[\\u0300-\\u036f]")
    private val DIGITISH = Regex("^[0-9OQIL|SB.,:/-]+$")
    private val DIGIT = Regex("[0-9]")
    private val LOOKALIKE = Regex("[OQIL|SB]")
    private val AMOUNT = Regex("(^|[^\\d.,])(\\d{1,3}(?:\\.\\d{3})+|\\d+)[.,](\\d{2})(?!\\d)(?!\\s?%)")
    private val NIF_SPACED = Regex("(\\d{3})[ .](\\d{3})[ .](\\d{3})(?!\\d)")
    private val DIGITS = Regex("\\d+")
    private val YMD = Regex("(?:^|\\D)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
    private val DMY = Regex("(?:^|\\D)(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})(?!\\d)")
    private val TIME = Regex("(?:^|\\D)([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?(?!\\d)")
    private val RATE = Regex("(?:^|\\D)(\\d{1,2})(?:[.,]0{1,2})?\\s?%")
    private val MERCHANT_SKIP = Regex(
        "NIF|NIPC|CONTRIB|^(RUA|AVENIDA|LARGO|PRACA|ESTRADA|TRAVESSA|ALAMEDA|ROTUNDA)\\b|^(R|AV)\\.|\\bTEL|TLF|TELEF|FAX|WWW|HTTP|@|FATURA|FACTURA|ORIGINAL|DUPLICADO|RECIBO|\\bDATA\\b|CAPITAL SOCIAL|CONSERVATORIA|\\d{4}-\\d{3}",
    )
    private val LETTER = Regex("[A-Za-zÀ-ÿ]")
    private val LEAD = Regex("^[^A-Za-zÀ-ÿ0-9]+")
    private val TRAIL = Regex("[^A-Za-zÀ-ÿ0-9.)]+$")
    private val WORD_START = Regex("(^|[\\s\\-/&(.])([a-zà-ÿ])")
    private val BUYER = Regex("CLIENTE|ADQUIRENTE|CONSUMIDOR|COMPRADOR")
    private val MERCHANT_LABEL = Regex("NIF|NIPC|CONTRIB|N\\.? ?FISCAL|\\bVAT\\b")
    private val TOTAL = Regex("TOTAL|A PAGAR")
    private val NOT_TOTAL = Regex("SUB-? ?TOTAL|TOTAL (DO )?IVA|IVA TOTAL|ILIQ|S/ ?IVA|SEM IVA|S/ ?IMP|BASE|INCID|DESC|ARTIGOS|ITENS|QTD|QUANT|PONTOS|POUPA|ENTREGUE|TROCO|RECEBIDO")
    private val NOT_AMOUNT_LINE = Regex("ENTREGUE|TROCO|RECEBIDO|NUMERARIO|DINHEIRO|%")
    private val VAT_LINE = Regex("TOTAL (DO )?IVA|IVA TOTAL|VALOR (DO )?IVA")
    private val ATCUD = Regex("ATCUD ?[:.]? ?([A-Z0-9]{8,}-\\d+)")
    private val DOC = Regex("\\b(FS|FT|FR|NC|ND) ?([A-Z0-9-]+/\\d+)")

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").uppercase()

    private fun fixToken(t: String): String {
        if (!DIGITISH.matches(t)) return t
        val digits = DIGIT.findAll(t).count()
        val lookalikes = LOOKALIKE.findAll(t).count()
        if (digits == 0 || digits < lookalikes) return t
        return t.replace(Regex("[OQ]"), "0").replace(Regex("[IL|]"), "1").replace("S", "5").replace("B", "8")
    }

    private fun amounts(line: String): List<Long> = AMOUNT.findAll(line).mapNotNull { m ->
        val v = (m.groupValues[2].replace(".", "").toLongOrNull() ?: return@mapNotNull null) * 100 + m.groupValues[3].toLong()
        v.takeIf { it in 1..100_000_000L }
    }.toList()

    private fun nifsIn(line: String): List<String> =
        DIGITS.findAll(line.replace(NIF_SPACED, "$1$2$3")).map { it.value }.filter { it.length == 9 && AtQr.validNif(it) }.toList()

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    private fun validDate(y: Int, m: Int, d: Int, todayYmd: String): String {
        if (y < 2000 || m < 1 || m > 12 || d < 1) return ""
        if (d > YearMonth.of(y, m).lengthOfMonth()) return ""
        val s = "$y-${pad(m)}-${pad(d)}"
        return if (s <= todayYmd) s else ""
    }

    private fun dateIn(line: String, todayYmd: String): String {
        for (m in YMD.findAll(line)) {
            val s = validDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), todayYmd)
            if (s.isNotEmpty()) return s
        }
        for (m in DMY.findAll(line)) {
            val yy = m.groupValues[3]
            val y = if (yy.length == 2) 2000 + yy.toInt() else yy.toInt()
            val s = validDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt(), todayYmd)
            if (s.isNotEmpty()) return s
        }
        return ""
    }

    private fun timeIn(line: String): String {
        val m = TIME.find(line) ?: return ""
        return "${pad(m.groupValues[1].toInt())}:${m.groupValues[2]}"
    }

    private fun jsRound(v: Double): Long = floor(v + 0.5).toLong()

    private fun vatOfRateLine(line: String): Long {
        val r = RATE.find(line) ?: return 0
        val rate = r.groupValues[1].toInt()
        if (rate !in RATES) return 0
        val a = amounts(line)
        for (i in a.indices) {
            for (j in i + 1 until a.size) {
                if (abs(jsRound(a[i] * rate / 100.0) - a[j]) <= 2) return a[j]
            }
        }
        return 0
    }

    private fun merchantOf(raw: List<String>, folded: List<String>): String {
        for (i in 0 until min(6, raw.size)) {
            if (MERCHANT_SKIP.containsMatchIn(folded[i]) || amounts(folded[i]).isNotEmpty()) continue
            val letters = LETTER.findAll(raw[i]).count()
            val visible = raw[i].replace(WS1, "").length
            if (letters < 3 || letters < visible * 0.6) continue
            var s = raw[i].replace(LEAD, "").replace(TRAIL, "")
            if (s == s.uppercase()) s = s.lowercase().replace(WORD_START) { it.groupValues[1] + it.groupValues[2].uppercase() }
            return s.take(120)
        }
        return ""
    }

    fun parse(text: String?, today: LocalDate = LocalDate.now()): OcrFields {
        val todayYmd = "${today.year}-${pad(today.monthValue)}-${pad(today.dayOfMonth)}"
        val raw = mutableListOf<String>()
        val lines = mutableListOf<String>()
        for (l in (text ?: "").split(Regex("\r?\n"))) {
            val r = l.replace(WS, " ").trim()
            if (r.isEmpty()) continue
            raw += r
            lines += fold(r).replace("€", " ").split(' ').filter { it.isNotEmpty() }.joinToString(" ") { fixToken(it) }
        }
        if (lines.isEmpty()) return OcrFields()

        // NIFs: os da zona do cliente não são o do comerciante.
        val buyerZone = sortedSetOf<Int>()
        lines.forEachIndexed { i, l ->
            if (BUYER.containsMatchIn(l)) {
                buyerZone += i
                buyerZone += i + 1
            }
        }
        var merchantNif = ""
        val half = (lines.size + 1) / 2
        for (i in 0 until half) {
            if (merchantNif.isNotEmpty()) break
            if (i in buyerZone || !MERCHANT_LABEL.containsMatchIn(lines[i])) continue
            merchantNif = nifsIn(lines[i]).firstOrNull { it != FINAL_CONSUMER }.orEmpty()
        }
        for (i in lines.indices) {
            if (merchantNif.isNotEmpty()) break
            if (i !in buyerZone) merchantNif = nifsIn(lines[i]).firstOrNull { it != FINAL_CONSUMER }.orEmpty()
        }
        var buyerNif = ""
        for (i in buyerZone) {
            if (i >= lines.size || buyerNif.isNotEmpty()) continue
            buyerNif = nifsIn(lines[i]).firstOrNull { it != FINAL_CONSUMER && it != merchantNif }.orEmpty()
        }

        // Data (de preferência a linha com "DATA") e a hora da mesma linha.
        var date = ""
        var dateLine = -1
        for (pass in listOf(true, false)) {
            for (i in lines.indices) {
                if (date.isNotEmpty()) break
                if (pass && !lines[i].contains("DATA")) continue
                date = dateIn(lines[i], todayYmd)
                if (date.isNotEmpty()) dateLine = i
            }
        }
        var time = if (dateLine >= 0) timeIn(lines[dateLine]) else ""
        for (l in lines) {
            if (time.isNotEmpty()) break
            if (l.contains("HORA")) time = timeIn(l)
        }
        for (l in lines) {
            if (time.isNotEmpty()) break
            time = timeIn(l)
        }

        // Total: linhas TOTAL / A PAGAR (sem subtotais, IVA, troco…); o maior. Sem nenhuma: o maior valor.
        var total: Long? = null
        lines.forEachIndexed { i, l ->
            if (!TOTAL.containsMatchIn(l) || NOT_TOTAL.containsMatchIn(l)) return@forEachIndexed
            var v = amounts(l).lastOrNull()
            if (v == null && i + 1 < lines.size) v = amounts(lines[i + 1]).firstOrNull()
            if (v != null && (total == null || v > total!!)) total = v
        }
        if (total == null) {
            for (l in lines) {
                if (NOT_AMOUNT_LINE.containsMatchIn(l)) continue
                for (v in amounts(l)) if (total == null || v > total!!) total = v
            }
        }

        // IVA: linha "TOTAL IVA", senão a soma do quadro de taxas.
        var vat: Long? = null
        for (l in lines) {
            if (vat == null && VAT_LINE.containsMatchIn(l)) vat = amounts(l).lastOrNull()
        }
        if (vat == null) {
            val sum = lines.sumOf { vatOfRateLine(it) }
            if (sum > 0) vat = sum
        }
        val t = total
        val v = vat
        val vatOut = if (v != null && t != null && v <= t) v else null

        val atcud = lines.firstNotNullOfOrNull { ATCUD.find(it)?.groupValues?.get(1) }.orEmpty()
        val doc = lines.firstNotNullOfOrNull { DOC.find(it) }
        val merchant = merchantOf(raw, lines)

        val values = linkedMapOf<String, Any?>(
            "merchant" to merchant, "merchant_nif" to merchantNif, "buyer_nif" to buyerNif, "date" to date,
            "time" to time, "total" to t, "vat" to vatOut, "atcud" to atcud,
        )
        return OcrFields(
            merchant = merchant, merchantNif = merchantNif, buyerNif = buyerNif, date = date, time = time,
            total = t, vat = vatOut, atcud = atcud,
            docNo = doc?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }.orEmpty(),
            docType = doc?.groupValues?.get(1).orEmpty(),
            filled = values.filter { (_, x) -> x != null && x != "" }.keys.toList(),
        )
    }
}
