package co.golink.tester.data.pass

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Configuração TOTP normalizada (igual a parseTotp de resources/js/services/pass/totp.js). */
data class TotpConfig(
    val secret: String,
    val digits: Int,
    val period: Int,
    val algorithm: String,
    val issuer: String,
    val account: String,
)

data class TotpNow(val code: String, val next: String, val remaining: Int, val period: Int)

/** TOTP (RFC 6238) local: o segredo nunca sai do aparelho. Espelho de totp.js. */
object Totp {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val ALGS = mapOf("SHA1" to "HmacSHA1", "SHA256" to "HmacSHA256", "SHA512" to "HmacSHA512")
    private val OTPAUTH = Regex("^otpauth://([^/?#]*)/?([^?#]*)(?:\\?([^#]*))?", RegexOption.IGNORE_CASE)
    private val PARSE_INT = Regex("^\\s*([+-]?\\d+)")
    private val macs = HashMap<String, Mac>()
    private val memo = object : LinkedHashMap<String, TotpConfig?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TotpConfig?>?) = size > 2000
    }

    fun base32Decode(input: String): ByteArray {
        val clean = input.uppercase().replace(Regex("[\\s-]"), "").trimEnd('=')
        require(clean.isNotEmpty() && clean.all { it in B32 }) { "invalid base32" }
        var bits = 0
        var value = 0
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        for (ch in clean) {
            value = (value shl 5) or B32.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                out.add(((value ushr (bits - 8)) and 0xff).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun parse(input: String?): TotpConfig? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return null
        synchronized(memo) { if (memo.containsKey(raw)) return memo[raw] }
        val cfg = parseRaw(raw)
        synchronized(memo) { memo[raw] = cfg }
        return cfg
    }

    /** Ao trancar o cofre: esquece as configs e as chaves HMAC já preparadas. */
    fun clearCache() {
        synchronized(memo) { memo.clear() }
        synchronized(macs) { macs.clear() }
    }

    private fun parseRaw(raw: String): TotpConfig? {
        if (raw.startsWith("otpauth://", ignoreCase = true)) {
            // À mão e não com java.net.URI (como a web): o URI rejeitaria o que muitos QR
            // trazem (espaços, '|', '%' solto…).
            val m = OTPAUTH.find(raw) ?: return null
            if (!m.groupValues[1].equals("totp", ignoreCase = true)) return null
            // URLSearchParams: '&' e '=', '+' é espaço; nos repetidos fica o 1.º (como p.get()).
            val params = HashMap<String, String>()
            m.groupValues[3].split('&').filter { it.isNotEmpty() }.forEach {
                val i = it.indexOf('=')
                val k = pctDecode(if (i < 0) it else it.substring(0, i), form = true)!!
                val v = pctDecode(if (i < 0) "" else it.substring(i + 1), form = true)!!
                if (k !in params) params[k] = v
            }
            // decodeURIComponent; '%' inválido → fica o rótulo em bruto.
            val label = pctDecode(m.groupValues[2], form = false) ?: m.groupValues[2]
            val i = label.indexOf(':')
            val labelIssuer = if (i >= 0) label.substring(0, i) else ""
            val account = if (i >= 0) label.substring(i + 1) else label
            val cfg = TotpConfig(
                secret = (params["secret"] ?: "").uppercase().replace(Regex("[\\s=-]"), ""),
                digits = parseIntJs(params["digits"]?.ifEmpty { null } ?: "6"),
                period = parseIntJs(params["period"]?.ifEmpty { null } ?: "30"),
                algorithm = (params["algorithm"]?.ifEmpty { null } ?: "SHA1").uppercase().replace("-", ""),
                issuer = params["issuer"]?.ifEmpty { null } ?: labelIssuer,
                account = account.trim(),
            )
            return cfg.takeIf(::valid)
        }
        val cfg = TotpConfig(raw.uppercase().replace(Regex("[\\s-]"), "").trimEnd('='), 6, 30, "SHA1", "", "")
        return cfg.takeIf(::valid)
    }

    /**
     * Inverso de [parse] (espelho de buildOtpauth em totp.js): com tudo por defeito
     * devolve só a base32; caso contrário um otpauth:// com os parâmetros.
     */
    fun build(secret: String, issuer: String = "", account: String = "", algorithm: String = "SHA1", digits: Int = 6, period: Int = 30): String {
        val key = secret.uppercase().replace(Regex("[\\s-]"), "").trimEnd('=')
        val alg = algorithm.uppercase()
        if (alg == "SHA1" && digits == 6 && period == 30) return key
        // ':' no emissor partiria o rótulo "Emissor:conta"; o parâmetro issuer mantém o original.
        // Sem emissor, um ':' na conta seria lido como "Emissor:conta": o ':' inicial marca emissor vazio (= web).
        val label = when {
            issuer.isNotEmpty() -> "${issuer.replace(':', ' ')}:$account"
            account.contains(':') -> ":$account"
            else -> account
        }
        val q = buildList {
            add("secret=" + enc(key))
            if (issuer.isNotEmpty()) add("issuer=" + enc(issuer))
            add("algorithm=$alg")
            add("digits=$digits")
            add("period=$period")
        }.joinToString("&")
        return "otpauth://totp/${enc(label)}?$q"
    }

    /** encodeURIComponent (espaço → %20). */
    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** parseInt(s, 10) do JS: inteiro no início ("6abc" → 6, "30.5" → 30); sem número → -1 (inválido). */
    private fun parseIntJs(s: String): Int = PARSE_INT.find(s)?.groupValues?.get(1)?.toIntOrNull() ?: -1

    /**
     * %XX → bytes UTF-8. form = URLSearchParams ('+' é espaço, '%' solto fica como está);
     * senão decodeURIComponent ('+' fica '+'; '%' ou UTF-8 inválidos → null).
     */
    private fun pctDecode(s: String, form: Boolean): String? {
        val src = s.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream(src.size)
        var i = 0
        while (i < src.size) {
            val b = src[i].toInt()
            if (b == '%'.code) {
                val hi = if (i + 2 < src.size) Character.digit(src[i + 1].toInt(), 16) else -1
                val lo = if (hi >= 0) Character.digit(src[i + 2].toInt(), 16) else -1
                if (lo >= 0) { out.write(hi * 16 + lo); i += 3; continue }
                if (!form) return null
            }
            out.write(if (form && b == '+'.code) ' '.code else b)
            i++
        }
        val bytes = out.toByteArray()
        if (form) return String(bytes, Charsets.UTF_8)
        return runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    }

    private fun valid(cfg: TotpConfig): Boolean {
        val ok = runCatching { base32Decode(cfg.secret).size >= 10 }.getOrDefault(false)
        return ok && cfg.digits in 6..8 && cfg.period in 10..120 && cfg.algorithm in ALGS
    }

    /** Mac já iniciado por (algoritmo, segredo), como o keyCache da web. O Mac não é thread-safe: usar com synchronized. */
    private fun macFor(cfg: TotpConfig): Mac {
        val id = "${cfg.algorithm}:${cfg.secret}"
        synchronized(macs) {
            macs[id]?.let { return it }
            // Acima do máximo de itens do cofre (5000) só por segredos que já não existem.
            if (macs.size > 5000) macs.clear()
            val mac = Mac.getInstance(ALGS.getValue(cfg.algorithm))
            mac.init(SecretKeySpec(base32Decode(cfg.secret), "RAW"))
            macs[id] = mac
            return mac
        }
    }

    fun hotp(cfg: TotpConfig, counter: Long): String {
        val mac = macFor(cfg)
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val h = synchronized(mac) { mac.doFinal(msg) }
        val o = h[h.size - 1].toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        var mod = 1L
        repeat(cfg.digits) { mod *= 10 }
        return (bin % mod).toString().padStart(cfg.digits, '0')
    }

    fun now(cfg: TotpConfig, nowMs: Long = System.currentTimeMillis()): TotpNow {
        val step = nowMs / 1000 / cfg.period
        val remaining = cfg.period - ((nowMs / 1000) % cfg.period).toInt()
        return TotpNow(hotp(cfg, step), hotp(cfg, step + 1), remaining, cfg.period)
    }

    /** "482913" → "482 913" */
    fun format(code: String): String {
        if (code.isEmpty()) return ""
        val h = (code.length + 1) / 2
        return if (code.length >= 6) code.substring(0, h) + " " + code.substring(h) else code
    }
}
