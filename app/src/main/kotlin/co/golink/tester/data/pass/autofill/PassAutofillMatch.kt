package co.golink.tester.data.pass.autofill

import co.golink.tester.data.pass.PassHealth
import co.golink.tester.data.pass.PassItem
import co.golink.tester.data.pass.obj
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A que logins corresponde o pedido de preenchimento. Lógica pura (testável na JVM).
 *  - Browser (webDomain): o host do item e o domínio da página têm de coincidir por
 *    sufixo de etiquetas ("accounts.google.com" ↔ "google.com"), nunca por substring.
 *  - App: o item lembra o pacote em login.apps (escolhido em "Outros logins…") →
 *    correspondência forte; senão, heurística pelo nome do pacote (com.loja.android ↔ loja.pt).
 */
object PassAutofillMatch {
    enum class Strength { Exact, Domain, Heuristic }

    data class Hit(val item: PassItem, val strength: Strength)

    /** Etiquetas que não identificam o serviço (nem no pacote nem no host). */
    private val NOISE = setOf(
        "com", "org", "net", "io", "co", "app", "apps", "android", "mobile", "www", "m", "login", "auth", "accounts", "account",
        "client", "prod", "release", "debug", "lite", "pt", "br", "es", "fr", "uk", "us", "de", "it", "eu",
    )

    fun appsOf(item: PassItem): List<String> =
        (item.payload.obj("login")?.get("apps") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    /** "accounts.google.com" ↔ "google.com": um é sufixo do outro em fronteira de etiqueta. */
    fun domainsMatch(a: String, b: String): Boolean {
        val x = a.lowercase().trimEnd('.').removePrefix("www.")
        val y = b.lowercase().trimEnd('.').removePrefix("www.")
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        // O lado curto tem de ter pelo menos "nome.tld" (evita casar só com "co.uk"/"com").
        if (short.count { it == '.' } < 1) return false
        val labels = short.split('.')
        if (labels.size == 2 && labels[0].length <= 3 && labels[1].length <= 3 && labels[0] in NOISE) return false
        return long.endsWith(".$short")
    }

    /** Etiqueta principal do host ("shop.example.co.uk" → "example"). */
    fun mainLabel(host: String): String? =
        host.split('.').filter { it.isNotEmpty() }.dropLast(1).filter { it !in NOISE }.lastOrNull()

    fun packageTokens(pkg: String): Set<String> =
        pkg.lowercase().split('.', '_', '-').filter { it.length >= 3 && it !in NOISE }.toSet()

    fun match(items: List<PassItem>, packageName: String?, webDomain: String?): List<Hit> {
        val logins = items.filter { it.type == "login" && !it.isTrashed && !it.locked && it.login != null }
        val hits = mutableListOf<Hit>()
        if (!webDomain.isNullOrBlank()) {
            for (i in logins) {
                val host = PassHealth.hostOf(i.login?.url)
                if (host.isEmpty()) continue
                if (host == webDomain.lowercase().removePrefix("www.")) hits += Hit(i, Strength.Exact)
                else if (domainsMatch(host, webDomain)) hits += Hit(i, Strength.Domain)
            }
        } else if (!packageName.isNullOrBlank()) {
            val tokens = packageTokens(packageName)
            for (i in logins) {
                if (packageName in appsOf(i)) { hits += Hit(i, Strength.Exact); continue }
                val host = PassHealth.hostOf(i.login?.url)
                val label = mainLabel(host)
                if (label != null && label.length >= 3 && label in tokens) hits += Hit(i, Strength.Heuristic)
            }
        }
        return hits.sortedWith(compareBy<Hit> { it.strength.ordinal }.thenByDescending { it.item.favourite }.thenBy { it.item.name.lowercase() })
    }
}
