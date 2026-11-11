package co.golink.tester.data.receipts

import kotlin.math.roundToLong

/**
 * Porte de services/receipts/atQr.js — QR das faturas portuguesas (Portaria 195/2020).
 * A NIF emitente · B NIF adquirente (999999990 = consumidor final) · C país · D tipo ·
 * F data AAAAMMDD · G n.º do documento · H ATCUD · I2 base isenta · I3/I4, I5/I6, I7/I8
 * base/IVA às taxas reduzida/intermédia/normal · N total de IVA · O total.
 */
object AtQr {
    private const val FINAL_CONSUMER = "999999990"

    data class Parsed(
        val merchantNif: String,
        val buyerNif: String,
        val buyerCountry: String,
        val docType: String,
        val date: String,
        val docNo: String,
        val atcud: String,
        val total: Long?,
        val vat: Long,
        val vatExempt: Boolean,
        val vatLines: List<VatLine>,
        /** Campos que o QR preencheu (marcados "QR" no formulário). */
        val filled: List<String>,
    )

    /** NIF português válido (dígito de controlo módulo 11). */
    fun validNif(nif: String?): Boolean {
        val s = (nif ?: "").filter { it.isDigit() }
        if (!Regex("^[1-9]\\d{8}$").matches(s)) return false
        var sum = 0
        for (i in 0 until 8) sum += (s[i] - '0') * (9 - i)
        val check = 11 - (sum % 11)
        return (if (check >= 10) 0 else check) == (s[8] - '0')
    }

    /** Number(String(v||'').replace(',', '.')) → cêntimos; "" conta como 0 (como no JS). */
    private fun cents(v: String?): Long? {
        val s = (v ?: "").replaceFirst(',', '.').trim()
        if (s.isEmpty()) return 0
        val n = s.toDoubleOrNull() ?: return null
        if (n.isNaN() || n.isInfinite()) return null
        return (n * 100).roundToLong()
    }

    private fun nz(v: Long?): Long = v ?: 0

    fun parse(text: String?): Parsed? {
        val raw = (text ?: "").trim()
        if (!Regex("^A:\\d{9}\\*").containsMatchIn(raw)) return null
        val f = HashMap<String, String>()
        for (part in raw.split('*')) {
            val i = part.indexOf(':')
            if (i > 0) f[part.substring(0, i)] = part.substring(i + 1)
        }
        if (f["A"].isNullOrEmpty() || f["F"].isNullOrEmpty() || f["O"].isNullOrEmpty()) return null

        val date = Regex("^(\\d{4})(\\d{2})(\\d{2})$").find(f["F"]!!)
        val buyer = f["B"]?.takeIf { it.isNotEmpty() && it != FINAL_CONSUMER }.orEmpty()
        val reduced = nz(cents(f["I4"]))
        val middle = nz(cents(f["I6"]))
        val normal = nz(cents(f["I8"]))
        val lines = mutableListOf<VatLine>()
        cents(f["I3"])?.takeIf { it != 0L }?.let { lines += VatLine("reduced", it, reduced) }
        cents(f["I5"])?.takeIf { it != 0L }?.let { lines += VatLine("intermediate", it, middle) }
        cents(f["I7"])?.takeIf { it != 0L }?.let { lines += VatLine("normal", it, normal) }
        val exempt = nz(cents(f["I2"]))
        val vat = if (f.containsKey("N")) nz(cents(f["N"])) else reduced + middle + normal
        val atcud = f["H"]?.takeIf { it.isNotEmpty() && it != "0" }.orEmpty()

        return Parsed(
            merchantNif = f["A"]!!,
            buyerNif = buyer,
            buyerCountry = f["C"]?.ifEmpty { null } ?: "PT",
            docType = f["D"].orEmpty(),
            date = date?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }.orEmpty(),
            docNo = f["G"].orEmpty(),
            atcud = atcud,
            total = cents(f["O"]),
            vat = vat,
            vatExempt = exempt > 0 && vat == 0L,
            vatLines = lines,
            filled = listOf("merchant_nif", "buyer_nif", "date", "total", "vat", "atcud").filter {
                when (it) {
                    "buyer_nif" -> buyer.isNotEmpty()
                    "atcud" -> atcud.isNotEmpty()
                    else -> true
                }
            },
        )
    }

    /** Taxa de IVA dominante (6/13/23), 0 sem linhas nem IVA, null se desconhecida. */
    fun mainVatRate(lines: List<VatLine>, vat: Long): Int? {
        if (lines.isEmpty()) return if (vat != 0L) null else 0
        val top = lines.sortedByDescending { it.base }.first()
        return when (top.rate) {
            "reduced" -> 6
            "intermediate" -> 13
            "normal" -> 23
            else -> null
        }
    }
}
