package co.golink.tester.data.pass

import java.net.URI
import java.net.URLDecoder
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

    fun clearCache() = synchronized(memo) { memo.clear() }

    private fun parseRaw(raw: String): TotpConfig? {
        if (raw.startsWith("otpauth://", ignoreCase = true)) {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (!uri.host.equals("totp", ignoreCase = true)) return null
            val params = (uri.rawQuery ?: "").split('&').filter { it.isNotEmpty() }.associate {
                val i = it.indexOf('=')
                val k = if (i < 0) it else it.substring(0, i)
                val v = if (i < 0) "" else it.substring(i + 1)
                decQuery(k) to decQuery(v)
            }
            val label = dec((uri.rawPath ?: "").removePrefix("/"))
            val i = label.indexOf(':')
            val labelIssuer = if (i >= 0) label.substring(0, i) else ""
            val account = if (i >= 0) label.substring(i + 1) else label
            val cfg = TotpConfig(
                secret = (params["secret"] ?: "").uppercase().replace(Regex("[\\s=]"), ""),
                digits = (params["digits"]?.ifEmpty { null } ?: "6").toIntOrNull() ?: -1,
                period = (params["period"]?.ifEmpty { null } ?: "30").toIntOrNull() ?: -1,
                algorithm = (params["algorithm"]?.ifEmpty { null } ?: "SHA1").uppercase(),
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
        val label = if (issuer.isNotEmpty()) "${issuer.replace(':', ' ')}:$account" else account
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

    /** decodeURIComponent (o "+" fica "+"). */
    private fun dec(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    /** URLSearchParams (o "+" é espaço). */
    private fun decQuery(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun valid(cfg: TotpConfig): Boolean {
        val ok = runCatching { base32Decode(cfg.secret).size >= 10 }.getOrDefault(false)
        return ok && cfg.digits in 6..8 && cfg.period in 10..120 && cfg.algorithm in ALGS
    }

    fun hotp(cfg: TotpConfig, counter: Long): String {
        val mac = Mac.getInstance(ALGS.getValue(cfg.algorithm))
        mac.init(SecretKeySpec(base32Decode(cfg.secret), "RAW"))
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val h = mac.doFinal(msg)
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
