package co.golink.tester.data.receipts

import java.util.regex.Pattern

/** Porte de services/receipts/categories.js. */
object RcCategories {
    /** Categorias de despesa (ordem fixa) → bucket IRS por defeito. */
    val SPEND: List<Pair<String, String>> = listOf(
        "groceries" to "gerais",
        "restaurants" to "iva",
        "transport" to "gerais",
        "health" to "saude",
        "home" to "gerais",
        "education" to "educacao",
        "shopping" to "gerais",
    )
    val SPEND_KEYS: List<String> = SPEND.map { it.first }

    /** Ordem das linhas no IRS overview e nos chips. */
    val IRS: List<String> = listOf("gerais", "saude", "educacao", "rendas", "iva")
    val IRS_ALL: List<String> = IRS + "none"

    /** Bucket IRS → categoria de despesa cuja cor reaproveita (IRS_COLOR_VAR). */
    val IRS_COLOR_OF: Map<String, String> = mapOf(
        "gerais" to "groceries",
        "saude" to "health",
        "educacao" to "transport",
        "rendas" to "home",
        "iva" to "restaurants",
    )

    fun defaultIrsFor(spendKey: String?): String = SPEND.firstOrNull { it.first == spendKey }?.second ?: "gerais"

    data class Suggestion(val spend: String, val irs: String, val multi: Boolean, val source: String, val kind: String?)

    private class Rule(src: String, val spend: String, val irs: String, val kind: String, val multi: Boolean = false) {
        val re: Pattern = Pattern.compile(src, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }

    private val RULES = listOf(
        Rule("farm[aá]cia|pharmac|parafarm", "health", "saude", "pharmacy"),
        Rule("cl[ií]nica|dent|m[eé]dic|hospital|laborat|[oó]ptica|optic|fisio|psic|health|sa[uú]de", "health", "saude", "clinic", multi = true),
        Rule("livraria|bookshop|book|papelaria|escola|col[eé]gio|universid|school|creche|explica", "education", "educacao", "school", multi = true),
        Rule("senhorio|landlord|renda|rent\\b|arrendamento|condom[ií]nio", "home", "rendas", "rent"),
        Rule("electric|eletric|edp|endesa|iberdrola|galp energia|[aá]gua|water|g[aá]s natural|internet|meo|nos\\b|vodafone|digi\\b", "home", "gerais", "bills"),
        Rule("oficina|repara[cç][aã]o|mec[aâ]nic|car repair|garage|pneu|tyre", "transport", "iva", "car_repair", multi = true),
        Rule("combust|fuel|galp|bp\\b|repsol|cepsa|prio|shell|esso|gas station|bomba", "transport", "gerais", "fuel"),
        Rule("cp\\b|comboio|metro|carris|navegante|uber|bolt|t[aá]xi|transport|portagem|via verde|parking|estacion", "transport", "gerais", "transport"),
        Rule("restaur|caf[eé]|pastelar|snack|tasca|pizz|sushi|bar\\b|cervejar|churrasq|hamburg|padaria.*caf", "restaurants", "iva", "restaurant"),
        Rule("cabeleir|barbear|hair|beleza|est[eé]tica|veterin|vet\\b|gin[aá]sio|gym|fitness", "shopping", "iva", "vat_service"),
        Rule("supermerc|hipermerc|mercado|market|continente|pingo doce|lidl|aldi|auchan|intermarch|minipre|mercadona|talho|butcher|padaria|bakery|frutaria|peixaria", "groceries", "gerais", "grocery"),
    )

    /** 1.º o que o utilizador escolheu antes para o mesmo NIF; depois palavras-chave do nome. */
    fun suggest(merchant: String?, merchantNif: String?, history: List<Receipt>): Suggestion? {
        if (!merchantNif.isNullOrEmpty()) {
            history.firstOrNull { it.merchantNif == merchantNif && !it.locked }?.let {
                return Suggestion(it.spendCat, it.irsCat, it.multiActivity, "history", null)
            }
        }
        val name = merchant.orEmpty()
        val rule = RULES.firstOrNull { it.re.matcher(name).find() } ?: return null
        return Suggestion(rule.spend, rule.irs, rule.multi, "name", rule.kind)
    }

    /** Nome do comerciante a partir de recibos anteriores com o mesmo NIF. */
    fun merchantFor(nif: String?, history: List<Receipt>): String? {
        if (nif.isNullOrEmpty()) return null
        return history.firstOrNull { it.merchantNif == nif && !it.locked && it.merchant.isNotBlank() }?.merchant
    }

    /** Comerciante → inicial do avatar. */
    fun initialOf(name: String?): String {
        val clean = (name ?: "").replace(Regex("^[\\s\\[\\](\"']+"), "")
        return if (clean.isEmpty()) "?" else clean.substring(0, clean.offsetByCodePoints(0, 1)).uppercase()
    }
}
